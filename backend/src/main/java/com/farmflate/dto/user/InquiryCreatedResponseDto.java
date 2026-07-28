package com.farmflate.dto.user;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InquiryCreatedResponseDto {
    private final String status;
    private final String inquiryId;
    private final String createdAt;
}
