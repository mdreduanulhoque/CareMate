CREATE TABLE caretaker_applications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    application_number INT NOT NULL,
    dob DATE NOT NULL,
    qualification VARCHAR(255) NOT NULL,
    experience VARCHAR(1500) NULL,
    documents_drive_link VARCHAR(1000) NOT NULL,
    drive_link_reachable BOOLEAN NULL,
    drive_check_note VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(1000) NULL,
    reviewed_by_admin_id BIGINT NULL,
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_caretaker_applications PRIMARY KEY (id),
    CONSTRAINT fk_caretaker_apps_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_caretaker_apps_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id),
    CONSTRAINT uq_caretaker_apps_user_number UNIQUE (user_id, application_number),
    CONSTRAINT chk_caretaker_apps_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_caretaker_apps_status_submitted ON caretaker_applications (status, submitted_at);
CREATE INDEX idx_caretaker_apps_user_number ON caretaker_applications (user_id, application_number);

CREATE TABLE caretaker_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    primary_qualification VARCHAR(255) NOT NULL,
    location_area VARCHAR(160) NOT NULL,
    profile_image_url VARCHAR(500) NULL,
    bio VARCHAR(2000) NULL,
    rate_amount DECIMAL(10, 2) NULL,
    rate_type VARCHAR(20) NOT NULL DEFAULT 'DAILY',
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    avg_rating DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    completed_hires_count INT NOT NULL DEFAULT 0,
    hire_again_percentage DECIMAL(5, 2) NOT NULL DEFAULT 0.00,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_caretaker_profiles PRIMARY KEY (id),
    CONSTRAINT fk_caretaker_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_caretaker_profiles_user UNIQUE (user_id),
    CONSTRAINT chk_caretaker_profiles_rate_type CHECK (rate_type IN ('HOURLY', 'DAILY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_caretaker_profiles_feed ON caretaker_profiles (is_available, rate_type, rate_amount);

CREATE TABLE caretaker_certifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    caretaker_profile_id BIGINT NOT NULL,
    title VARCHAR(180) NOT NULL,
    issuing_organization VARCHAR(180) NOT NULL,
    document_drive_link VARCHAR(1000) NOT NULL,
    drive_link_reachable BOOLEAN NULL,
    drive_check_note VARCHAR(255) NULL,
    verification_status VARCHAR(20) NOT NULL,
    rejection_reason VARCHAR(1000) NULL,
    reviewed_by_admin_id BIGINT NULL,
    submitted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_caretaker_certifications PRIMARY KEY (id),
    CONSTRAINT fk_caretaker_certs_profile FOREIGN KEY (caretaker_profile_id) REFERENCES caretaker_profiles (id),
    CONSTRAINT fk_caretaker_certs_admin FOREIGN KEY (reviewed_by_admin_id) REFERENCES users (id),
    CONSTRAINT chk_caretaker_certs_status CHECK (verification_status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_caretaker_certs_status_submitted ON caretaker_certifications (verification_status, submitted_at);
CREATE INDEX idx_caretaker_certs_profile_status ON caretaker_certifications (caretaker_profile_id, verification_status);
