package com.dagachi.backend.admin.notice.dto;

import com.dagachi.backend.domain.enums.NoticeStatus;
import jakarta.validation.constraints.Size;

// 관리자 공지 수정 요청 DTO
public record AdminNoticeUpdateRequest(

        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,

        String content,

        NoticeStatus status
) {
}