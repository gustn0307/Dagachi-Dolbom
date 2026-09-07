package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {
    Optional<ActivityRecord> findByActivity_Id(Long activityId);
}