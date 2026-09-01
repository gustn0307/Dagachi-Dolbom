package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 기관 담당자의 활동 관리 조회를 담당하는 Repository.
 *
 * 일반 사용자용 CareActivityRepository와 분리하여
 * 기관별 활동 조회 기능만 처리한다.
 */
public interface InstitutionActivityRepository
        extends Repository<CareActivity, Long> {

    /**
     * ACT-04 기관 활동 목록 조회.
     *
     * 조회 조건:
     * - 로그인 담당자의 소속 기관
     * - 활동 상태
     * - 돌봄 대상자
     * - 활동 예정 기간
     *
     * Service에서 필터 사용 여부와 기본값을 정리한 후 전달한다.
     */
    @Query(
            value = """
                    SELECT activity
                    FROM CareActivity activity
                    JOIN FETCH activity.recipient recipient
                    WHERE activity.institution.id = :institutionId

                      AND (
                          :hasStatus = false
                          OR activity.status = :status
                      )

                      AND (
                          :hasRecipient = false
                          OR recipient.id = :recipientId
                      )

                      AND activity.scheduledAt >= :dateFrom
                      AND activity.scheduledAt < :dateTo
                    """,
            countQuery = """
                    SELECT COUNT(activity.id)
                    FROM CareActivity activity
                    WHERE activity.institution.id = :institutionId

                      AND (
                          :hasStatus = false
                          OR activity.status = :status
                      )

                      AND (
                          :hasRecipient = false
                          OR activity.recipient.id = :recipientId
                      )

                      AND activity.scheduledAt >= :dateFrom
                      AND activity.scheduledAt < :dateTo
                    """
    )
    Page<CareActivity> findInstitutionActivities(
            @Param("institutionId")
            Long institutionId,

            @Param("hasStatus")
            boolean hasStatus,

            @Param("status")
            ActivityStatus status,

            @Param("hasRecipient")
            boolean hasRecipient,

            @Param("recipientId")
            Long recipientId,

            @Param("dateFrom")
            LocalDateTime dateFrom,

            @Param("dateTo")
            LocalDateTime dateTo,

            Pageable pageable
    );
}