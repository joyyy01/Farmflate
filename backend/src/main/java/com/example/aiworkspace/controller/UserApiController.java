package com.example.aiworkspace.controller;

import com.example.aiworkspace.domain.user.InquiryEntity;
import com.example.aiworkspace.domain.user.InquiryRepository;
import com.example.aiworkspace.domain.user.User;
import com.example.aiworkspace.domain.user.UserRepository;
import com.example.aiworkspace.security.UserPrincipal;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";
        
        User user = userRepository.findByEmail(email)
                .orElse(User.builder()
                        .email(email)
                        .nickname("사용자님")
                        .provider("kakao")
                        .providerId("local_dev_id")
                        .build());

        UserProfileResponse response = new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getRole() != null ? user.getRole().name() : "USER"
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/inquiries")
    public ResponseEntity<Map<String, Object>> createInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody InquiryRequestDto request) {

        String email = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";

        InquiryEntity inquiry = InquiryEntity.builder()
                .userEmail(email)
                .inquiryText(request.getInquiryText())
                .category(request.getCategory() != null ? request.getCategory() : "GENERAL")
                .status("PENDING")
                .build();

        InquiryEntity saved = inquiryRepository.save(inquiry);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "SUCCESS");
        result.put("inquiryId", saved.getId().toString());
        result.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : "");

        return ResponseEntity.ok(result);
    }

    @GetMapping("/inquiries")
    public ResponseEntity<List<InquiryEntity>> getUserInquiries(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = userPrincipal != null ? userPrincipal.getEmail() : "user@farmflate.com";
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
}
