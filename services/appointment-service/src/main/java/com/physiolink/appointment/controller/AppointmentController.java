package com.physiolink.appointment.controller;

import com.physiolink.appointment.client.PatientServiceClient;
import com.physiolink.appointment.entity.Appointment;
import com.physiolink.appointment.service.AppointmentService;
import com.physiolink.appointment.service.EmailService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private final AppointmentService appointmentService;
    private final EmailService emailService;
    private final PatientServiceClient patientServiceClient;

    public AppointmentController(AppointmentService appointmentService,
                                 EmailService emailService,
                                 PatientServiceClient patientServiceClient) {
        this.appointmentService = appointmentService;
        this.emailService = emailService;
        this.patientServiceClient = patientServiceClient;
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
        String physioIdStr = (String) body.get("physioId");
        if (physioIdStr == null || physioIdStr.isBlank()) {
            throw new IllegalArgumentException("physioId is required");
        }
        UUID physioId = UUID.fromString(physioIdStr);
        UUID patientId;

        // Physio books on behalf of patient; patient books for themselves
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PHYSIO"))) {
            String patientIdStr = (String) body.get("patientId");
            if (patientIdStr == null || patientIdStr.isBlank()) {
                throw new IllegalArgumentException("patientId is required when booking as a physio");
            }
            patientId = UUID.fromString(patientIdStr);
        } else {
            patientId = UUID.fromString(auth.getName());
        }

        OffsetDateTime scheduledAt = OffsetDateTime.parse((String) body.get("scheduledAt"));
        int duration = body.containsKey("durationMinutes") ? (Integer) body.get("durationMinutes") : 30;
        String notes = (String) body.getOrDefault("notes", "");

        Appointment appointment = appointmentService.book(patientId, physioId, scheduledAt, duration, notes);

        // Fetch patient's real name and email from patient-service, then send confirmation
        String callerId = auth.getName();
        String callerRole = auth.getAuthorities().iterator().next().getAuthority()
                .replace("ROLE_", "");
        PatientServiceClient.PatientInfo patientInfo =
                patientServiceClient.fetchPatientInfo(patientId, callerId, callerRole);

        emailService.sendBookingConfirmation(
                patientInfo.email(),
                patientInfo.name(),
                scheduledAt.format(DISPLAY_FMT));

        return ResponseEntity.status(HttpStatus.CREATED).body(appointment);
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
