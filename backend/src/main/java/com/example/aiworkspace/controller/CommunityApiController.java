package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.community.CommunityCommentEntity;
import com.example.aiworkspace.domain.community.CommunityPostEntity;
import com.example.aiworkspace.domain.community.CommunityPostRepository;
import com.example.aiworkspace.security.UserPrincipal;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityApiController {

    private final CommunityPostRepository communityPostRepository;

    @GetMapping("/posts")
    public ResponseEntity<List<PostResponse>> getPosts(@RequestParam(required = false) String category) {
        List<CommunityPostEntity> list = category != null && !category.isBlank()
                ? communityPostRepository.findByCategoryOrderByCreatedAtDesc(category)
                : communityPostRepository.findAllByOrderByCreatedAtDesc();
        List<PostResponse> responses = list.stream().map(this::toPostResponse).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/posts")
    public ResponseEntity<PostResponse> createPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CreatePostRequest request) {

        String authorEmail = userPrincipal != null && userPrincipal.getEmail() != null ? userPrincipal.getEmail() : "user@farmflate.com";

        CommunityPostEntity post = CommunityPostEntity.builder()
                .category(request.getCategory() != null ? request.getCategory() : "농가 노하우")
                .tagLocation(request.getTagLocation() != null ? request.getTagLocation() : "전북 고창군")
                .title(request.getTitle() != null ? request.getTitle() : "농가 소식")
                .content(request.getContent() != null ? request.getContent() : "")
                .author(request.getAuthor() != null && !request.getAuthor().isBlank() ? request.getAuthor() : "초보농부")
                .authorEmail(authorEmail)
                .imageUrl(request.getImageUrl() != null ? request.getImageUrl() : "")
                .likeCount(0)
                .commentCount(0)
                .build();

        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(toPostResponse(saved));
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<PostResponse> toggleLike(@PathVariable Long id) {
        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        
        post.incrementLike();
        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(toPostResponse(saved));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<PostResponse> addComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestBody AddCommentRequest request) {

        String authorEmail = userPrincipal != null && userPrincipal.getEmail() != null ? userPrincipal.getEmail() : "user@farmflate.com";
        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));

        CommunityCommentEntity comment = CommunityCommentEntity.builder()
                .post(post)
                .author(request.getAuthor() != null && !request.getAuthor().isBlank() ? request.getAuthor() : "사용자")
                .authorEmail(authorEmail)
                .content(request.getContent() != null ? request.getContent() : "")
                .build();

        post.addComment(comment);
        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(toPostResponse(saved));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable Long id) {
        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        return ResponseEntity.ok(toPostResponse(post));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> deletePost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id) {

        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));

        String email = userPrincipal != null && userPrincipal.getEmail() != null ? userPrincipal.getEmail() : "";
        // Only author or anyone in dev mode can delete
        if (post.getAuthorEmail() != null && !post.getAuthorEmail().isBlank() && !post.getAuthorEmail().equals(email)) {
            Map<String, Object> err = new HashMap<>();
            err.put("status", "FORBIDDEN");
            err.put("message", "본인이 작성한 게시글만 삭제할 수 있습니다.");
            return ResponseEntity.status(403).body(err);
        }

        communityPostRepository.delete(post);
        Map<String, Object> result = new HashMap<>();
        result.put("status", "DELETED");
        result.put("postId", id);
        return ResponseEntity.ok(result);
    }

    // ─── DTO response (Jackson-safe, no circular refs) ───

    private PostResponse toPostResponse(CommunityPostEntity entity) {
        PostResponse r = new PostResponse();
        r.id = entity.getId();
        r.category = entity.getCategory();
        r.tagLocation = entity.getTagLocation();
        r.title = entity.getTitle();
        r.content = entity.getContent();
        r.author = entity.getAuthor();
        r.authorEmail = entity.getAuthorEmail();
        r.imageUrl = entity.getImageUrl();
        r.likeCount = entity.getLikeCount();
        r.commentCount = entity.getCommentCount();
        r.createdAt = entity.getCreatedAt();
        r.updatedAt = entity.getUpdatedAt();
        r.timeAgo = formatTimeAgo(entity.getCreatedAt());

        if (entity.getComments() != null) {
            r.comments = entity.getComments().stream().map(c -> {
                CommentResponse cr = new CommentResponse();
                cr.id = String.valueOf(c.getId());
                cr.author = c.getAuthor();
                cr.authorEmail = c.getAuthorEmail();
                cr.content = c.getContent();
                cr.timeAgo = formatTimeAgo(c.getCreatedAt());
                cr.createdAt = c.getCreatedAt();
                return cr;
            }).collect(Collectors.toList());
        } else {
            r.comments = Collections.emptyList();
        }

        return r;
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

    // ─── Request / Response DTOs ───

    @Data
    public static class CreatePostRequest {
        private String category;
        private String tagLocation;
        private String title;
        private String content;
        private String author;
        private String imageUrl;
    }

    @Data
    public static class AddCommentRequest {
        private String author;
        private String content;
    }

    @Data
    public static class PostResponse {
        private Long id;
        private String category;
        private String tagLocation;
        private String title;
        private String content;
        private String author;
        private String authorEmail;
        private String imageUrl;
        private int likeCount;
        private int commentCount;
        private String timeAgo;
        private List<CommentResponse> comments;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime updatedAt;
    }

    @Data
    public static class CommentResponse {
        private String id;
        private String author;
        private String authorEmail;
        private String content;
        private String timeAgo;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
