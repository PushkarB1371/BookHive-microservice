package com.bookhive.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class GatewayRoutesTest {

    @Autowired
    private RouteLocator routeLocator;

    private Optional<Route> routeFor(String path) {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull();
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
        return routes.stream()
                .filter(r -> Boolean.TRUE.equals(Mono.from(r.getPredicate().apply(exchange)).block()))
                .findFirst();
    }

    @Test
    void authAndUserPathsGoToUserService() {
        assertThat(routeFor("/api/auth/login")).isPresent();
        assertThat(routeFor("/api/auth/login").orElseThrow().getUri().toString()).isEqualTo("lb://user-service");
        assertThat(routeFor("/api/users/me").orElseThrow().getUri().toString()).isEqualTo("lb://user-service");
    }

    @Test
    void bookAndClubPathsGoToClubService() {
        assertThat(routeFor("/api/books/paginated").orElseThrow().getUri().toString()).isEqualTo("lb://club-service");
        assertThat(routeFor("/api/books/abc/reviews").orElseThrow().getUri().toString()).isEqualTo("lb://club-service");
        assertThat(routeFor("/api/clubs/grouped/paged").orElseThrow().getUri().toString()).isEqualTo("lb://club-service");
    }

    @Test
    void notificationPathsGoToNotificationService() {
        assertThat(routeFor("/api/notifications/me/unread-count").orElseThrow().getUri().toString())
                .isEqualTo("lb://notification-service");
    }

    @Test
    void internalEndpointsAreNotReachableThroughTheGateway() {
        assertThat(routeFor("/internal/users/batch")).isEmpty();
    }
}
