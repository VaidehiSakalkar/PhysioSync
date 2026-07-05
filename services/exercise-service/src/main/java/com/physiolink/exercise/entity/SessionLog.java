package com.physiolink.exercise.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "session_logs")
@Getter @Setter @NoArgsConstructor
public class SessionLog implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(name = "reps_completed", nullable = false)
    private int repsCompleted;

    @Column(name = "avg_angle_accuracy", nullable = false)
    private double avgAngleAccuracy;

    @CreationTimestamp
    @Column(name = "session_time", nullable = false, updatable = false)
    private OffsetDateTime sessionTime;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Type(JsonType.class)
    @Column(name = "rep_angles", columnDefinition = "jsonb")
    private List<Double> repAngles = List.of();
}
