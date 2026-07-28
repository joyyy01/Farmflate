package com.farmflate.dto.field;

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
public class FieldActivityLogDto {
    private String id;
    private String fieldId;
    private FieldLogCategory category;
    private String categoryLabel;
    private String note;
    private String loggedAt;
}
