package com.dagachi.backend.admin.notice.service;

import com.dagachi.backend.admin.notice.dto.PublicNoticeResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.Notice;
import com.dagachi.backend.domain.enums.NoticeStatus;
import com.dagachi.backend.domain.repository.NoticeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicNoticeService {

    private final NoticeRepository noticeRepository;

    // NoticeRepository 의존성 주입
    public PublicNoticeService(NoticeRepository noticeRepository) {
        this.noticeRepository = noticeRepository;
    }

    // 공개 공지 목록 조회 기능
    @Transactional(readOnly = true)
    public PageResponse<PublicNoticeResponse> getNotices(
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by("createdAt").descending()
        );

        Page<Notice> notices =
                noticeRepository.findByStatusAndDeleted(
                        NoticeStatus.PUBLISHED,
                        false,
                        pageable
                );

        Page<PublicNoticeResponse> responsePage =
                notices.map(notice -> PublicNoticeResponse.from(notice));

        return PageResponse.from(responsePage);
    }

    // 공개 공지 상세 조회 기능
    @Transactional(readOnly = true)
    public PublicNoticeResponse getNotice(Long noticeId) {
        Notice notice = noticeRepository.findByIdAndDeletedFalse(noticeId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        if (notice.getStatus() != NoticeStatus.PUBLISHED) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        return PublicNoticeResponse.from(notice);
    }
}