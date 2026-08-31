package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerActivityResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerDetailResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 기관별 봉사자 관리 조회 Repository.
 *
 * 별도의 봉사자 테이블을 만들지 않고,
 * 활동 신청과 활동 기록을 이용해
 * 기관별 봉사자 정보를 조회한다.
 */
public interface InstitutionVolunteerRepository
        extends Repository<ActivityApplication, Long> {

    /**
     * VOL-01~03 기관 봉사자 목록, 검색 및 정렬.
     *
     * 삭제된 사용자는 목록에서 제외한다.
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
                      AND volunteer.deleted = false
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
                      AND volunteer.deleted = false
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
     * VOL-05 해당 기관의 활동에 한 번 이상
     * 참여 완료한 전체 봉사자 수를 조회한다.
     *
     * 동일 사용자는 한 명으로 계산하고,
     * 삭제된 사용자는 제외한다.
     */
    @Query("""
            SELECT COUNT(DISTINCT application.user.id)
            FROM ActivityApplication application
            JOIN application.activity activity
            JOIN ActivityRecord record
                ON record.activity = activity
            WHERE activity.institution.id = :institutionId
              AND application.status = :applicationStatus
              AND application.user.deleted = false
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
     * VOL-05 현재 진행 중인 활동에 참여하고 있는
     * 봉사자 수를 조회한다.
     *
     * 동일 사용자는 한 명으로 계산하고,
     * 삭제된 사용자는 제외한다.
     */
    @Query("""
            SELECT COUNT(DISTINCT application.user.id)
            FROM ActivityApplication application
            JOIN application.activity activity
            WHERE activity.institution.id = :institutionId
              AND application.status = :applicationStatus
              AND application.user.deleted = false
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
     * VOL-05 앞으로 진행될 READY 활동에 승인된
     * 참여 예정 봉사자 수를 조회한다.
     *
     * 예정 시각이 현재보다 뒤에 있는 활동만 포함하고,
     * 삭제된 사용자는 제외한다.
     */
    @Query("""
            SELECT COUNT(DISTINCT application.user.id)
            FROM ActivityApplication application
            JOIN application.activity activity
            WHERE activity.institution.id = :institutionId
              AND application.status = :applicationStatus
              AND application.user.deleted = false
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

    /**
     * VOL-06 기관 봉사자 기본 상세 조회.
     *
     * 기관 ID와 봉사자 ID를 함께 검사하며,
     * 삭제된 사용자는 조회하지 않는다.
     */
    @Query("""
            SELECT new com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerDetailResponse(
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
              AND volunteer.id = :volunteerId
              AND volunteer.deleted = false
              AND application.status = :applicationStatus
              AND record.reviewStatus = :reviewStatus
              AND record.completedAt IS NOT NULL
            GROUP BY
                volunteer.id,
                volunteer.name,
                volunteer.nickname,
                volunteer.phone,
                volunteer.gender
            """)
    Optional<InstitutionVolunteerDetailResponse>
    findInstitutionVolunteerDetail(
            @Param("institutionId")
            Long institutionId,

            @Param("volunteerId")
            Long volunteerId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("reviewStatus")
            ActivityReviewStatus reviewStatus
    );

    /**
     * VOL-06 기관별 봉사자 활동 이력 조회.
     *
     * 해당 기관에서 참여 완료하고 검토 승인된 활동만
     * 최근 완료일순으로 반환한다.
     *
     * 삭제된 사용자는 조회하지 않는다.
     */
    @Query(
            value = """
                    SELECT new com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerActivityResponse(
                        activity.id,
                        activity.recipient.name,
                        activity.scheduledAt,
                        record.startedAt,
                        record.completedAt,
                        record.visitResult,
                        record.reviewStatus
                    )
                    FROM ActivityApplication application
                    JOIN application.activity activity
                    JOIN ActivityRecord record
                        ON record.activity = activity
                    WHERE activity.institution.id = :institutionId
                      AND application.user.id = :volunteerId
                      AND application.user.deleted = false
                      AND application.status = :applicationStatus
                      AND record.reviewStatus = :reviewStatus
                      AND record.completedAt IS NOT NULL
                    ORDER BY record.completedAt DESC,
                             activity.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT activity.id)
                    FROM ActivityApplication application
                    JOIN application.activity activity
                    JOIN ActivityRecord record
                        ON record.activity = activity
                    WHERE activity.institution.id = :institutionId
                      AND application.user.id = :volunteerId
                      AND application.user.deleted = false
                      AND application.status = :applicationStatus
                      AND record.reviewStatus = :reviewStatus
                      AND record.completedAt IS NOT NULL
                    """
    )
    Page<InstitutionVolunteerActivityResponse>
    findInstitutionVolunteerActivities(
            @Param("institutionId")
            Long institutionId,

            @Param("volunteerId")
            Long volunteerId,

            @Param("applicationStatus")
            ApplicationStatus applicationStatus,

            @Param("reviewStatus")
            ActivityReviewStatus reviewStatus,

            Pageable pageable
    );
}