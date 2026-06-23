package com.physiolink.exercise.controller;

import com.physiolink.exercise.entity.Routine;
import com.physiolink.exercise.service.RoutineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/exercises/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    /** Patient fetches their own active routines */
    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<Routine>> getMyRoutines(Authentication auth) {
        return ResponseEntity.ok(routineService.getActiveForPatient(UUID.fromString(auth.getName())));
    }

    /** Physio fetches routines for a specific patient */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<List<Routine>> getForPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(routineService.getAllForPatient(patientId));
    }

    /** Physio creates a new routine */
    @PostMapping
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Routine> create(Authentication auth, @RequestBody Map<String, Object> body) {
        UUID physioId   = UUID.fromString(auth.getName());
        UUID patientId  = UUID.fromString((String) body.get("patientId"));
        String name     = (String) body.get("name");
        String notes    = (String) body.get("notes");

        @SuppressWarnings("unchecked")
        List<UUID> exerciseIds = ((List<String>) body.get("exerciseIds"))
                .stream().map(UUID::fromString).toList();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(routineService.create(physioId, patientId, name, notes, exerciseIds));
    }

    /** Physio deactivates a routine */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Routine> deactivate(@PathVariable UUID id) {
        return ResponseEntity.ok(routineService.deactivate(id));
    }
}
