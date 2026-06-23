package com.physiolink.appointment.service;

import com.physiolink.appointment.entity.Appointment;
import com.physiolink.appointment.repository.AppointmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentService {

    private final AppointmentRepository repo;

    public AppointmentService(AppointmentRepository repo) {
        this.repo = repo;
    }

    public List<Appointment> getByPatient(UUID patientId) {
        return repo.findByPatientIdOrderByScheduledAtDesc(patientId);
    }

    public List<Appointment> getByPhysio(UUID physioId) {
        return repo.findByPhysioIdOrderByScheduledAtDesc(physioId);
    }

    public Appointment getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found: " + id));
    }

    @Transactional
    public Appointment book(UUID patientId, UUID physioId, OffsetDateTime scheduledAt,
                            int durationMinutes, String notes) {
        Appointment appt = new Appointment();
        appt.setPatientId(patientId);
        appt.setPhysioId(physioId);
        appt.setScheduledAt(scheduledAt);
        appt.setDurationMinutes(durationMinutes);
        appt.setNotes(notes);
        appt.setVideoRoomId(UUID.randomUUID().toString());  // random room ID for WebRTC
        return repo.save(appt);
    }

    @Transactional
    public Appointment updateStatus(UUID id, Appointment.Status newStatus) {
        Appointment appt = getById(id);
        appt.setStatus(newStatus);
        return repo.save(appt);
    }
}
