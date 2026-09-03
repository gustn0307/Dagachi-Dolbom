package com.dagachi.backend.domain.repository;

import com.dagachi.backend.domain.entity.ReportImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportImageRepository extends JpaRepository<ReportImage, Long> {

    /**
     * 특정 제보에 첨부된 이미지를 저장 순서대로 조회합니다.
     *
     * ReportImage를 개별 조회하지 않고 한 번에 가져와
     * REPORT-04 상세 응답의 이미지 목록을 구성합니다.
     */
    List<ReportImage> findByReportIdOrderByIdAsc(
            Long reportId
    );
}