package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

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
}