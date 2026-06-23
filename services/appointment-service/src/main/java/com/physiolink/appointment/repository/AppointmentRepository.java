package com.physiolink.appointment.repository;

import com.physiolink.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientIdOrderByScheduledAtDesc(UUID patientId);

    List<Appointment> findByPhysioIdOrderByScheduledAtDesc(UUID physioId);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status = 'SCHEDULED'
              AND a.scheduledAt BETWEEN :from AND :to
            """)
    List<Appointment> findUpcoming(@Param("from") OffsetDateTime from,
                                   @Param("to")   OffsetDateTime to);
}
