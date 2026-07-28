package com.farmflate.service.community;

import com.farmflate.domain.community.CommunityCommentEntity;
import com.farmflate.domain.community.CommunityCommentRepository;
import com.farmflate.domain.community.CommunityLikeEntity;
import com.farmflate.domain.community.CommunityLikeRepository;
import com.farmflate.domain.community.CommunityPostEntity;
import com.farmflate.domain.community.CommunityPostRepository;
import com.farmflate.domain.community.CommunitySaveEntity;
import com.farmflate.domain.community.CommunitySaveRepository;
import com.farmflate.domain.region.RegionAnalysisEntity;
import com.farmflate.domain.region.RegionAnalysisRepository;
import com.farmflate.domain.user.UserRepository;
import com.farmflate.dto.community.AddCommentRequestDto;
import com.farmflate.dto.community.CommunityPostResponseDto;
import com.farmflate.dto.community.CreateCommunityPostRequestDto;
import com.farmflate.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Owns all community business rules the controller used to inline: per-user
 * like/save state, server-derived author/region snapshots, and comment
 * ordering. The controller is left doing only auth + HTTP status mapping.
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final CommunityPostRepository postRepository;
    private final CommunityCommentRepository commentRepository;
    private final CommunityLikeRepository likeRepository;
    private final CommunitySaveRepository saveRepository;
    private final CommunityAttachmentService attachmentService;
    private final RegionAnalysisRepository regionAnalysisRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CommunityPostResponseDto> listPosts(String viewerEmail) {
        List<CommunityPostEntity> posts = postRepository.findAllByOrderByCreatedAtDesc();
        List<Long> ids = posts.stream().map(CommunityPostEntity::getId).toList();

        Set<Long> likedIds = viewerEmail == null ? Set.of() : likeRepository.findByUserEmailAndPostIdIn(viewerEmail, ids)
                .stream().map(CommunityLikeEntity::getPostId).collect(Collectors.toSet());
        Set<Long> savedIds = viewerEmail == null ? Set.of() : saveRepository.findByUserEmail(viewerEmail)
                .stream().map(CommunitySaveEntity::getPostId).collect(Collectors.toSet());

        return posts.stream()
                .map(post -> toDto(post, likedIds.contains(post.getId()), savedIds.contains(post.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityPostResponseDto getPost(String viewerEmail, Long postId) {
        CommunityPostEntity post = requirePost(postId);
        boolean liked = viewerEmail != null && likeRepository.existsByPostIdAndUserEmail(postId, viewerEmail);
        boolean saved = viewerEmail != null && saveRepository.existsByUserEmailAndPostId(viewerEmail, postId);
        return toDto(post, liked, saved);
    }

    @Transactional
    public CommunityPostResponseDto createPost(String authorEmail, CreateCommunityPostRequestDto request) {
        String authorName = resolveNickname(authorEmail);
        RegionAnalysisEntity latest = regionAnalysisRepository
                .findFirstByUserEmailAndPurposeAndReportStatusInOrderByAnalyzedAtDesc(authorEmail, "PRIMARY", List.of("COMPLETED", "PARTIAL"))
                .orElse(null);
        String regionLabel = latest == null ? "지역 정보 없음"
                : ((latest.getSidoName() == null ? "" : latest.getSidoName()) + " " + (latest.getSigunguName() == null ? "" : latest.getSigunguName())).trim();

        CommunityPostEntity post = CommunityPostEntity.builder()
                .category("").tagLocation(regionLabel)
                .title(request.getTitle().trim()).content(request.getContent().trim())
                .author(authorName).authorEmail(authorEmail).imageUrl("")
                .likeCount(0).commentCount(0)
                .regionAnalysisId(latest == null ? null : latest.getId()).regionLabel(regionLabel)
                .build();
        CommunityPostEntity saved = postRepository.save(post);
        attachmentService.linkToPost(authorEmail, saved.getId(), request.getAttachmentIds());
        return toDto(saved, false, false);
    }

    @Transactional
    public CommunityPostResponseDto like(String email, Long postId) {
        requirePost(postId);
        if (!likeRepository.existsByPostIdAndUserEmail(postId, email)) {
            try {
                likeRepository.save(CommunityLikeEntity.builder().postId(postId).userEmail(email).build());
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent duplicate PUT lost the race; the row already exists, which is the desired end state.
            }
        }
        return getPost(email, postId);
    }

    @Transactional
    public CommunityPostResponseDto unlike(String email, Long postId) {
        requirePost(postId);
        likeRepository.deleteByPostIdAndUserEmail(postId, email);
        return getPost(email, postId);
    }

    @Transactional
    public Map<String, Object> toggleSave(String email, Long postId) {
        requirePost(postId);
        boolean nowSaved;
        if (saveRepository.existsByUserEmailAndPostId(email, postId)) {
            saveRepository.deleteByUserEmailAndPostId(email, postId);
            nowSaved = false;
        } else {
            saveRepository.save(CommunitySaveEntity.builder().userEmail(email).postId(postId).build());
            nowSaved = true;
        }
        return Map.of("postId", postId, "isSaved", nowSaved);
    }

    @Transactional
    public CommunityPostResponseDto addComment(String email, Long postId, AddCommentRequestDto request) {
        CommunityPostEntity post = requirePost(postId);
        String authorName = resolveNickname(email);
        CommunityCommentEntity comment = CommunityCommentEntity.builder()
                .post(post).author(authorName).authorEmail(email).content(request.getContent().trim()).build();
        // Saved directly through its own repository rather than via post.getComments().add(...):
        // mutating a mapped, orphanRemoval=true collection and relying on cascade-persist triggers
        // a Hibernate TransientObjectException the moment any other query in this transaction
        // forces an auto-flush before the cascade has run.
        commentRepository.save(comment);
        boolean liked = likeRepository.existsByPostIdAndUserEmail(postId, email);
        boolean savedFlag = saveRepository.existsByUserEmailAndPostId(email, postId);
        return toDto(post, liked, savedFlag);
    }

    @Transactional
    public void deletePost(String email, Long postId) {
        CommunityPostEntity post = requirePost(postId);
        if (post.getAuthorEmail() != null && !post.getAuthorEmail().isBlank() && !post.getAuthorEmail().equals(email)) {
            throw ApiException.forbidden("COMMUNITY_POST_FORBIDDEN", "본인이 작성한 게시글만 삭제할 수 있습니다.");
        }
        postRepository.delete(post);
    }

    private CommunityPostEntity requirePost(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("COMMUNITY_POST_NOT_FOUND", "게시글을 찾을 수 없습니다: " + id));
    }

    private String resolveNickname(String email) {
        return userRepository.findByEmail(email)
                .map(user -> user.getNickname() != null && !user.getNickname().isBlank() ? user.getNickname() : "사용자")
                .orElse("사용자");
    }

    private CommunityPostResponseDto toDto(CommunityPostEntity post, boolean liked, boolean saved) {
        long likeCount = likeRepository.countByPostId(post.getId());
        String regionLabel = post.getRegionLabel() != null ? post.getRegionLabel()
                : (post.getTagLocation() != null && !post.getTagLocation().isBlank() ? post.getTagLocation() : "지역 정보 없음");

        List<CommunityPostResponseDto.CommentResponseDto> comments = commentRepository
                .findByPostIdOrderByCreatedAtAsc(post.getId()).stream()
                        .map(comment -> CommunityPostResponseDto.CommentResponseDto.builder()
                                .id(String.valueOf(comment.getId()))
                                .author(CommunityPostResponseDto.AuthorDto.builder()
                                        .displayName(comment.getAuthor() != null ? comment.getAuthor() : "사용자")
                                        .profileType("DEFAULT").build())
                                .content(comment.getContent())
                                .timeAgo(formatTimeAgo(comment.getCreatedAt()))
                                .createdAt(comment.getCreatedAt())
                                .build())
                        .toList();

        return CommunityPostResponseDto.builder()
                .id(String.valueOf(post.getId()))
                .regionLabel(regionLabel)
                .title(post.getTitle())
                .content(post.getContent())
                .author(CommunityPostResponseDto.AuthorDto.builder()
                        .displayName(post.getAuthor() != null ? post.getAuthor() : "사용자")
                        .profileType("DEFAULT").build())
                .likeCount((int) likeCount)
                .likedByMe(liked)
                .savedByMe(saved)
                .commentCount(comments.size())
                .attachments(attachmentService.findForPost(post.getId()).stream().map(attachmentService::toDto).toList())
                .comments(comments)
                .timeAgo(formatTimeAgo(post.getCreatedAt()))
                .createdAt(post.getCreatedAt())
                .build();
    }

    private String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "시간 정보 없음";
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        Duration duration = Duration.between(dateTime, now);

        long minutes = duration.toMinutes();
        if (minutes < 1) return "방금 전";
        if (minutes < 60) return minutes + "분 전";
        long hours = duration.toHours();
        if (hours < 24) return hours + "시간 전";
        long days = duration.toDays();
        if (days < 7) return days + "일 전";
        if (days < 30) return (days / 7) + "주 전";
        if (days < 365) return (days / 30) + "개월 전";
        return (days / 365) + "년 전";
    }
}
