CREATE TABLE hire_contracts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    agreed_rate DECIMAL(10, 2) NOT NULL,
    rate_type VARCHAR(20) NOT NULL,
    scheduled_start_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    scheduled_end_time TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actual_end_time TIMESTAMP(6) NULL,
    cancellation_or_early_end_reason VARCHAR(1000) NULL,
    initiated_by_id BIGINT NULL,
    notes VARCHAR(1000) NULL,
    status VARCHAR(40) NOT NULL,
    customer_payment_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    caretaker_payment_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    payment_reference_note VARCHAR(500) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_hire_contracts PRIMARY KEY (id),
    CONSTRAINT fk_hire_contracts_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_hire_contracts_caretaker FOREIGN KEY (caretaker_id) REFERENCES users (id),
    CONSTRAINT fk_hire_contracts_initiated_by FOREIGN KEY (initiated_by_id) REFERENCES users (id),
    CONSTRAINT chk_hire_contracts_rate_type CHECK (rate_type IN ('HOURLY', 'DAILY', 'FIXED')),
    CONSTRAINT chk_hire_contracts_status CHECK (status IN (
        'REQUESTED',
        'SCHEDULED',
        'ACTIVE',
        'DECLINED',
        'CANCELLED',
        'COMPLETED_PENDING_SETTLEMENT',
        'CLOSED_PAID',
        'PAYMENT_DISPUTED',
        'DISPUTE_RESOLVED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_hire_contracts_caretaker_status_time
    ON hire_contracts (caretaker_id, status, scheduled_start_time, scheduled_end_time);
CREATE INDEX idx_hire_contracts_customer_created
    ON hire_contracts (customer_id, created_at);
CREATE INDEX idx_hire_contracts_caretaker_created
    ON hire_contracts (caretaker_id, created_at);
CREATE INDEX idx_hire_contracts_status_end
    ON hire_contracts (status, scheduled_end_time);
