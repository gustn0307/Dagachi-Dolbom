package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

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

    /**
     * 활동 번호와 기관 번호가 모두 일치하는 활동을 조회한다.
     *
     * 다른 기관의 활동이거나 존재하지 않는 활동이면
     * 조회 결과가 없는 것으로 처리된다.
     */
    @Query("""
            SELECT activity
            FROM CareActivity activity
            JOIN FETCH activity.recipient
            JOIN FETCH activity.createdBy
            WHERE activity.id = :activityId
              AND activity.institution.id = :institutionId
            """)
    Optional<CareActivity> findDetailActivity(
            @Param("institutionId")
            Long institutionId,

            @Param("activityId")
            Long activityId
    );

    /**
     * 해당 활동에서 특정 신청 상태인 인원수를 계산한다.
     *
     * status에 APPROVED를 전달하면 승인 인원,
     * PENDING을 전달하면 대기 인원이 계산된다.
     */
    @Query("""
            SELECT COUNT(application.id)
            FROM ActivityApplication application
            WHERE application.activity.id = :activityId
              AND application.status = :status
            """)
    long countApplications(
            @Param("activityId")
            Long activityId,

            @Param("status")
            ApplicationStatus status
    );

    /**
     * 해당 활동의 결과 기록을 조회한다.
     *
     * 아직 결과가 작성되지 않았다면
     * Optional.empty()가 반환된다.
     */
    @Query("""
            SELECT record
            FROM ActivityRecord record
            WHERE record.activity.id = :activityId
            """)
    Optional<ActivityRecord> findActivityRecord(
            @Param("activityId")
            Long activityId
    );
}