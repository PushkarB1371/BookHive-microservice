package com.bookhive.notification.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bookhive.notification.dto.NotificationEvent;
import com.bookhive.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationConsumer consumer;

    @Test
    void aWellFormedMessageIsHandedToTheService() {
        UUID recipient = UUID.randomUUID();

        consumer.onMessage("{\"recipientId\":\"" + recipient + "\",\"type\":\"NEW_REPLY\",\"message\":\"Bob replied\"}");

        ArgumentCaptor<NotificationEvent> event = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).handleEvent(event.capture());
        assertThat(event.getValue().recipientId()).isEqualTo(recipient);
        assertThat(event.getValue().type()).isEqualTo("NEW_REPLY");
        assertThat(event.getValue().message()).isEqualTo("Bob replied");
    }

    @Test
    void extraFieldsFromANewerProducerAreIgnored() {
        UUID recipient = UUID.randomUUID();

        consumer.onMessage("{\"recipientId\":\"" + recipient + "\",\"type\":\"NEW_REPLY\",\"message\":\"hi\",\"priority\":\"high\"}");

        verify(notificationService).handleEvent(any(NotificationEvent.class));
    }

    @Test
    void anUnreadableMessageIsSkippedNotRetriedForever() {
        consumer.onMessage("this is not json");
        consumer.onMessage("{\"recipientId\":\"not-a-uuid\",\"message\":\"hi\"}");
        consumer.onMessage(null);

        verifyNoInteractions(notificationService);
    }

    @Test
    void databaseFailuresPropagateSoKafkaRetriesTheMessage() {
        doThrow(new IllegalStateException("database is down")).when(notificationService).handleEvent(any());

        assertThatThrownBy(() -> consumer.onMessage(
                "{\"recipientId\":\"" + UUID.randomUUID() + "\",\"type\":\"NEW_REPLY\",\"message\":\"hi\"}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("database is down");
    }
}
