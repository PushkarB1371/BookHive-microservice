package com.bookhive.user.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookhive.user.dto.ProfileResponse;
import com.bookhive.user.dto.UserSummary;
import com.bookhive.user.entity.User;
import com.bookhive.user.repository.UserRepository;

@Service
public class UserService {

    /** Upper bound so one request can't ask for the whole table. */
    static final int MAX_BATCH = 200;

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> summaries(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> wanted = ids.stream().filter(Objects::nonNull).distinct().limit(MAX_BATCH).toList();
        return userRepository.findAllById(wanted).stream()
                .map(u -> new UserSummary(u.getId(), u.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileResponse profile(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new ProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
