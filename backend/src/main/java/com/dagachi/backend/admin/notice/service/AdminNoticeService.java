package com.dagachi.backend.admin.notice.service;

import com.dagachi.backend.admin.notice.dto.AdminNoticeResponse;
import com.dagachi.backend.admin.notice.dto.AdminNoticeCreateRequest;
import com.dagachi.backend.admin.notice.dto.AdminNoticeUpdateRequest;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.NoticeStatus;
import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.NoticeRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;

@Service
public class AdminNoticeService {

    private final NoticeRepository noticeRepository;
    private final UserRepository userRepository;

    // NoticeRepository 및 UserRepository 의존성 주입
    public AdminNoticeService(
            NoticeRepository noticeRepository,
            UserRepository userRepository
    ) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
    }

    // 공지 상태 전이 검증 기능
    private void validateStatusTransition(
            NoticeStatus currentStatus,
            NoticeStatus newStatus
    ) {
        if (currentStatus == newStatus) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        boolean validTransition =
                (currentStatus == NoticeStatus.DRAFT
                        && newStatus == NoticeStatus.PUBLISHED)
                        || (currentStatus == NoticeStatus.PUBLISHED
                        && newStatus == NoticeStatus.HIDDEN)
                        || (currentStatus == NoticeStatus.HIDDEN
                        && newStatus == NoticeStatus.PUBLISHED);

        if (!validTransition) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 관리자 공지 목록 조회 기능
    @Transactional(readOnly = true)
    public PageResponse<AdminNoticeResponse> getNotices(
            NoticeStatus status,
            Boolean deleted,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<Notice> notices;

        if (status != null && deleted != null) {
            notices = noticeRepository.findByStatusAndDeleted(
                    status,
                    deleted,
                    pageable
            );
        } else if (status != null) {
            notices = noticeRepository.findByStatus(
                    status,
                    pageable
            );
        } else if (deleted != null) {
            notices = noticeRepository.findByDeleted(
                    deleted,
                    pageable
            );
        } else {
            notices = noticeRepository.findAll(pageable);
        }

        Page<AdminNoticeResponse> responsePage =
                notices.map(notice -> AdminNoticeResponse.from(notice));

        return PageResponse.from(responsePage);
    }

    // 관리자 공지 등록 기능
    @Transactional
    public AdminNoticeResponse createNotice(
            AdminNoticeCreateRequest request,
            Long userId
    ) {
        User author = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.USER_NOT_FOUND)
                );

        Notice notice = Notice.create(
                request.title(),
                request.content(),
                author
        );

        Notice savedNotice = noticeRepository.save(notice);

        return AdminNoticeResponse.from(savedNotice);
    }

    // 관리자 공지 수정 및 상태 변경 기능
    @Transactional
    public AdminNoticeResponse updateNotice(
            Long noticeId,
            AdminNoticeUpdateRequest request
    ) {
        Notice notice = noticeRepository.findByIdAndDeletedFalse(noticeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        if (request.title() == null
                && request.content() == null
                && request.status() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.title() != null && request.title().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.content() != null && request.content().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (request.status() != null) {
            validateStatusTransition(
                    notice.getStatus(),
                    request.status()
            );
        }

        notice.update(
                request.title(),
                request.content()
        );

        if (request.status() != null) {
            notice.changeStatus(request.status());
        }

        noticeRepository.flush();

        return AdminNoticeResponse.from(notice);
    }

    // 관리자 공지 Soft Delete 기능
    @Transactional
    public void deleteNotice(Long noticeId) {
        Notice notice = noticeRepository.findByIdAndDeletedFalse(noticeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        notice.softDelete();
    }
}
