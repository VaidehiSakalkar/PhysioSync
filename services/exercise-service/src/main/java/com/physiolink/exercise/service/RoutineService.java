package com.physiolink.exercise.service;

import com.physiolink.exercise.entity.Exercise;
import com.physiolink.exercise.entity.Routine;
import com.physiolink.exercise.repository.ExerciseRepository;
import com.physiolink.exercise.repository.RoutineRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoutineService {

    private final RoutineRepository routineRepo;
    private final ExerciseRepository exerciseRepo;

    public RoutineService(RoutineRepository routineRepo, ExerciseRepository exerciseRepo) {
        this.routineRepo = routineRepo;
        this.exerciseRepo = exerciseRepo;
    }

    @Cacheable(value = "activeRoutinesByPatient", key = "#patientId")
    public List<Routine> getActiveForPatient(UUID patientId) {
        return routineRepo.findByPatientIdAndActiveTrue(patientId);
    }

    @Cacheable(value = "allRoutinesByPatient", key = "#patientId")
    public List<Routine> getAllForPatient(UUID patientId) {
        return routineRepo.findByPatientId(patientId);
    }

    @Cacheable(value = "routinesByPhysio", key = "#physioId")
    public List<Routine> getByPhysio(UUID physioId) {
        return routineRepo.findByPhysioId(physioId);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "activeRoutinesByPatient", key = "#patientId"),
        @CacheEvict(value = "allRoutinesByPatient", key = "#patientId"),
        @CacheEvict(value = "routinesByPhysio", key = "#physioId")
    })
    public Routine create(UUID physioId, UUID patientId, String name, String notes, List<UUID> exerciseIds) {
        Routine routine = new Routine();
        routine.setPhysioId(physioId);
        routine.setPatientId(patientId);
        routine.setName(name);
        routine.setNotes(notes);

        List<Exercise> exercises = exerciseRepo.findAllById(exerciseIds);
        routine.setExercises(exercises);

        return routineRepo.save(routine);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "activeRoutinesByPatient", key = "#result.patientId"),
        @CacheEvict(value = "allRoutinesByPatient", key = "#result.patientId"),
        @CacheEvict(value = "routinesByPhysio", key = "#result.physioId")
    })
    public Routine deactivate(UUID id) {
        Routine routine = routineRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Routine not found: " + id));
        routine.setActive(false);
        return routineRepo.save(routine);
    }
}
