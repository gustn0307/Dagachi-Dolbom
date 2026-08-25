package com.dagachi.backend.institution.volunteer.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.InstitutionVolunteerRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기관 봉사자 관리 기능을 처리하는 Service.
 */
@Service
public class InstitutionVolunteerService {

    private final UserRepository userRepository;
    private final InstitutionVolunteerRepository
            institutionVolunteerRepository;

    public InstitutionVolunteerService(
            UserRepository userRepository,
            InstitutionVolunteerRepository institutionVolunteerRepository
    ) {
        this.userRepository = userRepository;
        this.institutionVolunteerRepository =
                institutionVolunteerRepository;
    }

    /**
     * VOL-01, VOL-02 기관 봉사자 목록 조회.
     *
     * keyword가 있으면 이름, 닉네임, 전화번호를 검색한다.
     * keyword가 없으면 해당 기관의 전체 봉사자를 조회한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionVolunteerSummaryResponse>
    getInstitutionVolunteers(
            Long userId,
            String keyword,
            Pageable pageable
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user = findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution = getInstitution(user);

        // 검색어 앞뒤 공백 제거
        // null 또는 공백만 입력하면 검색 조건을 적용하지 않는다.
        String normalizedKeyword =
                normalizeKeyword(keyword);

        // 해당 기관의 봉사자 목록 조회
        Page<InstitutionVolunteerSummaryResponse> volunteerPage =
                institutionVolunteerRepository
                        .findInstitutionVolunteers(
                                institution.getId(),
                                ApplicationStatus.APPROVED,
                                ActivityReviewStatus.APPROVED,
                                normalizedKeyword,
                                pageable
                        );

        // 공통 페이지 응답으로 변환
        return PageResponse.from(volunteerPage);
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
     * 검색어를 정리한다.
     *
     * 앞뒤 공백은 제거하고,
     * 값이 없거나 공백만 있다면 null을 반환한다.
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
}