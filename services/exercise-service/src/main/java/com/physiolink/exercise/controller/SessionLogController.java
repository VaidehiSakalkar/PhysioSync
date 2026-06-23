package com.physiolink.exercise.controller;

import com.physiolink.exercise.entity.SessionLog;
import com.physiolink.exercise.service.SessionLogService;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exercises/sessions")
public class SessionLogController {

    private final SessionLogService sessionLogService;

    public SessionLogController(SessionLogService sessionLogService) {
        this.sessionLogService = sessionLogService;
    }

    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<SessionLog> logSession(Authentication auth, @RequestBody SessionLog log) {
        log.setPatientId(UUID.fromString(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionLogService.save(log));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<Page<SessionLog>> myHistory(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sessionLogService.getByPatient(UUID.fromString(auth.getName()), page, size));
    }

    @GetMapping("/my/recent")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<SessionLog>> myRecent(Authentication auth) {
        return ResponseEntity.ok(sessionLogService.getRecentByPatient(UUID.fromString(auth.getName())));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Page<SessionLog>> forPatient(
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sessionLogService.getByPatient(patientId, page, size));
    }
}
