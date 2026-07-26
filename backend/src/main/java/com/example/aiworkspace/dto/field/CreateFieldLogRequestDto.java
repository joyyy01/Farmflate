package com.example.aiworkspace.dto.field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateFieldLogRequestDto {
    @NotBlank
    private String category;

    @Size(max = 500)
    private String note = "";
}
