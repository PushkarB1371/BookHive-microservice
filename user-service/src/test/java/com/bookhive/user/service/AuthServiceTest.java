package com.bookhive.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.bookhive.user.dto.AuthResponse;
import com.bookhive.user.dto.LoginRequest;
import com.bookhive.user.dto.SignupRequest;
import com.bookhive.user.entity.User;
import com.bookhive.user.enums.UserRole;
import com.bookhive.user.exception.InvalidCredentialsException;
import com.bookhive.user.repository.UserRepository;
import com.bookhive.user.security.JwtUtil;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, encoder, jwtUtil);
    }

    @Test
    void signupCreatesAMemberWithAHashedPasswordAndReturnsAUsableToken() {
        when(userRepository.existsByEmail("ann@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AuthResponse response = authService.signup(new SignupRequest("  Ann ", " Ann@Example.com ", "secret1"));

        assertThat(response.name()).isEqualTo("Ann");
        assertThat(response.email()).isEqualTo("ann@example.com");
        assertThat(response.role()).isEqualTo("MEMBER");
        assertThat(jwtUtil.parse(response.token()).orElseThrow().id()).isEqualTo(response.userId());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isNotEqualTo("secret1");
        assertThat(encoder.matches("secret1", captor.getValue().getPassword())).isTrue();
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.MEMBER);
    }

    @Test
    void signupRejectsAnEmailThatIsAlreadyRegistered() {
        when(userRepository.existsByEmail("ann@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("Ann", "ann@example.com", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any());
    }

    @Test
    void signupRejectsABlankName() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("  ", "ann@example.com", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name");
    }

    @Test
    void signupRejectsAnInvalidEmail() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("Ann", "not-an-email", "secret1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void signupRejectsAShortPassword() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("Ann", "ann@example.com", "123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password");
    }

    @Test
    void loginSucceedsWithTheRightPassword() {
        User user = storedUser("ann@example.com", "secret1", UserRole.ADMIN);
        when(userRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("ANN@example.com", "secret1"));

        assertThat(response.role()).isEqualTo("ADMIN");
        assertThat(jwtUtil.parse(response.token()).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    void loginFailsWithTheWrongPassword() {
        User user = storedUser("ann@example.com", "secret1", UserRole.MEMBER);
        when(userRepository.findByEmail("ann@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("ann@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginFailsForAnUnknownEmailWithTheSameMessage() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "whatever1")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }

    private User storedUser(String email, String rawPassword, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Ann");
        user.setEmail(email);
        user.setPassword(encoder.encode(rawPassword));
        user.setRole(role);
        return user;
    }
}
