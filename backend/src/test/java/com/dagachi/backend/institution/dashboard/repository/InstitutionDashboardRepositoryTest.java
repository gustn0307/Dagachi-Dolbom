package com.dagachi.backend.institution.dashboard.repository;

import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Import(InstitutionDashboardRepository.class)
class InstitutionDashboardRepositoryTest
        extends PostgresContainerTestBase {

    @Autowired
    private InstitutionDashboardRepository dashboardRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 우려대상자와_장기미확인대상자만_기관범위에서_조회한다() {
        Long institutionId = insertInstitution("테스트 기관");
        Long otherInstitutionId = insertInstitution("다른 기관");
        Long managerId = insertManager(institutionId);

        Long concernRecipientId = insertRecipient(
                institutionId,
                "건강 우려 대상자",
                "CURRENT_TIMESTAMP"
        );

        insertRecipient(
                institutionId,
                "장기 미확인 대상자",
                "CURRENT_TIMESTAMP - INTERVAL '20 days'"
        );

        insertRecipient(
                institutionId,
                "최근 확인 정상 대상자",
                "CURRENT_TIMESTAMP"
        );

        insertRecipient(
                otherInstitutionId,
                "다른 기관 대상자",
                "CURRENT_TIMESTAMP - INTERVAL '30 days'"
        );

        Long checklistItemId = insertSupportNeededItem();
        Long activityId = insertCompletedActivity(
                institutionId,
                concernRecipientId,
                managerId
        );
        Long recordId = insertApprovedRecord(activityId);

        insertChecklistResponse(
                recordId,
                checklistItemId
        );

        List<InstitutionDashboardRepository.CarePriorityCandidateRow> result =
                dashboardRepository.findCarePriorityCandidates(institutionId);

        assertThat(result)
                .extracting(
                        InstitutionDashboardRepository
                                .CarePriorityCandidateRow::recipientName
                )
                .containsExactly(
                        "건강 우려 대상자",
                        "장기 미확인 대상자"
                );

        assertThat(result.getFirst().supportNeededCount())
                .isEqualTo(1);
    }

    private Long insertInstitution(String name) {
        return getId("""
                INSERT INTO institutions(name, type, address, phone)
                VALUES ('%s', 'WELFARE_CENTER', '서울시 강남구', '02-1234-5678')
                RETURNING id
                """.formatted(name));
    }

    private Long insertManager(Long institutionId) {
        return getId("""
                INSERT INTO users(
                    email,
                    password,
                    name,
                    nickname,
                    phone,
                    gender,
                    role,
                    institution_id
                )
                VALUES (
                    'manager@test.com',
                    'password',
                    '기관 담당자',
                    '담당자',
                    '010-1111-1111',
                    'MALE',
                    'INSTITUTION',
                    %d
                )
                RETURNING id
                """.formatted(institutionId));
    }

    private Long insertRecipient(
            Long institutionId,
            String name,
            String lastCheckedAt
    ) {
        return getId("""
                INSERT INTO care_recipients(
                    institution_id,
                    name,
                    gender,
                    birth_year,
                    address,
                    status,
                    consent_status,
                    last_checked_at
                )
                VALUES (
                    %d,
                    '%s',
                    'FEMALE',
                    1940,
                    '서울시 강남구',
                    'ACTIVE',
                    'AGREED',
                    %s
                )
                RETURNING id
                """.formatted(
                institutionId,
                name,
                lastCheckedAt
        ));
    }

    private Long insertSupportNeededItem() {
        return getId("""
                INSERT INTO checklist_items(
                    version,
                    code,
                    question,
                    item_type,
                    options_json,
                    required,
                    sort_order
                )
                VALUES (
                    1,
                    'SUPPORT_NEEDED',
                    '추가 지원이 필요한가요?',
                    'SINGLE_CHOICE',
                    '["YES", "NO"]'::jsonb,
                    true,
                    1
                )
                RETURNING id
                """);
    }

    private Long insertCompletedActivity(
            Long institutionId,
            Long recipientId,
            Long managerId
    ) {
        return getId("""
                INSERT INTO care_activities(
                    recipient_id,
                    institution_id,
                    created_by,
                    scheduled_at,
                    required_people,
                    gender_condition,
                    status
                )
                VALUES (
                    %d,
                    %d,
                    %d,
                    CURRENT_TIMESTAMP - INTERVAL '1 day',
                    1,
                    'NONE',
                    'COMPLETED'
                )
                RETURNING id
                """.formatted(
                recipientId,
                institutionId,
                managerId
        ));
    }

    private Long insertApprovedRecord(Long activityId) {
        return getId("""
                INSERT INTO activity_records(
                    activity_id,
                    checklist_version,
                    visit_result,
                    completed_at,
                    review_status,
                    reviewed_at
                )
                VALUES (
                    %d,
                    1,
                    'MET',
                    CURRENT_TIMESTAMP,
                    'APPROVED',
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """.formatted(activityId));
    }

    private void insertChecklistResponse(
            Long recordId,
            Long checklistItemId
    ) {
        entityManager.createNativeQuery("""
                INSERT INTO checklist_responses(
                    activity_record_id,
                    checklist_item_id,
                    selected_value
                )
                VALUES (
                    :recordId,
                    :checklistItemId,
                    'YES'
                )
                """)
                .setParameter("recordId", recordId)
                .setParameter("checklistItemId", checklistItemId)
                .executeUpdate();
    }

    private Long getId(String sql) {
        Number result = (Number) entityManager
                .createNativeQuery(sql)
                .getSingleResult();

        return result.longValue();
    }
}