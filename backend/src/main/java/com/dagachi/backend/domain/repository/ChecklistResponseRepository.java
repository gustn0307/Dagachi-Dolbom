package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistResponseRepository
        extends JpaRepository<ChecklistResponse, Long> {

    // 특정 활동 기록에 이미 저장된 체크리스트 응답을 모두 조회한다.
    List<ChecklistResponse> findByActivityRecordId(
            Long activityRecordId
    );
}