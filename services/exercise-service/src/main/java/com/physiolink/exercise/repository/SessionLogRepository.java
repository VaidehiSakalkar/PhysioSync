package com.physiolink.exercise.repository;

import com.physiolink.exercise.entity.SessionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionLogRepository extends JpaRepository<SessionLog, UUID> {
    Page<SessionLog> findByPatientIdOrderBySessionTimeDesc(UUID patientId, Pageable pageable);
    List<SessionLog> findTop10ByPatientIdOrderBySessionTimeDesc(UUID patientId);
}
