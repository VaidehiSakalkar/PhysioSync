package com.physiolink.patient.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String name,
        String role
) {}
