package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.domain.enums.UserGender;
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
     */
    boolean existsByUser_IdAndStatusIn(Long userId, List<ApplicationStatus> statuses);
}