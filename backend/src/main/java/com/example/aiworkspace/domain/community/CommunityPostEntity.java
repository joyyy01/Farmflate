package com.example.aiworkspace.domain.community;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "community_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityPostEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "tag_location", nullable = false, length = 100)
    private String tagLocation;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "like_count")
    private int likeCount = 0;

    @Column(name = "comment_count")
    private int commentCount = 0;

    @Column(name = "region_analysis_id", length = 36)
    private String regionAnalysisId;

    @Column(name = "region_label", length = 100)
    private String regionLabel;

    @Builder
    public CommunityPostEntity(String category, String tagLocation, String title, String content, String author,
                                String authorEmail, String imageUrl, int likeCount, int commentCount,
                                String regionAnalysisId, String regionLabel) {
        this.category = category;
        this.tagLocation = tagLocation;
        this.title = title;
        this.content = content;
        this.author = author;
        this.authorEmail = authorEmail;
        this.imageUrl = imageUrl;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.regionAnalysisId = regionAnalysisId;
        this.regionLabel = regionLabel;
    }

}
