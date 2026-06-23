package com.physiolink.exercise.controller;

import com.physiolink.exercise.entity.Exercise;
import com.physiolink.exercise.service.ExerciseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exercises")
public class ExerciseController {

    private final ExerciseService exerciseService;

    public ExerciseController(ExerciseService exerciseService) {
        this.exerciseService = exerciseService;
    }

    @GetMapping
    public ResponseEntity<List<Exercise>> list(@RequestParam(required = false) String joint) {
        List<Exercise> result = (joint != null)
                ? exerciseService.findByJoint(joint)
                : exerciseService.listAll();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Exercise> getOne(@PathVariable UUID id) {
        return ResponseEntity.ok(exerciseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Exercise> create(Authentication auth, @RequestBody Exercise exercise) {
        exercise.setCreatedBy(UUID.fromString(auth.getName()));
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciseService.create(exercise));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PHYSIO')")
    public ResponseEntity<Exercise> update(@PathVariable UUID id, @RequestBody Exercise exercise) {
        return ResponseEntity.ok(exerciseService.update(id, exercise));
    }
}
