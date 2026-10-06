package com.bookhive.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bookhive.club.cache.BookCache;
import com.bookhive.club.enums.NotificationType;
import com.bookhive.club.feign.UserDirectory;
import com.bookhive.club.messaging.NotificationPublisher;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * The whole service over real HTTP with real JWT security and a real Postgres. Only the things that live
 * outside this service are replaced: Kafka publishing, Redis, and the user-service name lookup.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "jwt.secret=" + ClubServiceFlowIntegrationTest.SECRET
})
class ClubServiceFlowIntegrationTest {

    static final String SECRET = "flow-test-secret-flow-test-secret-flow-test";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean private NotificationPublisher notificationPublisher;
    @MockitoBean private UserDirectory userDirectory;
    @MockitoBean private BookCache bookCache;

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void everyoneIsCalledReader() {
        when(userDirectory.namesFor(anyCollection())).thenAnswer(invocation -> {
            Map<UUID, String> names = new HashMap<>();
            for (Object id : (Collection<?>) invocation.getArgument(0)) {
                names.put((UUID) id, "Reader");
            }
            return names;
        });
    }

    @Test
    void aWholeClubJourney() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID annId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        String admin = token(adminId, "Admin", "ADMIN");
        String ann = token(annId, "Ann", "MEMBER");
        String bob = token(bobId, "Bob", "MEMBER");
        String outsider = token(outsiderId, "Zed", "MEMBER");

        // --- security basics
        assertThat(send("GET", "/api/books/paginated", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/api/books/paginated", null, ann + "x").statusCode()).isEqualTo(401);

        // --- only admins add books
        String bookJson = "{\"title\":\"Dune\",\"author\":\"Frank Herbert\",\"totalChapters\":20}";
        assertThat(send("POST", "/api/books", bookJson, ann).statusCode()).isEqualTo(403);
        HttpResponse<String> created = send("POST", "/api/books", bookJson, admin);
        assertThat(created.statusCode()).isEqualTo(200);
        String bookId = field(created.body(), "id");
        assertThat(send("GET", "/api/books/paginated", null, ann).body()).contains("Dune");
        assertThat(send("GET", "/api/books/" + bookId, null, ann).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/books/" + UUID.randomUUID(), null, ann).statusCode()).isEqualTo(404);

        // --- Ann starts a club, Bob joins
        HttpResponse<String> club = send("POST", "/api/clubs",
                "{\"name\":\"Sci-Fi Circle\",\"description\":\"We read sci-fi\"}", ann);
        assertThat(club.statusCode()).isEqualTo(200);
        String clubId = field(club.body(), "id");
        assertThat(club.body()).contains("\"isCurrentUserAdmin\":true");

        assertThat(send("POST", "/api/clubs/" + clubId + "/join", null, bob).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/clubs/" + clubId + "/join", null, bob).statusCode()).isEqualTo(400);
        verify(notificationPublisher).publish(eq(annId), eq(NotificationType.NEW_REPLY), contains("Bob joined"));

        // --- only the club admin picks the book; members get told
        assertThat(send("PUT", "/api/clubs/" + clubId + "/current-book/" + bookId, null, bob).statusCode()).isEqualTo(403);
        HttpResponse<String> picked = send("PUT", "/api/clubs/" + clubId + "/current-book/" + bookId, null, ann);
        assertThat(picked.statusCode()).isEqualTo(200);
        assertThat(picked.body()).contains("Dune");
        verify(notificationPublisher).publish(eq(bobId), eq(NotificationType.CHAPTER_UNLOCK), contains("Dune"));
        verify(notificationPublisher, never()).publish(eq(annId), eq(NotificationType.CHAPTER_UNLOCK), anyString());

        // --- a book that a club is reading can't be deleted
        assertThat(send("DELETE", "/api/books/" + bookId, null, admin).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/clubs/reading/" + bookId, null, bob).body()).contains("Sci-Fi Circle");

        // --- discussion: members only, replies notify the others in the chapter
        String annComment = "{\"chapterNumber\":1,\"content\":\"What a start\"}";
        assertThat(send("POST", "/api/clubs/" + clubId + "/comments", annComment, ann).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/clubs/" + clubId + "/comments",
                "{\"chapterNumber\":1,\"content\":\"Agreed!\"}", bob).statusCode()).isEqualTo(200);
        verify(notificationPublisher).publish(eq(annId), eq(NotificationType.NEW_REPLY), contains("Bob commented on Chapter 1"));
        assertThat(send("POST", "/api/clubs/" + clubId + "/comments", annComment, outsider).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/clubs/" + clubId + "/comments",
                "{\"chapterNumber\":1,\"content\":\"  \"}", ann).statusCode()).isEqualTo(400);

        HttpResponse<String> comments = send("GET", "/api/clubs/" + clubId + "/comments?chapter=1&page=0&size=10", null, bob);
        assertThat(comments.statusCode()).isEqualTo(200);
        assertThat(comments.body()).contains("What a start").contains("Agreed!").contains("\"userName\":\"Reader\"");
        assertThat(send("GET", "/api/clubs/" + clubId + "/comments?chapter=1", null, outsider).statusCode()).isEqualTo(403);

        // --- reading progress
        assertThat(send("GET", "/api/clubs/" + clubId + "/progress/me", null, bob).statusCode()).isEqualTo(204);
        assertThat(send("PUT", "/api/clubs/" + clubId + "/progress/me", "{\"currentChapter\":99}", bob).statusCode()).isEqualTo(400);
        assertThat(send("PUT", "/api/clubs/" + clubId + "/progress/me", "{\"currentChapter\":3}", bob).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/clubs/" + clubId + "/progress/me", null, bob).body()).contains("\"currentChapter\":3");
        assertThat(send("GET", "/api/clubs/" + clubId + "/progress", null, ann).body()).contains("\"currentChapter\":3");
        assertThat(send("GET", "/api/clubs/" + clubId + "/progress", null, outsider).statusCode()).isEqualTo(403);

        // --- reviews: one per person, admin who added the book is told
        assertThat(send("POST", "/api/books/" + bookId + "/reviews", "{\"rating\":9}", bob).statusCode()).isEqualTo(400);
        assertThat(send("POST", "/api/books/" + bookId + "/reviews",
                "{\"rating\":5,\"reviewText\":\"A masterpiece\"}", bob).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/books/" + bookId + "/reviews", "{\"rating\":4}", bob).statusCode()).isEqualTo(400);
        verify(notificationPublisher).publish(eq(adminId), eq(NotificationType.NEW_REPLY), contains("Bob reviewed Dune (5 stars)"));
        HttpResponse<String> reviews = send("GET", "/api/books/" + bookId + "/reviews?page=0&size=5", null, ann);
        assertThat(reviews.body()).contains("A masterpiece").contains("\"averageRating\":5.0");

        // --- the three tabs on the clubs page
        assertThat(send("GET", "/api/clubs/grouped/paged?type=mine", null, ann).body()).contains("Sci-Fi Circle");
        assertThat(send("GET", "/api/clubs/grouped/paged?type=joined", null, bob).body()).contains("Sci-Fi Circle");
        assertThat(send("GET", "/api/clubs/grouped/paged?type=discover", null, outsider).body()).contains("Sci-Fi Circle");
        assertThat(send("GET", "/api/clubs/grouped/paged?type=discover", null, ann).body()).doesNotContain("Sci-Fi Circle");
        assertThat(send("GET", "/api/clubs/grouped/paged?type=bogus", null, ann).statusCode()).isEqualTo(400);

        // --- deleting: only the creator, and then the book is free to delete
        assertThat(send("DELETE", "/api/clubs/" + clubId, null, bob).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/api/clubs/" + clubId, null, ann).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/clubs/" + clubId, null, ann).statusCode()).isEqualTo(404);
        assertThat(send("DELETE", "/api/books/" + bookId, null, ann).statusCode()).isEqualTo(403);
        assertThat(send("DELETE", "/api/books/" + bookId, null, admin).statusCode()).isEqualTo(200);
        verify(bookCache, org.mockito.Mockito.atLeastOnce()).evictAll();
    }

    // ------------------------------------------------------------------ helpers

    private String token(UUID id, String name, String role) {
        return Jwts.builder()
                .subject(id.toString())
                .claim("name", name)
                .claim("email", name.toLowerCase() + "@example.com")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private HttpResponse<String> send(String method, String path, String json, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** First "name":"value" in a JSON body (our records put the id first). */
    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertThat(matcher.find()).as("field '" + name + "' in " + json).isTrue();
        return matcher.group(1);
    }
}
