package com.example.aiworkspace.dto.field;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskAcknowledgementResponseDto {
    private String taskKey;
    private boolean acknowledged;
    private String acknowledgedAt;
}
