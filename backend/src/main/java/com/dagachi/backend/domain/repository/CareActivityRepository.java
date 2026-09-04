package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.UserGender;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CareActivity Entity의 DB 조회를 담당하는 공통 Repository.
 *
 * ACT-01 목록 조회는 region/dateFrom/dateTo/ageGroups/gender 각각의 null 여부에 따라
 * Service에서 미리 걸러 파라미터를 채운다.
 * (PostgreSQL JDBC가 IS NULL과만 비교되는 파라미터의 타입을 추론하지 못해
 *  단순 "? IS NULL OR ..." 패턴을 그대로 쓰면 500 에러가 발생하기 때문에,
 *  region은 항상 빈 문자열로, dateFrom/dateTo는 항상 실제 경계값으로,
 *  ageGroups는 hasAgeGroups 플래그 + 항상 비어있지 않은 sentinel 리스트로,
 *  gender는 hasGender 플래그로 채워서 호출한다.)
 *
 * 연령대는 "50대 이하"(~59세) / "90대 이상"(90세~)처럼 열린 구간이 있어서
 * decade를 그대로 쓰지 않고 CASE WHEN으로 50/90 경계에서 클램핑한 뒤 비교한다.
 * AddressUtils.calculateAgeGroup()의 분기와 반드시 같은 경계를 유지해야 한다.
 */
public interface CareActivityRepository extends JpaRepository<CareActivity, Long> {

    /**
     * ACT-01 모집 활동 목록 조회 (좌표 없이 페이징, scheduledAt 기준 정렬).
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
              AND (
                    :hasAgeGroups = false
                    OR (
                        CASE
                            WHEN ((:currentYear - cr.birthYear) / 10) * 10 <= 50 THEN 50
                            WHEN ((:currentYear - cr.birthYear) / 10) * 10 >= 90 THEN 90
                            ELSE ((:currentYear - cr.birthYear) / 10) * 10
                        END
                    ) IN :ageBuckets
                  )
              AND (:hasGender = false OR cr.gender = :gender)
            """)
    Page<CareActivity> findRecruitingActivitiesPaged(
            @Param("region") String region,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("hasAgeGroups") boolean hasAgeGroups,
            @Param("ageBuckets") List<Integer> ageBuckets,
            @Param("currentYear") int currentYear,
            @Param("hasGender") boolean hasGender,
            @Param("gender") UserGender gender,
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
              AND (
                    :hasAgeGroups = false
                    OR (
                        CASE
                            WHEN ((:currentYear - cr.birthYear) / 10) * 10 <= 50 THEN 50
                            WHEN ((:currentYear - cr.birthYear) / 10) * 10 >= 90 THEN 90
                            ELSE ((:currentYear - cr.birthYear) / 10) * 10
                        END
                    ) IN :ageBuckets
                  )
              AND (:hasGender = false OR cr.gender = :gender)
            """)
    List<CareActivity> findRecruitingActivitiesForDistanceSort(
            @Param("region") String region,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("hasAgeGroups") boolean hasAgeGroups,
            @Param("ageBuckets") List<Integer> ageBuckets,
            @Param("currentYear") int currentYear,
            @Param("hasGender") boolean hasGender,
            @Param("gender") UserGender gender
    );

    @Query("""
            SELECT ca
            FROM CareActivity ca
            JOIN FETCH ca.recipient cr
            WHERE ca.id = :activityId
            """)
    Optional<CareActivity> findDetailById(@Param("activityId") Long activityId);

    /**
     * APP-05 승인 취소 시 정원/상태 경쟁 조건 방지용 락 조회.
     * DB_ENTITY_GUIDE 06번 문서 동시성 규칙에 따라 신청 승인/승인취소/활동시작 시 사용한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ca FROM CareActivity ca WHERE ca.id = :activityId")
    Optional<CareActivity> findByIdForUpdate(@Param("activityId") Long activityId);
}