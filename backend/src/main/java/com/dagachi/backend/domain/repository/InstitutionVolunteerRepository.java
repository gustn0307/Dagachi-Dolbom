package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import com.dagachi.backend.domain.enums.ActivityStatus;

/**
 * 기관별 봉사자 목록 조회 Repository.
 *
 * 별도의 봉사자 테이블을 만들지 않고,
 * 활동 신청과 활동 기록을 이용해 기관별 봉사자를 조회한다.
 */
public interface InstitutionVolunteerRepository
        extends Repository<ActivityApplication, Long> {

    /**
     * 기관 봉사자 목록을 조회한다.
     *
     * 검색 대상:
     * - 이름
     * - 닉네임
     * - 전화번호
     *
     * keyword가 빈 문자열이면 검색 조건을 적용하지 않는다.
     */
    @Query(
            value = """
                    SELECT new com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse(
                        volunteer.id,
                        volunteer.name,
                        volunteer.nickname,
                        volunteer.phone,
                        volunteer.gender,
                        COUNT(DISTINCT activity.id),
                        MAX(record.completedAt)
                    )
                    FROM ActivityApplication application
                    JOIN application.activity activity
                    JOIN application.user volunteer
                    JOIN ActivityRecord record
                        ON record.activity = activity
                    WHERE activity.institution.id = :institutionId
                      AND application.status = :applicationStatus
                      AND record.reviewStatus = :reviewStatus
                      AND record.completedAt IS NOT NULL
                      AND (
                          :keyword = ''
                          OR LOWER(volunteer.name)
                              LIKE CONCAT('%', :keyword, '%')
                          OR LOWER(volunteer.nickname)
                              LIKE CONCAT('%', :keyword, '%')
                          OR volunteer.phone
                              LIKE CONCAT('%', :keyword, '%')
                      )
                    GROUP BY
                        volunteer.id,
                        volunteer.name,
                        volunteer.nickname,
                        volunteer.phone,
                        volunteer.gender
                    ORDER BY
                        CASE
                            WHEN :sortType = 'participation'
                            THEN COUNT(DISTINCT activity.id)
                        END DESC,
                        CASE
                            WHEN :sortType = 'recent'
                            THEN MAX(record.completedAt)
                        END DESC,
                        MAX(record.completedAt) DESC,
                        volunteer.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT volunteer.id)
                    FROM ActivityApplication application
                    JOIN application.activity activity
                    JOIN application.user volunteer
                    JOIN ActivityRecord record
                        ON record.activity = activity
                    WHERE activity.institution.id = :institutionId
                      AND application.status = :applicationStatus
                      AND record.reviewStatus = :reviewStatus
                      AND record.completedAt IS NOT NULL
                      AND (
                          :keyword = ''
                          OR LOWER(volunteer.name)
                              LIKE CONCAT('%', :keyword, '%')
                          OR LOWER(volunteer.nickname)
                              LIKE CONCAT('%', :keyword, '%')
                          OR volunteer.phone
                              LIKE CONCAT('%', :keyword, '%')
                      )
                    """
    )
    Page<InstitutionVolunteerSummaryResponse>
    findInstitutionVolunteers(
            @Param("institutionId")
            Long institutionId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("reviewStatus")
            ActivityReviewStatus reviewStatus,

            @Param("keyword")
            String keyword,

            @Param("sortType")
            String sortType,

            Pageable pageable
    );

    /**
     * 해당 기관의 활동에 한 번 이상 참여 완료한
     * 전체 봉사자 수를 조회한다.
     *
     * 같은 사용자가 여러 활동에 참여해도 한 명으로 계산한다.
     */
    @Query("""
        SELECT COUNT(DISTINCT application.user.id)
        FROM ActivityApplication application
        JOIN application.activity activity
        JOIN ActivityRecord record
            ON record.activity = activity
        WHERE activity.institution.id = :institutionId
          AND application.status = :applicationStatus
          AND record.reviewStatus = :reviewStatus
          AND record.completedAt IS NOT NULL
        """)
    long countTotalVolunteers(
            @Param("institutionId")
            Long institutionId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("reviewStatus")
            ActivityReviewStatus reviewStatus
    );

    /**
     * 현재 진행 중인 활동에 참여하고 있는
     * 봉사자 수를 조회한다.
     *
     * 같은 사용자가 여러 진행 중 활동에 참여해도
     * 한 명으로 계산한다.
     */
    @Query("""
        SELECT COUNT(DISTINCT application.user.id)
        FROM ActivityApplication application
        JOIN application.activity activity
        WHERE activity.institution.id = :institutionId
          AND application.status = :applicationStatus
          AND activity.status = :activityStatus
        """)
    long countActiveVolunteers(
            @Param("institutionId")
            Long institutionId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("activityStatus")
            ActivityStatus activityStatus
    );

    /**
     * 앞으로 진행될 준비 완료 활동에 승인된
     * 봉사자 수를 조회한다.
     *
     * READY 상태이면서 예정 시각이 현재보다
     * 뒤에 있는 활동만 포함한다.
     */
    @Query("""
        SELECT COUNT(DISTINCT application.user.id)
        FROM ActivityApplication application
        JOIN application.activity activity
        WHERE activity.institution.id = :institutionId
          AND application.status = :applicationStatus
          AND activity.status = :activityStatus
          AND activity.scheduledAt > CURRENT_TIMESTAMP
        """)
    long countScheduledVolunteers(
            @Param("institutionId")
            Long institutionId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("activityStatus")
            ActivityStatus activityStatus
    );
}