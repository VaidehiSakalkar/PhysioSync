package com.physiolink.exercise.service;

import com.physiolink.exercise.entity.RecoveryPlan;
import com.physiolink.exercise.repository.RecoveryPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecoveryPlanService {

    private final RecoveryPlanRepository planRepo;

    public RecoveryPlanService(RecoveryPlanRepository planRepo) {
        this.planRepo = planRepo;
    }

    public List<RecoveryPlan> getByPatient(UUID patientId) {
        return planRepo.findByPatientIdOrderByGeneratedAtDesc(patientId);
    }

    @Transactional
    public RecoveryPlan save(RecoveryPlan plan) {
        return planRepo.save(plan);
    }
}
