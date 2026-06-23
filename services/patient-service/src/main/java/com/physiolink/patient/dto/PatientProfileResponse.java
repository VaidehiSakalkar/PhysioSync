package com.physiolink.patient.dto;

import java.util.UUID;

public record PatientProfileResponse(
        UUID id,
        String email,
        String name,
        String phone,
        String medicalHistory,
        UUID assignedPhysioId,
        String emergencyContact
) {}
