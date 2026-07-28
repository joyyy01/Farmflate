package com.farmflate.service.user;

import com.farmflate.domain.user.InquiryEntity;
import com.farmflate.domain.user.InquiryRepository;
import com.farmflate.domain.user.UserRepository;
import com.farmflate.dto.user.CreateInquiryRequestDto;
import com.farmflate.dto.user.InquiryCreatedResponseDto;
import com.farmflate.dto.user.UpdateUserProfileRequestDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final InquiryRepository inquiryRepository = mock(InquiryRepository.class);
    private final UserProfileService service = new UserProfileService(userRepository, inquiryRepository);

    @Test
    void creates_a_trimmed_inquiry_with_the_default_category() {
        CreateInquiryRequestDto request = new CreateInquiryRequestDto();
        ReflectionTestUtils.setField(request, "inquiryText", "  분석 결과 문의  ");
        ReflectionTestUtils.setField(request, "category", "   ");
        when(inquiryRepository.save(any(InquiryEntity.class))).thenAnswer(invocation -> {
            InquiryEntity inquiry = invocation.getArgument(0);
            ReflectionTestUtils.setField(inquiry, "id", 7L);
            ReflectionTestUtils.setField(inquiry, "createdAt", LocalDateTime.of(2026, 7, 28, 12, 0));
            return inquiry;
        });

        InquiryCreatedResponseDto response = service.createInquiry("owner@example.com", request);

        ArgumentCaptor<InquiryEntity> saved = ArgumentCaptor.forClass(InquiryEntity.class);
        verify(inquiryRepository).save(saved.capture());
        assertThat(saved.getValue().getInquiryText()).isEqualTo("분석 결과 문의");
        assertThat(saved.getValue().getCategory()).isEqualTo("GENERAL");
        assertThat(response.getInquiryId()).isEqualTo("7");
    }

    @Test
    void rejects_blank_profile_and_inquiry_values_at_the_request_boundary() {
        UpdateUserProfileRequestDto profile = new UpdateUserProfileRequestDto();
        CreateInquiryRequestDto inquiry = new CreateInquiryRequestDto();
        ReflectionTestUtils.setField(profile, "nickname", "  ");
        ReflectionTestUtils.setField(inquiry, "inquiryText", "  ");
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(profile)).isNotEmpty();
        assertThat(validator.validate(inquiry)).isNotEmpty();
    }
}
