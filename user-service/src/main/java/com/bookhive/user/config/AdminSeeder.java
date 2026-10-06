package com.bookhive.user.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.bookhive.user.entity.User;
import com.bookhive.user.enums.UserRole;
import com.bookhive.user.repository.UserRepository;

/** Creates the first ADMIN account (admins can add and delete books). Runs on every start, only acts once. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String name;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${bookhive.admin.email:}") String email,
                       @Value("${bookhive.admin.password:}") String password,
                       @Value("${bookhive.admin.name:BookHive Admin}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (userRepository.existsByEmail(normalized)) {
            return;
        }
        User admin = new User();
        admin.setName(name);
        admin.setEmail(normalized);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        log.info("Created default admin account {}", normalized);
    }
}
