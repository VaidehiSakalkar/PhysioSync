package com.physiolink.exercise.kafka;

import java.util.List;
import java.util.UUID;

/**
 * Event payload published to Kafka topic "session.completed".
 * Consumed by the ML service to trigger recovery plan generation.
 */
public record SessionCompletedEvent(
        UUID sessionLogId,
        UUID patientId,
        UUID exerciseId,
        int repsCompleted,
        double avgAngleAccuracy,
        List<Double> repAngles
) {}
