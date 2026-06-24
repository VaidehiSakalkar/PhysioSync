package com.physiolink.patient.service;

import com.physiolink.patient.dto.AuthResponse;
import com.physiolink.patient.dto.LoginRequest;
import com.physiolink.patient.dto.RegisterRequest;
import com.physiolink.patient.entity.User;
import com.physiolink.patient.repository.PatientRepository;
import com.physiolink.patient.repository.UserRepository;
import com.physiolink.patient.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService — pure Mockito, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private PatientRepository patientRepo;
    @Mock private JwtUtil jwtUtil;

    // Use real BCrypt so password matching works correctly
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepo, patientRepo, passwordEncoder, jwtUtil);
    }

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    void register_patientRole_savesUserAndPatient() {
        RegisterRequest req = new RegisterRequest("alice@test.com", "password123", "Alice", "+1234", "PATIENT");

        when(userRepo.existsByEmail("alice@test.com")).thenReturn(false);

        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("alice@test.com");
        saved.setName("Alice");
        saved.setRole(User.Role.PATIENT);
        saved.setPasswordHash(passwordEncoder.encode("password123"));
        when(userRepo.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken(any(), eq("PATIENT"))).thenReturn("mock-token");

        AuthResponse resp = authService.register(req);

        assertThat(resp.token()).isEqualTo("mock-token");
        assertThat(resp.email()).isEqualTo("alice@test.com");
        assertThat(resp.name()).isEqualTo("Alice");
        assertThat(resp.role()).isEqualTo("PATIENT");

        // Should save a Patient profile row
        verify(patientRepo, times(1)).save(any());
    }

    @Test
    void register_physioRole_doesNotCreatePatientRow() {
        RegisterRequest req = new RegisterRequest("physio@test.com", "securepass", "Dr Bob", null, "PHYSIO");

        when(userRepo.existsByEmail("physio@test.com")).thenReturn(false);

        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("physio@test.com");
        saved.setName("Dr Bob");
        saved.setRole(User.Role.PHYSIO);
        saved.setPasswordHash(passwordEncoder.encode("securepass"));
        when(userRepo.save(any(User.class))).thenReturn(saved);
        when(jwtUtil.generateToken(any(), eq("PHYSIO"))).thenReturn("physio-token");

        AuthResponse resp = authService.register(req);

        assertThat(resp.role()).isEqualTo("PHYSIO");
        // Physio should NOT get a Patient row
        verify(patientRepo, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsIllegalArgument() {
        RegisterRequest req = new RegisterRequest("dup@test.com", "pass", "Dup", null, "PATIENT");
        when(userRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void register_invalidRole_defaultsToPatient() {
        RegisterRequest req = new RegisterRequest("x@test.com", "pass", "X", null, "ADMIN");

        when(userRepo.existsByEmail("x@test.com")).thenReturn(false);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("x@test.com");
        saved.setName("X");
        saved.setRole(User.Role.PATIENT);
        saved.setPasswordHash(passwordEncoder.encode("pass"));
        when(userRepo.save(userCaptor.capture())).thenReturn(saved);
        when(jwtUtil.generateToken(any(), eq("PATIENT"))).thenReturn("tok");

        authService.register(req);

        assertThat(userCaptor.getValue().getRole()).isEqualTo(User.Role.PATIENT);
    }

    @Test
    void register_passwordIsHashed() {
        RegisterRequest req = new RegisterRequest("h@test.com", "plaintext", "H", null, "PATIENT");

        when(userRepo.existsByEmail("h@test.com")).thenReturn(false);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        User saved = new User();
        saved.setId(UUID.randomUUID());
        saved.setEmail("h@test.com");
        saved.setName("H");
        saved.setRole(User.Role.PATIENT);
        saved.setPasswordHash(passwordEncoder.encode("plaintext"));
        when(userRepo.save(userCaptor.capture())).thenReturn(saved);
        when(jwtUtil.generateToken(any(), anyString())).thenReturn("tok");

        authService.register(req);

        String captured = userCaptor.getValue().getPasswordHash();
        // Must not store plaintext
        assertThat(captured).isNotEqualTo("plaintext");
        // BCrypt hash starts with $2a$ or $2b$
        assertThat(captured).startsWith("$2");
        // BCrypt can verify the original password
        assertThat(passwordEncoder.matches("plaintext", captured)).isTrue();
    }

    // ─── login ────────────────────────────────────────────────────────────────

    @Test
    void login_correctCredentials_returnsToken() {
        String rawPassword = "mypassword";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setName("User");
        user.setRole(User.Role.PATIENT);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(user.getId(), "PATIENT")).thenReturn("login-token");

        LoginRequest req = new LoginRequest("user@test.com", rawPassword);
        AuthResponse resp = authService.login(req);

        assertThat(resp.token()).isEqualTo("login-token");
        assertThat(resp.email()).isEqualTo("user@test.com");
    }

    @Test
    void login_wrongPassword_throwsIllegalArgument() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("u@test.com");
        user.setPasswordHash(passwordEncoder.encode("correct"));
        user.setRole(User.Role.PATIENT);

        when(userRepo.findByEmail("u@test.com")).thenReturn(Optional.of(user));

        LoginRequest req = new LoginRequest("u@test.com", "wrong");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_unknownEmail_throwsIllegalArgument() {
        when(userRepo.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest("ghost@test.com", "pass");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_tokenContainsUserId() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("t@test.com");
        user.setRole(User.Role.PHYSIO);
        user.setPasswordHash(passwordEncoder.encode("pw"));

        when(userRepo.findByEmail("t@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken(userId, "PHYSIO")).thenReturn("physio-jwt");

        AuthResponse resp = authService.login(new LoginRequest("t@test.com", "pw"));
        assertThat(resp.userId()).isEqualTo(userId);
        assertThat(resp.token()).isEqualTo("physio-jwt");
    }
}
