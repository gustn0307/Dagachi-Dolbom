package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ActivityRecord;
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

    /**
     * AI 활동 매칭용 사용자 경험 조회.
     *
     * 사용자가 APPROVED 참여자로 참가했고,
     * 기관 검토가 APPROVED이며 실제로 만난(MET)
     * 완료 활동만 조회합니다.
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
     * AI 활동 매칭 후보 대상자의 기관 승인 활동기록을 조회합니다.
     *
     * 후보 대상자들의 기록을 한 번에 조회하고,
     * Service에서 대상자별 최근 3건까지만 사용합니다.
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