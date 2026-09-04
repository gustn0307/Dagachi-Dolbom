package com.dagachi.backend.institution.report.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.kakao.dto.Coordinate;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.common.util.GeoUtils;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.Report;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.AIAnalysisType;
import com.dagachi.backend.domain.enums.AITargetType;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.ReportStatus;
import com.dagachi.backend.domain.repository.*;
import com.dagachi.backend.institution.recipient.dto.CareRecipientCreateRequest;
import com.dagachi.backend.institution.recipient.dto.CareRecipientDetailResponse;
import com.dagachi.backend.institution.report.dto.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class InstitutionReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private final ReportImageRepository reportImageRepository;
    private final AIAnalysisRepository aiAnalysisRepository;
    private final S3StorageService s3StorageService;
    private final CareRecipientRepository careRecipientRepository;

    public InstitutionReportService(
            ReportRepository reportRepository,
            UserRepository userRepository,
            KakaoLocalClient kakaoLocalClient,
            ReportImageRepository reportImageRepository,
            AIAnalysisRepository aiAnalysisRepository,
            S3StorageService s3StorageService,
            CareRecipientRepository careRecipientRepository
    ) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.kakaoLocalClient = kakaoLocalClient;
        this.reportImageRepository = reportImageRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.s3StorageService = s3StorageService;
        this.careRecipientRepository = careRecipientRepository;
    }

    /**
     * 현재 로그인한 기관에 배정된 제보 목록을 조회합니다.
     * <p>
     * 기존 REPORT-03 계약에 해당하며,
     * 다른 기관의 제보와 미배정 제보는 포함하지 않습니다.
     * <p>
     * status와 접수 날짜 범위는 선택 조건입니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionReportListItemResponse> getInstitutionReports(
            Long userId,
            ReportStatus status,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        validateDateRange(from, to);

        Institution institution =
                getInstitutionByUserId(userId);

        LocalDateTime fromDateTime =
                toStartDateTime(from);

        LocalDateTime toExclusive =
                toExclusiveDateTime(to);

        Specification<Report> specification =
                createReportSpecification(
                        institution.getId(),
                        false,
                        status,
                        fromDateTime,
                        toExclusive
                );

        /*
         * REPORT-03의 기본 정렬 계약인 createdAt DESC를
         * 클라이언트의 임의 sort 값과 무관하게 적용합니다.
         */
        Pageable reportPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        Page<InstitutionReportListItemResponse> responsePage =
                reportRepository.findAll(
                                specification,
                                reportPageable
                        )
                        .map(InstitutionReportListItemResponse::from);

        return PageResponse.from(responsePage);
    }

    /**
     * 아직 어떤 기관에도 배정되지 않은 제보 목록을 조회합니다.
     * <p>
     * 기관 주소를 Kakao Local API로 좌표 변환한 뒤,
     * 각 제보 좌표와의 거리를 계산하여 가까운 순으로 정렬합니다.
     * <p>
     * DB에서 먼저 pagination하면 전체 데이터 기준 거리순이 깨질 수 있으므로,
     * 조건에 맞는 미배정 제보를 조회한 뒤 거리 계산/정렬을 완료하고
     * 마지막에 page/size를 적용합니다.
     */
    @Transactional(readOnly = true)
    public PageResponse<UnassignedReportListItemResponse> getUnassignedReports(
            Long userId,
            ReportStatus status,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    ) {
        validateDateRange(from, to);

        Institution institution =
                getInstitutionByUserId(userId);

        LocalDateTime fromDateTime =
                toStartDateTime(from);

        LocalDateTime toExclusive =
                toExclusiveDateTime(to);

        Specification<Report> specification =
                createReportSpecification(
                        null,
                        true,
                        status,
                        fromDateTime,
                        toExclusive
                );

        List<Report> reports =
                reportRepository.findAll(
                        specification,
                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );

        /*
         * 기관 주소가 등록되어 있으면 요청당 한 번만 Kakao API를 호출합니다.
         *
         * 제보마다 기관 주소를 다시 좌표로 변환하지 않고,
         * 얻은 기관 좌표 하나를 모든 Report 거리 계산에 재사용합니다.
         */
        Coordinate institutionCoordinate =
                getInstitutionCoordinate(
                        institution.getAddress()
                );

        List<UnassignedReportListItemResponse> sortedReports =
                reports.stream()
                        .map(report ->
                                UnassignedReportListItemResponse.from(
                                        report,
                                        calculateDistance(
                                                institutionCoordinate,
                                                report
                                        )
                                )
                        )
                        /*
                         * 좌표가 있는 제보는 가까운 순으로,
                         * 거리 계산이 불가능한 제보(null)는 뒤로 보냅니다.
                         *
                         * 거리가 동일하면 최신 접수 제보를 먼저 보여줍니다.
                         */
                        .sorted(
                                Comparator
                                        .comparing(
                                                UnassignedReportListItemResponse::distanceKm,
                                                Comparator.nullsLast(
                                                        Comparator.naturalOrder()
                                                )
                                        )
                                        .thenComparing(
                                                UnassignedReportListItemResponse::createdAt,
                                                Comparator.reverseOrder()
                                        )
                        )
                        .toList();

        Page<UnassignedReportListItemResponse> responsePage =
                createPage(
                        sortedReports,
                        pageable
                );

        return PageResponse.from(responsePage);
    }

    /**
     * 미배정 제보를 현재 로그인한 기관 사용자의 소속 기관에 배정합니다.
     */
    @Transactional
    public ReportAssignmentResponse assignReportToMyInstitution(
            Long userId,
            Long reportId
    ) {
        Institution institution =
                getInstitutionByUserId(userId);

        Report report = reportRepository.findWithLockById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        if (report.getInstitution() != null) {
            throw new CustomException(
                    ErrorCode.REPORT_ALREADY_ASSIGNED
            );
        }

        report.assignInstitution(institution);

        return ReportAssignmentResponse.from(report);
    }

    /**
     * 현재 로그인한 기관에 배정된 제보의 상세 정보를 조회합니다.
     * <p>
     * REPORT-04에 해당하며,
     * 단순히 기관 Role만 확인하는 것이 아니라
     * 실제 Report가 로그인 사용자의 기관에 배정되어 있는지도 검증합니다.
     * <p>
     * 상세 응답에는:
     * - 제보 원문 / 정확한 위치
     * - 첨부 이미지의 Presigned URL
     * - 연결된 돌봄 대상자 요약
     * - 저장된 최신 AI 요약
     * 을 포함합니다.
     */
    @Transactional(readOnly = true)
    public InstitutionReportDetailResponse getInstitutionReportDetail(
            Long userId,
            Long reportId
    ) {
        Institution institution =
                getInstitutionByUserId(userId);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * /api/institution/** 권한 검사는
         * "기관 사용자"라는 것만 보장합니다.
         * 다른 기관에 배정된 제보의 원문, 위치, 이미지가
         * 노출되지 않도록 실제 기관 소유권을 별도로 확인합니다.
         */
        if (report.getInstitution() == null
                || !report.getInstitution().getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 데이터 오류나 향후 연결 로직의 회귀로
         * 다른 기관 대상자가 연결되어 있더라도
         * 대상자 개인정보가 상세 응답에 노출되지 않도록 재검증합니다.
         */
        if (report.getCareRecipient() != null
                && !report.getCareRecipient()
                .getInstitution()
                .getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        List<ReportDetailImageResponse> images =
                reportImageRepository
                        .findByReportIdOrderByIdAsc(reportId)
                        .stream()
                        .map(reportImage ->
                                ReportDetailImageResponse.from(
                                        reportImage,
                                        s3StorageService.generatePresignedUrl(
                                                reportImage.getS3Key()
                                        )
                                )
                        )
                        .toList();

        ReportDetailAiSummaryResponse aiSummary =
                getLatestReportSummary(reportId);

        return InstitutionReportDetailResponse.from(
                report,
                images,
                aiSummary
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 제보의 처리 상태를 변경합니다.
     *
     * 기관 Role만 확인하는 것으로 끝내지 않고,
     * 실제 Report가 로그인 사용자의 기관 소유인지 검증한 뒤
     * Entity의 상태 전이 규칙을 통해 상태를 변경합니다.
     */
    @Transactional
    public ReportStatusUpdateResponse updateReportStatus(
            Long userId,
            Long reportId,
            ReportStatus newStatus
    ) {
        Institution institution =
                getInstitutionByUserId(userId);

        /*
         * 동일 Report에 대한 상태 변경 요청을 직렬화합니다.
         *
         * 두 요청이 동시에 같은 이전 상태를 읽고 각각 상태 전이를
         * 성공시키는 lost update를 방지합니다.
         */
        Report report = reportRepository.findWithLockById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * 미배정 제보 및 다른 기관의 제보 상태를
         * 임의로 변경하지 못하도록 기관 소유권을 확인합니다.
         */
        if (report.getInstitution() == null
                || !report.getInstitution()
                .getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 실제 상태 전이 가능 여부는 Report Entity가 판단합니다.
         */
        report.changeStatus(newStatus);

        /*
         * BaseTimeEntity의 @LastModifiedDate는 flush 시점에 갱신되므로,
         * 응답 DTO를 만들기 전에 UPDATE를 DB에 반영하여
         * updatedAt이 실제 저장값과 동일하도록 합니다.
         */
        reportRepository.flush();

        /*
         * @Transactional 안의 managed Entity이므로
         * 별도의 save() 호출 없이 dirty checking으로 UPDATE됩니다.
         */
        return ReportStatusUpdateResponse.from(report);
    }

    /**
     * 현재 로그인한 기관에 배정된 제보를
     * 같은 기관의 기존 돌봄 대상자와 연결합니다.
     *
     * Report와 CareRecipient 모두 로그인 기관 소속인지 확인하며,
     * 이미 대상자가 연결된 제보에는 다시 연결할 수 없습니다.
     */
    @Transactional
    public ReportCareRecipientLinkResponse linkCareRecipient(
            Long userId,
            Long reportId,
            Long careRecipientId
    ) {
        Institution institution =
                getInstitutionByUserId(userId);

        /*
         * 같은 제보에 대한 기존 대상자 연결 요청을 직렬화하여
         * 서로 다른 대상자가 동시에 연결되는 경쟁 상태를 방지합니다.
         */
        Report report = reportRepository.findWithLockById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * 미배정 제보 또는 다른 기관의 제보에는
         * 대상자를 연결할 수 없습니다.
         */
        if (report.getInstitution() == null
                || !report.getInstitution()
                .getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 이미 연결된 대상자가 있으면
         * 다른 대상자로 덮어쓰지 않습니다.
         */
        if (report.getCareRecipient() != null) {
            throw new CustomException(
                    ErrorCode.REPORT_CARE_RECIPIENT_ALREADY_LINKED
            );
        }

        CareRecipient careRecipient =
                careRecipientRepository.findById(careRecipientId)
                        .orElseThrow(
                                () -> new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
                        );

        /*
         * 논리 삭제된 대상자는 연결 대상으로 사용할 수 없습니다.
         */
        if (Boolean.TRUE.equals(careRecipient.getDeleted())) {
            throw new CustomException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        /*
         * 다른 기관의 돌봄 대상자가 연결되면
         * 기관 간 개인정보가 섞이므로 반드시 차단합니다.
         */
        if (!careRecipient.getInstitution()
                .getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        report.linkCareRecipient(
                careRecipient
        );

        return ReportCareRecipientLinkResponse.from(
                report
        );
    }

    /**
     * 현재 로그인한 기관에 배정된 제보를 기준으로
     * 신규 돌봄 대상자를 생성하고 즉시 해당 제보와 연결합니다.
     *
     * CareRecipient 생성과 Report 연결은 하나의 Transaction에서 처리하여
     * 둘 중 하나라도 실패하면 전체 작업을 rollback합니다.
     */
    @Transactional
    public ReportCareRecipientCreateResponse createAndLinkCareRecipient(
            Long userId,
            Long reportId,
            CareRecipientCreateRequest request
    ) {
        Institution institution =
                getInstitutionByUserId(userId);

        /*
         * 신규 대상자 생성 전에 Report row를 잠급니다.
         *
         * 동일 Report에 REPORT-06/07 또는 REPORT-07 두 요청이
         * 동시에 들어와 고아 CareRecipient가 생성되는 것을 방지합니다.
         */
        Report report = reportRepository.findWithLockById(reportId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );

        /*
         * 미배정 제보나 다른 기관의 제보를 기준으로
         * 신규 대상자를 만들 수 없습니다.
         */
        if (report.getInstitution() == null
                || !report.getInstitution()
                .getId()
                .equals(institution.getId())) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        /*
         * 이미 대상자가 연결된 제보라면
         * 또 다른 신규 대상자를 생성하지 않습니다.
         *
         * 이 검증을 CareRecipient 생성보다 먼저 수행해야
         * 불필요한 대상자 데이터가 생성되지 않습니다.
         */
        if (report.getCareRecipient() != null) {
            throw new CustomException(
                    ErrorCode.REPORT_CARE_RECIPIENT_ALREADY_LINKED
            );
        }

        /*
         * 제보 기반 신규 대상자 등록 시점에는
         * 아직 동의 여부를 확인하지 않은 PENDING 또는
         * 이미 동의를 확보한 AGREED만 허용합니다.
         *
         * WITHDRAWN은 기존 동의를 철회했다는 의미이므로
         * 신규 등록 초기 상태로는 허용하지 않습니다.
         */
        if (request.consentStatus() == ConsentStatus.WITHDRAWN) {
            throw new CustomException(
                    ErrorCode.REPORT_INVALID_INITIAL_CONSENT_STATUS
            );
        }

        /*
         * CARE-03과 동일한 입력값을 사용해
         * 로그인 사용자의 기관 소속 대상자를 생성합니다.
         */
        CareRecipient careRecipient =
                CareRecipient.create(
                        institution,
                        request.name().trim(),
                        request.gender(),
                        request.birthYear(),
                        normalizeNullableText(request.phone()),
                        request.address().trim(),
                        normalizeNullableText(request.detailAddress()),
                        request.latitude(),
                        request.longitude(),
                        request.consentStatus()
                );

        CareRecipient savedCareRecipient =
                careRecipientRepository.save(
                        careRecipient
                );

        /*
         * 방금 생성한 대상자를 현재 제보와 연결합니다.
         *
         * 이 메서드 전체가 @Transactional이므로
         * 이후 예외가 발생하면 CareRecipient INSERT도 함께 rollback됩니다.
         */
        report.linkCareRecipient(
                savedCareRecipient
        );

        /*
         * 신규 대상자는 현재 이 Report 한 건과 연결되고,
         * 아직 활동은 없으므로 각각 1, 0으로 반환합니다.
         */
        CareRecipientDetailResponse recipientResponse =
                CareRecipientDetailResponse.of(
                        savedCareRecipient,
                        1L,
                        0L
                );

        return new ReportCareRecipientCreateResponse(
                report.getId(),
                recipientResponse
        );
    }

    /**
     * 특정 제보에 저장된 REPORT_SUMMARY 분석 중
     * 가장 최신 결과를 조회합니다.
     * <p>
     * REPORT-04에서는 AI를 새로 실행하지 않고,
     * 이미 저장되어 있는 결과가 있을 때만 참고정보로 반환합니다.
     */
    private ReportDetailAiSummaryResponse getLatestReportSummary(
            Long reportId
    ) {
        return aiAnalysisRepository
                .findTopByTargetTypeAndTargetIdAndAnalysisTypeOrderByCreatedAtDescIdDesc(
                        AITargetType.REPORT,
                        reportId,
                        AIAnalysisType.REPORT_SUMMARY
                )
                .map(analysis -> {
                    /*
                     * AIAnalysis는 여러 분석 타입이 공통 JSONB 컬럼을 사용하므로,
                     * summary가 존재하는 것뿐 아니라 실제 문자열인지도 확인합니다.
                     */
                    if (analysis.getResultJson() == null
                            || !analysis.getResultJson().has("summary")
                            || !analysis.getResultJson()
                            .get("summary")
                            .isTextual()) {
                        return null;
                    }

                    String summary =
                            analysis.getResultJson()
                                    .get("summary")
                                    .asText();

                    if (summary.isBlank()) {
                        return null;
                    }

                    return ReportDetailAiSummaryResponse.from(
                            analysis,
                            summary
                    );
                })
                .orElse(null);
    }

    /**
     * JWT principal의 userId를 기준으로 현재 사용자의 소속 기관을 조회합니다.
     * <p>
     * 기관 관련 API에서 institutionId를 클라이언트로부터 직접 받지 않고
     * 서버가 인증 사용자 기준으로 기관 범위를 결정합니다.
     */
    private Institution getInstitutionByUserId(
            Long userId
    ) {
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );

        Institution institution = user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        return institution;
    }

    /**
     * 조회 시작 날짜를 해당 날짜 00:00으로 변환합니다.
     */
    private LocalDateTime toStartDateTime(
            LocalDate from
    ) {
        return from != null
                ? from.atStartOfDay()
                : null;
    }

    /**
     * 조회 종료 날짜는 다음 날 00:00 미만으로 처리합니다.
     * <p>
     * 예:
     * to = 2026-09-03
     * -> createdAt < 2026-09-04T00:00
     * <p>
     * 이렇게 하면 9월 3일 하루 전체를 안전하게 포함할 수 있습니다.
     */
    private LocalDateTime toExclusiveDateTime(
            LocalDate to
    ) {
        return to != null
                ? to.plusDays(1).atStartOfDay()
                : null;
    }

    /**
     * 기관 주소를 Kakao Local API로 좌표 변환합니다.
     * <p>
     * Institution.address는 현재 nullable이므로,
     * 주소 자체가 없는 경우에는 거리 계산을 수행하지 않습니다.
     */
    private Coordinate getInstitutionCoordinate(
            String institutionAddress
    ) {
        if (!StringUtils.hasText(institutionAddress)) {
            return null;
        }

        return kakaoLocalClient.searchCoordinate(
                institutionAddress
        );
    }

    /**
     * 기관 좌표와 제보 좌표 사이의 거리를 km 단위로 계산합니다.
     * <p>
     * 기관 또는 제보 좌표가 없으면 null을 반환하고,
     * 해당 제보는 거리순 정렬에서 뒤로 배치합니다.
     */
    private BigDecimal calculateDistance(
            Coordinate institutionCoordinate,
            Report report
    ) {
        if (institutionCoordinate == null) {
            return null;
        }

        return GeoUtils.calculateDistanceKm(
                institutionCoordinate.latitude(),
                institutionCoordinate.longitude(),
                report.getLatitude(),
                report.getLongitude()
        );
    }

    /**
     * 거리 계산과 정렬이 끝난 전체 결과를
     * 요청한 page/size 범위로 잘라 Page로 변환합니다.
     *
     * Pageable offset은 long이므로 매우 큰 page 요청에서도
     * int 변환 overflow가 발생하지 않도록 범위를 먼저 확인합니다.
     */
    private <T> Page<T> createPage(
            List<T> content,
            Pageable pageable
    ) {
        long offset = pageable.getOffset();

        /*
         * 요청 시작 위치가 전체 결과보다 뒤라면
         * 빈 페이지를 반환합니다.
         *
         * 이 검사를 먼저 수행하면 매우 큰 page 값도
         * int로 강제 변환할 필요가 없습니다.
         */
        if (offset >= content.size()) {
            return new PageImpl<>(
                    List.of(),
                    pageable,
                    content.size()
            );
        }

        /*
         * offset < content.size()가 보장됐고
         * List.size() 자체가 int 범위이므로 안전하게 변환할 수 있습니다.
         */
        int start = (int) offset;

        /*
         * content.size() - start를 이용해
         * start + pageSize 자체의 int overflow 가능성도 피합니다.
         */
        int length =
                Math.min(
                        pageable.getPageSize(),
                        content.size() - start
                );

        int end = start + length;

        return new PageImpl<>(
                content.subList(start, end),
                pageable,
                content.size()
        );
    }

    /**
     * 조회 시작일이 종료일보다 늦은 잘못된 날짜 범위를 차단합니다.
     */
    private void validateDateRange(
            LocalDate from,
            LocalDate to
    ) {
        if (from != null
                && to != null
                && from.isAfter(to)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE
            );
        }
    }

    /**
     * 기관 제보 조회에 필요한 선택 조건을 동적으로 구성합니다.
     * <p>
     * null인 검색 조건은 SQL에 아예 포함하지 않습니다.
     * PostgreSQL에서 nullable parameter에
     * "(:param IS NULL OR ...)" 패턴을 사용할 때 발생할 수 있는
     * parameter type 추론 문제를 피할 수 있습니다.
     * <p>
     * unassigned = true:
     * - institution IS NULL
     * <p>
     * unassigned = false:
     * - institution.id = 로그인 사용자의 기관 ID
     */
    private Specification<Report> createReportSpecification(
            Long institutionId,
            boolean unassigned,
            ReportStatus status,
            LocalDateTime fromDateTime,
            LocalDateTime toExclusive
    ) {
        return (root, query, criteriaBuilder) -> {

            var predicate =
                    criteriaBuilder.conjunction();

            if (unassigned) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.isNull(
                                root.get("institution")
                        )
                );
            } else {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(
                                root.get("institution").get("id"),
                                institutionId
                        )
                );
            }

            if (status != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(
                                root.get("status"),
                                status
                        )
                );
            }

            if (fromDateTime != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.greaterThanOrEqualTo(
                                root.get("createdAt"),
                                fromDateTime
                        )
                );
            }

            if (toExclusive != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.lessThan(
                                root.get("createdAt"),
                                toExclusive
                        )
                );
            }

            return predicate;
        };
    }

    /**
     * 선택 입력값의 앞뒤 공백을 제거하고,
     * null 또는 공백 문자열은 null로 변환합니다.
     */
    private String normalizeNullableText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmedValue =
                value.trim();

        return trimmedValue.isEmpty()
                ? null
                : trimmedValue;
    }
}