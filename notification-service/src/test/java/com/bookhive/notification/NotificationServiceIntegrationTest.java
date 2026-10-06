package com.bookhive.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bookhive.notification.messaging.NotificationConsumer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Feeds the consumer exactly what Kafka would deliver, then reads the result over real HTTP with real JWT
 * security and a real Postgres. (The Kafka transport itself is covered by scripts/smoke-test.mjs.)
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "bookhive.kafka.listener-auto-startup=false",
        "jwt.secret=" + NotificationServiceIntegrationTest.SECRET
})
class NotificationServiceIntegrationTest {

    static final String SECRET = "notify-test-secret-notify-test-secret-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificationConsumer consumer;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void aKafkaMessageBecomesAVisibleNotificationThatCanBeReadAndDismissed() throws Exception {
        UUID annId = UUID.randomUUID();
        String ann = token(annId);
        String bob = token(UUID.randomUUID());

        assertThat(send("GET", "/api/notifications/me/unread-count", ann + "x").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/notifications/me/unread-count", null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/notifications/me/unread-count", ann).body()).contains("\"count\":0");

        consumer.onMessage("{\"recipientId\":\"" + annId + "\",\"type\":\"NEW_REPLY\",\"message\":\"Bob commented on Chapter 1\"}");
        consumer.onMessage("{\"recipientId\":\"" + annId + "\",\"type\":\"CHAPTER_UNLOCK\",\"message\":\"Club is now reading Dune\"}");

        assertThat(send("GET", "/api/notifications/me/unread-count", ann).body()).contains("\"count\":2");
        HttpResponse<String> list = send("GET", "/api/notifications/me", ann);
        assertThat(list.body()).contains("Bob commented on Chapter 1").contains("Club is now reading Dune");

        // notifications are private
        assertThat(send("GET", "/api/notifications/me/unread-count", bob).body()).contains("\"count\":0");
        String firstId = field(list.body(), "id");
        assertThat(send("PUT", "/api/notifications/" + firstId + "/read", bob).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/notifications/" + firstId, bob).statusCode()).isEqualTo(404);

        // read one, then the rest
        assertThat(send("PUT", "/api/notifications/" + firstId + "/read", ann).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications/me/unread-count", ann).body()).contains("\"count\":1");
        assertThat(send("PUT", "/api/notifications/read-all", ann).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications/me/unread-count", ann).body()).contains("\"count\":0");

        // dismiss
        assertThat(send("DELETE", "/api/notifications/" + firstId, ann).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/notifications/me", ann).body()).doesNotContain(firstId);
    }

    @Test
    void garbageOnTheTopicDoesNotCreateNotificationsOrCrashTheConsumer() throws Exception {
        UUID annId = UUID.randomUUID();
        String ann = token(annId);

        consumer.onMessage("not json at all");
        consumer.onMessage("{\"recipientId\":\"" + annId + "\",\"message\":\"   \"}");

        assertThat(send("GET", "/api/notifications/me/unread-count", ann).body()).contains("\"count\":0");
    }

    private String token(UUID id) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("name", "Ann")
                .claim("email", "ann@example.com")
                .claim("role", "MEMBER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, HttpRequest.BodyPublishers.noBody());
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(matcher.find()).as("field '" + name + "' in " + json).isTrue();
        return matcher.group(1);
    }
}
