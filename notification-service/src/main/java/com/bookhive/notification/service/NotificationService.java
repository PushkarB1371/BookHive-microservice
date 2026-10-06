package com.bookhive.notification.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.notification.dto.NotificationEvent;
import com.bookhive.notification.dto.NotificationResponse;
import com.bookhive.notification.entity.Notification;
import com.bookhive.notification.enums.NotificationType;
import com.bookhive.notification.exception.NotFoundException;
import com.bookhive.notification.repository.NotificationRepository;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Turns a Kafka event into a stored notification. Invalid events are logged and dropped. */
    @Transactional
    public void handleEvent(NotificationEvent event) {
        if (event == null || event.recipientId() == null || event.message() == null || event.message().isBlank()) {
            log.warn("Dropping invalid notification event: {}", event);
            return;
        }
        String message = event.message().length() > MAX_MESSAGE_LENGTH
                ? event.message().substring(0, MAX_MESSAGE_LENGTH)
                : event.message();

        Notification notification = new Notification();
        notification.setUserId(event.recipientId());
        notification.setType(parseType(event.type()));
        notification.setMessage(message);
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }

    /** The 50 most recent notifications for this user. */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(UUID userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        Notification notification = findOwned(notificationId, userId);
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllRead(userId);
    }

    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        notificationRepository.delete(findOwned(notificationId, userId));
    }

    /** Someone else's notification looks exactly like a missing one, so ids can't be probed. */
    private Notification findOwned(UUID notificationId, UUID userId) {
        return notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }

    private NotificationType parseType(String type) {
        if (type == null) {
            return NotificationType.NEW_REPLY;
        }
        try {
            return NotificationType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown notification type '{}', using NEW_REPLY", type);
            return NotificationType.NEW_REPLY;
        }
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType().name(), n.getMessage(), Boolean.TRUE.equals(n.getIsRead()), n.getCreatedAt());
    }
}
