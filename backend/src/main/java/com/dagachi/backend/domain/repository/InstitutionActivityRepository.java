package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
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
import java.util.List;
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
     * APPROVED를 전달하면 승인 인원,
     * PENDING을 전달하면 승인 대기 인원이 계산된다.
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

    /**
     * 새로운 기관 활동을 저장한다.
     */
    CareActivity save(
            CareActivity activity
    );

    /**
     * 특정 기관 활동의 신청자 목록을 조회한다.
     */
    @Query(
            value = """
                    SELECT application
                    FROM ActivityApplication application
                    JOIN FETCH application.user volunteer
                    LEFT JOIN FETCH application.approvedBy
                    WHERE application.activity.id = :activityId
                      AND application.activity.institution.id = :institutionId
                      AND (
                          :hasStatus = false
                          OR application.status = :status
                      )
                    """,
            countQuery = """
                    SELECT COUNT(application.id)
                    FROM ActivityApplication application
                    WHERE application.activity.id = :activityId
                      AND application.activity.institution.id = :institutionId
                      AND (
                          :hasStatus = false
                          OR application.status = :status
                      )
                    """
    )
    Page<ActivityApplication> findActivityApplications(
            @Param("institutionId")
            Long institutionId,

            @Param("activityId")
            Long activityId,

            @Param("hasStatus")
            boolean hasStatus,

            @Param("status")
            ApplicationStatus status,

            Pageable pageable
    );

    /**
     * 기관, 활동, 신청 번호가 모두 일치하는 신청서를 조회한다.
     */
    @Query("""
            SELECT application
            FROM ActivityApplication application
            JOIN FETCH application.user
            LEFT JOIN FETCH application.approvedBy
            WHERE application.id = :applicationId
              AND application.activity.id = :activityId
              AND application.activity.institution.id = :institutionId
            """)
    Optional<ActivityApplication>
    findActivityApplication(
            @Param("institutionId")
            Long institutionId,

            @Param("activityId")
            Long activityId,

            @Param("applicationId")
            Long applicationId
    );

    /**
     * 해당 기관에서 처리 가능한 전체 승인 대기 신청자 수를 조회한다.
     *
     * 모집 중인 활동의 PENDING 신청만 집계한다.
     * 사이드바 활동 관리 배지에 사용한다.
     */
    @Query("""
            SELECT COUNT(application.id)
            FROM ActivityApplication application
            WHERE application.activity.institution.id = :institutionId
              AND application.activity.status = :activityStatus
              AND application.status = :applicationStatus
            """)
    long countInstitutionApplications(
            @Param("institutionId")
            Long institutionId,

            @Param("activityStatus")
            ActivityStatus activityStatus,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus
    );

    /**
     * 승인 대기 신청이 존재하는 활동을 활동별로 집계한다.
     *
     * 모집 중인 활동만 조회하며,
     * 승인 대기 인원이 많은 활동부터 정렬한다.
     */
    @Query("""
            SELECT
                activity.id AS activityId,
                recipient.id AS recipientId,
                recipient.name AS recipientName,
                activity.scheduledAt AS scheduledAt,
                COUNT(application.id) AS pendingCount
            FROM ActivityApplication application
            JOIN application.activity activity
            JOIN activity.recipient recipient
            WHERE activity.institution.id = :institutionId
              AND activity.status = :activityStatus
              AND application.status = :applicationStatus
            GROUP BY
                activity.id,
                recipient.id,
                recipient.name,
                activity.scheduledAt
            ORDER BY
                COUNT(application.id) DESC,
                activity.scheduledAt ASC
            """)
    List<PendingApplicationActivityProjection>
    findPendingApplicationActivities(
            @Param("institutionId")
            Long institutionId,

            @Param("activityStatus")
            ActivityStatus activityStatus,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus
    );

    /**
     * 활동별 승인 대기 신청 현황 Projection.
     */
    interface PendingApplicationActivityProjection {

        Long getActivityId();

        Long getRecipientId();

        String getRecipientName();

        LocalDateTime getScheduledAt();

        Long getPendingCount();
    }
}