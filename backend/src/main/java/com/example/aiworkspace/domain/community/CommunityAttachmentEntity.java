package com.example.aiworkspace.domain.community;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommunityAttachmentEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Setter
    @Column(name = "post_id")
    private Long postId;

    @Column(name = "owner_email", nullable = false)
    private String ownerEmail;

    @Column(name = "attachment_type", nullable = false, length = 20)
    private String attachmentType;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "external_url", length = 2000)
    private String externalUrl;

    @Setter
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public CommunityAttachmentEntity(String id, Long postId, String ownerEmail, String attachmentType,
                                      String originalName, String contentType, long sizeBytes,
                                      String storageKey, String externalUrl, int sortOrder) {
        this.id = id;
        this.postId = postId;
        this.ownerEmail = ownerEmail;
        this.attachmentType = attachmentType;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.externalUrl = externalUrl;
        this.sortOrder = sortOrder;
        this.createdAt = LocalDateTime.now();
    }
}
