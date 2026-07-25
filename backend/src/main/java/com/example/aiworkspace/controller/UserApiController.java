package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.InquiryEntity;
import com.example.aiworkspace.domain.user.InquiryRepository;
import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;

    private String requireEmail(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.getEmail();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = requireEmail(userPrincipal);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        String displayName = (user.getNickname() != null && !user.getNickname().isBlank()) ? user.getNickname() : "사용자님";
        String provider = user.getProvider() != null ? user.getProvider() : "kakao";
        String role = user.getRole() != null ? user.getRole().name() : "USER";

        return ResponseEntity.ok(new UserProfileResponse(user.getEmail(), displayName, provider, role));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody UpdateProfileRequestDto request) {

        String email = requireEmail(userPrincipal);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.update(request.getNickname());
            userRepository.save(user);
        }

        String displayName = (user.getNickname() != null && !user.getNickname().isBlank()) ? user.getNickname() : "사용자님";
        String provider = user.getProvider() != null ? user.getProvider() : "kakao";
        String role = user.getRole() != null ? user.getRole().name() : "USER";

        return ResponseEntity.ok(new UserProfileResponse(user.getEmail(), displayName, provider, role));
    }

    @PostMapping("/inquiries")
    public ResponseEntity<Map<String, Object>> createInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody InquiryRequestDto request) {

        String email = requireEmail(userPrincipal);

        InquiryEntity inquiry = InquiryEntity.builder()
                .userEmail(email)
                .inquiryText(request.getInquiryText() != null ? request.getInquiryText() : "")
                .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
                .status("PENDING")
                .build();

        InquiryEntity saved = inquiryRepository.save(inquiry);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("inquiryId", saved.getId() != null ? saved.getId().toString() : "inq_1");
        result.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : "");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/inquiries")
    public ResponseEntity<List<InquiryEntity>> getUserInquiries(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = requireEmail(userPrincipal);
        List<InquiryEntity> list = inquiryRepository.findByUserEmailOrderByCreatedAtDesc(email);
        return ResponseEntity.ok(list);
    }

    @Data
    public static class UserProfileResponse {
        private final String email;
        private final String displayName;
        private final String provider;
        private final String role;
    }

    @Data
    public static class InquiryRequestDto {
        private String inquiryText;
        private String category;
    }

    @Data
    public static class UpdateProfileRequestDto {
        private String nickname;
    }
}
