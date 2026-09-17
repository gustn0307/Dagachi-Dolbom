package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
import com.dagachi.backend.common.util.AddressUtils;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ApplicationType;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.user.activity.dto.ActivityDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityExecutionDetailResponse;
import com.dagachi.backend.user.activity.dto.ActivityResponse;
import com.dagachi.backend.user.activity.dto.ActivitySearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

/**
 * ActivityService(ACT-01~03) 비즈니스 규칙 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock
    private CareActivityRepository careActivityRepository;
    @Mock
    private ActivityApplicationRepository activityApplicationRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @InjectMocks
    private ActivityService activityService;

    private static final LocalDateTime MIN_DATE = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE = LocalDateTime.of(2100, 1, 1, 0, 0);
    private static final Long ACTIVITY_ID = 1L;
    private static final Long USER_ID = 100L;

    private CareRecipient buildRecipient(BigDecimal lat, BigDecimal lng, LocalDateTime lastCheckedAt, Integer birthYear) {
        CareRecipient recipient = CareRecipient.create(
                null, "김할머니", UserGender.FEMALE, birthYear, "010-1111-2222",
                "서울시 강남구 역삼동 123", null, lat, lng, ConsentStatus.AGREED
        );
        ReflectionTestUtils.setField(recipient, "lastCheckedAt", lastCheckedAt);
        return recipient;
    }

    private CareActivity buildActivity(long id, ActivityStatus status, CareRecipient recipient) {
        CareActivity activity = CareActivity.create(
                recipient, null, null, LocalDateTime.now().plusDays(1), 2, GenderCondition.NONE
        );
        activity.changeStatus(status);
        ReflectionTestUtils.setField(activity, "id", id);
        return activity;
    }

    private User buildUser(long id) {
        User user = User.create("user" + id + "@test.com", "pw", "테스터", "닉네임", "010-0000-0000", UserGender.MALE);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ActivityApplication buildApplication(CareActivity activity, User user, ApplicationStatus status) {
        ActivityApplication application = ActivityApplication.createDirect(activity, user);
        ReflectionTestUtils.setField(application, "status", status);
        return application;
    }

    // ---------------------------------------------------------------
    // ACT-01 목록 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ACT-01 활동 목록 - 필터가 없으면 기본값(전체 기간/전체 연령/전체 성별)으로 조회한다")
    void getActivities_기본조건이면_기본값으로_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivitySearchCondition condition =
                new ActivitySearchCondition(null, null, null, null, null, null, null, null);

        CareRecipient recipient = buildRecipient(null, null, LocalDateTime.now().minusDays(3), 1950);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, recipient);
        Page<CareActivity> page = new PageImpl<>(List.of(activity), pageable, 1);

        int currentYear = LocalDate.now().getYear();
        given(careActivityRepository.findRecruitingActivitiesPaged(
                eq(""), eq(MIN_DATE), eq(MAX_DATE), eq(false), eq(List.of(-1)),
                eq(currentYear), eq(false), isNull(), eq(pageable)
        )).willReturn(page);
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 1L));
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(eq(List.of(ACTIVITY_ID))))
                .willReturn(List.of());

        PageResponse<ActivityResponse> response = activityService.getActivities(condition, pageable, null);

        assertThat(response.content()).hasSize(1);
        ActivityResponse item = response.content().get(0);
        assertThat(item.activityId()).isEqualTo(ACTIVITY_ID);
        assertThat(item.region()).isEqualTo("역삼동");
        assertThat(item.ageGroup()).isEqualTo(AddressUtils.calculateAgeGroup(1950));
        assertThat(item.gender()).isEqualTo("FEMALE");
        assertThat(item.approvedCount()).isEqualTo(1L);
        assertThat(item.distanceKm()).isNull();
        assertThat(item.myApplicationStatus()).isNull();
    }

    @Test
    @DisplayName("ACT-01 활동 목록 - 좌표가 있으면 거리가 가까운 순으로 정렬한다")
    void getActivities_좌표가_있으면_거리순으로_정렬한다() {
        Pageable pageable = PageRequest.of(0, 20);
        BigDecimal userLat = new BigDecimal("37.5665");
        BigDecimal userLng = new BigDecimal("126.9780");
        ActivitySearchCondition condition =
                new ActivitySearchCondition(userLat, userLng, null, null, null, null, null, null);

        CareRecipient near = buildRecipient(userLat, userLng, null, 1960);
        CareActivity activityNear = buildActivity(1L, ActivityStatus.RECRUITING, near);

        CareRecipient far = buildRecipient(new BigDecimal("35.1796"), new BigDecimal("129.0756"), null, 1960);
        CareActivity activityFar = buildActivity(2L, ActivityStatus.RECRUITING, far);

        given(careActivityRepository.findRecruitingActivitiesForDistanceSort(
                eq(""), eq(MIN_DATE), eq(MAX_DATE), eq(false), eq(List.of(-1)),
                anyInt(), eq(false), isNull()
        )).willReturn(List.of(activityFar, activityNear)); // 일부러 먼 활동을 먼저 반환
        given(activityApplicationRepository.countApprovedMap(anyList())).willReturn(Map.of());
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(anyList())).willReturn(List.of());

        PageResponse<ActivityResponse> response = activityService.getActivities(condition, pageable, null);

        assertThat(response.content()).extracting(ActivityResponse::activityId)
                .containsExactly(1L, 2L); // 가까운 activityNear가 먼저 와야 한다
    }

    @Test
    @DisplayName("ACT-01 활동 목록 - sortBy=STALE이면 안부확인이 오래된 순으로 정렬한다")
    void getActivities_STALE정렬이면_안부확인이_오래된순으로_정렬한다() {
        Pageable pageable = PageRequest.of(0, 20);
        ActivitySearchCondition condition =
                new ActivitySearchCondition(null, null, null, null, null, null, null, "STALE");

        CareRecipient recentlyChecked = buildRecipient(null, null, LocalDateTime.now().minusDays(1), 1960);
        CareActivity activityRecent = buildActivity(1L, ActivityStatus.RECRUITING, recentlyChecked);

        CareRecipient neverChecked = buildRecipient(null, null, null, 1960); // null -> 가장 오래된 것으로 취급
        CareActivity activityStale = buildActivity(2L, ActivityStatus.RECRUITING, neverChecked);

        given(careActivityRepository.findRecruitingActivitiesForDistanceSort(
                eq(""), eq(MIN_DATE), eq(MAX_DATE), eq(false), eq(List.of(-1)),
                anyInt(), eq(false), isNull()
        )).willReturn(List.of(activityRecent, activityStale));
        given(activityApplicationRepository.countApprovedMap(anyList())).willReturn(Map.of());
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(anyList())).willReturn(List.of());

        PageResponse<ActivityResponse> response = activityService.getActivities(condition, pageable, null);

        assertThat(response.content()).extracting(ActivityResponse::activityId)
                .containsExactly(2L, 1L); // 안부확인 이력이 없는(가장 오래된) 활동이 먼저 와야 한다
    }

    @Test
    @DisplayName("ACT-01 활동 목록 - 허용되지 않는 연령대 라벨이면 400(INVALID_INPUT_VALUE)")
    void getActivities_잘못된_연령대라벨이면_예외를_던진다() {
        ActivitySearchCondition condition = new ActivitySearchCondition(
                null, null, null, null, null, List.of("100대"), null, null
        );

        assertThatThrownBy(() -> activityService.getActivities(condition, PageRequest.of(0, 20), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("ACT-01 활동 목록 - 허용되지 않는 성별 값이면 400(INVALID_INPUT_VALUE)")
    void getActivities_잘못된_성별값이면_예외를_던진다() {
        ActivitySearchCondition condition = new ActivitySearchCondition(
                null, null, null, null, null, null, "UNKNOWN", null
        );

        assertThatThrownBy(() -> activityService.getActivities(condition, PageRequest.of(0, 20), null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // ---------------------------------------------------------------
    // ACT-02 상세 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ACT-02 활동 상세 - 존재하면 본인 신청 상태를 포함해 반환한다")
    void getActivityDetail_정상활동이면_상세를_반환한다() {
        CareRecipient recipient = buildRecipient(null, null, null, 1955);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.PENDING);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID)))).willReturn(Map.of());
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(eq(List.of(ACTIVITY_ID))))
                .willReturn(List.of(application));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));

        ActivityDetailResponse response = activityService.getActivityDetail(ACTIVITY_ID, USER_ID);

        assertThat(response.activityId()).isEqualTo(ACTIVITY_ID);
        assertThat(response.myApplicationStatus()).isEqualTo("PENDING");
        assertThat(response.applicantCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ACT-02 활동 상세 - 존재하지 않으면 예외를 던진다")
    void getActivityDetail_존재하지_않으면_예외를_던진다() {
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getActivityDetail(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    // ---------------------------------------------------------------
    // ACT-03 수행정보 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("ACT-03 수행정보 조회 - 신청 이력이 없으면 FORBIDDEN")
    void getExecutionDetail_신청이_없으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null, 1955);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.READY, recipient);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getExecutionDetail(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ACT-03 수행정보 조회 - PENDING 신청자는 FORBIDDEN")
    void getExecutionDetail_PENDING신청자는_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null, 1955);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.READY, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.PENDING);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));

        assertThatThrownBy(() -> activityService.getExecutionDetail(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("ACT-03 수행정보 조회 - APPROVED이고 아직 시작 전(READY)이면 activityRecordId는 null이다")
    void getExecutionDetail_APPROVED이고_시작전이면_activityRecordId는_null이다() {
        CareRecipient recipient = buildRecipient(null, null, null, 1955);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.READY, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 1L));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());

        ActivityExecutionDetailResponse response = activityService.getExecutionDetail(ACTIVITY_ID, USER_ID);

        assertThat(response.activityRecordId()).isNull();
        assertThat(response.approvedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("ACT-03 수행정보 조회 - 이미 다른 참여자가 활동을 시작했다면 기존 activityRecordId를 함께 내려준다")
    void getExecutionDetail_이미_시작된_활동이면_기존_activityRecordId를_반환한다() {
        CareRecipient recipient = buildRecipient(null, null, null, 1955);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.IN_PROGRESS, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        ActivityRecord record = ActivityRecord.createDraft(activity, 1, LocalDateTime.now());
        ReflectionTestUtils.setField(record, "id", 555L);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.of(record));

        ActivityExecutionDetailResponse response = activityService.getExecutionDetail(ACTIVITY_ID, USER_ID);

        assertThat(response.activityRecordId()).isEqualTo(555L);
    }
}