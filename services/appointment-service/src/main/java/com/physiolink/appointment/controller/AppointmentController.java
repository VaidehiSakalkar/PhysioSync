package com.physiolink.appointment.controller;

import com.physiolink.appointment.entity.Appointment;
import com.physiolink.appointment.service.AppointmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<Appointment>> myAppointments(Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        String role = auth.getAuthorities().iterator().next().getAuthority();
        List<Appointment> result = role.contains("PHYSIO")
                ? appointmentService.getByPhysio(userId)
                : appointmentService.getByPatient(userId);
        return ResponseEntity.ok(result);
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT') or hasRole('PHYSIO')")
    public ResponseEntity<Appointment> book(Authentication auth, @RequestBody Map<String, Object> body) {
        UUID physioId = UUID.fromString((String) body.get("physioId"));
        UUID patientId;

        // Physio books on behalf of patient, patient books for themselves
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PHYSIO"))) {
            patientId = UUID.fromString((String) body.get("patientId"));
        } else {
            patientId = UUID.fromString(auth.getName());
        }

        OffsetDateTime scheduledAt = OffsetDateTime.parse((String) body.get("scheduledAt"));
        int duration = body.containsKey("durationMinutes") ? (Integer) body.get("durationMinutes") : 30;
        String notes = (String) body.getOrDefault("notes", "");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(appointmentService.book(patientId, physioId, scheduledAt, duration, notes));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Appointment> updateStatus(@PathVariable UUID id,
                                                    @RequestBody Map<String, String> body) {
        Appointment.Status newStatus = Appointment.Status.valueOf(body.get("status").toUpperCase());
        return ResponseEntity.ok(appointmentService.updateStatus(id, newStatus));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(appointmentService.getById(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
