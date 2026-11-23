package com.carebridge.notification;

import com.carebridge.notification.dto.AdminQueueAlertEvent;
import com.carebridge.notification.dto.FeedAvailabilityEvent;
import com.carebridge.notification.dto.UserNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Asynchronous real-time event publisher running on the bounded notificationExecutor pool
 * so HTTP requests on weak hospital cellular connections remain fast.
 */
@Service
public class RealtimeEventDispatcher {

    public static final String TOPIC_FEED_AVAILABILITY = "/topic/feed/availability";
    public static final String TOPIC_ADMIN_ALERTS = "/topic/admin/alerts";
    public static final String USER_QUEUE_NOTIFICATIONS = "/queue/notifications";

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventDispatcher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeEventDispatcher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Async("notificationExecutor")
    public void publishFeedAvailability(FeedAvailabilityEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_FEED_AVAILABILITY, event);
        } catch (MessagingException ex) {
            log.warn("Failed to broadcast feed availability event for caretaker {}: {}",
                    event.caretakerUserId(), ex.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void publishAdminAlert(AdminQueueAlertEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ADMIN_ALERTS, event);
        } catch (MessagingException ex) {
            log.warn("Failed to publish admin alert {}: {}", event.eventType(), ex.getMessage());
        }
    }

    @Async("notificationExecutor")
    public void publishUserNotification(Long targetUserId, UserNotificationEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(targetUserId),
                    USER_QUEUE_NOTIFICATIONS,
                    event
            );
        } catch (MessagingException ex) {
            log.warn("Failed to push user notification {} to user {}: {}",
                    event.eventType(), targetUserId, ex.getMessage());
        }
    }
}
