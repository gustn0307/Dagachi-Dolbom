package com.dagachi.backend.admin.notice.controller;

import com.dagachi.backend.admin.notice.service.PublicNoticeService;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PublicNoticeControllerTest {

    @Test
    @DisplayName(
            "[REQ-NOTICE-01] 공개 공지 목록 size가 100을 초과하면 INVALID_INPUT_VALUE"
    )
    void getNotices_size가_100초과면_INVALID_INPUT_VALUE() {

        PublicNoticeService service =
                mock(
                        PublicNoticeService.class
                );

        PublicNoticeController controller =
                new PublicNoticeController(
                        service
                );

        assertThatThrownBy(
                () ->
                        controller.getNotices(
                                0,
                                101
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );

        verifyNoInteractions(
                service
        );
    }
}