package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ChecklistResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChecklistResponseRepository
        extends JpaRepository<ChecklistResponse, Long> {

    // 특정 활동 기록에 이미 저장된 체크리스트 응답을 모두 조회한다.
    List<ChecklistResponse> findByActivityRecordId(
            Long activityRecordId
    );

    /**
     * AI 활동 매칭용 최근 활동기록의 체크리스트 응답을
     * 여러 ActivityRecord 기준으로 한 번에 조회합니다.
     *
     * AI 요청에서 ChecklistItem.code를 사용하므로
     * checklistItem도 함께 조회합니다.
     */
    @Query("""
        SELECT cr
        FROM ChecklistResponse cr
        JOIN FETCH cr.activityRecord ar
        JOIN FETCH cr.checklistItem ci
        WHERE ar.id IN :recordIds
        """)
    List<ChecklistResponse> findByActivityRecordIdsWithItems(
            @Param("recordIds") List<Long> recordIds
    );
}