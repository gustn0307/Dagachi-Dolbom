package com.dagachi.backend.institution.recipient.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.kakao.client.KakaoLocalClient;
import com.dagachi.backend.common.kakao.dto.Coordinate;
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

/**
 * 기관 담당자의 돌봄 대상자 조회, 등록 및 수정 기능을 처리하는 Service.
 */
@Service
public class CareRecipientService {

    private final UserRepository userRepository;
    private final CareRecipientRepository careRecipientRepository;
    private final KakaoLocalClient kakaoLocalClient;

    public CareRecipientService(
            UserRepository userRepository,
            CareRecipientRepository careRecipientRepository,
            KakaoLocalClient kakaoLocalClient
    ) {
        this.userRepository = userRepository;
        this.careRecipientRepository = careRecipientRepository;
        this.kakaoLocalClient = kakaoLocalClient;
    }

    /**
     * CARE-01 기관 돌봄 대상자 목록 조회.
     */
    @Transactional(readOnly = true)
    public PageResponse<CareRecipientSummaryResponse> getCareRecipients(
            Long userId,
            CareRecipientStatus status,
            ConsentStatus consentStatus,
            String keyword,
            Pageable pageable
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        String normalizedKeyword = normalizeKeyword(keyword);

        Page<CareRecipient> recipientPage =
                careRecipientRepository.findAllByCondition(
                        institution.getId(),
                        status,
                        consentStatus,
                        normalizedKeyword,
                        pageable
                );

        Page<CareRecipientSummaryResponse> responsePage =
                recipientPage.map(CareRecipientSummaryResponse::from);

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

        CareRecipient recipient = findRecipient(
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
     *
     * 입력받은 주소를 Kakao Local API로 변환하여
     * 위도와 경도를 함께 저장한다.
     */
    @Transactional
    public CareRecipientDetailResponse createCareRecipient(
            Long userId,
            CareRecipientCreateRequest request
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        String address = request.address().trim();

        Coordinate coordinate =
                kakaoLocalClient.searchCoordinate(address);

        CareRecipient recipient = CareRecipient.create(
                institution,
                request.name().trim(),
                request.gender(),
                request.birthYear(),
                normalizeNullableText(request.phone()),
                address,
                normalizeNullableText(request.detailAddress()),
                coordinate.latitude(),
                coordinate.longitude(),
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
     * 주소가 변경된 경우 Kakao Local API로 좌표를 다시 조회한다.
     * 주소가 전달되지 않은 경우에는 기존 주소와 좌표를 유지한다.
     */
    @Transactional
    public CareRecipientDetailResponse updateCareRecipient(
            Long userId,
            Long recipientId,
            CareRecipientUpdateRequest request
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient = findRecipient(
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
                        ? normalizeNullableText(request.detailAddress())
                        : recipient.getDetailAddress();

        Coordinate coordinate;

        if (request.address() != null) {
            coordinate =
                    kakaoLocalClient.searchCoordinate(address);
        } else {
            coordinate = new Coordinate(
                    recipient.getLatitude(),
                    recipient.getLongitude()
            );
        }

        recipient.updateInformation(
                name,
                gender,
                birthYear,
                phone,
                address,
                detailAddress,
                coordinate.latitude(),
                coordinate.longitude()
        );

        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-05 기관 돌봄 대상자의 동의 상태 변경.
     */
    @Transactional
    public CareRecipientDetailResponse updateConsentStatus(
            Long userId,
            Long recipientId,
            CareRecipientConsentRequest request
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient = findRecipient(
                recipientId,
                institution.getId()
        );

        recipient.changeConsentStatus(
                request.consentStatus()
        );

        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-06 기관 돌봄 대상자 관리 종료.
     */
    @Transactional
    public CareRecipientDetailResponse closeCareRecipient(
            Long userId,
            Long recipientId
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient = findRecipient(
                recipientId,
                institution.getId()
        );

        recipient.closeManagement();

        return createDetailResponse(
                recipient,
                recipientId
        );
    }

    /**
     * CARE-07 돌봄 대상자 관리 재개.
     */
    @Transactional
    public CareRecipientDetailResponse reopenCareRecipient(
            Long userId,
            Long recipientId
    ) {
        User user = findUser(userId);
        Institution institution = getInstitution(user);

        CareRecipient recipient = findRecipient(
                recipientId,
                institution.getId()
        );

        recipient.reopenManagement();

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
        Institution institution = user.getInstitution();

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
                careRecipientRepository.countReportsByRecipientId(
                        recipientId
                );

        long activityCount =
                careRecipientRepository.countActivitiesByRecipientId(
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

        String trimmedKeyword = keyword.trim();

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

        String trimmedValue = value.trim();

        return trimmedValue.isEmpty()
                ? null
                : trimmedValue;
    }
}