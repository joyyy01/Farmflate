package com.example.aiworkspace.dto.community;

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
public class CommunityAttachmentDto {
    private String id;
    private String type;
    private String name;
    private String contentType;
    private Long sizeBytes;
    private String url;
    private int order;
}
