package com.dagachi.backend.institution.dashboard.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InstitutionDashboardRepository {

    private final EntityManager entityManager;

    public InstitutionDashboardRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public long countParticipants(Long institutionId) {
        return count("""
                SELECT COUNT(DISTINCT aa.user_id)
                FROM activity_applications aa
                JOIN care_activities ca ON ca.id = aa.activity_id
                WHERE ca.institution_id = :institutionId
                  AND aa.status = 'APPROVED'
                """, institutionId);
    }

    public long countRecipients(Long institutionId) {
        return count("""
                SELECT COUNT(*)
                FROM care_recipients
                WHERE institution_id = :institutionId
                  AND is_deleted = false
                  AND status = 'ACTIVE'
                """, institutionId);
    }

    public long countActivities(Long institutionId) {
        return count("SELECT COUNT(*) FROM care_activities WHERE institution_id = :institutionId", institutionId);
    }

    public long countPendingApplications(Long institutionId) {
        return count("""
                SELECT COUNT(*)
                FROM activity_applications aa
                JOIN care_activities ca ON ca.id = aa.activity_id
                WHERE ca.institution_id = :institutionId
                  AND aa.status = 'PENDING'
                """, institutionId);
    }

    public long countActivitiesByStatus(Long institutionId, String status) {
        Number result = (Number) entityManager.createNativeQuery("""
                        SELECT COUNT(*) FROM care_activities
                        WHERE institution_id = :institutionId AND status = :status
                        """)
                .setParameter("institutionId", institutionId)
                .setParameter("status", status)
                .getSingleResult();
        return result.longValue();
    }

    public long countUnassignedReports() {
        Number result = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM reports WHERE institution_id IS NULL")
                .getSingleResult();
        return result.longValue();
    }

    public long countPendingRecordReviews(Long institutionId) {
        return count("""
                SELECT COUNT(*)
                FROM activity_records ar
                JOIN care_activities ca ON ca.id = ar.activity_id
                WHERE ca.institution_id = :institutionId
                  AND ar.review_status = 'SUBMITTED'
                """, institutionId);
    }

    @SuppressWarnings("unchecked")
    public List<TrendRow> findCompletedTrend(
            Long institutionId,
            String datePart,
            LocalDateTime from
    ) {
        String sql = """
                SELECT date_trunc('%s', ar.reviewed_at) AS bucket, COUNT(*)
                FROM activity_records ar
                JOIN care_activities ca ON ca.id = ar.activity_id
                WHERE ca.institution_id = :institutionId
                  AND ar.review_status = 'APPROVED'
                  AND ar.reviewed_at >= :from
                GROUP BY bucket
                ORDER BY bucket
                """.formatted(datePart);

        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("institutionId", institutionId)
                .setParameter("from", from)
                .getResultList();

        return rows.stream()
                .map(row -> new TrendRow(toLocalDateTime(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<UpcomingRow> findUpcomingActivities(Long institutionId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT ca.id,
                               cr.name,
                               ca.scheduled_at,
                               ca.required_people,
                               (SELECT COUNT(*)
                                  FROM activity_applications aa
                                 WHERE aa.activity_id = ca.id
                                   AND aa.status = 'APPROVED') AS approved_people,
                               ca.status
                        FROM care_activities ca
                        JOIN care_recipients cr ON cr.id = ca.recipient_id
                        WHERE ca.institution_id = :institutionId
                          AND ca.scheduled_at >= CURRENT_TIMESTAMP
                          AND ca.status IN ('RECRUITING', 'READY')
                        ORDER BY ca.scheduled_at ASC
                        LIMIT 5
                        """)
                .setParameter("institutionId", institutionId)
                .getResultList();

        return rows.stream()
                .map(row -> new UpcomingRow(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        toLocalDateTime(row[2]),
                        ((Number) row[3]).intValue(),
                        ((Number) row[4]).longValue(),
                        (String) row[5]
                ))
                .toList();
    }

    @SuppressWarnings("unchecked")
    public List<CarePriorityCandidateRow> findCarePriorityCandidates(Long institutionId) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT cr.id,
                               cr.name,
                               CASE WHEN cr.last_checked_at IS NULL THEN NULL
                                    ELSE GREATEST(0, CURRENT_DATE - cr.last_checked_at::date) END,
                               (SELECT COUNT(*)
                                  FROM care_activities ca
                                 WHERE ca.recipient_id = cr.id
                                   AND ca.status = 'COMPLETED'
                                   AND ca.updated_at >= CURRENT_TIMESTAMP - INTERVAL '30 days'),
                               (SELECT COUNT(*)
                                  FROM checklist_responses response
                                  JOIN checklist_items item ON item.id = response.checklist_item_id
                                  JOIN activity_records record ON record.id = response.activity_record_id
                                  JOIN care_activities ca ON ca.id = record.activity_id
                                 WHERE ca.recipient_id = cr.id
                                   AND record.review_status = 'APPROVED'
                                   AND item.code = 'MEAL_STATUS'
                                   AND UPPER(COALESCE(response.selected_value, '')) IN ('NO', 'BAD', 'POOR')),
                               (SELECT COUNT(*)
                                  FROM checklist_responses response
                                  JOIN checklist_items item ON item.id = response.checklist_item_id
                                  JOIN activity_records record ON record.id = response.activity_record_id
                                  JOIN care_activities ca ON ca.id = record.activity_id
                                 WHERE ca.recipient_id = cr.id
                                   AND record.review_status = 'APPROVED'
                                   AND item.code = 'HEALTH_CONDITION'
                                   AND UPPER(COALESCE(response.selected_value, '')) IN ('NO', 'BAD', 'POOR')),
                               (SELECT COUNT(*)
                                  FROM checklist_responses response
                                  JOIN checklist_items item ON item.id = response.checklist_item_id
                                  JOIN activity_records record ON record.id = response.activity_record_id
                                  JOIN care_activities ca ON ca.id = record.activity_id
                                 WHERE ca.recipient_id = cr.id
                                   AND record.review_status = 'APPROVED'
                                   AND item.code = 'SUPPORT_NEEDED'
                                   AND UPPER(COALESCE(response.selected_value, '')) = 'YES'),
                               EXISTS(SELECT 1
                                        FROM care_activities ca
                                       WHERE ca.recipient_id = cr.id
                                         AND ca.scheduled_at >= CURRENT_TIMESTAMP
                                         AND ca.status IN ('RECRUITING', 'READY', 'IN_PROGRESS'))
                        FROM care_recipients cr
                        WHERE cr.institution_id = :institutionId
                          AND cr.is_deleted = false
                          AND cr.status = 'ACTIVE'
                          AND cr.consent_status = 'AGREED'
                          AND (cr.last_checked_at IS NULL
                               OR cr.last_checked_at < CURRENT_TIMESTAMP - INTERVAL '7 days')
                        ORDER BY cr.last_checked_at ASC NULLS FIRST
                        LIMIT 10
                        """)
                .setParameter("institutionId", institutionId)
                .getResultList();

        return rows.stream().map(row -> new CarePriorityCandidateRow(
                ((Number) row[0]).longValue(),
                (String) row[1],
                row[2] == null ? null : ((Number) row[2]).intValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                ((Number) row[5]).longValue(),
                ((Number) row[6]).longValue(),
                (Boolean) row[7]
        )).toList();
    }

    private long count(String sql, Long institutionId) {
        Number result = (Number) entityManager.createNativeQuery(sql)
                .setParameter("institutionId", institutionId)
                .getSingleResult();
        return result.longValue();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    public record TrendRow(LocalDateTime bucket, long count) {
    }

    public record UpcomingRow(
            Long activityId,
            String recipientName,
            LocalDateTime scheduledAt,
            int requiredPeople,
            long approvedPeople,
            String status
    ) {
    }

    public record CarePriorityCandidateRow(
            Long recipientId,
            String recipientName,
            Integer daysSinceLastCheck,
            long recentActivityCount,
            long mealConcernCount,
            long healthConcernCount,
            long supportNeededCount,
            boolean hasUpcomingActivity
    ) {
    }
}
