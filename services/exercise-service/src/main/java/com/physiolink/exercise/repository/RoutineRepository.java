package com.physiolink.exercise.repository;

import com.physiolink.exercise.entity.Routine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoutineRepository extends JpaRepository<Routine, UUID> {
    List<Routine> findByPatientId(UUID patientId);
    List<Routine> findByPhysioId(UUID physioId);
    List<Routine> findByPatientIdAndActiveTrue(UUID patientId);
}
