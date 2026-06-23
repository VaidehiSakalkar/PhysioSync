package com.physiolink.patient.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter @Setter @NoArgsConstructor
public class Patient {

    @Id
    private UUID id;   // same UUID as users.id

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id")
    private User user;

    @Column(name = "medical_history", columnDefinition = "TEXT")
    private String medicalHistory;

    @Column(name = "assigned_physio_id")
    private UUID assignedPhysioId;

    @Column(name = "emergency_contact")
    private String emergencyContact;
}
