package com.dagachi.backend.user.application.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.response.PageResponse;
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
import com.dagachi.backend.domain.enums.UserStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.application.dto.ApplicationResponse;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ActivityApplicationService(APP-01~05) 비즈니스 규칙 단위 테스트.
 *
 * DB나 Spring Context 없이 Repository를 모두 Mock 처리한다.
 * 요구사항 정의서의 REQ-ID와 직접 대응하는 테스트는 @DisplayName에 REQ-ID를 표기한다.
 *
 * [동시성 보완] applyDirect/applyAuto의 User 조회는 findById가 아니라
 * findByIdAndDeletedFalseForUpdate(PESSIMISTIC_WRITE 락)를 사용한다.
 * (회원 탈퇴 withdraw()와 동일한 User row 락 전략으로 TOCTOU 방지)
 * filterValidAutoMatchCandidates(자동배정 후보 조회, 읽기 전용)는
 * 락 대상이 아니므로 기존 findById를 그대로 사용한다.
 *
 * [신규] cancelApplication()도 application.getUser()로 로드된 User의
 * 계정 상태(정지/탈퇴)를 검증하므로, 관련 테스트를 APP-05 섹션에 추가했다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityApplicationServiceTest {

    @Mock
    private ActivityApplicationRepository activityApplicationRepository;
    @Mock
    private CareActivityRepository careActivityRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @InjectMocks
    private ActivityApplicationService service;

    private static final Long ACTIVITY_ID = 1L;
    private static final Long USER_ID = 100L;

    // ---------------------------------------------------------------
    // 테스트 픽스처 헬퍼
    // ---------------------------------------------------------------

    private User buildUser(long id, UserGender gender) {
        User user = User.create(
                "user" + id + "@test.com", "encoded-pw", "테스터" + id,
                "닉네임" + id, "010-0000-0000", gender
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // [동시성 보완] WITHDRAWN/SUSPENDED 재검증 테스트용 헬퍼.
    // User.create()는 항상 status=ACTIVE로 생성하므로, 필요 시 강제로 바꿔준다.
    private User buildUserWithStatus(long id, UserGender gender, UserStatus status) {
        User user = buildUser(id, gender);
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    private CareRecipient buildRecipient(BigDecimal lat, BigDecimal lng, LocalDateTime lastCheckedAt) {
        CareRecipient recipient = CareRecipient.create(
                null, "김할머니", UserGender.FEMALE, 1950, "010-1111-2222",
                "서울시 강남구 역삼동 123", null, lat, lng, ConsentStatus.AGREED
        );
        ReflectionTestUtils.setField(recipient, "lastCheckedAt", lastCheckedAt);
        return recipient;
    }

    private CareActivity buildActivity(long id, ActivityStatus status, Integer requiredPeople, CareRecipient recipient) {
        CareActivity activity = CareActivity.create(
                recipient, null, null, LocalDateTime.now().plusDays(1),
                requiredPeople, GenderCondition.NONE
        );
        activity.changeStatus(status);
        ReflectionTestUtils.setField(activity, "id", id);
        return activity;
    }

    private ActivityApplication buildExistingApplication(
            long id, CareActivity activity, User user, ApplicationType type, ApplicationStatus status
    ) {
        ActivityApplication application = type == ApplicationType.AUTO
                ? ActivityApplication.createAuto(activity, user)
                : ActivityApplication.createDirect(activity, user);
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "status", status);
        return application;
    }

    // ---------------------------------------------------------------
    // APP-01 직접 신청
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-ACT-12 - APP-01 직접 신청 - 신규 신청이면 DIRECT/PENDING으로 생성한다")
    void applyDirect_신규신청이면_PENDING상태로_생성한다() {
        // given
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.empty());
        given(activityApplicationRepository.save(any(ActivityApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        ApplicationResponse response = service.applyDirect(ACTIVITY_ID, USER_ID);

        // then
        assertThat(response.applicationType()).isEqualTo(ApplicationType.DIRECT);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.activityId()).isEqualTo(ACTIVITY_ID);
        verify(activityApplicationRepository).save(any(ActivityApplication.class));
    }

    @Test
    @DisplayName("REQ-ACT-12 - APP-01 직접 신청 - 모집 중(RECRUITING)이 아니면 예외를 던진다")
    void applyDirect_모집중이_아니면_예외를_던진다() {
        // given
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.READY, 2, recipient);
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));

        // when & then
        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_NOT_RECRUITING);

        verify(activityApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-ACT-12 - APP-01 직접 신청 - 존재하지 않는 활동이면 RESOURCE_NOT_FOUND")
    void applyDirect_활동이_없으면_예외를_던진다() {
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("REQ-ACT-12 - APP-01 직접 신청 - 존재하지 않는 사용자면 USER_NOT_FOUND")
    void applyDirect_사용자가_없으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // [동시성 보완 신규] 락 조회는 deleted=false 조건이 있어 탈퇴 계정은
    // Optional.empty()로 걸러진다. 락 획득 시점에 이미 탈퇴가 완료된 케이스.
    @Test
    @DisplayName("[동시성 보완] APP-01 직접 신청 - 이미 탈퇴(deleted=true)된 계정이면 USER_NOT_FOUND")
    void applyDirect_이미_탈퇴한_계정이면_USER_NOT_FOUND() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(activityApplicationRepository, never()).save(any());
    }

    // [동시성 보완 신규] deleted=false이지만 status가 WITHDRAWN인 케이스
    // (예: 락 경합 사이 미세한 타이밍 차, 또는 데이터 정합성이 어긋난 방어적 케이스)
    @Test
    @DisplayName("[동시성 보완] APP-01 직접 신청 - status가 WITHDRAWN이면 ACCOUNT_WITHDRAWN")
    void applyDirect_status가_WITHDRAWN이면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUserWithStatus(USER_ID, UserGender.MALE, UserStatus.WITHDRAWN);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(activityApplicationRepository, never()).save(any());
    }

    // [동시성 보완 신규] 정지 계정은 신청 불가 (기존에 막혀있지 않던 갭)
    @Test
    @DisplayName("[동시성 보완] APP-01 직접 신청 - status가 SUSPENDED이면 ACCOUNT_SUSPENDED")
    void applyDirect_status가_SUSPENDED면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUserWithStatus(USER_ID, UserGender.MALE, UserStatus.SUSPENDED);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        verify(activityApplicationRepository, never()).save(any());
    }

    @Test
    @DisplayName("REQ-ACT-13 - APP-01 직접 신청 - 이미 PENDING/APPROVED 신청이 있으면 APPLICATION_ALREADY_EXISTS")
    void applyDirect_이미_신청이_있으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication existing = buildExistingApplication(
                10L, activity, user, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.applyDirect(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("REQ-ACT-15 - APP-01 직접 신청 - CANCELED 신청은 새 행을 만들지 않고 기존 행을 PENDING으로 되돌린다")
    void applyDirect_CANCELED_신청은_기존_행을_재사용한다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication existing = buildExistingApplication(
                10L, activity, user, ApplicationType.DIRECT, ApplicationStatus.CANCELED
        );

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(existing));
        given(activityApplicationRepository.save(any(ActivityApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = service.applyDirect(ACTIVITY_ID, USER_ID);

        assertThat(response.applicationId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("REQ-ACT-15 - [현재 동작 기록] AUTO로 취소했던 신청을 직접 신청(APP-01)으로 재신청해도 applicationType은 AUTO로 유지된다")
    void applyDirect_CANCELED된_AUTO신청_재사용시_타입은_그대로_AUTO다() {
        // reactivate()는 status/approvedBy/approvedAt/rejectedReason만 초기화하고
        // applicationType은 변경하지 않는다. DB_ENTITY_GUIDE의 "재신청 방식에 따라
        // applicationType을 갱신할 수 있다"는 표현과 달리, 현재 코드는 갱신하지 않는다.
        // 팀 정책 확인이 필요한 지점이라 회귀 테스트로 고정해 둔다.
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication existing = buildExistingApplication(
                10L, activity, user, ApplicationType.AUTO, ApplicationStatus.CANCELED
        );

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(existing));
        given(activityApplicationRepository.save(any(ActivityApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = service.applyDirect(ACTIVITY_ID, USER_ID);

        assertThat(response.applicationType()).isEqualTo(ApplicationType.AUTO);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
    }

    // ---------------------------------------------------------------
    // APP-02 자동배정
    //
    // 아래 getAutoMatchCandidate 관련 테스트들은 filterValidAutoMatchCandidates
    // (읽기 전용, @Transactional(readOnly = true)) 경로를 사용하므로
    // userRepository.findById를 그대로 유지한다 (락 대상 아님).
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-ACT-17 - APP-02 자동배정 후보 조회 - 후보가 없으면 NO_AUTO_MATCH_CANDIDATE")
    void getAutoMatchCandidate_후보가_없으면_예외를_던진다() {
        given(careActivityRepository.findAutoMatchCandidates(eq(USER_ID), anyList()))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.getAutoMatchCandidate(USER_ID, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_AUTO_MATCH_CANDIDATE);
    }

    @Test
    @DisplayName("REQ-ACT-17 - APP-02 자동배정 후보 조회 - excludeActivityIds가 없으면 sentinel(List.of(-1L))로 조회한다")
    void getAutoMatchCandidate_제외목록이_없으면_기본값으로_조회한다() {
        given(careActivityRepository.findAutoMatchCandidates(eq(USER_ID), eq(List.of(-1L))))
                .willReturn(List.of());

        assertThatThrownBy(() -> service.getAutoMatchCandidate(USER_ID, null, null, null))
                .isInstanceOf(CustomException.class);

        verify(careActivityRepository).findAutoMatchCandidates(eq(USER_ID), eq(List.of(-1L)));
    }

    @Test
    @DisplayName("REQ-ACT-16, REQ-ACT-17 - APP-02 자동배정 후보 조회 - 좌표가 있으면 거리와 안부확인 경과를 함께 고려해 최적 후보를 고른다")
    void getAutoMatchCandidate_좌표가_있으면_거리와_안부경과를_모두_고려한다() {
        BigDecimal userLat = new BigDecimal("37.5665");
        BigDecimal userLng = new BigDecimal("126.9780");

        CareRecipient recipientA =
                buildRecipient(userLat, userLng, LocalDateTime.now().minusDays(30));
        CareActivity activityA =
                buildActivity(1L, ActivityStatus.RECRUITING, 2, recipientA);

        CareRecipient recipientB =
                buildRecipient(new BigDecimal("35.1796"), new BigDecimal("129.0756"), LocalDateTime.now().minusDays(1));
        CareActivity activityB =
                buildActivity(2L, ActivityStatus.RECRUITING, 2, recipientB);

        User user = buildUser(USER_ID, UserGender.MALE);

        given(careActivityRepository.findAutoMatchCandidates(eq(USER_ID), anyList()))
                .willReturn(List.of(activityA, activityB));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(1L, 2L))))
                .willReturn(Map.of());
        given(activityApplicationRepository.countApprovedSameGenderMap(eq(List.of(1L, 2L))))
                .willReturn(Map.of());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(1L))))
                .willReturn(Map.of(1L, 1L));
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(eq(List.of(1L))))
                .willReturn(List.of());

        var response = service.getAutoMatchCandidate(USER_ID, userLat, userLng, null);

        assertThat(response.activityId()).isEqualTo(1L);
        assertThat(response.myApplicationStatus()).isNull();
        assertThat(response.approvedCount()).isEqualTo(1L);
        assertThat(response.applicantCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("REQ-ACT-16, REQ-ACT-17 - APP-02 자동배정 후보 조회 - 좌표가 없으면 안부확인이 오래된 순으로 후보를 고른다")
    void getAutoMatchCandidate_좌표가_없으면_안부경과만으로_후보를_고른다() {
        CareRecipient recipientOld = buildRecipient(null, null, LocalDateTime.now().minusDays(60));
        CareActivity activityOld = buildActivity(1L, ActivityStatus.RECRUITING, 2, recipientOld);

        CareRecipient recipientRecent = buildRecipient(null, null, LocalDateTime.now().minusDays(1));
        CareActivity activityRecent = buildActivity(2L, ActivityStatus.RECRUITING, 2, recipientRecent);

        User user = buildUser(USER_ID, UserGender.MALE);

        given(careActivityRepository.findAutoMatchCandidates(eq(USER_ID), anyList()))
                .willReturn(List.of(activityOld, activityRecent));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(1L, 2L))))
                .willReturn(Map.of());
        given(activityApplicationRepository.countApprovedSameGenderMap(eq(List.of(1L, 2L))))
                .willReturn(Map.of());
        given(activityApplicationRepository.countApprovedMap(eq(List.of(1L))))
                .willReturn(Map.of());
        given(activityApplicationRepository.findActiveApplicationsByActivityIds(eq(List.of(1L))))
                .willReturn(List.of());

        var response = service.getAutoMatchCandidate(USER_ID, null, null, null);

        assertThat(response.activityId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("REQ-ACT-18 - APP-02 자동배정 신청 확정 - 정상 신청이면 AUTO/PENDING으로 생성한다")
    void applyAuto_신규신청이면_AUTO_PENDING으로_생성한다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.empty());
        given(activityApplicationRepository.save(any(ActivityApplication.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ApplicationResponse response = service.applyAuto(ACTIVITY_ID, USER_ID);

        assertThat(response.applicationType()).isEqualTo(ApplicationType.AUTO);
        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("REQ-ACT-18 - APP-02 자동배정 신청 확정 - 모집 중이 아니면 예외를 던진다")
    void applyAuto_모집중이_아니면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.IN_PROGRESS, 2, recipient);
        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.applyAuto(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACTIVITY_NOT_RECRUITING);
    }

    @Test
    @DisplayName("REQ-ACT-18, REQ-ACT-13 - APP-02 자동배정 신청 확정 - 이미 신청이 있으면 예외를 던진다")
    void applyAuto_이미_신청이_있으면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication existing = buildExistingApplication(
                10L, activity, user, ApplicationType.AUTO, ApplicationStatus.APPROVED
        );

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));
        given(activityApplicationRepository.findByActivity_IdAndUser_Id(ACTIVITY_ID, USER_ID))
                .willReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.applyAuto(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_ALREADY_EXISTS);
    }

    // [동시성 보완 신규] applyAuto도 applyDirect와 동일한 재검증을 거친다.
    @Test
    @DisplayName("[동시성 보완] APP-02 자동배정 신청 확정 - status가 WITHDRAWN이면 ACCOUNT_WITHDRAWN")
    void applyAuto_status가_WITHDRAWN이면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUserWithStatus(USER_ID, UserGender.MALE, UserStatus.WITHDRAWN);

        given(careActivityRepository.findDetailById(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(userRepository.findByIdAndDeletedFalseForUpdate(USER_ID)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> service.applyAuto(ACTIVITY_ID, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        verify(activityApplicationRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // APP-03 / APP-04 목록 조회
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-ACT-23 - APP-03 내 신청 목록 - PENDING, APPROVED, REJECTED, CANCELED 신청을 모두 조회할 수 있다")
    void getMyApplications_전체조회시_모든신청상태를_반환한다() {

        Pageable pageable = PageRequest.of(0, 20);
        CareRecipient recipient = buildRecipient(null, null, null);
        User user = buildUser(USER_ID, UserGender.MALE);

        ActivityApplication pending = buildExistingApplication(
                1L, buildActivity(21L, ActivityStatus.RECRUITING, 2, recipient),
                user, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );
        ActivityApplication approved = buildExistingApplication(
                2L, buildActivity(22L, ActivityStatus.READY, 2, recipient),
                user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );
        ActivityApplication rejected = buildExistingApplication(
                3L, buildActivity(23L, ActivityStatus.RECRUITING, 2, recipient),
                user, ApplicationType.DIRECT, ApplicationStatus.REJECTED
        );
        ActivityApplication canceled = buildExistingApplication(
                4L, buildActivity(24L, ActivityStatus.RECRUITING, 2, recipient),
                user, ApplicationType.DIRECT, ApplicationStatus.CANCELED
        );

        Page<ActivityApplication> page = new PageImpl<>(
                List.of(pending, approved, rejected, canceled), pageable, 4
        );

        given(activityApplicationRepository.findMyApplications(
                eq(USER_ID), eq(false), isNull(), eq(false), isNull(), eq(pageable)
        )).willReturn(page);

        PageResponse<ApplicationResponse> response = service.getMyApplications(USER_ID, null, null, pageable);

        assertThat(response.content())
                .extracting(ApplicationResponse::status)
                .containsExactly(
                        ApplicationStatus.PENDING, ApplicationStatus.APPROVED,
                        ApplicationStatus.REJECTED, ApplicationStatus.CANCELED
                );
    }

    @Test
    @DisplayName("REQ-ACT-23 - APP-03 내 신청 목록 - status만 지정하면 hasStatus=true/hasType=false로 조회한다")
    void getMyApplications_status만_지정하면_해당_플래그로_조회한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ActivityApplication> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        given(activityApplicationRepository.findMyApplications(
                eq(USER_ID), eq(true), eq(ApplicationStatus.PENDING), eq(false), isNull(), eq(pageable)
        )).willReturn(emptyPage);

        PageResponse<ApplicationResponse> response =
                service.getMyApplications(USER_ID, ApplicationStatus.PENDING, null, pageable);

        assertThat(response.content()).isEmpty();
        verify(activityApplicationRepository).findMyApplications(
                eq(USER_ID), eq(true), eq(ApplicationStatus.PENDING), eq(false), isNull(), eq(pageable)
        );
    }

    @Test
    @DisplayName("REQ-ACT-23 - APP-03 내 신청 목록 - 필터가 없으면 hasStatus/hasType 모두 false로 조회한다")
    void getMyApplications_필터가_없으면_전체_조회_플래그로_요청한다() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<ActivityApplication> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        given(activityApplicationRepository.findMyApplications(
                eq(USER_ID), eq(false), isNull(), eq(false), isNull(), eq(pageable)
        )).willReturn(emptyPage);

        service.getMyApplications(USER_ID, null, null, pageable);

        verify(activityApplicationRepository).findMyApplications(
                eq(USER_ID), eq(false), isNull(), eq(false), isNull(), eq(pageable)
        );
    }

    @Test
    @DisplayName("REQ-ACT-25 - APP-04 내 활동 목록 - APPROVED된 예정 및 진행 활동을 조회할 수 있다")
    void getMyActivities_APPROVED된_예정_진행활동을_반환한다() {

        Pageable pageable = PageRequest.of(0, 20);
        CareRecipient recipient = buildRecipient(null, null, null);
        User user = buildUser(USER_ID, UserGender.MALE);

        CareActivity readyActivity = buildActivity(31L, ActivityStatus.READY, 2, recipient);
        CareActivity inProgressActivity = buildActivity(32L, ActivityStatus.IN_PROGRESS, 2, recipient);

        ActivityApplication readyApplication = buildExistingApplication(
                1L, readyActivity, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );
        ActivityApplication inProgressApplication = buildExistingApplication(
                2L, inProgressActivity, user, ApplicationType.AUTO, ApplicationStatus.APPROVED
        );

        Page<ActivityApplication> page = new PageImpl<>(
                List.of(readyApplication, inProgressApplication), pageable, 2
        );

        given(activityApplicationRepository.findMyActivities(eq(USER_ID), eq(false), isNull(), eq(pageable)))
                .willReturn(page);
        given(activityRecordRepository.findByActivity_IdIn(eq(List.of(31L, 32L))))
                .willReturn(List.of());

        PageResponse<ApplicationResponse> response = service.getMyActivities(USER_ID, null, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content())
                .extracting(ApplicationResponse::status)
                .containsOnly(ApplicationStatus.APPROVED);
        assertThat(response.content())
                .extracting(ApplicationResponse::activityStatus)
                .containsExactly(ActivityStatus.READY, ActivityStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("REQ-ACT-25 - APP-04 내 활동 목록 - 페이지에 나온 활동들의 activityRecordId를 한 번의 IN 조회로 채운다")
    void getMyActivities_activityRecordId를_bulk조회로_채운다() {
        Pageable pageable = PageRequest.of(0, 20);

        CareRecipient recipient = buildRecipient(null, null, null);
        User user = buildUser(USER_ID, UserGender.MALE);

        CareActivity activityWithRecord = buildActivity(10L, ActivityStatus.IN_PROGRESS, 2, recipient);
        CareActivity activityWithoutRecord = buildActivity(11L, ActivityStatus.READY, 2, recipient);

        ActivityApplication appWithRecord = buildExistingApplication(
                1L, activityWithRecord, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );
        ActivityApplication appWithoutRecord = buildExistingApplication(
                2L, activityWithoutRecord, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );

        Page<ActivityApplication> page = new PageImpl<>(
                List.of(appWithRecord, appWithoutRecord), pageable, 2
        );

        ActivityRecord record = ActivityRecord.createDraft(activityWithRecord, 1, LocalDateTime.now());
        ReflectionTestUtils.setField(record, "id", 999L);

        given(activityApplicationRepository.findMyActivities(eq(USER_ID), eq(false), isNull(), eq(pageable)))
                .willReturn(page);
        given(activityRecordRepository.findByActivity_IdIn(eq(List.of(10L, 11L))))
                .willReturn(List.of(record));

        PageResponse<ApplicationResponse> response = service.getMyActivities(USER_ID, null, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).activityRecordId()).isEqualTo(999L);
        assertThat(response.content().get(1).activityRecordId()).isNull();
    }

    // ---------------------------------------------------------------
    // APP-05 신청 취소
    // ---------------------------------------------------------------

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - 존재하지 않는 신청이면 RESOURCE_NOT_FOUND")
    void cancelApplication_신청이_없으면_예외를_던진다() {
        given(activityApplicationRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelApplication(1L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - 본인 신청이 아니면 FORBIDDEN")
    void cancelApplication_본인_신청이_아니면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User owner = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, owner, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));

        long otherUserId = 999L;
        assertThatThrownBy(() -> service.cancelApplication(1L, otherUserId))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // [신규] cancelApplication()에 추가된 계정 상태 검증 회귀 테스트.
    // application.getUser()로 이미 로드된 User의 status를 그대로 확인하므로
    // userRepository는 별도로 stub할 필요가 없다.
    @Test
    @DisplayName("[신규] APP-05 신청 취소 - 신청자 계정이 SUSPENDED이면 ACCOUNT_SUSPENDED")
    void cancelApplication_신청자가_SUSPENDED면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User suspendedUser = buildUserWithStatus(USER_ID, UserGender.MALE, UserStatus.SUSPENDED);
        ActivityApplication application = buildExistingApplication(
                1L, activity, suspendedUser, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> service.cancelApplication(1L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);

        // 상태 검증에서 막혔으므로 신청 상태는 그대로 PENDING이어야 한다.
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        verify(careActivityRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("[신규] APP-05 신청 취소 - 신청자 계정이 WITHDRAWN이면 ACCOUNT_WITHDRAWN")
    void cancelApplication_신청자가_WITHDRAWN이면_예외를_던진다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User withdrawnUser = buildUserWithStatus(USER_ID, UserGender.MALE, UserStatus.WITHDRAWN);
        ActivityApplication application = buildExistingApplication(
                1L, activity, withdrawnUser, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> service.cancelApplication(1L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ACCOUNT_WITHDRAWN);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - PENDING 신청은 CareActivity 조회 없이 바로 취소된다")
    void cancelApplication_PENDING이면_바로_취소된다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, user, ApplicationType.DIRECT, ApplicationStatus.PENDING
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));

        ApplicationResponse response = service.cancelApplication(1L, USER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELED);
        verify(careActivityRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - APPROVED이고 활동이 READY면 취소 후 정원 미달 시 RECRUITING으로 되돌린다")
    void cancelApplication_APPROVED_READY에서_취소하면_정원미달시_RECRUITING으로_돌아간다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.READY, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));
        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 1L));

        ApplicationResponse response = service.cancelApplication(1L, USER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.RECRUITING);
    }

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - APPROVED이고 활동이 RECRUITING이면 상태 재계산 없이 취소만 된다")
    void cancelApplication_APPROVED_RECRUITING에서_취소하면_상태변경없이_취소만_된다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));
        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));
        given(activityApplicationRepository.countApprovedMap(eq(List.of(ACTIVITY_ID))))
                .willReturn(Map.of(ACTIVITY_ID, 0L));

        ApplicationResponse response = service.cancelApplication(1L, USER_ID);

        assertThat(response.status()).isEqualTo(ApplicationStatus.CANCELED);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.RECRUITING);
    }

    @Test
    @DisplayName("REQ-ACT-14 - APP-05 신청 취소 - APPROVED이지만 활동이 이미 IN_PROGRESS면 취소할 수 없다")
    void cancelApplication_활동이_시작된_이후에는_APPROVED를_취소할_수_없다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.IN_PROGRESS, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, user, ApplicationType.DIRECT, ApplicationStatus.APPROVED
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));
        given(careActivityRepository.findByIdForUpdate(ACTIVITY_ID)).willReturn(Optional.of(activity));

        assertThatThrownBy(() -> service.cancelApplication(1L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_NOT_CANCELABLE);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
    }

    @Test
    @DisplayName("REQ-ACT-14, REQ-ACT-15 - APP-05 신청 취소 - REJECTED/CANCELED 신청은 취소할 수 없다")
    void cancelApplication_REJECTED된_신청은_취소할_수_없다() {
        CareRecipient recipient = buildRecipient(null, null, null);
        CareActivity activity = buildActivity(ACTIVITY_ID, ActivityStatus.RECRUITING, 2, recipient);
        User user = buildUser(USER_ID, UserGender.MALE);
        ActivityApplication application = buildExistingApplication(
                1L, activity, user, ApplicationType.DIRECT, ApplicationStatus.REJECTED
        );

        given(activityApplicationRepository.findById(1L)).willReturn(Optional.of(application));

        assertThatThrownBy(() -> service.cancelApplication(1L, USER_ID))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLICATION_NOT_CANCELABLE);
    }
}