package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.VisitResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface ActivityApplicationRepository extends JpaRepository<ActivityApplication, Long> {

    Optional<ActivityApplication> findByActivity_IdAndUser_Id(Long activityId, Long userId);

    @Query("""
            SELECT aa.activity.id AS activityId, COUNT(aa) AS count
            FROM ActivityApplication aa
            WHERE aa.activity.id IN :activityIds
              AND aa.status = :status
            GROUP BY aa.activity.id
            """)
    List<ActivityApplicationCountProjection> countApprovedByActivityIds(
            @Param("activityIds") List<Long> activityIds,
            @Param("status") ApplicationStatus status
    );

    /**
     * REQ-ACT-17 자동배정 SAME_GENDER_ONE 유효 후보 검증용.
     *
     * 후보 활동 전체를 대상으로,
     * 현재 APPROVED 참여자 중 돌봄 대상자와 성별이 같은 인원 수를
     * 활동별로 한 번에 조회한다.
     *
     * 후보마다 개별 조회하지 않고 배치 조회하여
     * 자동배정 후보 수가 늘어나도 N+1 조회가 발생하지 않도록 한다.
     */
    @Query("""
        SELECT aa.activity.id AS activityId, COUNT(aa) AS count
        FROM ActivityApplication aa
        JOIN aa.activity ca
        JOIN ca.recipient cr
        JOIN aa.user u
        WHERE aa.activity.id IN :activityIds
          AND aa.status = com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
          AND u.gender = cr.gender
        GROUP BY aa.activity.id
        """)
    List<ActivityApplicationCountProjection> countApprovedSameGenderByActivityIds(
            @Param("activityIds") List<Long> activityIds
    );

    /**
     * ACT-01 목록 배지 표시용. 취소된 신청은 "신청 안 한 것"과 동일하게 취급하므로 제외한다.
     */
    @Query("""
            SELECT aa
            FROM ActivityApplication aa
            WHERE aa.user.id = :userId
              AND aa.activity.id IN :activityIds
              AND aa.status <> com.dagachi.backend.domain.enums.ApplicationStatus.CANCELED
            """)
    List<ActivityApplication> findActiveApplicationsByUserAndActivityIds(
            @Param("userId") Long userId,
            @Param("activityIds") List<Long> activityIds
    );

    /**
     * ACT-01 목록의 "신청자수" 표시용.
     * 활동별로 현재 걸려있는 신청(PENDING+APPROVED)만 센다. CANCELED/REJECTED는 제외.
     */
    @Query("""
            SELECT aa
            FROM ActivityApplication aa
            WHERE aa.activity.id IN :activityIds
              AND aa.status IN (
                    com.dagachi.backend.domain.enums.ApplicationStatus.PENDING,
                    com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
                  )
            """)
    List<ActivityApplication> findActiveApplicationsByActivityIds(
            @Param("activityIds") List<Long> activityIds
    );

    // 특정 활동에 대해 사용자가 특정 신청 상태인지 확인한다.
    boolean existsByActivityIdAndUserIdAndStatus(
            Long activityId,
            Long userId,
            ApplicationStatus status
    );

    interface ActivityApplicationCountProjection {
        Long getActivityId();

        Long getCount();
    }

    default Map<Long, Long> countApprovedMap(List<Long> activityIds) {
        return countApprovedByActivityIds(activityIds, ApplicationStatus.APPROVED)
                .stream()
                .collect(Collectors.toMap(
                        ActivityApplicationCountProjection::getActivityId,
                        ActivityApplicationCountProjection::getCount
                ));
    }

    /**
     * REQ-ACT-17 자동배정 후보 검증에서 사용할
     * 활동별 '대상자와 같은 성별의 APPROVED 참여자 수' Map을 만든다.
     *
     * 조회 결과가 없는 활동은 Map에 포함되지 않으며,
     * Service에서 기본값 0으로 처리한다.
     */
    default Map<Long, Long> countApprovedSameGenderMap(
            List<Long> activityIds
    ) {
        return countApprovedSameGenderByActivityIds(activityIds)
                .stream()
                .collect(Collectors.toMap(
                        ActivityApplicationCountProjection::getActivityId,
                        ActivityApplicationCountProjection::getCount
                ));
    }

    /**
     * REQ-AUTH-08 회원 탈퇴 차단 여부 확인.
     *
     * 탈퇴를 차단하는 경우:
     * 1. PENDING 신청이 존재하는 경우
     * 2. APPROVED 신청이면서 연결된 활동이 아직 종료되지 않은 경우
     *    - RECRUITING
     *    - READY
     *    - IN_PROGRESS
     *
     * 탈퇴를 차단하지 않는 경우:
     * - APPROVED + COMPLETED
     * - APPROVED + CANCELED
     * - REJECTED
     * - CANCELED
     *
     * 완료·취소된 과거 활동의 APPROVED 신청 이력은 보존하되,
     * 현재 진행 중인 참여로 간주하지 않습니다.
     */
    @Query("""
        SELECT CASE
                   WHEN COUNT(aa) > 0 THEN true
                   ELSE false
               END
        FROM ActivityApplication aa
        WHERE aa.user.id = :userId
          AND (
                aa.status = com.dagachi.backend.domain.enums.ApplicationStatus.PENDING
                OR (
                    aa.status = com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
                    AND aa.activity.status IN (
                        com.dagachi.backend.domain.enums.ActivityStatus.RECRUITING,
                        com.dagachi.backend.domain.enums.ActivityStatus.READY,
                        com.dagachi.backend.domain.enums.ActivityStatus.IN_PROGRESS
                    )
                )
              )
        """)
    boolean existsBlockingWithdrawalParticipation(
            @Param("userId") Long userId
    );

    /**
     * APP-03 내 신청 목록 조회.
     */
    @Query("""
            SELECT aa
            FROM ActivityApplication aa
            JOIN FETCH aa.activity ca
            JOIN FETCH ca.recipient cr
            WHERE aa.user.id = :userId
              AND (:hasStatus = false OR aa.status = :status)
              AND (:hasType = false OR aa.applicationType = :type)
            ORDER BY aa.createdAt DESC
            """)
    Page<ActivityApplication> findMyApplications(
            @Param("userId") Long userId,
            @Param("hasStatus") boolean hasStatus,
            @Param("status") ApplicationStatus status,
            @Param("hasType") boolean hasType,
            @Param("type") ApplicationType type,
            Pageable pageable
    );

    /**
     * APP-04 내 활동 목록. APPROVED 신청 기준으로 조회한다.
     */
    @Query("""
            SELECT aa
            FROM ActivityApplication aa
            JOIN FETCH aa.activity ca
            JOIN FETCH ca.recipient cr
            WHERE aa.user.id = :userId
              AND aa.status = com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
              AND (:hasActivityStatus = false OR ca.status = :activityStatus)
            ORDER BY ca.scheduledAt DESC
            """)
    Page<ActivityApplication> findMyActivities(
            @Param("userId") Long userId,
            @Param("hasActivityStatus") boolean hasActivityStatus,
            @Param("activityStatus") ActivityStatus activityStatus,
            Pageable pageable
    );

    /**
     * RECORD-01 활동 시작 시 SAME_GENDER_ONE 조건 검증용.
     */
    @Query("""
            SELECT u.gender
            FROM ActivityApplication aa
            JOIN aa.user u
            WHERE aa.activity.id = :activityId
              AND aa.status = com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
            """)
    List<UserGender> findApprovedUserGenders(@Param("activityId") Long activityId);

    /**
     * USER-03 탈퇴 시 진행 중인 신청/활동 여부 확인.
     * [팀 미확정 정책 임시 적용] PENDING 또는 APPROVED가 하나라도 있으면 탈퇴를 막는다.
     *
     * [참고] 이 메서드는 CareActivity 상태를 보지 않아 REQ-AUTH-08 정확한 정책과
     * 다르다. 현재는 existsBlockingWithdrawalParticipation()이 실제로 사용되며,
     * 이 메서드는 더 이상 호출되지 않는다. 다른 곳에서 참조가 없으면 정리 대상이다.
     */
    boolean existsByUser_IdAndStatusIn(Long userId, List<ApplicationStatus> statuses);

    /**
     * STAT-01 내 활동 통계 - 완료한 안부 확인 횟수.
     *
     * 로그인 사용자가 APPROVED 참여자로 참여한 CareActivity 중,
     * 공동 ActivityRecord가 기관에 의해 APPROVED되었고
     * visitResult가 MET인 활동 수를 센다.
     *
     * CareActivity : ActivityRecord = 1 : 0..1 이므로
     * activity.id 기준 distinct count가 record 기준 count와 동일하다.
     */
    @Query("""
            SELECT COUNT(DISTINCT application.activity.id)
            FROM ActivityApplication application
            JOIN application.activity activity
            JOIN ActivityRecord record
                ON record.activity = activity
            WHERE application.user.id = :userId
              AND application.status = :applicationStatus
              AND record.reviewStatus = :reviewStatus
              AND record.visitResult = :visitResult
            """)
    long countCompletedCareChecks(
            @Param("userId") Long userId,
            @Param("applicationStatus") ApplicationStatus applicationStatus,
            @Param("reviewStatus") ActivityReviewStatus reviewStatus,
            @Param("visitResult") VisitResult visitResult
    );

    /**
     * STAT-01 내 활동 통계 - 함께한 이웃(고유 대상자) 수.
     *
     * 위와 동일한 조건에서 서로 다른 CareRecipient가 몇 명인지 센다.
     * 같은 어르신을 여러 번 방문해도 1명으로만 계산한다.
     */
    @Query("""
            SELECT COUNT(DISTINCT activity.recipient.id)
            FROM ActivityApplication application
            JOIN application.activity activity
            JOIN ActivityRecord record
                ON record.activity = activity
            WHERE application.user.id = :userId
              AND application.status = :applicationStatus
              AND record.reviewStatus = :reviewStatus
              AND record.visitResult = :visitResult
            """)
    long countDistinctCareRecipients(
            @Param("userId") Long userId,
            @Param("applicationStatus") ApplicationStatus applicationStatus,
            @Param("reviewStatus") ActivityReviewStatus reviewStatus,
            @Param("visitResult") VisitResult visitResult
    );
}