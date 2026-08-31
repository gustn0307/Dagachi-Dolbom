package com.dagachi.backend.institution.volunteer.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.InstitutionVolunteerRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerOverviewResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerSummaryResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerActivityResponse;
import com.dagachi.backend.institution.volunteer.dto.InstitutionVolunteerDetailResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 기관 봉사자 목록, 검색, 정렬 및
 * 현황 요약 기능을 처리하는 Service.
 */
@Service
public class InstitutionVolunteerService {

    private final UserRepository userRepository;

    private final InstitutionVolunteerRepository
            institutionVolunteerRepository;

    /**
     * 생성자 주입으로 필요한 Repository를 전달받는다.
     */
    public InstitutionVolunteerService(
            UserRepository userRepository,
            InstitutionVolunteerRepository institutionVolunteerRepository
    ) {
        this.userRepository =
                userRepository;

        this.institutionVolunteerRepository =
                institutionVolunteerRepository;
    }

    /**
     * VOL-01~03 기관 봉사자 목록, 검색 및 정렬.
     *
     * keyword가 있으면 이름, 닉네임, 전화번호를 검색한다.
     *
     * sortType이 participation이면 참여 횟수순으로 정렬하고,
     * 그 외에는 최근 활동순으로 정렬한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionVolunteerSummaryResponse>
    getInstitutionVolunteers(
            Long userId,
            String keyword,
            String sortType,
            Pageable pageable
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 검색어 앞뒤 공백 제거 및 소문자 변환
        String normalizedKeyword =
                normalizeKeyword(keyword);

        // 허용된 정렬값으로 변환
        String normalizedSortType =
                normalizeSortType(sortType);

        // 로그인 담당자의 기관 봉사자만 조회
        Page<InstitutionVolunteerSummaryResponse> volunteerPage =
                institutionVolunteerRepository
                        .findInstitutionVolunteers(
                                institution.getId(),
                                ApplicationStatus.APPROVED,
                                ActivityReviewStatus.APPROVED,
                                normalizedKeyword,
                                normalizedSortType,
                                pageable
                        );

        // 공통 페이지 응답 형식으로 변환
        return PageResponse.from(
                volunteerPage
        );
    }

    /**
     * VOL-05 기관 봉사자 현황 요약 조회.
     *
     * 로그인 담당자의 소속 기관을 기준으로
     * 전체 봉사자, 현재 활동 중인 봉사자,
     * 참여 예정 봉사자 수를 반환한다.
     */
    @Transactional(readOnly = true)
    public InstitutionVolunteerOverviewResponse
    getInstitutionVolunteerOverview(
            Long userId
    ) {
        // 삭제되지 않은 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        Long institutionId =
                institution.getId();

        // 해당 기관의 활동에 한 번 이상
        // 참여 완료한 전체 봉사자 수
        long totalVolunteerCount =
                institutionVolunteerRepository
                        .countTotalVolunteers(
                                institutionId,
                                ApplicationStatus.APPROVED,
                                ActivityReviewStatus.APPROVED
                        );

        // 현재 진행 중인 활동에
        // 참여하고 있는 승인 봉사자 수
        long activeVolunteerCount =
                institutionVolunteerRepository
                        .countActiveVolunteers(
                                institutionId,
                                ApplicationStatus.APPROVED,
                                ActivityStatus.IN_PROGRESS
                        );

        // 앞으로 진행될 READY 활동에
        // 참여 예정인 승인 봉사자 수
        long scheduledVolunteerCount =
                institutionVolunteerRepository
                        .countScheduledVolunteers(
                                institutionId,
                                ApplicationStatus.APPROVED,
                                ActivityStatus.READY
                        );

        // 통계 카드용 응답 생성
        return new InstitutionVolunteerOverviewResponse(
                totalVolunteerCount,
                activeVolunteerCount,
                scheduledVolunteerCount
        );
    }

    /**
     * VOL-06 기관 봉사자 기본 상세 조회.
     *
     * 로그인 담당자의 소속 기관과 봉사자 ID를 함께 검사한다.
     * 다른 기관에서만 활동한 봉사자는 조회할 수 없다.
     */
    @Transactional(readOnly = true)
    public InstitutionVolunteerDetailResponse
    getInstitutionVolunteer(
            Long userId,
            Long volunteerId
    ) {
        // 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        // 기관 ID와 봉사자 ID를 함께 검사하여 상세 조회
        return findInstitutionVolunteerDetail(
                institution.getId(),
                volunteerId
        );
    }

    /**
     * VOL-06 기관별 봉사자 활동 이력 조회.
     *
     * 해당 기관에서 참여 완료하고 검토 승인된
     * 활동 이력만 페이지로 반환한다.
     */
    @Transactional(readOnly = true)
    public PageResponse<InstitutionVolunteerActivityResponse>
    getInstitutionVolunteerActivities(
            Long userId,
            Long volunteerId,
            Pageable pageable
    ) {
        // 로그인 사용자 조회
        User user =
                findUser(userId);

        // 로그인 사용자의 소속 기관 확인
        Institution institution =
                getInstitution(user);

        Long institutionId =
                institution.getId();

        /**
         * 활동 이력을 조회하기 전에 해당 사용자가
         * 이 기관의 봉사자가 맞는지 확인한다.
         *
         * 다른 기관 봉사자이거나 존재하지 않는 ID라면
         * RESOURCE_NOT_FOUND 예외가 발생한다.
         */
        findInstitutionVolunteerDetail(
                institutionId,
                volunteerId
        );

        // 해당 기관에서의 승인 완료된 활동 이력 조회
        Page<InstitutionVolunteerActivityResponse> activityPage =
                institutionVolunteerRepository
                        .findInstitutionVolunteerActivities(
                                institutionId,
                                volunteerId,
                                ApplicationStatus.APPROVED,
                                ActivityReviewStatus.APPROVED,
                                pageable
                        );

        // 공통 페이지 응답으로 변환
        return PageResponse.from(
                activityPage
        );
    }

    /**
     * 기관 ID와 봉사자 ID를 함께 검사하여
     * 해당 기관에 속한 봉사자 상세정보를 조회한다.
     */
    private InstitutionVolunteerDetailResponse
    findInstitutionVolunteerDetail(
            Long institutionId,
            Long volunteerId
    ) {
        return institutionVolunteerRepository
                .findInstitutionVolunteerDetail(
                        institutionId,
                        volunteerId,
                        ApplicationStatus.APPROVED,
                        ActivityReviewStatus.APPROVED
                )
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.RESOURCE_NOT_FOUND
                                )
                );
    }

    /**
     * 삭제되지 않은 로그인 사용자를 조회한다.
     */
    private User findUser(
            Long userId
    ) {
        return userRepository
                .findByIdAndDeletedFalse(
                        userId
                )
                .orElseThrow(
                        () ->
                                new CustomException(
                                        ErrorCode.USER_NOT_FOUND
                                )
                );
    }

    /**
     * 로그인 사용자의 소속 기관을 확인한다.
     *
     * 소속 기관이 없는 일반 사용자는
     * 기관 봉사자 관리 기능을 사용할 수 없다.
     */
    private Institution getInstitution(
            User user
    ) {
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
     * 검색어를 정리한다.
     *
     * 앞뒤 공백을 제거하고 소문자로 변환한다.
     * 값이 없으면 빈 문자열을 반환한다.
     */
    private String normalizeKeyword(
            String keyword
    ) {
        if (keyword == null) {
            return "";
        }

        return keyword
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    /**
     * 정렬 요청값을 검사한다.
     *
     * participation이면 참여 횟수 많은 순을 사용하고,
     * 나머지는 최근 활동순을 사용한다.
     */
    private String normalizeSortType(
            String sortType
    ) {
        if (
                "participation"
                        .equalsIgnoreCase(
                                sortType
                        )
        ) {
            return "participation";
        }

        return "recent";
    }
}