package com.bookhive.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the whole service on a random port against a real, throwaway Postgres (Docker must be running)
 * and drives it over HTTP exactly like the gateway would.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "jwt.secret=integration-test-secret-integration-test-secret",
        "bookhive.admin.email="
})
class UserServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void signupLoginAndProtectedEndpointsWorkEndToEnd() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        String signupBody = "{\"name\":\"Ann\",\"email\":\"" + email + "\",\"password\":\"secret1\"}";

        HttpResponse<String> signup = send("POST", "/api/auth/signup", signupBody, null);
        assertThat(signup.statusCode()).isEqualTo(200);
        String signupToken = field(signup.body(), "token");

        HttpResponse<String> duplicate = send("POST", "/api/auth/signup", signupBody, null);
        assertThat(duplicate.statusCode()).isEqualTo(400);
        assertThat(duplicate.body()).contains("already registered");

        HttpResponse<String> login = send("POST", "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"secret1\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        String token = field(login.body(), "token");
        assertThat(token).isNotBlank().isNotEqualTo("");
        assertThat(signupToken).isNotBlank();

        HttpResponse<String> me = send("GET", "/api/users/me", null, token);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains(email).contains("MEMBER");

        assertThat(send("GET", "/api/users/me", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/users/me", null, token + "tampered").statusCode()).isEqualTo(401);
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        send("POST", "/api/auth/signup", "{\"name\":\"Bob\",\"email\":\"" + email + "\",\"password\":\"secret1\"}", null);

        HttpResponse<String> login = send("POST", "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}", null);

        assertThat(login.statusCode()).isEqualTo(401);
    }

    @Test
    void internalBatchLookupReturnsNamesButNeverEmails() throws Exception {
        String email = "it-" + UUID.randomUUID() + "@example.com";
        HttpResponse<String> signup = send("POST", "/api/auth/signup",
                "{\"name\":\"Carol\",\"email\":\"" + email + "\",\"password\":\"secret1\"}", null);
        String token = field(signup.body(), "token");
        String userId = field(signup.body(), "userId");

        HttpResponse<String> batch = send("POST", "/internal/users/batch", "[\"" + userId + "\"]", token);

        assertThat(batch.statusCode()).isEqualTo(200);
        assertThat(batch.body()).contains("Carol").contains(userId);
        assertThat(batch.body()).doesNotContain(email);

        assertThat(send("POST", "/internal/users/batch", "[\"" + userId + "\"]", null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json);
        builder.method(method, body);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(matcher.find()).as("field '" + name + "' in " + json).isTrue();
        return matcher.group(1);
    }
}
