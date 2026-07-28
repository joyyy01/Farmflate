package com.farmflate.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateUserProfileRequestDto {

    @NotBlank(message = "표시 이름을 입력해 주세요.")
    @Size(max = 100, message = "표시 이름은 100자 이하여야 합니다.")
    private String nickname;
}
