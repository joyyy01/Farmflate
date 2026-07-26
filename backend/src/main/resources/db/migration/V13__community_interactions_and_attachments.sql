CREATE TABLE community_likes (
    id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_like_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    CONSTRAINT uq_community_like
        UNIQUE (post_id, user_email)
);

CREATE INDEX ix_community_likes_user
    ON community_likes(user_email, created_at DESC);

CREATE TABLE community_attachments (
    id VARCHAR(36) PRIMARY KEY,
    post_id BIGINT,
    owner_email VARCHAR(255) NOT NULL,
    attachment_type VARCHAR(20) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500),
    external_url VARCHAR(2000),
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_community_attachment_post
        FOREIGN KEY (post_id) REFERENCES community_posts(id) ON DELETE CASCADE,
    CONSTRAINT ck_community_attachment_type
        CHECK (attachment_type IN ('IMAGE', 'FILE', 'LINK')),
    CONSTRAINT ck_community_attachment_location
        CHECK (
            (attachment_type IN ('IMAGE', 'FILE') AND storage_key IS NOT NULL AND external_url IS NULL)
            OR
            (attachment_type = 'LINK' AND storage_key IS NULL AND external_url IS NOT NULL)
        )
);

CREATE INDEX ix_community_attachments_post_order
    ON community_attachments(post_id, sort_order);

ALTER TABLE community_posts
    ADD COLUMN region_analysis_id VARCHAR(36);

ALTER TABLE community_posts
    ADD COLUMN region_label VARCHAR(100);

UPDATE community_posts
SET region_label = NULLIF(tag_location, '')
WHERE region_label IS NULL;
