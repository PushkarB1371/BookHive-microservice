package com.bookhive.club.feign;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.bookhive.club.security.CurrentUser;

/**
 * Looks up display names in user-service. Wrapped in a circuit breaker: if user-service is slow or down,
 * the page still loads and shows "Unknown reader" instead of failing the whole request.
 */
@Component
public class UserDirectory {

    public static final String UNKNOWN_USER = "Unknown reader";

    private static final Logger log = LoggerFactory.getLogger(UserDirectory.class);

    private final UserClient userClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public UserDirectory(UserClient userClient, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.userClient = userClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    /** One batched call for any number of ids. Ids that can't be resolved are simply missing from the map. */
    public Map<UUID, String> namesFor(Collection<UUID> ids) {
        Set<UUID> unique = ids.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.isEmpty()) {
            return Map.of();
        }

        // Read the caller's token here, on the request thread; the circuit breaker may run the call elsewhere.
        String authorization = CurrentUser.bearerToken();
        List<UUID> requested = new ArrayList<>(unique);

        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("user-service");
        List<UserSummary> found = circuitBreaker.run(
                () -> userClient.getUsers(authorization, requested),
                throwable -> {
                    log.warn("user-service lookup failed ({}), showing placeholder names", throwable.toString());
                    return List.<UserSummary>of();
                });

        Map<UUID, String> names = new HashMap<>();
        if (found != null) {
            for (UserSummary user : found) {
                names.put(user.id(), user.name());
            }
        }
        return names;
    }

    public String nameFor(UUID id) {
        if (id == null) {
            return UNKNOWN_USER;
        }
        return namesFor(List.of(id)).getOrDefault(id, UNKNOWN_USER);
    }
}
