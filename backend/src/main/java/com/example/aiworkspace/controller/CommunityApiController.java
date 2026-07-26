package com.example.aiworkspace.controller;

import com.example.aiworkspace.dto.community.AddCommentRequestDto;
import com.example.aiworkspace.dto.community.CommunityPostResponseDto;
import com.example.aiworkspace.dto.community.CreateCommunityPostRequestDto;
import com.example.aiworkspace.security.UserPrincipal;
import com.example.aiworkspace.service.community.CommunityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Thin HTTP boundary: auth + validation only, business rules live in CommunityService. */
@RestController
@RequestMapping("/api/community")
@RequiredArgsConstructor
public class CommunityApiController {

    private final CommunityService communityService;

    @GetMapping("/posts")
    public ResponseEntity<List<CommunityPostResponseDto>> getPosts(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(communityService.listPosts(optionalEmail(principal)));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<CommunityPostResponseDto> getPost(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(communityService.getPost(optionalEmail(principal), id));
    }

    @PostMapping("/posts")
    public ResponseEntity<CommunityPostResponseDto> createPost(
            @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody CreateCommunityPostRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(communityService.createPost(requireEmail(principal), request));
    }

    @PutMapping("/posts/{id}/like")
    public ResponseEntity<CommunityPostResponseDto> like(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(communityService.like(requireEmail(principal), id));
    }

    @DeleteMapping("/posts/{id}/like")
    public ResponseEntity<CommunityPostResponseDto> unlike(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(communityService.unlike(requireEmail(principal), id));
    }

    @PostMapping("/posts/{id}/save")
    public ResponseEntity<Map<String, Object>> toggleSave(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        return ResponseEntity.ok(communityService.toggleSave(requireEmail(principal), id));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<CommunityPostResponseDto> addComment(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id,
            @Valid @RequestBody AddCommentRequestDto request) {
        return ResponseEntity.ok(communityService.addComment(requireEmail(principal), id, request));
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        communityService.deletePost(requireEmail(principal), id);
        return ResponseEntity.noContent().build();
    }

    private String requireEmail(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.getEmail();
    }

    private String optionalEmail(UserPrincipal principal) {
        return principal == null ? null : principal.getEmail();
    }
}
