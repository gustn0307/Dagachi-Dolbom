package com.dagachi.backend.institution.recipient.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.recipient.dto.CareRecipientConsentRequest;
import com.dagachi.backend.institution.recipient.dto.CareRecipientCreateRequest;
import com.dagachi.backend.institution.recipient.dto.CareRecipientDetailResponse;
import com.dagachi.backend.institution.recipient.dto.CareRecipientSummaryResponse;
import com.dagachi.backend.institution.recipient.dto.CareRecipientUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 기관 담당자의 돌봄 대상자 조회, 등록 및 수정 기능을 처리하는 Service.
 */
@Service
public class CareRecipientService {

    private final UserRepository userRepository;
    private final CareRecipientRepository careRecipientRepository;

    public CareRecipientService(
            UserRepository userRepository,
            CareRecipientRepository careRecipientRepository
    ) {
        this.userRepository = userRepository;
        this.careRecipientRepository = careRecipientRepository;
    }

    /**
     * CARE-01 기관 돌봄 대상자 목록 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<CareRecipientSummaryResponse>
    getCareRecipients(
            Long userId,
            CareRecipientStatus status,
            ConsentStatus consentStatus,
            String keyword,
            Pageable pageable
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        String normalizedKeyword =
                normalizeKeyword(keyword);

        Page<CareRecipient> recipientPage =
                careRecipientRepository.findAllByCondition(
                        institution.getId(),
                        status,
                        consentStatus,
                        normalizedKeyword,
                        pageable
                );

        Page<CareRecipientSummaryResponse> responsePage =
                recipientPage.map(
                        CareRecipientSummaryResponse::from
                );

        return PageResponse.from(responsePage);
    }

    /**
     * CARE-02 기관 돌봄 대상자 상세 조회.
     */
    @Transactional(readOnly = true)
    public CareRecipientDetailResponse getCareRecipient(
            Long userId,
            Long recipientId
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient =
                findRecipient(
                        recipientId,
                        institution.getId()
                );

        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-03 기관 돌봄 대상자 등록.
     */
    @Transactional
    public CareRecipientDetailResponse createCareRecipient(
            Long userId,
            CareRecipientCreateRequest request
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient = CareRecipient.create(
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

        CareRecipient savedRecipient =
                careRecipientRepository.save(recipient);

        return CareRecipientDetailResponse.of(
                savedRecipient,
                0L,
                0L
        );
    }

    /**
     * CARE-04 기관 돌봄 대상자 기본정보 수정.
     *
     * PATCH 요청에서 전달되지 않은 필드는 기존 값을 유지한다.
     */
    @Transactional
    public CareRecipientDetailResponse updateCareRecipient(
            Long userId,
            Long recipientId,
            CareRecipientUpdateRequest request
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient =
                findRecipient(
                        recipientId,
                        institution.getId()
                );

        String name =
                request.name() != null
                        ? request.name().trim()
                        : recipient.getName();

        UserGender gender =
                request.gender() != null
                        ? request.gender()
                        : recipient.getGender();

        Integer birthYear =
                request.birthYear() != null
                        ? request.birthYear()
                        : recipient.getBirthYear();

        String phone =
                request.phone() != null
                        ? normalizeNullableText(request.phone())
                        : recipient.getPhone();

        String address =
                request.address() != null
                        ? request.address().trim()
                        : recipient.getAddress();

        String detailAddress =
                request.detailAddress() != null
                        ? normalizeNullableText(
                        request.detailAddress()
                )
                        : recipient.getDetailAddress();

        BigDecimal latitude =
                request.latitude() != null
                        ? request.latitude()
                        : recipient.getLatitude();

        BigDecimal longitude =
                request.longitude() != null
                        ? request.longitude()
                        : recipient.getLongitude();

        recipient.updateInformation(
                name,
                gender,
                birthYear,
                phone,
                address,
                detailAddress,
                latitude,
                longitude
        );

        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-05 기관 돌봄 대상자 동의 상태 변경.
     */
    @Transactional
    public CareRecipientDetailResponse updateConsentStatus(
            Long userId,
            Long recipientId,
            CareRecipientConsentRequest request
    ) {
        // 로그인 사용자 조회
        User user = findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 로그인 사용자의 기관에 속한 대상자만 조회
        CareRecipient recipient =
                findRecipient(
                        recipientId,
                        institution.getId()
                );

        // Entity에서 동의 상태와 관련 시각을 함께 변경
        recipient.changeConsentStatus(
                request.consentStatus()
        );

        // 변경된 대상자 상세 정보 반환
        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-06 기관 돌봄 대상자 관리 종료.
     *
     * 대상자를 실제 삭제하지 않고 관리 상태를 INACTIVE로 변경한다.
     */
    @Transactional
    public CareRecipientDetailResponse closeCareRecipient(
            Long userId,
            Long recipientId
    ) {
        // 로그인 사용자 조회
        User user = findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 로그인 사용자의 기관에 속한 대상자만 조회
        CareRecipient recipient =
                findRecipient(
                        recipientId,
                        institution.getId()
                );

        // 대상자 관리 상태를 INACTIVE로 변경
        recipient.closeManagement();

        // 변경된 대상자 상세 정보 반환
        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-07 돌봄 대상자 관리 재개.
     *
     * 로그인 담당자의 기관에 속한 대상자인지 확인한 후
     * 관리 상태를 INACTIVE에서 ACTIVE로 변경한다.
     *
     * 대상자를 새로 생성하지 않으므로 기존 제보, 활동,
     * 동의 정보와 최근 확인일은 그대로 유지된다.
     */
    @Transactional
    public CareRecipientDetailResponse reopenCareRecipient(
            Long userId,
            Long recipientId
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user = findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 대상자 ID와 기관 ID를 함께 검증
        CareRecipient recipient =
                findRecipient(
                        recipientId,
                        institution.getId()
                );

        // 기존 대상자의 관리 상태를 ACTIVE로 변경
        recipient.reopenManagement();

        // 기존 제보 수와 활동 수를 포함한 상세 응답 반환
        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * 삭제되지 않은 로그인 사용자를 조회한다.
     */
    private User findUser(Long userId) {
        return userRepository
                .findByIdAndDeletedFalse(userId)
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.USER_NOT_FOUND
                        )
                );
    }

    /**
     * 로그인 사용자의 소속 기관을 확인한다.
     */
    private Institution getInstitution(User user) {
        Institution institution =
                user.getInstitution();

        if (institution == null) {
            throw new CustomException(
                    ErrorCode.FORBIDDEN
            );
        }

        return institution;
    }

    /**
     * 대상자 ID와 기관 ID를 함께 검사하여 대상자를 조회한다.
     */
    private CareRecipient findRecipient(
            Long recipientId,
            Long institutionId
    ) {
        return careRecipientRepository
                .findByIdAndInstitution_IdAndDeletedFalse(
                        recipientId,
                        institutionId
                )
                .orElseThrow(
                        () -> new CustomException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );
    }

    /**
     * 대상자의 제보 및 활동 수를 포함한 상세 응답을 생성한다.
     */
    private CareRecipientDetailResponse createDetailResponse(
            CareRecipient recipient,
            Long recipientId
    ) {
        long reportCount =
                careRecipientRepository
                        .countReportsByRecipientId(
                                recipientId
                        );

        long activityCount =
                careRecipientRepository
                        .countActivitiesByRecipientId(
                                recipientId
                        );

        return CareRecipientDetailResponse.of(
                recipient,
                reportCount,
                activityCount
        );
    }

    /**
     * 검색어 앞뒤 공백을 제거한다.
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String trimmedKeyword =
                keyword.trim();

        return trimmedKeyword.isEmpty()
                ? null
                : trimmedKeyword;
    }

    /**
     * 선택 입력값의 앞뒤 공백을 제거한다.
     * null 또는 공백만 입력된 값은 null로 변환한다.
     */
    private String normalizeNullableText(String value) {
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