package com.bookhive.notification.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bookhive.notification.dto.NotificationEvent;
import com.bookhive.notification.service.NotificationService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Listens on the notifications topic and stores each event as a notification for its recipient. */
@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "${bookhive.kafka.notifications-topic:notifications}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(String payload) {
        NotificationEvent event;
        try {
            event = mapper.readValue(payload, NotificationEvent.class);
        } catch (Exception e) {
            // A malformed message can never succeed, so skip it instead of retrying forever.
            log.warn("Skipping unreadable notification message: {}", e.toString());
            return;
        }
        // Database errors are NOT caught here: they propagate so Spring Kafka retries the message.
        notificationService.handleEvent(event);
    }
}
