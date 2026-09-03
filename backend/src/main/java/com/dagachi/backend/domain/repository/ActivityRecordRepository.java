package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {

    // 활동기록 수정/서명/제출 시 동시 변경을 막기 위해 해당 기록을 잠금 조회합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from ActivityRecord ar where ar.id = :id")
    Optional<ActivityRecord> findByIdForUpdate(@Param("id") Long id);
}