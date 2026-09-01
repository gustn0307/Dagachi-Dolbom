package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityApplicationRepository
        extends JpaRepository<ActivityApplication, Long> {

    // 특정 활동에 대해 사용자가 특정 신청 상태인지 확인한다.
    boolean existsByActivityIdAndUserIdAndStatus(
            Long activityId,
            Long userId,
            ApplicationStatus status
    );
}