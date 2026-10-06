package com.bookhive.notification.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.notification.dto.NotificationResponse;
import com.bookhive.notification.security.CurrentUser;
import com.bookhive.notification.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/me")
    public List<NotificationResponse> myNotifications() {
        return notificationService.getMyNotifications(CurrentUser.id());
    }

    @GetMapping("/me/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", notificationService.getUnreadCount(CurrentUser.id()));
    }

    @PutMapping("/{id}/read")
    public Map<String, String> markAsRead(@PathVariable UUID id) {
        notificationService.markAsRead(id, CurrentUser.id());
        return Map.of("message", "Marked as read");
    }

    @PutMapping("/read-all")
    public Map<String, String> markAllAsRead() {
        notificationService.markAllAsRead(CurrentUser.id());
        return Map.of("message", "All marked as read");
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@PathVariable UUID id) {
        notificationService.deleteNotification(id, CurrentUser.id());
        return Map.of("message", "Notification removed");
    }
}
