CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    reviewee_id BIGINT NOT NULL,
    reviewer_role VARCHAR(20) NOT NULL,
    rating INT NULL,
    comment VARCHAR(2000) NULL,
    would_engage_again BOOLEAN NULL,
    is_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    is_edited BOOLEAN NOT NULL DEFAULT FALSE,
    is_locked BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMP(6) NULL DEFAULT NULL,
    updated_at TIMESTAMP(6) NULL DEFAULT NULL,
    editable_until TIMESTAMP(6) NULL DEFAULT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_reviews PRIMARY KEY (id),
    CONSTRAINT fk_reviews_contract FOREIGN KEY (contract_id) REFERENCES hire_contracts (id),
    CONSTRAINT fk_reviews_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_reviewee FOREIGN KEY (reviewee_id) REFERENCES users (id),
    CONSTRAINT uq_reviews_contract_reviewer UNIQUE (contract_id, reviewer_id),
    CONSTRAINT chk_reviews_reviewer_role CHECK (reviewer_role IN ('CUSTOMER', 'CARETAKER')),
    CONSTRAINT chk_reviews_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reviews_reviewer_submitted
    ON reviews (reviewer_id, is_submitted, created_at);
CREATE INDEX idx_reviews_reviewee_submitted
    ON reviews (reviewee_id, is_submitted, submitted_at);
CREATE INDEX idx_reviews_lock_maintenance
    ON reviews (is_submitted, is_locked, editable_until);
