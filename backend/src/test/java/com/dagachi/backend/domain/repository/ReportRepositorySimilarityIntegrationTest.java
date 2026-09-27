package com.dagachi.backend.domain.repository;

import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class ReportRepositorySimilarityIntegrationTest
        extends PostgresContainerTestBase {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName(
            "REQ-AI-03 - pgvector 유사도 검색은 "
                    + "자기 자신·다른 기관·기간 밖·embedding 없는 제보·threshold 미만을 제외하고 "
                    + "현재 기관과 미배정 제보를 유사도 순으로 반환한다"
    )
    void findSimilarReports_pgvector_검색조건과_유사도정렬을_적용한다() {

        // given
        Long institutionAId =
                insertInstitution("기관 A");

        Long institutionBId =
                insertInstitution("기관 B");

        LocalDateTime now =
                LocalDateTime.now();

        /*
         * 기준 제보.
         *
         * cosine similarity 계산을 쉽게 확인하기 위해
         * [1, 0, 0]을 기준 벡터로 사용한다.
         */
        Long targetId =
                insertReport(
                        institutionAId,
                        "기준 제보",
                        "[1,0,0]",
                        now.minusDays(1)
                );

        /*
         * 같은 기관 + 최근 + 높은 유사도.
         *
         * target과 거의 같은 방향이므로
         * 결과의 첫 번째 후보가 되어야 한다.
         */
        Long sameInstitutionCandidateId =
                insertReport(
                        institutionAId,
                        "같은 기관 유사 제보",
                        "[0.99,0.10,0]",
                        now.minusDays(2)
                );

        /*
         * 미배정 + 최근 + threshold 이상.
         *
         * [1,0,0]과 [0.8,0.6,0]의 cosine similarity는 0.8이다.
         * threshold 0.70 이상이므로 결과에 포함되어야 한다.
         */
        Long unassignedCandidateId =
                insertReport(
                        null,
                        "미배정 유사 제보",
                        "[0.8,0.6,0]",
                        now.minusDays(3)
                );

        /*
         * 다른 기관.
         *
         * 내용 벡터는 완전히 같아서 similarity=1이지만,
         * 기관 범위가 다르므로 반드시 제외되어야 한다.
         */
        Long otherInstitutionCandidateId =
                insertReport(
                        institutionBId,
                        "다른 기관 제보",
                        "[1,0,0]",
                        now.minusDays(2)
                );

        /*
         * 기간 밖.
         *
         * 같은 기관이고 similarity=1이어도
         * 최근 30일 범위 밖이므로 제외되어야 한다.
         */
        Long oldCandidateId =
                insertReport(
                        institutionAId,
                        "오래된 제보",
                        "[1,0,0]",
                        now.minusDays(40)
                );

        /*
         * embedding 없음.
         *
         * pgvector similarity 계산 대상에서 제외되어야 한다.
         */
        Long missingEmbeddingCandidateId =
                insertReport(
                        institutionAId,
                        "embedding 없는 제보",
                        null,
                        now.minusDays(2)
                );

        /*
         * threshold 미만.
         *
         * target [1,0,0]과 [0,1,0]은 직교하므로
         * cosine similarity는 0이다.
         */
        Long lowSimilarityCandidateId =
                insertReport(
                        institutionAId,
                        "유사도 낮은 제보",
                        "[0,1,0]",
                        now.minusDays(2)
                );

        LocalDateTime fromDateTime =
                now.minusDays(30);

        // when
        List<ReportSimilarityProjection> result =
                reportRepository.findSimilarReports(
                        targetId,
                        institutionAId,
                        fromDateTime,
                        0.70,
                        5
                );

        // then
        /*
         * 조건을 모두 만족하는 후보는
         * 같은 기관 후보와 미배정 후보 2건뿐이다.
         */
        assertThat(result)
                .hasSize(2);

        /*
         * ORDER BY cosine distance ASC이므로
         * similarity가 가장 높은 후보가 먼저 나와야 한다.
         */
        assertThat(result.get(0).getReportId())
                .isEqualTo(sameInstitutionCandidateId);

        assertThat(result.get(1).getReportId())
                .isEqualTo(unassignedCandidateId);

        /*
         * target 자신은 결과에 포함되지 않는다.
         */
        assertThat(result)
                .extracting(ReportSimilarityProjection::getReportId)
                .doesNotContain(targetId);

        /*
         * 기관/기간/embedding/threshold 조건에 걸린 후보들도
         * 모두 제외되어야 한다.
         */
        assertThat(result)
                .extracting(ReportSimilarityProjection::getReportId)
                .doesNotContain(
                        otherInstitutionCandidateId,
                        oldCandidateId,
                        missingEmbeddingCandidateId,
                        lowSimilarityCandidateId
                );

        /*
         * 실제 pgvector cosine similarity 계산 결과도 확인한다.
         *
         * 첫 번째 후보는 target과 거의 같은 방향이므로
         * 두 번째 후보보다 similarity가 높아야 한다.
         */
        assertThat(result.get(0).getSimilarity())
                .isGreaterThan(result.get(1).getSimilarity());

        /*
         * 두 번째 후보의 예상 cosine similarity는 약 0.8.
         */
        assertThat(result.get(1).getSimilarity())
                .isCloseTo(
                        0.8,
                        org.assertj.core.data.Offset.offset(0.0001)
                );
    }

    private Long insertInstitution(
            String name
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO institutions (
                    name,
                    type,
                    status
                )
                VALUES (
                    ?,
                    'WELFARE_CENTER',
                    'ACTIVE'
                )
                RETURNING id
                """,
                Long.class,
                name
        );
    }

    private Long insertReport(
            Long institutionId,
            String content,
            String embedding,
            LocalDateTime createdAt
    ) {

        if (embedding == null) {
            return jdbcTemplate.queryForObject(
                    """
                    INSERT INTO reports (
                        institution_id,
                        content,
                        status,
                        embedding,
                        created_at,
                        updated_at
                    )
                    VALUES (
                        ?,
                        ?,
                        'SUBMITTED',
                        NULL,
                        ?,
                        ?
                    )
                    RETURNING id
                    """,
                    Long.class,
                    institutionId,
                    content,
                    createdAt,
                    createdAt
            );
        }

        return jdbcTemplate.queryForObject(
                """
                INSERT INTO reports (
                    institution_id,
                    content,
                    status,
                    embedding,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?,
                    ?,
                    'SUBMITTED',
                    ?::vector,
                    ?,
                    ?
                )
                RETURNING id
                """,
                Long.class,
                institutionId,
                content,
                embedding,
                createdAt,
                createdAt
        );
    }
}