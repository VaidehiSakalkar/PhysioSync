package com.physiolink.patient.repository;

import com.physiolink.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    List<Patient> findByAssignedPhysioId(UUID physioId);
}
