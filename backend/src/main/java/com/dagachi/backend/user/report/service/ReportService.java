package com.dagachi.backend.user.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.kakao.dto.Coordinate;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.ReportImage;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.ReportImageRepository;
import com.dagachi.backend.domain.repository.ReportRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.report.dto.ReportCreateRequest;
import com.dagachi.backend.user.report.dto.ReportCreateResponse;
import com.dagachi.backend.institution.report.service.ReportEmbeddingService;
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
    private final KakaoLocalClient kakaoLocalClient;
    private final ReportEmbeddingService reportEmbeddingService;

    public ReportService(
            ReportRepository reportRepository,
            ReportImageRepository reportImageRepository,
            UserRepository userRepository,
            S3StorageService s3StorageService,
            KakaoLocalClient kakaoLocalClient,
            ReportEmbeddingService reportEmbeddingService
    ) {
        this.reportRepository = reportRepository;
        this.reportImageRepository = reportImageRepository;
        this.userRepository = userRepository;
        this.s3StorageService = s3StorageService;
        this.kakaoLocalClient = kakaoLocalClient;
        this.reportEmbeddingService = reportEmbeddingService;
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

        String address = request.address() == null
                ? null
                : request.address().trim();

        Coordinate coordinate = resolveCoordinate(address);

        Report report = Report.create(
                reporter,
                guestPhone,
                request.content().trim(),
                address,
                coordinate == null ? null : coordinate.latitude(),
                coordinate == null ? null : coordinate.longitude()
        );

        Report savedReport = reportRepository.save(report);

        // 첨부 이미지가 있는 경우 S3 업로드 후 제보와 연결해서 저장합니다.
        saveImages(savedReport, images);

        // Report와 이미지 저장이 최종 commit된 뒤 embedding 생성을 시도합니다.
        registerEmbeddingAfterCommit(savedReport.getId());

        return ReportCreateResponse.from(savedReport);
    }

    /**
     * 제보 주소를 Kakao Local API로 조회하여 좌표로 변환합니다.
     * 좌표 조회는 제보 접수의 보조 기능이므로,
     * Kakao API 장애나 주소 검색 실패 때문에 제보 자체가 취소되지 않도록
     * <p>
     * 실패 시 null을 반환합니다.
     */
    private Coordinate resolveCoordinate(String address) {

        if (address == null || address.isBlank()) {
            return null;
        }

        try {
            return kakaoLocalClient.searchCoordinate(address);

        } catch (RuntimeException exception) {
            log.warn(
                    "제보 주소 좌표 변환에 실패했습니다. exceptionType={}",
                    exception.getClass().getSimpleName()
            );
            return null;
        }
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

    /**
     * 신규 제보 트랜잭션이 정상 commit된 이후 embedding 생성을 시도합니다.
     * <p>
     * embedding은 중복 제보 분석을 위한 보조 데이터이므로,
     * FastAPI/OpenAI 장애 때문에 이미 접수된 제보를 실패 처리하지 않습니다.
     */
    private void registerEmbeddingAfterCommit(
            Long reportId
    ) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn(
                    "제보 embedding 후처리를 등록할 트랜잭션이 활성화되어 있지 않습니다. reportId={}",
                    reportId
            );
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {

                        try {
                            reportEmbeddingService.ensureEmbedding(
                                    reportId
                            );

                        } catch (RuntimeException exception) {

                            log.warn(
                                    "신규 제보 embedding 생성에 실패했습니다. reportId={}",
                                    reportId,
                                    exception
                            );
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