package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityRecordRepository extends JpaRepository<ActivityRecord, Long> {

    // 활동기록 수정/서명/제출 시 동시 변경을 막기 위해 해당 기록을 잠금 조회합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ar from ActivityRecord ar where ar.id = :id")
    Optional<ActivityRecord> findByIdForUpdate(@Param("id") Long id);

    Optional<ActivityRecord> findByActivity_Id(Long activityId);

    /**
     * APP-04 내 활동 목록에서 여러 활동의 activityRecordId를 한 번에 조회하기 위한
     * bulk 조회. N+1 방지용.
     */
    List<ActivityRecord> findByActivity_IdIn(List<Long> activityIds);

    // 메인페이지 통계 조회
    long countByReviewStatus(ActivityReviewStatus reviewStatus);

    /**
     * AI 매칭 사용자 경험 프로필 조회.
     *
     * 승인된 활동 신청 + 기관 승인 활동기록 + 실제 만남(MET) 기록만 조회합니다.
     */
    @Query("""
            SELECT DISTINCT ar
            FROM ActivityRecord ar
            JOIN FETCH ar.activity ca
            JOIN FETCH ca.recipient cr
            JOIN ActivityApplication aa
                ON aa.activity = ca
            WHERE aa.user.id = :userId
              AND aa.status =
                  com.dagachi.backend.domain.enums.ApplicationStatus.APPROVED
              AND ar.reviewStatus =
                  com.dagachi.backend.domain.enums.ActivityReviewStatus.APPROVED
              AND ar.visitResult =
                  com.dagachi.backend.domain.enums.VisitResult.MET
            """)
    List<ActivityRecord> findApprovedMetRecordsByUserId(
            @Param("userId") Long userId
    );

    /**
     * AI 매칭 후보 대상자의 최근 기관 승인 활동기록 조회.
     */
    @Query("""
            SELECT ar
            FROM ActivityRecord ar
            JOIN FETCH ar.activity ca
            JOIN FETCH ca.recipient cr
            WHERE cr.id IN :recipientIds
              AND ar.reviewStatus =
                  com.dagachi.backend.domain.enums.ActivityReviewStatus.APPROVED
            ORDER BY cr.id ASC, ar.completedAt DESC
            """)
    List<ActivityRecord> findApprovedRecordsByRecipientIds(
            @Param("recipientIds") List<Long> recipientIds
    );
}