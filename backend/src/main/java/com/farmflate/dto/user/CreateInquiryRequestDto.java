package com.farmflate.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateInquiryRequestDto {

    @NotBlank(message = "문의 내용을 입력해 주세요.")
    @Size(max = 4_000, message = "문의 내용은 4,000자 이하여야 합니다.")
    private String inquiryText;

    @Size(max = 50, message = "문의 분류는 50자 이하여야 합니다.")
    private String category;
}
