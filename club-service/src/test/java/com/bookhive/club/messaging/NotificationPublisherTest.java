package com.bookhive.club.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bookhive.club.enums.NotificationType;

@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(kafkaTemplate, "notifications");
    }

    @AfterEach
    void cleanUpTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsAJsonEventKeyedByRecipient() {
        UUID recipient = UUID.randomUUID();
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(recipient, NotificationType.CHAPTER_UNLOCK, "Club is now reading Dune");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("notifications"), eq(recipient.toString()), payload.capture());
        assertThat(payload.getValue())
                .contains("\"recipientId\":\"" + recipient + "\"")
                .contains("\"type\":\"CHAPTER_UNLOCK\"")
                .contains("\"message\":\"Club is now reading Dune\"");
    }

    @Test
    void nothingIsSentWithoutARecipient() {
        publisher.publish(null, NotificationType.NEW_REPLY, "hello");

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void aKafkaOutageNeverBreaksTheCaller() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> publisher.publish(UUID.randomUUID(), NotificationType.NEW_REPLY, "hello"))
                .doesNotThrowAnyException();
    }

    @Test
    void insideATransactionTheEventIsOnlySentAfterCommit() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(UUID.randomUUID(), NotificationType.NEW_REPLY, "hello");

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
        verify(kafkaTemplate).send(eq("notifications"), anyString(), anyString());
    }

    @Test
    void aRolledBackTransactionSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish(UUID.randomUUID(), NotificationType.NEW_REPLY, "hello");
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verifyNoInteractions(kafkaTemplate);
    }
}
