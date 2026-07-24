package com.example.aiworkspace.domain.community;

import com.example.aiworkspace.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "community_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityCommentEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private CommunityPostEntity post;

    @Column(nullable = false, length = 100)
    private String author;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder
    public CommunityCommentEntity(CommunityPostEntity post, String author, String authorEmail, String content) {
        this.post = post;
        this.author = author;
        this.authorEmail = authorEmail;
        this.content = content;
    }
}
