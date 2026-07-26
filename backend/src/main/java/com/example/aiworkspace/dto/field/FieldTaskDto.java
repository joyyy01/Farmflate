package com.example.aiworkspace.dto.field;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FieldTaskDto {
    private String key;
    private String title;
    private String description;
    private FieldTaskBadge badge;
    private boolean acknowledged;
}
