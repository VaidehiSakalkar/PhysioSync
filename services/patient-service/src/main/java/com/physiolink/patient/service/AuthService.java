package com.physiolink.patient.service;

import com.physiolink.patient.dto.AuthResponse;
import com.physiolink.patient.dto.LoginRequest;
import com.physiolink.patient.dto.RegisterRequest;
import com.physiolink.patient.entity.Patient;
import com.physiolink.patient.entity.User;
import com.physiolink.patient.repository.PatientRepository;
import com.physiolink.patient.repository.UserRepository;
import com.physiolink.patient.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepo;
    private final PatientRepository patientRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepo,
                       PatientRepository patientRepo,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.patientRepo = patientRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new IllegalArgumentException("Email already registered: " + req.email());
        }

        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setName(req.name());
        user.setPhone(req.phone());

        // Default to PATIENT if role is not specified or invalid
        User.Role role = User.Role.PATIENT;
        if ("PHYSIO".equalsIgnoreCase(req.role())) {
            role = User.Role.PHYSIO;
        }
        user.setRole(role);
        userRepo.save(user);

        // Create patient profile row for PATIENT role
        if (role == User.Role.PATIENT) {
            Patient patient = new Patient();
            patient.setUser(user);
            patientRepo.save(patient);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }
}
