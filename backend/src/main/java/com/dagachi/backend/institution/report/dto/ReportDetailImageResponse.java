package com.dagachi.backend.institution.report.dto;

import com.dagachi.backend.domain.entity.ReportImage;

public record ReportDetailImageResponse(
        Long imageId,
        String originalFilename,
        String contentType,
        Long fileSize,
        String imageUrl
) {

    public static ReportDetailImageResponse from(
            ReportImage reportImage,
            String imageUrl
    ) {
        return new ReportDetailImageResponse(
                reportImage.getId(),
                reportImage.getOriginalFilename(),
                reportImage.getContentType(),
                reportImage.getFileSize(),
                imageUrl
        );
    }
}