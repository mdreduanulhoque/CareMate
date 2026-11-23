package com.carebridge.notification.dto;

import java.time.Instant;

public record AdminQueueAlertEvent(
        String eventType,
        Long entityId,
        String title,
        String message,
        Long caretakerUserId,
        String caretakerName,
        Instant createdAt
) {
    public static AdminQueueAlertEvent of(
            String eventType,
            Long entityId,
            String title,
            String message,
            Long caretakerUserId,
            String caretakerName
    ) {
        return new AdminQueueAlertEvent(
                eventType,
                entityId,
                title,
                message,
                caretakerUserId,
                caretakerName,
                Instant.now()
        );
    }
}
