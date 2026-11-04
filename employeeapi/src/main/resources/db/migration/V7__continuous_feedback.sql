CREATE TABLE feedback (
    id varchar(36) NOT NULL,
    subject_id varchar(36) NOT NULL,
    author_id varchar(36) NOT NULL,
    comment text NOT NULL,
    rating integer,
    visibility varchar(20) NOT NULL DEFAULT 'SUBJECT',
    created_at timestamp,
    CONSTRAINT feedback_pkey PRIMARY KEY (id),
    CONSTRAINT fk_feedback_subject FOREIGN KEY (subject_id) REFERENCES employees(id),
    CONSTRAINT fk_feedback_author FOREIGN KEY (author_id) REFERENCES employees(id),
    CONSTRAINT ck_feedback_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5))
);

CREATE INDEX idx_feedback_subject_created ON feedback (subject_id, created_at);
CREATE INDEX idx_feedback_author_created ON feedback (author_id, created_at);
