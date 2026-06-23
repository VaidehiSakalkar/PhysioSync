package com.physiolink.exercise.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "exercises")
@Getter @Setter @NoArgsConstructor
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_joint", nullable = false, length = 50)
    private String targetJoint;

    @Column(name = "target_angle_min", nullable = false)
    private double targetAngleMin;

    @Column(name = "target_angle_max", nullable = false)
    private double targetAngleMax;

    @Column(name = "target_reps", nullable = false)
    private int targetReps = 10;

    @Column(name = "target_sets", nullable = false)
    private int targetSets = 3;

    @Column(name = "video_url", length = 512)
    private String videoUrl;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
