package com.bookhive.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.user.dto.UserSummary;
import com.bookhive.user.service.UserService;

/**
 * Service-to-service endpoints. The API gateway has no route for /internal/**, so browsers can't reach this;
 * club-service calls it through OpenFeign (passing the caller's JWT along).
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserService userService;

    public InternalUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/batch")
    public List<UserSummary> batch(@RequestBody List<UUID> ids) {
        return userService.summaries(ids);
    }
}
