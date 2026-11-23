CREATE TABLE payment_disputes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    raised_by_id BIGINT NOT NULL,
    assigned_admin_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    customer_statement VARCHAR(2000) NULL,
    caretaker_statement VARCHAR(2000) NULL,
    admin_resolution_note VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at TIMESTAMP(6) NULL DEFAULT NULL,
    CONSTRAINT pk_payment_disputes PRIMARY KEY (id),
    CONSTRAINT fk_payment_disputes_contract FOREIGN KEY (contract_id) REFERENCES hire_contracts (id),
    CONSTRAINT fk_payment_disputes_raised_by FOREIGN KEY (raised_by_id) REFERENCES users (id),
    CONSTRAINT fk_payment_disputes_admin FOREIGN KEY (assigned_admin_id) REFERENCES users (id),
    CONSTRAINT uq_payment_disputes_contract UNIQUE (contract_id),
    CONSTRAINT chk_payment_disputes_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_disputes_status_created
    ON payment_disputes (status, created_at);
CREATE INDEX idx_payment_disputes_assigned_admin
    ON payment_disputes (assigned_admin_id, status);
