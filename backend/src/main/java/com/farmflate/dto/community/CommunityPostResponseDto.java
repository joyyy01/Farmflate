package com.farmflate.dto.community;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommunityPostResponseDto {
    private String id;
    private String regionLabel;
    private String title;
    private String content;
    private AuthorDto author;
    private int likeCount;
    private boolean likedByMe;
    private boolean savedByMe;
    private int commentCount;
    @Builder.Default
    private List<CommunityAttachmentDto> attachments = List.of();
    @Builder.Default
    private List<CommentResponseDto> comments = List.of();
    private String timeAgo;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuthorDto {
        private String displayName;
        @Builder.Default
        private String profileType = "DEFAULT";
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CommentResponseDto {
        private String id;
        private AuthorDto author;
        private String content;
        private String timeAgo;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
    }
}
