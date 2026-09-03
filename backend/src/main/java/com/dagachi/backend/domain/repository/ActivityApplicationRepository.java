package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
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

    /**
     * ACT-02, ACT-03에서 본인 신청 상태 확인 및 APPROVED 검증에 사용.
     */
    Optional<ActivityApplication> findByActivity_IdAndUser_Id(Long activityId, Long userId);

    /**
     * ACT-01, ACT-02, ACT-03 목록의 activityId 여러 개에 대한 APPROVED 신청 수를
     * 한 번에 조회한다. N+1 방지를 위해 activityId 리스트 기준으로 배치 조회한다.
     */
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
     * status/applicationType 필터는 CareActivityRepository의 hasX 플래그 패턴을 그대로 따른다.
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
}