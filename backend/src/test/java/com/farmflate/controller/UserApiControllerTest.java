package com.farmflate.controller;

import com.farmflate.dto.user.CreateInquiryRequestDto;
import com.farmflate.dto.user.InquiryCreatedResponseDto;
import com.farmflate.security.UserPrincipal;
import com.farmflate.service.user.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserApiControllerTest {

    @Test
    void creates_inquiries_with_the_created_http_status() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        UserApiController controller = new UserApiController(userProfileService);
        CreateInquiryRequestDto request = new CreateInquiryRequestDto();
        ReflectionTestUtils.setField(request, "inquiryText", "문의");
        when(userProfileService.createInquiry(eq("owner@example.com"), any()))
                .thenReturn(new InquiryCreatedResponseDto("SUCCESS", "7", "2026-07-28T12:00"));

        var response = controller.createInquiry(
                new UserPrincipal(1L, "owner@example.com", List.of()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getInquiryId()).isEqualTo("7");
    }
}
