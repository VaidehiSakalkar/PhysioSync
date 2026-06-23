package com.physiolink.exercise.controller;

import com.physiolink.exercise.entity.RecoveryPlan;
import com.physiolink.exercise.service.RecoveryPlanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exercises/plans")
public class RecoveryPlanController {

    private final RecoveryPlanService planService;

    public RecoveryPlanController(RecoveryPlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<RecoveryPlan>> myPlans(Authentication auth) {
        return ResponseEntity.ok(planService.getByPatient(UUID.fromString(auth.getName())));
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<List<RecoveryPlan>> forPatient(@PathVariable UUID patientId) {
        return ResponseEntity.ok(planService.getByPatient(patientId));
    }

    /** Called by the ML service (internal) to store a generated plan */
    @PostMapping
    public ResponseEntity<RecoveryPlan> create(@RequestBody RecoveryPlan plan) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.save(plan));
    }
}
