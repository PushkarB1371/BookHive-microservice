package com.bookhive.club;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.ApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bookhive.club.feign.UserClient;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class ClubServiceApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithFeignCircuitBreakerAndKafkaWired() {
        assertThat(context.getBean(UserClient.class)).isNotNull();
        assertThat(context.getBean(CircuitBreakerFactory.class)).isNotNull();
        assertThat(context.getBean(UserDirectory.class)).isNotNull();
        assertThat(context.getBean(NotificationPublisher.class)).isNotNull();
    }
}
