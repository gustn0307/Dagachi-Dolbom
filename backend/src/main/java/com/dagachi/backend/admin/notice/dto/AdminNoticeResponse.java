package com.dagachi.backend.admin.notice.dto;

import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.enums.NoticeStatus;

import java.time.LocalDateTime;

public record AdminNoticeResponse(
        Long id,
        String title,
        String content,
        NoticeStatus status,
        Boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static AdminNoticeResponse from(Notice notice) {
        return new AdminNoticeResponse(
            notice.getId(),
            notice.getTitle(),
            notice.getContent(),
            notice.getStatus(),
            notice.getDeleted(),
            notice.getCreatedAt(),
            notice.getUpdatedAt()
        );
    }
}
