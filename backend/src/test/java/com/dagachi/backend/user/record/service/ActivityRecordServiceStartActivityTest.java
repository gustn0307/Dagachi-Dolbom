package com.dagachi.backend.user.record.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
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
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.record.dto.ActivityRecordResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ActivityRecordService.startActivity(RECORD-01, 활동 시작) 단위 테스트.
 *
 * saveDraft/uploadSignature/submit(RECORD-02~05)는 이미 별도의
 * {@link ActivityRecordServiceTest}(맹동영님 담당)에서 검증하고 있어
 * 이 파일에서는 다루지 않는다. 같은 대상 클래스를 테스트하지만
 * 담당자가 다른 메서드를 검증하는 것이라 파일을 분리했다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityRecordServiceStartActivityTest {

    @Mock
    private CareActivityRepository careActivityRepository;
    @Mock
    private ActivityApplicationRepository activityApplicationRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private ChecklistResponseRepository checklistResponseRepository;
    @Mock
    private S3StorageService s3StorageService;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActivityRecordService activityRecordService;

    private static final Long ACTIVITY_ID = 1L;
    private static final Long USER_ID = 100L;

    // ---------------------------------------------------------------
    // 테스트 픽스처 헬퍼
    // ---------------------------------------------------------------

    private User buildUser(long id) {
        User user = User.create(
                "user" + id + "@test.com", "encoded-pw", "테스터" + id,
                "닉네임" + id, "010-0000-0000", UserGender.MALE
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private CareRecipient buildRecipient(UserGender gender) {
        return CareRecipient.create(
                null, "김할머니", gender, 1950, "010-1111-2222",
                "서울시 강남구 역삼동 123", null, null, null, ConsentStatus.AGREED
        );
    }

    private CareActivity buildActivity(
            ActivityStatus status, Integer requiredPeople, GenderCondition genderCondition, CareRecipient recipient
    ) {
        CareActivity activity = CareActivity.create(
                recipient, null, null, LocalDateTime.now().plusHours(1), requiredPeople, genderCondition
        );
        activity.changeStatus(status);
        ReflectionTestUtils.setField(activity, "id", ACTIVITY_ID);
        return activity;
    }

    private ActivityApplication buildApplication(CareActivity activity, User user, ApplicationStatus status) {
        ActivityApplication application = ActivityApplication.createDirect(activity, user);
        ReflectionTestUtils.setField(application, "status", status);
        return application;
    }

    // ---------------------------------------------------------------
    // RECORD-01 활동 시작
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 존재하지 않는 활동이면 RESOURCE_NOT_FOUND")
    void startActivity_존재하지_않는_활동이면_예외를_던진다() {
        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - READY 상태가 아니면 ACTIVITY_NOT_READY")
    void startActivity_READY가_아니면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.RECRUITING, 2, GenderCondition.NONE, recipient);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_NOT_READY);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 본인의 신청 이력이 없으면 FORBIDDEN")
    void startActivity_신청이_없으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - PENDING 신청자는 FORBIDDEN")
    void startActivity_PENDING신청자는_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.PENDING);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 이미 ActivityRecord가 있으면 ACTIVITY_ALREADY_STARTED")
    void startActivity_이미_시작된_활동이면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID))
                .willReturn(Optional.of(ActivityRecord.createDraft(activity, 1, LocalDateTime.now())));

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_ALREADY_STARTED);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 락 이후 재검증한 승인 인원이 정원 미달이면 ACTIVITY_NOT_READY")
    void startActivity_정원이_미달이면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        // 정원 2명인데 락 이후 재조회하니 1명만 승인된 상태(다른 승인이 방금 취소됨)라고 가정
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 1L));

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_NOT_READY);
    }

    @Test
    @DisplayName("REQ-REC-01, REQ-ACT-05 - RECORD-01 활동 시작 - SAME_GENDER_ONE인데 대상자와 같은 성별 승인자가 없으면 ACTIVITY_GENDER_CONDITION_NOT_MET")
    void startActivity_성별조건을_충족하지_못하면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.SAME_GENDER_ONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        // 대상자는 FEMALE인데 승인자 전원 MALE인 경우
        given(activityApplicationRepository.findApprovedUserGenders(ACTIVITY_ID))
                .willReturn(List.of(UserGender.MALE, UserGender.MALE));

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_GENDER_CONDITION_NOT_MET);

        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-REC-01, REQ-ACT-05 - RECORD-01 활동 시작 - SAME_GENDER_ONE이어도 대상자와 같은 성별이 1명이라도 있으면 통과한다")
    void startActivity_성별조건을_충족하면_정상적으로_시작된다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.SAME_GENDER_ONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        // 승인자 중 최소 1명(FEMALE)이 대상자와 같은 성별
        given(activityApplicationRepository.findApprovedUserGenders(ACTIVITY_ID))
                .willReturn(List.of(UserGender.MALE, UserGender.FEMALE));
        given(checklistItemRepository.findCurrentActiveVersion()).willReturn(Optional.of(1));
        given(activityRecordRepository.save(any(ActivityRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ActivityRecordResponse response = activityRecordService.startActivity(ACTIVITY_ID, USER_ID);

        assertThat(response).isNotNull();
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("REQ-REC-01, REQ-ACT-05 - RECORD-01 활동 시작 - GenderCondition이 NONE이면 성별 검사를 하지 않는다")
    void startActivity_성별조건이_NONE이면_검사를_생략한다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        given(checklistItemRepository.findCurrentActiveVersion()).willReturn(Optional.of(1));
        given(activityRecordRepository.save(any(ActivityRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        activityRecordService.startActivity(ACTIVITY_ID, USER_ID);

        verify(activityApplicationRepository, never()).findApprovedUserGenders(any());
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 활성화된 체크리스트 버전이 없으면 RESOURCE_NOT_FOUND")
    void startActivity_활성_체크리스트_버전이_없으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        given(checklistItemRepository.findCurrentActiveVersion()).willReturn(Optional.empty());

        assertThatThrownBy(() -> activityRecordService.startActivity(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-REC-01 - RECORD-01 활동 시작 - 정상 시작이면 DRAFT 기록을 만들고 활동을 IN_PROGRESS로 바꾼다")
    void startActivity_정상시작이면_DRAFT기록을_생성하고_활동상태를_변경한다() {
        CareRecipient recipient = buildRecipient(UserGender.FEMALE);
        CareActivity activity = buildActivity(ActivityStatus.READY, 2, GenderCondition.NONE, recipient);
        User user = buildUser(USER_ID);
        ActivityApplication application = buildApplication(activity, user, ApplicationStatus.APPROVED);

        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(application));
        given(activityRecordRepository.findByActivity_Id(ACTIVITY_ID)).willReturn(Optional.empty());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 2L));
        given(checklistItemRepository.findCurrentActiveVersion()).willReturn(Optional.of(3));
        given(activityRecordRepository.save(any(ActivityRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ActivityRecordResponse response = activityRecordService.startActivity(ACTIVITY_ID, USER_ID);

        assertThat(response).isNotNull();
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.IN_PROGRESS);
        verify(activityRecordRepository).save(any(ActivityRecord.class));
    }
}