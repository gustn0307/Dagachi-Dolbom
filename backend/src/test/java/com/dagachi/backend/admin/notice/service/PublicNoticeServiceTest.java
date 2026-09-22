package com.dagachi.backend.admin.notice.service;

import com.dagachi.backend.admin.notice.dto.PublicNoticeResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.NoticeStatus;
import com.dagachi.backend.domain.repository.NoticeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublicNoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @InjectMocks
    private PublicNoticeService publicNoticeService;

    private static final Long NOTICE_ID = 1L;

    private Notice buildNotice(
            NoticeStatus status
    ) {
        User author =
                mock(User.class);

        Notice notice =
                Notice.create(
                        "공지 제목",
                        "공지 내용",
                        author
                );

        ReflectionTestUtils.setField(
                notice,
                "id",
                NOTICE_ID
        );

        if (status != NoticeStatus.DRAFT) {
            notice.changeStatus(status);
        }

        return notice;
    }

    @Test
    @DisplayName(
            "[REQ-NOTICE-01] 공개 목록은 PUBLISHED이며 삭제되지 않은 공지만 조회한다"
    )
    void getNotices_PUBLISHED_비삭제_공지만_조회한다() {

        Notice notice =
                buildNotice(
                        NoticeStatus.PUBLISHED
                );

        given(
                noticeRepository.findByStatusAndDeleted(
                        eq(NoticeStatus.PUBLISHED),
                        eq(false),
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(
                        List.of(notice)
                )
        );

        PageResponse<PublicNoticeResponse> response =
                publicNoticeService.getNotices(
                        0,
                        20
                );

        assertThat(
                response.content()
        ).hasSize(1);

        assertThat(
                response.content()
                        .get(0)
                        .id()
        ).isEqualTo(
                NOTICE_ID
        );

        assertThat(
                response.content()
                        .get(0)
                        .title()
        ).isEqualTo(
                "공지 제목"
        );

        verify(
                noticeRepository
        ).findByStatusAndDeleted(
                eq(NoticeStatus.PUBLISHED),
                eq(false),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName(
            "[REQ-NOTICE-01] 공개 공지 목록을 createdAt 내림차순으로 조회한다"
    )
    void getNotices_createdAt_내림차순으로_조회한다() {

        given(
                noticeRepository.findByStatusAndDeleted(
                        eq(NoticeStatus.PUBLISHED),
                        eq(false),
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(
                        List.of()
                )
        );

        publicNoticeService.getNotices(
                2,
                30
        );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(
                noticeRepository
        ).findByStatusAndDeleted(
                eq(NoticeStatus.PUBLISHED),
                eq(false),
                captor.capture()
        );

        Pageable pageable =
                captor.getValue();

        assertThat(
                pageable.getPageNumber()
        ).isEqualTo(
                2
        );

        assertThat(
                pageable.getPageSize()
        ).isEqualTo(
                30
        );

        assertThat(
                pageable.getSort()
                        .getOrderFor("createdAt")
        ).isNotNull();

        assertThat(
                pageable.getSort()
                        .getOrderFor("createdAt")
                        .isDescending()
        ).isTrue();
    }

    @Test
    @DisplayName(
            "[REQ-NOTICE-02] PUBLISHED 공지는 공개 상세 조회할 수 있다"
    )
    void getNotice_PUBLISHED이면_정상조회한다() {

        Notice notice =
                buildNotice(
                        NoticeStatus.PUBLISHED
                );

        given(
                noticeRepository.findByIdAndDeletedFalse(
                        NOTICE_ID
                )
        ).willReturn(
                Optional.of(notice)
        );

        PublicNoticeResponse response =
                publicNoticeService.getNotice(
                        NOTICE_ID
                );

        assertThat(
                response.id()
        ).isEqualTo(
                NOTICE_ID
        );

        assertThat(
                response.title()
        ).isEqualTo(
                "공지 제목"
        );

        assertThat(
                response.content()
        ).isEqualTo(
                "공지 내용"
        );
    }

    @Test
    @DisplayName(
            "[REQ-NOTICE-02] PUBLISHED 상태가 아닌 공지는 공개 상세 조회할 수 없다"
    )
    void getNotice_PUBLISHED가_아니면_RESOURCE_NOT_FOUND() {

        Notice notice =
                buildNotice(
                        NoticeStatus.DRAFT
                );

        given(
                noticeRepository.findByIdAndDeletedFalse(
                        NOTICE_ID
                )
        ).willReturn(
                Optional.of(notice)
        );

        assertThatThrownBy(
                () ->
                        publicNoticeService.getNotice(
                                NOTICE_ID
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.RESOURCE_NOT_FOUND
                );
    }

    @Test
    @DisplayName(
            "[REQ-NOTICE-02] 존재하지 않거나 Soft Delete된 공지는 공개 상세 조회할 수 없다"
    )
    void getNotice_없거나_삭제된공지면_RESOURCE_NOT_FOUND() {

        given(
                noticeRepository.findByIdAndDeletedFalse(
                        NOTICE_ID
                )
        ).willReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        publicNoticeService.getNotice(
                                NOTICE_ID
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.RESOURCE_NOT_FOUND
                );
    }
}