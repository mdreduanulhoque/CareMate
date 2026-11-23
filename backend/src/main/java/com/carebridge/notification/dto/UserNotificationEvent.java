package com.carebridge.notification.dto;

import java.time.Instant;

public record UserNotificationEvent(
        String eventType,
        String roomId,
        Long contractId,
        String title,
        String message,
        Long actorUserId,
        String actorName,
        Instant createdAt
) {
    public static UserNotificationEvent of(
            String eventType,
            String roomId,
            String title,
            String message,
            Long actorUserId,
            String actorName
    ) {
        return new UserNotificationEvent(
                eventType,
                roomId,
                null,
                title,
                message,
                actorUserId,
                actorName,
                Instant.now()
        );
    }

    public static UserNotificationEvent forContract(
            String eventType,
            Long contractId,
            String title,
            String message,
            Long actorUserId,
            String actorName
    ) {
        return new UserNotificationEvent(
                eventType,
                null,
                contractId,
                title,
                message,
                actorUserId,
                actorName,
                Instant.now()
        );
    }
}
