package com.example.aiworkspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseDto<T> {
    private String status;
    private String message;
    private T data;

    public static <T> ApiResponseDto<T> success(String message, T data) {
        return ApiResponseDto.<T>builder()
                .status("ok")
                .message(message)
                .data(data)
                .build();
    }
}
