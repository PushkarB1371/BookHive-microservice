package com.bookhive.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest
class ConfigServerApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void servesSharedAndPerServiceConfigFiles() {
        assertThat(new ClassPathResource("config/application.yml").exists()).isTrue();
        assertThat(new ClassPathResource("config/user-service.yml").exists()).isTrue();
        assertThat(new ClassPathResource("config/club-service.yml").exists()).isTrue();
        assertThat(new ClassPathResource("config/notification-service.yml").exists()).isTrue();
    }
}
