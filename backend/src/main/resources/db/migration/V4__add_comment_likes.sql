CREATE TABLE comment_likes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    comment_id BIGINT NOT NULL REFERENCES comments(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_likes_comment_id ON comment_likes (comment_id);
