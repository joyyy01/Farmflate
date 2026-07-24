package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.community.CommunityCommentEntity;
import com.example.aiworkspace.domain.community.CommunityPostEntity;
import com.example.aiworkspace.domain.community.CommunityPostRepository;
import com.example.aiworkspace.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityApiController {

    private final CommunityPostRepository communityPostRepository;

    @GetMapping("/posts")
    public ResponseEntity<List<CommunityPostEntity>> getPosts(@RequestParam(required = false) String category) {
        List<CommunityPostEntity> list = category != null && !category.isBlank()
                ? communityPostRepository.findByCategoryOrderByCreatedAtDesc(category)
                : communityPostRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(list);
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPostEntity> createPost(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody CreatePostRequest request) {

        String authorEmail = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";

        CommunityPostEntity post = CommunityPostEntity.builder()
                .category(request.getCategory())
                .tagLocation(request.getTagLocation())
                .title(request.getTitle())
                .content(request.getContent())
                .author(request.getAuthor() != null ? request.getAuthor() : "초보농부")
                .authorEmail(authorEmail)
                .imageUrl(request.getImageUrl())
                .likeCount(0)
                .commentCount(0)
                .build();

        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<CommunityPostEntity> toggleLike(@PathVariable Long id) {
        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
        
        post.incrementLike();
        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityPostEntity> addComment(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable Long id,
            @RequestBody AddCommentRequest request) {

        String authorEmail = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";
        CommunityPostEntity post = communityPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));

        CommunityCommentEntity comment = CommunityCommentEntity.builder()
                .post(post)
                .author(request.getAuthor() != null ? request.getAuthor() : "사용자")
                .authorEmail(authorEmail)
                .content(request.getContent())
                .build();

        post.addComment(comment);
        CommunityPostEntity saved = communityPostRepository.save(post);
        return ResponseEntity.ok(saved);
    }

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
}
