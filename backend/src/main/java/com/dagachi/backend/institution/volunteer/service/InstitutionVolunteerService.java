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
 *
 * 로그인 기관 담당자의 소속 기관을 확인하고,
 * 해당 기관의 활동에 실제 참여한 봉사자만 조회한다.
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
     * VOL-01 기관 봉사자 목록 조회.
     *
     * 봉사자 포함 기준:
     * 1. 해당 기관이 만든 활동
     * 2. 봉사 신청 상태가 APPROVED
     * 3. 활동 기록 검토 상태가 APPROVED
     * 4. 활동 완료 시각이 존재함
     *
     * 동일한 사용자가 여러 활동에 참여했더라도
     * 봉사자 목록에는 한 번만 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionVolunteerSummaryResponse>
    getInstitutionVolunteers(
            Long userId,
            Pageable pageable
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user = findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution = getInstitution(user);

        // 해당 기관의 활동에 참여 완료한 봉사자 조회
        Page<InstitutionVolunteerSummaryResponse> volunteerPage =
                institutionVolunteerRepository
                        .findInstitutionVolunteers(
                                institution.getId(),
                                ApplicationStatus.APPROVED,
                                ActivityReviewStatus.APPROVED,
                                pageable
                        );

        // 공통 페이지 응답 형태로 변환
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
     *
     * 일반 사용자처럼 소속 기관이 없는 사용자는
     * 기관 봉사자 목록을 조회할 수 없다.
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
}