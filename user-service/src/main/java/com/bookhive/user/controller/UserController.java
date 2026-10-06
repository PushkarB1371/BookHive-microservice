package com.bookhive.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bookhive.user.dto.ProfileResponse;
import com.bookhive.user.security.CurrentUser;
import com.bookhive.user.service.UserService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        return userService.profile(CurrentUser.id());
    }
}
