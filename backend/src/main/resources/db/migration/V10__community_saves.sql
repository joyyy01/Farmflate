CREATE TABLE community_saves (
    id BIGSERIAL PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_community_save UNIQUE (user_email, post_id)
);

CREATE INDEX ix_community_saves_user ON community_saves(user_email);
