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
     * keyword가 null이면 검색 조건을 적용하지 않는다.
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
                          :keyword IS NULL
                          OR LOWER(volunteer.name)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(volunteer.nickname)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR volunteer.phone
                              LIKE CONCAT('%', :keyword, '%')
                      )
                    GROUP BY
                        volunteer.id,
                        volunteer.name,
                        volunteer.nickname,
                        volunteer.phone,
                        volunteer.gender
                    ORDER BY MAX(record.completedAt) DESC
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
                          :keyword IS NULL
                          OR LOWER(volunteer.name)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
                          OR LOWER(volunteer.nickname)
                              LIKE LOWER(CONCAT('%', :keyword, '%'))
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

            Pageable pageable
    );
}