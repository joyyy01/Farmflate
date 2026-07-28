package com.farmflate.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InquiryResponseDto {
    private final Long id;
    private final String userEmail;
    private final String inquiryText;
    private final String category;
    private final String status;
    private final String createdAt;
    private final String updatedAt;
}
