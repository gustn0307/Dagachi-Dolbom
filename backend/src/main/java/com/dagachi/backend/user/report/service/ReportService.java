package com.dagachi.backend.user.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.ReportImage;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.ReportImageRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.report.dto.ReportCreateRequest;
import com.dagachi.backend.user.report.dto.ReportCreateResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.user.report.dto.ReportListItemResponse;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.dagachi.backend.domain.enums.UserStatus;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReportImageRepository reportImageRepository;
    private final UserRepository userRepository;
    private final S3StorageService s3StorageService;

    public ReportService(
            ReportRepository reportRepository,
            ReportImageRepository reportImageRepository,
            UserRepository userRepository,
            S3StorageService s3StorageService
    ) {
        this.reportRepository = reportRepository;
        this.reportImageRepository = reportImageRepository;
        this.userRepository = userRepository;
        this.s3StorageService = s3StorageService;
    }

    @Transactional
    public ReportCreateResponse createReport(
            Long userId,
            ReportCreateRequest request,
            List<MultipartFile> images
    ) {
        User reporter = null;
        String guestPhone = request.guestPhone();

        if (userId != null) {
            reporter = userRepository.findByIdAndDeletedFalse(userId)
                    .orElseThrow(() ->
                            new CustomException(ErrorCode.USER_NOT_FOUND)
                    );

            if (reporter.getStatus() == UserStatus.SUSPENDED) {
                throw new CustomException(
                        ErrorCode.ACCOUNT_SUSPENDED
                );
            }

            if (reporter.getStatus() == UserStatus.WITHDRAWN) {
                throw new CustomException(
                        ErrorCode.ACCOUNT_WITHDRAWN
                );
            }

            guestPhone = null;
        } else {
            if (guestPhone == null || guestPhone.isBlank()) {
                throw new CustomException(
                        ErrorCode.REPORT_GUEST_PHONE_REQUIRED
                );
            }

            guestPhone = guestPhone.trim();
        }

        Report report = Report.create(
                reporter,
                guestPhone,
                request.content(),
                request.address(),
                request.latitude(),
                request.longitude()
        );

        Report savedReport = reportRepository.save(report);

        // 첨부 이미지가 있는 경우 S3 업로드 후 제보와 연결해서 저장합니다.
        saveImages(savedReport, images);

        return ReportCreateResponse.from(savedReport);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportListItemResponse> getMyReports(
            Long userId,
            ReportStatus status,
            Pageable pageable
    ) {
        var reportPage = status == null
                ? reportRepository.findByReporterId(userId, pageable)
                : reportRepository.findByReporterIdAndStatus(
                userId,
                status,
                pageable
        );

        var responsePage = reportPage.map(
                ReportListItemResponse::from
        );

        return PageResponse.from(responsePage);
    }

    private void saveImages(
            Report report,
            List<MultipartFile> images
    ) {
        if (images == null || images.isEmpty()) {
            return;
        }

        // 제보 사진은 팀 정책에 따라 최대 3장까지만 허용합니다.
        if (images.size() > 3) {
            throw new CustomException(
                    ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED
            );
        }

        List<String> uploadedKeys = new ArrayList<>();

        /*
         * S3는 DB 트랜잭션에 참여하지 않으므로,
         * DB 트랜잭션이 최종적으로 rollback되면
         * 이번 요청에서 업로드한 S3 객체를 보상 삭제합니다.
         *
         * saveAllAndFlush() 이후 최종 commit 단계에서 실패하는 경우까지
         * 처리하기 위해 메서드 내부 catch가 아니라
         * transaction afterCompletion을 사용합니다.
         */
        registerRollbackCleanup(uploadedKeys);

        List<ReportImage> reportImages = new ArrayList<>();

        for (MultipartFile image : images) {
            String s3Key = s3StorageService.upload(
                    image,
                    "reports"
            );

            uploadedKeys.add(s3Key);

            ReportImage reportImage = ReportImage.create(
                    report,
                    s3Key,
                    image.getOriginalFilename(),
                    image.getContentType(),
                    image.getSize()
            );

            reportImages.add(reportImage);
        }

        /*
         * DB 오류를 가능한 한 commit 이전에 발생시켜
         * 트랜잭션 rollback 및 S3 보상 삭제가 수행되도록 합니다.
         */
        reportImageRepository.saveAllAndFlush(reportImages);
    }

    private void registerRollbackCleanup(
            List<String> uploadedKeys
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "S3 업로드 보상 처리를 위한 트랜잭션이 활성화되어 있지 않습니다."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            deleteUploadedFiles(uploadedKeys);
                        }
                    }
                }
        );
    }

    private void deleteUploadedFiles(
            List<String> uploadedKeys
    ) {
        for (String key : uploadedKeys) {
            try {
                s3StorageService.delete(key);
            } catch (RuntimeException e) {
                log.error(
                        "S3 보상 삭제에 실패했습니다. key={}",
                        key,
                        e
                );
            }
        }
    }
}