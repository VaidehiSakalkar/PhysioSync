package com.physiolink.exercise.repository;

import com.physiolink.exercise.entity.RecoveryPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RecoveryPlanRepository extends JpaRepository<RecoveryPlan, UUID> {
    List<RecoveryPlan> findByPatientIdOrderByGeneratedAtDesc(UUID patientId);
}
