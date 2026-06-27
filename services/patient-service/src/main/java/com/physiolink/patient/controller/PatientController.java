package com.physiolink.patient.controller;

import com.physiolink.patient.dto.PatientProfileResponse;
import com.physiolink.patient.dto.UserResponse;
import com.physiolink.patient.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class PatientController {

    private final UserService userService;

    public PatientController(UserService userService) {
        this.userService = userService;
    }

    /** GET /api/patients/me — current patient's own profile */
    @GetMapping("/api/patients/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientProfileResponse> getMyProfile(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.getPatientProfile(userId));
    }

    /** PUT /api/patients/me — update own patient profile fields */
    @PutMapping("/api/patients/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<PatientProfileResponse> updateMyProfile(
            Authentication auth,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(auth.getName());
        UUID assignedPhysioId = body.containsKey("assignedPhysioId") 
                ? UUID.fromString(body.get("assignedPhysioId")) : null;
        
        return ResponseEntity.ok(userService.updatePatientProfile(
                userId,
                body.get("medicalHistory"),
                body.get("emergencyContact"),
                assignedPhysioId));
    }

    /** GET /api/patients/{id} — physio looks up a specific patient */
    @GetMapping("/api/patients/{id}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<PatientProfileResponse> getPatient(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getPatientProfile(id));
    }

    /** GET /api/physios/patients — physio lists their assigned patients */
    @GetMapping("/api/physios/patients")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<List<PatientProfileResponse>> getMyPatients(Authentication auth) {
        UUID physioId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.getPatientsForPhysio(physioId));
    }

    /** GET /api/physios/me — physio's own user profile */
    @GetMapping("/api/physios/me")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<UserResponse> getPhysioProfile(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /** GET /api/physios — list all physios */
    @GetMapping("/api/physios")
    @PreAuthorize("hasRole('PATIENT') or hasRole('PHYSIO')")
    public ResponseEntity<List<UserResponse>> getAllPhysios() {
        return ResponseEntity.ok(userService.getAllPhysios());
    }
}
