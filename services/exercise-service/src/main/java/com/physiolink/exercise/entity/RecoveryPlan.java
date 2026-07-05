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
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "recovery_plans")
@Getter @Setter @NoArgsConstructor
public class RecoveryPlan implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @Type(JsonType.class)
    @Column(name = "plan_json", columnDefinition = "jsonb")
    private Map<String, Object> planJson;

    @Column(name = "progression_notes", columnDefinition = "TEXT")
    private String progressionNotes;

    @Type(JsonType.class)
    @Column(name = "red_flags", columnDefinition = "jsonb")
    private java.util.List<String> redFlags = java.util.List.of();

    @Column(name = "session_log_id")
    private UUID sessionLogId;
}
