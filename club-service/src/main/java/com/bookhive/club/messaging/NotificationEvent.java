package com.bookhive.club.messaging;

import java.util.UUID;

import com.bookhive.club.enums.NotificationType;

/** The message put on the "notifications" topic. notification-service turns it into a stored notification. */
public record NotificationEvent(UUID recipientId, NotificationType type, String message) {}
