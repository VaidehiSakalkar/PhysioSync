package com.physiolink.patient.dto;

import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        String phone,
        LocalDate dateOfBirth,
        String role
) {}
