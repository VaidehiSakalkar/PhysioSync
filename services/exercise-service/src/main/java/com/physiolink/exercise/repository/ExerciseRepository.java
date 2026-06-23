package com.physiolink.exercise.repository;

import com.physiolink.exercise.entity.Exercise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {
    List<Exercise> findByTargetJoint(String targetJoint);
}
