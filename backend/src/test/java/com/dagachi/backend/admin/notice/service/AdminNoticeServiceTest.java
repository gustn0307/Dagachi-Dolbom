package com.dagachi.backend.admin.notice.service;

import com.dagachi.backend.admin.notice.dto.AdminNoticeCreateRequest;
import com.dagachi.backend.admin.notice.dto.AdminNoticeResponse;
import com.dagachi.backend.admin.notice.dto.AdminNoticeUpdateRequest;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.NoticeStatus;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.NoticeRepository;
import com.dagachi.backend.domain.repository.UserRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminNoticeServiceTest {

    @Mock
    private NoticeRepository noticeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminNoticeService service;

    private static final Long USER_ID = 100L;
    private static final Long NOTICE_ID = 1L;

    private User buildUser() {
        User user = User.create(
                "admin@test.com",
                "encoded-pw",
                "관리자",
                "관리자",
                "010-0000-0000",
                UserGender.MALE
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                USER_ID
        );

        return user;
    }

    private Notice buildNotice(
            NoticeStatus status
    ) {
        Notice notice = Notice.create(
                "기존 제목",
                "기존 내용",
                buildUser()
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

    // ---------------------------------------------------------------
    // 목록 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("공지 목록 - status와 deleted가 모두 있으면 두 조건으로 조회한다")
    void getNotices_status_deleted_모두있으면_두조건으로_조회한다() {

        Notice notice = buildNotice(
                NoticeStatus.PUBLISHED
        );

        given(
                noticeRepository.findByStatusAndDeleted(
                        eq(NoticeStatus.PUBLISHED),
                        eq(false),
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(List.of(notice))
        );

        PageResponse<AdminNoticeResponse> response =
                service.getNotices(
                        NoticeStatus.PUBLISHED,
                        false,
                        0,
                        20
                );

        assertThat(response.content())
                .hasSize(1);

        assertThat(response.content().get(0).id())
                .isEqualTo(NOTICE_ID);

        verify(noticeRepository)
                .findByStatusAndDeleted(
                        eq(NoticeStatus.PUBLISHED),
                        eq(false),
                        any(Pageable.class)
                );
    }

    @Test
    @DisplayName("공지 목록 - status만 있으면 상태 조건으로 조회한다")
    void getNotices_status만_있으면_상태조건으로_조회한다() {

        given(
                noticeRepository.findByStatus(
                        eq(NoticeStatus.DRAFT),
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(List.of())
        );

        service.getNotices(
                NoticeStatus.DRAFT,
                null,
                0,
                20
        );

        verify(noticeRepository)
                .findByStatus(
                        eq(NoticeStatus.DRAFT),
                        any(Pageable.class)
                );
    }

    @Test
    @DisplayName("공지 목록 - deleted만 있으면 삭제 여부 조건으로 조회한다")
    void getNotices_deleted만_있으면_삭제여부조건으로_조회한다() {

        given(
                noticeRepository.findByDeleted(
                        eq(true),
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(List.of())
        );

        service.getNotices(
                null,
                true,
                0,
                20
        );

        verify(noticeRepository)
                .findByDeleted(
                        eq(true),
                        any(Pageable.class)
                );
    }

    @Test
    @DisplayName("공지 목록 - 필터가 없으면 전체 조회하고 createdAt 내림차순 Pageable을 사용한다")
    void getNotices_필터가_없으면_전체조회한다() {

        given(
                noticeRepository.findAll(
                        any(Pageable.class)
                )
        ).willReturn(
                new PageImpl<>(List.of())
        );

        service.getNotices(
                null,
                null,
                2,
                30
        );

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(
                        Pageable.class
                );

        verify(noticeRepository)
                .findAll(
                        captor.capture()
                );

        Pageable pageable =
                captor.getValue();

        assertThat(pageable.getPageNumber())
                .isEqualTo(2);

        assertThat(pageable.getPageSize())
                .isEqualTo(30);

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

    // ---------------------------------------------------------------
    // 등록
    // ---------------------------------------------------------------

    @Test
    @DisplayName( "[REQ-NOTICE-03] ADMIN이 공지를 정상 등록하면 DRAFT 상태로 저장한다")
    void createNotice_정상요청이면_DRAFT로_저장한다() {

        User user = buildUser();

        AdminNoticeCreateRequest request =
                new AdminNoticeCreateRequest(
                        "새 공지",
                        "새 공지 내용"
                );

        given(
                userRepository.findByIdAndDeletedFalse(
                        USER_ID
                )
        ).willReturn(
                Optional.of(user)
        );

        given(
                noticeRepository.save(
                        any(Notice.class)
                )
        ).willAnswer(
                invocation -> {
                    Notice notice =
                            invocation.getArgument(0);

                    ReflectionTestUtils.setField(
                            notice,
                            "id",
                            NOTICE_ID
                    );

                    return notice;
                }
        );

        AdminNoticeResponse response =
                service.createNotice(
                        request,
                        USER_ID
                );

        assertThat(response.id())
                .isEqualTo(NOTICE_ID);

        assertThat(response.title())
                .isEqualTo("새 공지");

        assertThat(response.content())
                .isEqualTo("새 공지 내용");

        assertThat(response.status())
                .isEqualTo(NoticeStatus.DRAFT);

        assertThat(response.deleted())
                .isFalse();
    }

    @Test
    @DisplayName( "[REQ-NOTICE-03] 공지 등록 시 작성자를 찾을 수 없으면 USER_NOT_FOUND")
    void createNotice_작성자가_없으면_USER_NOT_FOUND() {

        given(
                userRepository.findByIdAndDeletedFalse(
                        USER_ID
                )
        ).willReturn(
                Optional.empty()
        );

        AdminNoticeCreateRequest request =
                new AdminNoticeCreateRequest(
                        "제목",
                        "내용"
                );

        assertThatThrownBy(
                () ->
                        service.createNotice(
                                request,
                                USER_ID
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.USER_NOT_FOUND
                );
    }

    // ---------------------------------------------------------------
    // 수정 / 상태 전이
    // ---------------------------------------------------------------

    @Test
    @DisplayName( "[REQ-NOTICE-04] 존재하지 않는 공지는 수정할 수 없다")
    void updateNotice_공지없으면_RESOURCE_NOT_FOUND() {

        given(
                noticeRepository.findByIdAndDeletedFalse(
                        NOTICE_ID
                )
        ).willReturn(
                Optional.empty()
        );

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        "수정 제목",
                        null,
                        null
                );

        assertThatThrownBy(
                () ->
                        service.updateNotice(
                                NOTICE_ID,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.RESOURCE_NOT_FOUND
                );
    }

    @Test
    @DisplayName( "[REQ-NOTICE-04] 제목·내용·상태 변경값이 없으면 공지를 수정할 수 없다")
    void updateNotice_변경값이_없으면_INVALID_INPUT_VALUE() {

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

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        null,
                        null,
                        null
                );

        assertThatThrownBy(
                () ->
                        service.updateNotice(
                                NOTICE_ID,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName("[REQ-NOTICE-04] 공지 제목은 공백으로 수정할 수 없다")
    void updateNotice_제목이_공백이면_INVALID_INPUT_VALUE() {

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

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        "   ",
                        null,
                        null
                );

        assertThatThrownBy(
                () ->
                        service.updateNotice(
                                NOTICE_ID,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName("[REQ-NOTICE-04] 공지 내용은 공백으로 수정할 수 없다")
    void updateNotice_내용이_공백이면_INVALID_INPUT_VALUE() {

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

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        null,
                        "   ",
                        null
                );

        assertThatThrownBy(
                () ->
                        service.updateNotice(
                                NOTICE_ID,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName("[REQ-NOTICE-05] DRAFT 공지를 바로 HIDDEN 상태로 변경할 수 없다")
    void updateNotice_DRAFT에서_HIDDEN은_INVALID_INPUT_VALUE() {

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

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        null,
                        null,
                        NoticeStatus.HIDDEN
                );

        assertThatThrownBy(
                () ->
                        service.updateNotice(
                                NOTICE_ID,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.INVALID_INPUT_VALUE
                );
    }

    @Test
    @DisplayName("[REQ-NOTICE-04][REQ-NOTICE-05] ADMIN이 공지 내용과 공개 상태를 정상 수정한다")
    void updateNotice_정상수정과_상태전이를_적용한다() {

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

        AdminNoticeUpdateRequest request =
                new AdminNoticeUpdateRequest(
                        "수정 제목",
                        "수정 내용",
                        NoticeStatus.PUBLISHED
                );

        AdminNoticeResponse response =
                service.updateNotice(
                        NOTICE_ID,
                        request
                );

        assertThat(response.title())
                .isEqualTo("수정 제목");

        assertThat(response.content())
                .isEqualTo("수정 내용");

        assertThat(response.status())
                .isEqualTo(
                        NoticeStatus.PUBLISHED
                );

        verify(noticeRepository)
                .flush();
    }

    // ---------------------------------------------------------------
    // Soft Delete
    // ---------------------------------------------------------------

    @Test
    @DisplayName("[REQ-NOTICE-06] ADMIN이 공지를 삭제하면 실제 삭제하지 않고 Soft Delete한다")
    void deleteNotice_정상요청이면_SoftDelete한다() {

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

        service.deleteNotice(
                NOTICE_ID
        );

        assertThat(notice.getDeleted())
                .isTrue();

        assertThat(notice.getDeletedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("[REQ-NOTICE-06] 존재하지 않는 공지는 Soft Delete할 수 없다")
    void deleteNotice_공지없으면_RESOURCE_NOT_FOUND() {

        given(
                noticeRepository.findByIdAndDeletedFalse(
                        NOTICE_ID
                )
        ).willReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        service.deleteNotice(
                                NOTICE_ID
                        )
        )
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(
                        ErrorCode.RESOURCE_NOT_FOUND
                );
    }
}