package com.bookhive.notification.dto;

import java.util.UUID;

/**
 * Message published by club-service on the "notifications" topic. The type is read as plain text on purpose,
 * so an unknown value from a newer producer doesn't make the whole message unreadable.
 */
public record NotificationEvent(UUID recipientId, String type, String message) {}
