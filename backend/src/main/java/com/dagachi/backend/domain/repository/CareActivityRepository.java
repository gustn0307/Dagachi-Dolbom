package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.CareActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CareActivity Entity의 DB 조회를 담당하는 공통 Repository.
 *
 * ACT-01 목록 조회는 region/dateFrom/dateTo 각각의 null 여부에 따라
 * Service에서 미리 걸러 파라미터를 채운다.
 * (PostgreSQL JDBC가 IS NULL과만 비교되는 파라미터의 타입을 추론하지 못해
 *  단순 "? IS NULL OR ..." 패턴을 그대로 쓰면 500 에러가 발생하기 때문에,
 *  region은 항상 빈 문자열로, dateFrom/dateTo는 항상 실제 경계값으로 채워서 호출한다.)
 */
public interface CareActivityRepository extends JpaRepository<CareActivity, Long> {

    /**
     * ACT-01 모집 활동 목록 조회 (좌표 없이 페이징, scheduledAt 기준 정렬).
     *
     * region은 필터가 없을 때 Service에서 빈 문자열("")로 채워 전달한다.
     * dateFrom/dateTo는 필터가 없을 때 Service에서 각각
     * 아주 먼 과거/아주 먼 미래 값으로 채워 전달한다.
     */
    @Query("""
            SELECT ca
            FROM CareActivity ca
            JOIN FETCH ca.recipient cr
            WHERE ca.status IN (
                    com.dagachi.backend.domain.enums.ActivityStatus.RECRUITING,
                    com.dagachi.backend.domain.enums.ActivityStatus.READY
                  )
              AND (:region = '' OR LOWER(cr.address) LIKE LOWER(CONCAT('%', :region, '%')))
              AND ca.scheduledAt >= :dateFrom
              AND ca.scheduledAt < :dateTo
            """)
    Page<CareActivity> findRecruitingActivitiesPaged(
            @Param("region") String region,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            Pageable pageable
    );

    /**
     * ACT-01 좌표 기반 정렬용. 파라미터 규칙은 위와 동일하다.
     */
    @Query("""
            SELECT ca
            FROM CareActivity ca
            JOIN FETCH ca.recipient cr
            WHERE ca.status IN (
                    com.dagachi.backend.domain.enums.ActivityStatus.RECRUITING,
                    com.dagachi.backend.domain.enums.ActivityStatus.READY
                  )
              AND (:region = '' OR LOWER(cr.address) LIKE LOWER(CONCAT('%', :region, '%')))
              AND ca.scheduledAt >= :dateFrom
              AND ca.scheduledAt < :dateTo
            """)
    List<CareActivity> findRecruitingActivitiesForDistanceSort(
            @Param("region") String region,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    @Query("""
            SELECT ca
            FROM CareActivity ca
            JOIN FETCH ca.recipient cr
            WHERE ca.id = :activityId
            """)
    Optional<CareActivity> findDetailById(@Param("activityId") Long activityId);
}