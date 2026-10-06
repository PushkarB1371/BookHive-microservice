package com.bookhive.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookhive.notification.dto.NotificationEvent;
import com.bookhive.notification.dto.NotificationResponse;
import com.bookhive.notification.entity.Notification;
import com.bookhive.notification.enums.NotificationType;
import com.bookhive.notification.exception.NotFoundException;
import com.bookhive.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final UUID ann = UUID.randomUUID();

    private Notification stored(UUID owner, boolean read) {
        Notification n = new Notification();
        n.setId(UUID.randomUUID());
        n.setUserId(owner);
        n.setType(NotificationType.NEW_REPLY);
        n.setMessage("Bob commented");
        n.setIsRead(read);
        return n;
    }

    @Test
    void aValidEventBecomesAnUnreadNotificationForTheRecipient() {
        notificationService.handleEvent(new NotificationEvent(ann, "CHAPTER_UNLOCK", "Club is now reading Dune"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(ann);
        assertThat(saved.getValue().getType()).isEqualTo(NotificationType.CHAPTER_UNLOCK);
        assertThat(saved.getValue().getMessage()).isEqualTo("Club is now reading Dune");
        assertThat(saved.getValue().getIsRead()).isFalse();
    }

    @Test
    void anUnknownOrMissingTypeFallsBackToNewReply() {
        notificationService.handleEvent(new NotificationEvent(ann, "SOMETHING_NEW", "hello"));
        notificationService.handleEvent(new NotificationEvent(ann, null, "hello again"));
        notificationService.handleEvent(new NotificationEvent(ann, "chapter_unlock", "lowercase works too"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getType).containsExactly(
                NotificationType.NEW_REPLY, NotificationType.NEW_REPLY, NotificationType.CHAPTER_UNLOCK);
    }

    @Test
    void invalidEventsAreDroppedWithoutError() {
        notificationService.handleEvent(null);
        notificationService.handleEvent(new NotificationEvent(null, "NEW_REPLY", "no recipient"));
        notificationService.handleEvent(new NotificationEvent(ann, "NEW_REPLY", "   "));
        notificationService.handleEvent(new NotificationEvent(ann, "NEW_REPLY", null));

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void veryLongMessagesAreTruncatedToFitTheColumn() {
        notificationService.handleEvent(new NotificationEvent(ann, "NEW_REPLY", "x".repeat(5000)));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).hasSize(1000);
    }

    @Test
    void myNotificationsAreMappedForTheApi() {
        Notification n = stored(ann, true);
        when(notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(ann)).thenReturn(List.of(n));

        List<NotificationResponse> result = notificationService.getMyNotifications(ann);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).type()).isEqualTo("NEW_REPLY");
        assertThat(result.get(0).isRead()).isTrue();
        assertThat(result.get(0).message()).isEqualTo("Bob commented");
    }

    @Test
    void unreadCountComesFromTheRepository() {
        when(notificationRepository.countByUserIdAndIsReadFalse(ann)).thenReturn(3L);

        assertThat(notificationService.getUnreadCount(ann)).isEqualTo(3L);
    }

    @Test
    void ownersCanMarkTheirNotificationAsRead() {
        Notification n = stored(ann, false);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        notificationService.markAsRead(n.getId(), ann);

        assertThat(n.getIsRead()).isTrue();
        verify(notificationRepository).save(n);
    }

    @Test
    void someoneElsesNotificationLooksLikeItDoesNotExist() {
        Notification n = stored(ann, false);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));
        UUID intruder = UUID.randomUUID();

        assertThatThrownBy(() -> notificationService.markAsRead(n.getId(), intruder))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> notificationService.deleteNotification(n.getId(), intruder))
                .isInstanceOf(NotFoundException.class);

        assertThat(n.getIsRead()).isFalse();
        verify(notificationRepository, never()).delete(any(Notification.class));
    }

    @Test
    void ownersCanDismissTheirNotification() {
        Notification n = stored(ann, false);
        when(notificationRepository.findById(n.getId())).thenReturn(Optional.of(n));

        notificationService.deleteNotification(n.getId(), ann);

        verify(notificationRepository).delete(n);
    }

    @Test
    void markAllReadDelegatesToASingleUpdate() {
        when(notificationRepository.markAllRead(ann)).thenReturn(4);

        assertThat(notificationService.markAllAsRead(ann)).isEqualTo(4);
    }
}
