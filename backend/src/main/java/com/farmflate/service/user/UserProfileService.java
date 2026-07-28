package com.farmflate.service.user;

import com.farmflate.domain.user.InquiryEntity;
import com.farmflate.domain.user.InquiryRepository;
import com.farmflate.domain.user.User;
import com.farmflate.domain.user.UserRepository;
import com.farmflate.dto.user.CreateInquiryRequestDto;
import com.farmflate.dto.user.InquiryCreatedResponseDto;
import com.farmflate.dto.user.InquiryResponseDto;
import com.farmflate.dto.user.UpdateUserProfileRequestDto;
import com.farmflate.dto.user.UserProfileResponseDto;
import com.farmflate.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String DEFAULT_DISPLAY_NAME = "사용자님";

    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;

    @Transactional(readOnly = true)
    public UserProfileResponseDto getProfile(String email) {
        return toProfile(requireUser(email));
    }

    @Transactional
    public UserProfileResponseDto updateProfile(String email, UpdateUserProfileRequestDto request) {
        User user = requireUser(email);
        user.update(request.getNickname().trim());
        return toProfile(user);
    }

    @Transactional(readOnly = true)
    public String getDisplayName(String email, String fallback) {
        return userRepository.findByEmail(email)
                .map(User::getNickname)
                .filter(name -> name != null && !name.isBlank())
                .orElse(fallback);
    }

    @Transactional
    public InquiryCreatedResponseDto createInquiry(String email, CreateInquiryRequestDto request) {
        InquiryEntity saved = inquiryRepository.save(InquiryEntity.builder()
                .userEmail(email)
                .inquiryText(request.getInquiryText().trim())
                .category(normalizeCategory(request.getCategory()))
                .status("PENDING")
                .build());
        return new InquiryCreatedResponseDto(
                "SUCCESS",
                String.valueOf(saved.getId()),
                saved.getCreatedAt() == null ? "" : saved.getCreatedAt().toString());
    }

    @Transactional(readOnly = true)
    public List<InquiryResponseDto> getInquiries(String email) {
        return inquiryRepository.findByUserEmailOrderByCreatedAtDesc(email).stream()
                .map(this::toInquiry)
                .toList();
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private UserProfileResponseDto toProfile(User user) {
        String displayName = user.getNickname() == null || user.getNickname().isBlank()
                ? DEFAULT_DISPLAY_NAME : user.getNickname();
        String provider = user.getProvider() == null ? "kakao" : user.getProvider();
        String role = user.getRole() == null ? "USER" : user.getRole().name();
        return new UserProfileResponseDto(user.getEmail(), displayName, provider, role);
    }

    private InquiryResponseDto toInquiry(InquiryEntity inquiry) {
        return new InquiryResponseDto(
                inquiry.getId(),
                inquiry.getUserEmail(),
                inquiry.getInquiryText(),
                inquiry.getCategory(),
                inquiry.getStatus(),
                inquiry.getCreatedAt() == null ? "" : inquiry.getCreatedAt().toString(),
                inquiry.getUpdatedAt() == null ? "" : inquiry.getUpdatedAt().toString());
    }

    private String normalizeCategory(String category) {
        return category == null || category.isBlank() ? "GENERAL" : category.trim();
    }
}
