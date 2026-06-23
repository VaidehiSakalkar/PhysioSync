package com.physiolink.exercise.service;

import com.physiolink.exercise.entity.Exercise;
import com.physiolink.exercise.repository.ExerciseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepo;

    public ExerciseService(ExerciseRepository exerciseRepo) {
        this.exerciseRepo = exerciseRepo;
    }

    public List<Exercise> listAll() {
        return exerciseRepo.findAll();
    }

    public List<Exercise> findByJoint(String joint) {
        return exerciseRepo.findByTargetJoint(joint);
    }

    public Exercise getById(UUID id) {
        return exerciseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exercise not found: " + id));
    }

    @Transactional
    public Exercise create(Exercise exercise) {
        return exerciseRepo.save(exercise);
    }

    @Transactional
    public Exercise update(UUID id, Exercise updated) {
        Exercise ex = getById(id);
        ex.setName(updated.getName());
        ex.setDescription(updated.getDescription());
        ex.setTargetJoint(updated.getTargetJoint());
        ex.setTargetAngleMin(updated.getTargetAngleMin());
        ex.setTargetAngleMax(updated.getTargetAngleMax());
        ex.setTargetReps(updated.getTargetReps());
        ex.setTargetSets(updated.getTargetSets());
        ex.setVideoUrl(updated.getVideoUrl());
        return exerciseRepo.save(ex);
    }
}
