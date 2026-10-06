package com.bookhive.notification.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bookhive.notification.dto.NotificationResponse;
import com.bookhive.notification.exception.GlobalExceptionHandler;
import com.bookhive.notification.exception.NotFoundException;
import com.bookhive.notification.security.AuthenticatedUser;
import com.bookhive.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private final AuthenticatedUser ann = new AuthenticatedUser(UUID.randomUUID(), "Ann", "ann@example.com", "MEMBER");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ann, "jwt", List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unreadCountIsWrappedInAnObject() throws Exception {
        when(notificationService.getUnreadCount(ann.id())).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/me/unread-count"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"count\":3")));
    }

    @Test
    void myNotificationsAreListed() throws Exception {
        when(notificationService.getMyNotifications(ann.id())).thenReturn(List.of(
                new NotificationResponse(UUID.randomUUID(), "NEW_REPLY", "Bob replied", false, LocalDateTime.now())));

        mockMvc.perform(get("/api/notifications/me"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bob replied")))
                .andExpect(content().string(containsString("\"isRead\":false")));
    }

    @Test
    void markingAsReadCallsTheServiceForTheCurrentUser() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(put("/api/notifications/" + id + "/read"))
                .andExpect(status().isOk());

        verify(notificationService).markAsRead(id, ann.id());
    }

    @Test
    void markAllReadHasItsOwnEndpoint() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all"))
                .andExpect(status().isOk());

        verify(notificationService).markAllAsRead(ann.id());
    }

    @Test
    void touchingSomeoneElsesNotificationIsA404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotFoundException("Notification not found")).when(notificationService).deleteNotification(id, ann.id());

        mockMvc.perform(delete("/api/notifications/" + id))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Notification not found")));
    }
}
