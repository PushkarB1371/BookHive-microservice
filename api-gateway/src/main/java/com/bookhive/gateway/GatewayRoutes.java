package com.bookhive.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table. "lb://name" means: ask Eureka where "name" is running and load-balance across its instances.
 * Note there is deliberately no route for /internal/** - those endpoints are service-to-service only.
 */
@Configuration
public class GatewayRoutes {

    public static final String USER_ROUTE = "user-service";
    public static final String CLUB_ROUTE = "club-service";
    public static final String NOTIFICATION_ROUTE = "notification-service";

    @Bean
    public RouteLocator bookhiveRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route(USER_ROUTE, r -> r.path("/api/auth/**", "/api/users/**").uri("lb://user-service"))
                .route(NOTIFICATION_ROUTE, r -> r.path("/api/notifications/**").uri("lb://notification-service"))
                .route(CLUB_ROUTE, r -> r.path("/api/books/**", "/api/clubs/**").uri("lb://club-service"))
                .build();
    }
}
