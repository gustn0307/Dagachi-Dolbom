package com.dagachi.backend.admin.notice.service;

import com.dagachi.backend.admin.notice.dto.AdminNoticeResponse;
import com.dagachi.backend.admin.notice.dto.AdminNoticeCreateRequest;
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

    public AdminNoticeService(
            NoticeRepository noticeRepository,
            UserRepository userRepository
    ) {
        this.noticeRepository = noticeRepository;
        this.userRepository = userRepository;
    }

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
}
