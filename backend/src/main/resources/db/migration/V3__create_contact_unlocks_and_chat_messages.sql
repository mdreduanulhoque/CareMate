CREATE TABLE contact_unlocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    caretaker_id BIGINT NOT NULL,
    unlocked_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_contact_unlocks PRIMARY KEY (id),
    CONSTRAINT fk_contact_unlocks_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_contact_unlocks_caretaker FOREIGN KEY (caretaker_id) REFERENCES users (id),
    CONSTRAINT uq_contact_unlocks_pair UNIQUE (customer_id, caretaker_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_contact_unlocks_customer ON contact_unlocks (customer_id, unlocked_at);
CREATE INDEX idx_contact_unlocks_caretaker ON contact_unlocks (caretaker_id, unlocked_at);

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    room_id VARCHAR(120) NOT NULL,
    room_type VARCHAR(20) NOT NULL,
    sender_id BIGINT NOT NULL,
    contract_id BIGINT NULL,
    content VARCHAR(2000) NOT NULL,
    sent_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT chk_chat_messages_room_type CHECK (room_type IN ('NEGOTIATION', 'DISPUTE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_chat_messages_room_sent ON chat_messages (room_id, sent_at, id);
CREATE INDEX idx_chat_messages_sender ON chat_messages (sender_id);
