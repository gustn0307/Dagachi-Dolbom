package com.dagachi.backend.domain.repository;

import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ChecklistResponseConstraintIntegrationTest
        extends PostgresContainerTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName(
            "[REQ-REC-10] 같은 ActivityRecord와 ChecklistItem 조합은 DB UNIQUE 제약으로 중복 저장할 수 없다"
    )
    void duplicateActivityRecordAndChecklistItem_DB에서_중복저장을_거부한다() {

        // -----------------------------------------------------------
        // given
        // 실제 Flyway Schema의 FK 조건을 만족하는 최소 테스트 데이터를 생성합니다.
        // -----------------------------------------------------------

        Long institutionId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO institutions (
                            name,
                            type,
                            status
                        )
                        VALUES (
                            '테스트 행정복지센터',
                            'COMMUNITY_CENTER',
                            'ACTIVE'
                        )
                        RETURNING id
                        """,
                        Long.class
                );

        Long userId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO users (
                            email,
                            password,
                            name,
                            phone,
                            gender,
                            role,
                            status,
                            is_deleted
                        )
                        VALUES (
                            'record-constraint@test.com',
                            'encoded-password',
                            '테스트 사용자',
                            '010-0000-0000',
                            'MALE',
                            'USER',
                            'ACTIVE',
                            false
                        )
                        RETURNING id
                        """,
                        Long.class
                );

        Long recipientId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO care_recipients (
                            institution_id,
                            name,
                            gender,
                            address,
                            status,
                            consent_status,
                            is_deleted
                        )
                        VALUES (
                            ?,
                            '테스트 대상자',
                            'FEMALE',
                            '테스트 주소',
                            'ACTIVE',
                            'AGREED',
                            false
                        )
                        RETURNING id
                        """,
                        Long.class,
                        institutionId
                );

        Long activityId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO care_activities (
                            recipient_id,
                            institution_id,
                            created_by,
                            scheduled_at,
                            required_people,
                            gender_condition,
                            status
                        )
                        VALUES (
                            ?,
                            ?,
                            ?,
                            ?,
                            2,
                            'NONE',
                            'IN_PROGRESS'
                        )
                        RETURNING id
                        """,
                        Long.class,
                        recipientId,
                        institutionId,
                        userId,
                        Timestamp.valueOf(
                                LocalDateTime.of(
                                        2026,
                                        9,
                                        21,
                                        10,
                                        0
                                )
                        )
                );

        Long recordId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO activity_records (
                            activity_id,
                            checklist_version,
                            review_status
                        )
                        VALUES (
                            ?,
                            1,
                            'DRAFT'
                        )
                        RETURNING id
                        """,
                        Long.class,
                        activityId
                );

        Long checklistItemId =
                jdbcTemplate.queryForObject(
                        """
                        INSERT INTO checklist_items (
                            version,
                            code,
                            question,
                            item_type,
                            options_json,
                            required,
                            sort_order,
                            active
                        )
                        VALUES (
                            1,
                            'MEAL_STATUS_TEST',
                            '식사는 하셨나요?',
                            'SINGLE_CHOICE',
                            '["YES","NO","UNKNOWN"]'::jsonb,
                            true,
                            1,
                            true
                        )
                        RETURNING id
                        """,
                        Long.class
                );

        /*
         * 첫 번째 응답은 정상 저장되어야 합니다.
         */
        jdbcTemplate.update(
                """
                INSERT INTO checklist_responses (
                    activity_record_id,
                    checklist_item_id,
                    selected_value
                )
                VALUES (?, ?, 'YES')
                """,
                recordId,
                checklistItemId
        );

        // -----------------------------------------------------------
        // when
        // 동일한 record + item 조합으로 두 번째 응답 저장을 시도합니다.
        // -----------------------------------------------------------

        DataIntegrityViolationException exception =
                assertThrows(
                        DataIntegrityViolationException.class,
                        () ->
                                jdbcTemplate.update(
                                        """
                                        INSERT INTO checklist_responses (
                                            activity_record_id,
                                            checklist_item_id,
                                            selected_value
                                        )
                                        VALUES (?, ?, 'NO')
                                        """,
                                        recordId,
                                        checklistItemId
                                )
                );

        // -----------------------------------------------------------
        // then
        // Flyway에 정의된 실제 UNIQUE Constraint가 중복을 차단했는지 확인합니다.
        // -----------------------------------------------------------

        assertThat(
                exception
                        .getMostSpecificCause()
                        .getMessage()
        ).contains(
                "uq_checklist_responses_record_item"
        );
    }
}