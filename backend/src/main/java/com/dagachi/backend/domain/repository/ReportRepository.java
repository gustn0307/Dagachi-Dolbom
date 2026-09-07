package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends
        JpaRepository<Report, Long>,
        JpaSpecificationExecutor<Report> {

    // 로그인 사용자의 전체 제보 목록 조회
    Page<Report> findByReporterId(
            Long reporterId,
            Pageable pageable
    );

    // 로그인 사용자의 제보 중 특정 상태만 조회
    Page<Report> findByReporterIdAndStatus(
            Long reporterId,
            ReportStatus status,
            Pageable pageable
    );

    /**
     * 기관 관할 지정 시 같은 Report를 여러 기관이 동시에 가져가는 것을
     * 방지하기 위해 DB의 비관적 쓰기 잠금을 사용합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Report> findWithLockById(Long id);

    /**
     * 특정 제보가 지정한 기관에 배정되어 있는지 확인합니다.
     * <p>
     * AI 호출 전에 기관 소유권을 검증하되,
     * LAZY Institution Entity를 직접 초기화하지 않기 위해 사용합니다.
     */
    boolean existsByIdAndInstitutionId(
            Long reportId,
            Long institutionId
    );

    /**
     * 중복 제보 분석 범위 안에서 아직 embedding이 생성되지 않은
     * 제보 ID들을 조회합니다.
     * <p>
     * 검색 범위:
     * - 자기 자신 제외
     * - 최근 지정 기간
     * - 미배정 제보 또는 현재 기관에 배정된 제보
     */
    @Query(
            value = """
                    SELECT r.id
                    FROM reports r
                    WHERE r.id <> :reportId
                      AND r.created_at >= :fromDateTime
                      AND (
                          r.institution_id IS NULL
                          OR r.institution_id = :institutionId
                      )
                      AND r.embedding IS NULL
                    ORDER BY r.created_at DESC
                    """,
            nativeQuery = true
    )
    List<Long> findMissingEmbeddingCandidateIds(
            @Param("reportId") Long reportId,
            @Param("institutionId") Long institutionId,
            @Param("fromDateTime") LocalDateTime fromDateTime
    );

    /**
     * 현재 Report와 내용 embedding이 유사한 제보를 조회합니다.
     * <p>
     * PostgreSQL pgvector의 cosine distance 연산자(<=>)를 사용하며,
     * similarity = 1 - cosine distance 로 계산합니다.
     */
    @Query(
            value = """
                    SELECT
                        r.id AS reportId,
                        r.content AS content,
                        r.latitude AS latitude,
                        r.longitude AS longitude,
                        r.status AS status,
                        r.institution_id AS institutionId,
                        r.created_at AS createdAt,
                        1 - (r.embedding <=> target.embedding) AS similarity
                    FROM reports r
                    JOIN reports target
                      ON target.id = :reportId
                    WHERE r.id <> :reportId
                      AND target.embedding IS NOT NULL
                      AND r.embedding IS NOT NULL
                      AND r.created_at >= :fromDateTime
                      AND (
                          r.institution_id IS NULL
                          OR r.institution_id = :institutionId
                      )
                      AND (
                          1 - (r.embedding <=> target.embedding)
                      ) >= :threshold
                    ORDER BY r.embedding <=> target.embedding
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<ReportSimilarityProjection> findSimilarReports(
            @Param("reportId") Long reportId,
            @Param("institutionId") Long institutionId,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("threshold") double threshold,
            @Param("limit") int limit
    );
}