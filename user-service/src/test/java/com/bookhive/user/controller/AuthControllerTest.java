package com.bookhive.user.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bookhive.user.dto.AuthResponse;
import com.bookhive.user.dto.LoginRequest;
import com.bookhive.user.dto.SignupRequest;
import com.bookhive.user.exception.GlobalExceptionHandler;
import com.bookhive.user.exception.InvalidCredentialsException;
import com.bookhive.user.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void signupReturnsTheTokenPayload() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenReturn(new AuthResponse("tok", UUID.randomUUID(), "Ann", "ann@example.com", "MEMBER"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ann\",\"email\":\"ann@example.com\",\"password\":\"secret1\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"token\":\"tok\"")))
                .andExpect(content().string(containsString("\"role\":\"MEMBER\"")));
    }

    @Test
    void duplicateEmailIsABadRequestWithAMessage() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new IllegalArgumentException("Email already registered"));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ann\",\"email\":\"ann@example.com\",\"password\":\"secret1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Email already registered")));
    }

    @Test
    void badCredentialsAreUnauthorized() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ann@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("Invalid email or password")));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
