package com.dagachi.backend.admin.notice.dto;

import com.dagachi.backend.domain.entity.Notice;

import java.time.LocalDateTime;

// 공개 공지 응답 DTO
public record PublicNoticeResponse(
        Long id,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    // Notice Entity를 공개 공지 응답 DTO로 변환
    public static PublicNoticeResponse from(Notice notice) {
        return new PublicNoticeResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                notice.getCreatedAt(),
                notice.getUpdatedAt()
        );
    }
}