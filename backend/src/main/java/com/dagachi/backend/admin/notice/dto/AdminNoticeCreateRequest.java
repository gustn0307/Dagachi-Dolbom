package com.dagachi.backend.admin.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 관리자 공지 등록 요청 DTO
public record AdminNoticeCreateRequest(

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,

        @NotBlank(message = "내용은 필수입니다.")
        String content
) {
}