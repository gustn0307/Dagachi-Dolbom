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

import java.util.ArrayList;
import java.util.List;

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
            reporter = userRepository.findById(userId)
                    .orElseThrow(() ->
                            new CustomException(ErrorCode.USER_NOT_FOUND)
                    );

            guestPhone = null;
        } else {
            if (guestPhone == null || guestPhone.isBlank()) {
                throw new CustomException(
                        ErrorCode.REPORT_GUEST_PHONE_REQUIRED
                );
            }
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

        try {
            List<ReportImage> reportImages = new ArrayList<>();

            for (MultipartFile image : images) {
                String s3Key = s3StorageService.upload(image, "reports");
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

            reportImageRepository.saveAllAndFlush(reportImages);

        } catch (RuntimeException e) {
            // DB 트랜잭션이 롤백되더라도 S3 파일은 자동 삭제되지 않으므로
            // 현재 요청에서 이미 업로드된 파일을 직접 정리합니다.
            deleteUploadedFiles(uploadedKeys);

            throw e;
        }
    }

    private void deleteUploadedFiles(List<String> uploadedKeys) {
        for (String key : uploadedKeys) {
            try {
                s3StorageService.delete(key);
            } catch (RuntimeException ignored) {
                // 보상 삭제 실패가 원래 업로드/저장 예외를 덮어쓰지 않도록 합니다.
            }
        }
    }
}