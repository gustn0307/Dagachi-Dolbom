package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 기관별 봉사자 목록 조회 Repository.
 *
 * 별도의 Volunteer Entity나 테이블을 만들지 않고,
 * 기존 활동 신청 및 활동 기록을 조합해 봉사자를 조회한다.
 */
public interface InstitutionVolunteerRepository
        extends Repository<ActivityApplication, Long> {

    /**
     * 해당 기관의 활동에 실제로 참여 완료한 봉사자 목록을 조회한다.
     *
     * 조회 조건:
     * 1. 로그인 담당자가 소속된 기관의 활동
     * 2. 활동 신청 상태가 APPROVED
     * 3. 활동 기록 검토 상태가 APPROVED
     * 4. 활동 완료 시각이 존재하는 활동
     *
     * 동일한 사용자가 여러 활동에 참여해도 목록에는 한 번만 반환된다.
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
                    GROUP BY
                        volunteer.id,
                        volunteer.name,
                        volunteer.nickname,
                        volunteer.phone,
                        volunteer.gender
                    ORDER BY MAX(record.completedAt) DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT application.user.id)
                    FROM ActivityApplication application
                    JOIN application.activity activity
                    JOIN ActivityRecord record
                        ON record.activity = activity
                    WHERE activity.institution.id = :institutionId
                      AND application.status = :applicationStatus
                      AND record.reviewStatus = :reviewStatus
                      AND record.completedAt IS NOT NULL
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

            Pageable pageable
    );
}