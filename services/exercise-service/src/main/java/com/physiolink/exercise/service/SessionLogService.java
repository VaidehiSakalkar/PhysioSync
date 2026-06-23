package com.physiolink.exercise.service;

import com.physiolink.exercise.entity.SessionLog;
import com.physiolink.exercise.kafka.SessionCompletedEvent;
import com.physiolink.exercise.kafka.SessionEventProducer;
import com.physiolink.exercise.repository.SessionLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SessionLogService {

    private final SessionLogRepository sessionLogRepo;
    private final SessionEventProducer eventProducer;

    public SessionLogService(SessionLogRepository sessionLogRepo, SessionEventProducer eventProducer) {
        this.sessionLogRepo = sessionLogRepo;
        this.eventProducer = eventProducer;
    }

    public Page<SessionLog> getByPatient(UUID patientId, int page, int size) {
        return sessionLogRepo.findByPatientIdOrderBySessionTimeDesc(patientId, PageRequest.of(page, size));
    }

    public List<SessionLog> getRecentByPatient(UUID patientId) {
        return sessionLogRepo.findTop10ByPatientIdOrderBySessionTimeDesc(patientId);
    }

    @Transactional
    public SessionLog save(SessionLog log) {
        SessionLog saved = sessionLogRepo.save(log);

        // Publish Kafka event for ML service to process asynchronously
        SessionCompletedEvent event = new SessionCompletedEvent(
                saved.getId(), saved.getPatientId(), saved.getExerciseId(),
                saved.getRepsCompleted(), saved.getAvgAngleAccuracy(), saved.getRepAngles());
        eventProducer.publish(event);

        return saved;
    }
}
