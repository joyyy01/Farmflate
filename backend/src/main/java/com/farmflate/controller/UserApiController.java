package com.farmflate.controller;

import com.farmflate.dto.user.CreateInquiryRequestDto;
import com.farmflate.dto.user.InquiryCreatedResponseDto;
import com.farmflate.dto.user.InquiryResponseDto;
import com.farmflate.dto.user.UpdateUserProfileRequestDto;
import com.farmflate.dto.user.UserProfileResponseDto;
import com.farmflate.security.UserPrincipal;
import com.farmflate.service.user.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserProfileService userProfileService;

    private String requireEmail(UserPrincipal principal) {
        if (principal == null || principal.getEmail() == null || principal.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return principal.getEmail();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(userProfileService.getProfile(email));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDto> updateProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody UpdateUserProfileRequestDto request) {

        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(userProfileService.updateProfile(email, request));
    }

    @PostMapping("/inquiries")
    public ResponseEntity<InquiryCreatedResponseDto> createInquiry(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateInquiryRequestDto request) {

        String email = requireEmail(userPrincipal);
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfileService.createInquiry(email, request));
    }

    @GetMapping("/inquiries")
    public ResponseEntity<List<InquiryResponseDto>> getUserInquiries(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        String email = requireEmail(userPrincipal);
        return ResponseEntity.ok(userProfileService.getInquiries(email));
    }
}
