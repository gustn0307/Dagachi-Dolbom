package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ApplicationStatus;
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
}