package com.bookhive.club.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

@Configuration
public class ResilienceConfig {

    /**
     * Default policy for every circuit breaker: open after 50% failures (min 5 calls in a window of 10),
     * stay open 15s, and give up on any single call after bookhive.circuit-breaker.timeout-ms.
     */
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCircuitBreaker(
            @Value("${bookhive.circuit-breaker.timeout-ms:4000}") long timeoutMs) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(15))
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofMillis(timeoutMs))
                        .build())
                .build());
    }
}
