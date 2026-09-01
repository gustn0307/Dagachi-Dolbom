package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRecordRepository
        extends JpaRepository<ActivityRecord, Long> {
}