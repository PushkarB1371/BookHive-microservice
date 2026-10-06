package com.bookhive.club.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bookhive.club.enums.NotificationType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publishes notification events to Kafka. Two safety rules:
 *  1. inside a transaction the event is sent only AFTER the commit (no notification for a rolled-back change);
 *  2. a Kafka problem is logged, never thrown - a missing notification must not break posting a comment.
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final ObjectMapper mapper = new ObjectMapper();

    public NotificationPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                 @Value("${bookhive.kafka.notifications-topic:notifications}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(UUID recipientId, NotificationType type, String message) {
        if (recipientId == null) {
            return;
        }
        String payload;
        try {
            payload = mapper.writeValueAsString(new NotificationEvent(recipientId, type, message));
        } catch (JsonProcessingException e) {
            log.error("Could not serialize notification for {}", recipientId, e);
            return;
        }

        Runnable send = () -> send(recipientId, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private void send(UUID recipientId, String payload) {
        try {
            kafkaTemplate.send(topic, recipientId.toString(), payload).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Failed to publish notification for {}: {}", recipientId, error.toString());
                }
            });
        } catch (Exception e) {
            log.error("Failed to publish notification for {}: {}", recipientId, e.toString());
        }
    }
}
