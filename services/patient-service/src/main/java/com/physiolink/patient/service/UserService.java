package com.physiolink.patient.service;

import com.physiolink.patient.dto.PatientProfileResponse;
import com.physiolink.patient.dto.UserResponse;
import com.physiolink.patient.entity.Patient;
import com.physiolink.patient.entity.User;
import com.physiolink.patient.repository.PatientRepository;
import com.physiolink.patient.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final PatientRepository patientRepo;

    public UserService(UserRepository userRepo, PatientRepository patientRepo) {
        this.userRepo = userRepo;
        this.patientRepo = patientRepo;
    }

    public UserResponse getProfile(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toUserResponse(user);
    }

    public PatientProfileResponse getPatientProfile(UUID patientId) {
        Patient patient = patientRepo.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        return toPatientProfile(patient);
    }

    public List<PatientProfileResponse> getPatientsForPhysio(UUID physioId) {
        return patientRepo.findByAssignedPhysioId(physioId)
                .stream()
                .map(this::toPatientProfile)
                .toList();
    }

    public List<UserResponse> getAllPhysios() {
        return userRepo.findByRole(User.Role.PHYSIO)
                .stream()
                .map(this::toUserResponse)
                .toList();
    }

    public PatientProfileResponse updatePatientProfile(UUID patientId,
                                                       String medicalHistory,
                                                       String emergencyContact,
                                                       UUID assignedPhysioId) {
        Patient patient = patientRepo.findById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found"));
        if (medicalHistory != null)   patient.setMedicalHistory(medicalHistory);
        if (emergencyContact != null) patient.setEmergencyContact(emergencyContact);
        if (assignedPhysioId != null) patient.setAssignedPhysioId(assignedPhysioId);
        patientRepo.save(patient);
        return toPatientProfile(patient);
    }

    // ─── Mapping helpers ───────────────────────────────────────────────────────

    private UserResponse toUserResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(),
                u.getPhone(), u.getDateOfBirth(), u.getRole().name());
    }

    private PatientProfileResponse toPatientProfile(Patient p) {
        User u = p.getUser();
        return new PatientProfileResponse(p.getId(), u.getEmail(), u.getName(),
                u.getPhone(), p.getMedicalHistory(), p.getAssignedPhysioId(), p.getEmergencyContact());
    }
}
