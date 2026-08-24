package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}