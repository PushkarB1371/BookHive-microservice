package com.bookhive.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String message, boolean isRead, LocalDateTime createdAt) {}
