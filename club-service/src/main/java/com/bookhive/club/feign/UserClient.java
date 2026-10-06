package com.bookhive.club.feign;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Declarative HTTP client for user-service. "user-service" is resolved through Eureka and load-balanced.
 * Set bookhive.clients.user-service.url to call a fixed address instead (handy without Eureka).
 */
@FeignClient(name = "user-service", url = "${bookhive.clients.user-service.url:}")
public interface UserClient {

    @PostMapping("/internal/users/batch")
    List<UserSummary> getUsers(@RequestHeader("Authorization") String authorization,
                               @RequestBody List<UUID> ids);
}
