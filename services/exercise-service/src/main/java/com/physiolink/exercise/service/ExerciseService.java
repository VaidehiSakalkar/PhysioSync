package com.physiolink.exercise.service;

import com.physiolink.exercise.entity.Exercise;
import com.physiolink.exercise.repository.ExerciseRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

    @Cacheable(value = "allExercises")
    public List<Exercise> listAll() {
        return exerciseRepo.findAll();
    }

    @Cacheable(value = "exercisesByJoint", key = "#joint")
    public List<Exercise> findByJoint(String joint) {
        return exerciseRepo.findByTargetJoint(joint);
    }

    @Cacheable(value = "exercises", key = "#id")
    public Exercise getById(UUID id) {
        return exerciseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exercise not found: " + id));
    }

    @Transactional
    @CachePut(value = "exercises", key = "#result.id")
    @Caching(evict = {
        @CacheEvict(value = "allExercises", allEntries = true),
        @CacheEvict(value = "exercisesByJoint", key = "#exercise.targetJoint")
    })
    public Exercise create(Exercise exercise) {
        return exerciseRepo.save(exercise);
    }

    @Transactional
    @CachePut(value = "exercises", key = "#id")
    @Caching(evict = {
        @CacheEvict(value = "allExercises", allEntries = true),
        @CacheEvict(value = "exercisesByJoint", allEntries = true)
    })
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
