package com.dagachi.backend.user.mypage.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.*;
import com.dagachi.backend.domain.enums.*;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.testsupport.PostgresContainerTestBase;
import com.dagachi.backend.user.mypage.dto.WithdrawRequest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WithdrawalPolicyIntegrationTest
        extends PostgresContainerTestBase {

    @Autowired
    private UserProfileService userProfileService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareRecipientRepository careRecipientRepository;

    @Autowired
    private CareActivityRepository careActivityRepository;

    @Autowired
    private ActivityApplicationRepository activityApplicationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("REQ-AUTH-08 - 완료된 활동의 APPROVED 신청은 회원 탈퇴를 차단하지 않는다")
    void withdraw_완료된활동의_APPROVED신청은_탈퇴를_차단하지않는다() {
        // given

        /*
         * Institution은 현재 별도 Repository/factory가 없으므로
         * 테스트에서 실제 Entity를 생성하여 EntityManager로 저장합니다.
         *
         * 운영 DB의 실제 기관 데이터를 사용하는 것이 아니라
         * Testcontainers PostgreSQL에서만 존재하는 테스트 fixture입니다.
         */
        Institution institution =
                BeanUtils.instantiateClass(
                        Institution.class
                );

        ReflectionTestUtils.setField(
                institution,
                "name",
                "테스트 주민센터"
        );

        ReflectionTestUtils.setField(
                institution,
                "type",
                InstitutionType.COMMUNITY_CENTER
        );

        ReflectionTestUtils.setField(
                institution,
                "address",
                "경기도 평택시 테스트 주소"
        );

        ReflectionTestUtils.setField(
                institution,
                "phone",
                "0311234567"
        );

        ReflectionTestUtils.setField(
                institution,
                "status",
                InstitutionStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                institution,
                "deleted",
                false
        );

        entityManager.persist(institution);
        entityManager.flush();

        /*
         * CareActivity.createdBy로 사용할 기관 담당자입니다.
         *
         * User.create()는 USER Role로 생성되므로
         * 테스트 fixture에서만 INSTITUTION Role과 institution을 설정합니다.
         */
        User institutionUser =
                User.create(
                        "institution@test.com",
                        passwordEncoder.encode("institution-password"),
                        "기관 담당자",
                        "기관담당",
                        "0311234567",
                        UserGender.FEMALE
                );

        ReflectionTestUtils.setField(
                institutionUser,
                "role",
                UserRole.INSTITUTION
        );

        ReflectionTestUtils.setField(
                institutionUser,
                "institution",
                institution
        );

        institutionUser =
                userRepository.saveAndFlush(
                        institutionUser
                );

        /*
         * 탈퇴 정책을 검증할 일반 USER입니다.
         */
        String rawPassword =
                "test-password";

        User user =
                User.create(
                        "withdraw-policy@test.com",
                        passwordEncoder.encode(rawPassword),
                        "탈퇴 정책 사용자",
                        "탈퇴정책",
                        "01012345678",
                        UserGender.MALE
                );

        user =
                userRepository.saveAndFlush(
                        user
                );

        CareRecipient recipient =
                CareRecipient.create(
                        institution,
                        "테스트 대상자",
                        UserGender.FEMALE,
                        1950,
                        "01099998888",
                        "경기도 평택시 테스트 주소",
                        null,
                        null,
                        null,
                        ConsentStatus.AGREED
                );

        recipient =
                careRecipientRepository.saveAndFlush(
                        recipient
                );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        institution,
                        institutionUser,
                        LocalDateTime.now().minusDays(1),
                        1,
                        GenderCondition.NONE
                );

        /*
         * 과거에 정상적으로 완료된 활동을 재현합니다.
         */
        activity.changeStatus(
                ActivityStatus.COMPLETED
        );

        activity =
                careActivityRepository.saveAndFlush(
                        activity
                );

        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        user
                );

        /*
         * 신청은 승인 이력을 유지하지만
         * 연결된 CareActivity는 이미 COMPLETED 상태입니다.
         */
        application.approve(
                institutionUser
        );

        activityApplicationRepository.saveAndFlush(
                application
        );

        entityManager.clear();

        // when
        userProfileService.withdraw(
                user.getId(),
                new WithdrawRequest(rawPassword)
        );

        entityManager.flush();
        entityManager.clear();

        // then
        User withdrawnUser =
                userRepository.findById(
                                user.getId()
                        )
                        .orElseThrow();

        /*
         * COMPLETED 활동은 더 이상 진행 중인 참여가 아니므로
         * 과거 APPROVED 신청이 남아 있어도 탈퇴가 허용되어야 합니다.
         */
        assertThat(withdrawnUser.getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);

        assertThat(withdrawnUser.getDeleted())
                .isTrue();

        assertThat(withdrawnUser.getDeletedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("REQ-AUTH-08 - 진행 중 활동의 APPROVED 신청이 있으면 회원 탈퇴를 차단한다")
    void withdraw_진행중활동의_APPROVED신청이있으면_탈퇴를_차단한다() {
        // given

        /*
         * Institution은 현재 별도 Repository/factory가 없으므로
         * 테스트에서 실제 Entity를 생성하여 EntityManager로 저장합니다.
         *
         * 운영 DB의 실제 기관 데이터를 사용하는 것이 아니라
         * Testcontainers PostgreSQL에서만 존재하는 테스트 fixture입니다.
         */
        Institution institution =
                BeanUtils.instantiateClass(
                        Institution.class
                );

        ReflectionTestUtils.setField(
                institution,
                "name",
                "테스트 주민센터"
        );

        ReflectionTestUtils.setField(
                institution,
                "type",
                InstitutionType.COMMUNITY_CENTER
        );

        ReflectionTestUtils.setField(
                institution,
                "address",
                "경기도 평택시 테스트 주소"
        );

        ReflectionTestUtils.setField(
                institution,
                "phone",
                "0311234567"
        );

        ReflectionTestUtils.setField(
                institution,
                "status",
                InstitutionStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                institution,
                "deleted",
                false
        );

        entityManager.persist(institution);
        entityManager.flush();

        /*
         * CareActivity.createdBy로 사용할 기관 담당자입니다.
         *
         * User.create()는 USER Role로 생성되므로
         * 테스트 fixture에서만 INSTITUTION Role과 institution을 설정합니다.
         */
        User institutionUser =
                User.create(
                        "institution@test.com",
                        passwordEncoder.encode("institution-password"),
                        "기관 담당자",
                        "기관담당",
                        "0311234567",
                        UserGender.FEMALE
                );

        ReflectionTestUtils.setField(
                institutionUser,
                "role",
                UserRole.INSTITUTION
        );

        ReflectionTestUtils.setField(
                institutionUser,
                "institution",
                institution
        );

        institutionUser =
                userRepository.saveAndFlush(
                        institutionUser
                );

        /*
         * 탈퇴 정책을 검증할 일반 USER입니다.
         */
        String rawPassword =
                "test-password";

        User user =
                User.create(
                        "withdraw-in-progress@test.com",
                        passwordEncoder.encode(rawPassword),
                        "탈퇴 정책 사용자",
                        "탈퇴정책",
                        "01012345678",
                        UserGender.MALE
                );

        user =
                userRepository.saveAndFlush(
                        user
                );

        Long userId =
                user.getId();

        CareRecipient recipient =
                CareRecipient.create(
                        institution,
                        "테스트 대상자",
                        UserGender.FEMALE,
                        1950,
                        "01099998888",
                        "경기도 평택시 테스트 주소",
                        null,
                        null,
                        null,
                        ConsentStatus.AGREED
                );

        recipient =
                careRecipientRepository.saveAndFlush(
                        recipient
                );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        institution,
                        institutionUser,
                        LocalDateTime.now().minusDays(1),
                        1,
                        GenderCondition.NONE
                );

        /*
         * 진행 중인 활동으로 재현합니다.
         */
        activity.changeStatus(
                ActivityStatus.IN_PROGRESS
        );

        activity =
                careActivityRepository.saveAndFlush(
                        activity
                );

        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        user
                );

        /*
         * APPROVED 신청이 연결된 활동이 실제 IN_PROGRESS 상태인
         * 진행 중 참여 상황을 재현합니다.
         */
        application.approve(
                institutionUser
        );

        activityApplicationRepository.saveAndFlush(
                application
        );

        entityManager.clear();

        // when & then
        assertThatThrownBy(
                () -> userProfileService.withdraw(
                        userId,
                        new WithdrawRequest(rawPassword)
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED);
                });

        /*
         * 탈퇴가 차단되었으므로 사용자 상태는 그대로 ACTIVE여야 합니다.
         */
        entityManager.clear();

        User activeUser =
                userRepository.findById(
                                userId
                        )
                        .orElseThrow();

        assertThat(activeUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(activeUser.getDeleted())
                .isFalse();

        assertThat(activeUser.getDeletedAt())
                .isNull();
    }

    @Test
    @DisplayName("REQ-AUTH-08 - 취소된 활동의 APPROVED 신청은 회원 탈퇴를 차단하지 않는다")
    void withdraw_취소된활동의_APPROVED신청은_탈퇴를_차단하지않는다() {
        // given

        /*
         * Institution은 현재 별도 Repository/factory가 없으므로
         * 테스트에서 실제 Entity를 생성하여 EntityManager로 저장합니다.
         */
        Institution institution =
                BeanUtils.instantiateClass(
                        Institution.class
                );

        ReflectionTestUtils.setField(
                institution,
                "name",
                "테스트 주민센터"
        );

        ReflectionTestUtils.setField(
                institution,
                "type",
                InstitutionType.COMMUNITY_CENTER
        );

        ReflectionTestUtils.setField(
                institution,
                "address",
                "경기도 평택시 테스트 주소"
        );

        ReflectionTestUtils.setField(
                institution,
                "phone",
                "0311234567"
        );

        ReflectionTestUtils.setField(
                institution,
                "status",
                InstitutionStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                institution,
                "deleted",
                false
        );

        entityManager.persist(institution);
        entityManager.flush();

        User institutionUser =
                User.create(
                        "institution-canceled@test.com",
                        passwordEncoder.encode("institution-password"),
                        "기관 담당자",
                        "기관담당",
                        "0311234567",
                        UserGender.FEMALE
                );

        ReflectionTestUtils.setField(
                institutionUser,
                "role",
                UserRole.INSTITUTION
        );

        ReflectionTestUtils.setField(
                institutionUser,
                "institution",
                institution
        );

        institutionUser =
                userRepository.saveAndFlush(
                        institutionUser
                );

        String rawPassword =
                "test-password";

        User user =
                User.create(
                        "withdraw-canceled@test.com",
                        passwordEncoder.encode(rawPassword),
                        "탈퇴 정책 사용자",
                        "탈퇴정책",
                        "01012345678",
                        UserGender.MALE
                );

        user =
                userRepository.saveAndFlush(
                        user
                );

        Long userId =
                user.getId();

        CareRecipient recipient =
                CareRecipient.create(
                        institution,
                        "테스트 대상자",
                        UserGender.FEMALE,
                        1950,
                        "01099998888",
                        "경기도 평택시 테스트 주소",
                        null,
                        null,
                        null,
                        ConsentStatus.AGREED
                );

        recipient =
                careRecipientRepository.saveAndFlush(
                        recipient
                );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        institution,
                        institutionUser,
                        LocalDateTime.now().minusDays(1),
                        1,
                        GenderCondition.NONE
                );

        /*
         * 활동 자체가 취소되어 더 이상 진행 중이지 않은 상태를 재현합니다.
         */
        activity.changeStatus(
                ActivityStatus.CANCELED
        );

        activity =
                careActivityRepository.saveAndFlush(
                        activity
                );

        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        user
                );

        /*
         * APPROVED 신청 이력은 남아 있지만,
         * 연결된 CareActivity는 이미 CANCELED 상태입니다.
         */
        application.approve(
                institutionUser
        );

        activityApplicationRepository.saveAndFlush(
                application
        );

        entityManager.clear();

        // when
        userProfileService.withdraw(
                userId,
                new WithdrawRequest(rawPassword)
        );

        entityManager.flush();
        entityManager.clear();

        // then
        User withdrawnUser =
                userRepository.findById(
                                userId
                        )
                        .orElseThrow();

        /*
         * CANCELED 활동은 진행 중 참여가 아니므로
         * 과거 APPROVED 신청이 남아 있어도 탈퇴가 허용되어야 합니다.
         */
        assertThat(withdrawnUser.getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);

        assertThat(withdrawnUser.getDeleted())
                .isTrue();

        assertThat(withdrawnUser.getDeletedAt())
                .isNotNull();
    }

    @Test
    @DisplayName("REQ-AUTH-08 - PENDING 신청이 있으면 회원 탈퇴를 차단한다")
    void withdraw_PENDING신청이있으면_탈퇴를_차단한다() {
        // given

        Institution institution =
                BeanUtils.instantiateClass(
                        Institution.class
                );

        ReflectionTestUtils.setField(
                institution,
                "name",
                "테스트 주민센터"
        );

        ReflectionTestUtils.setField(
                institution,
                "type",
                InstitutionType.COMMUNITY_CENTER
        );

        ReflectionTestUtils.setField(
                institution,
                "address",
                "경기도 평택시 테스트 주소"
        );

        ReflectionTestUtils.setField(
                institution,
                "phone",
                "0311234567"
        );

        ReflectionTestUtils.setField(
                institution,
                "status",
                InstitutionStatus.ACTIVE
        );

        ReflectionTestUtils.setField(
                institution,
                "deleted",
                false
        );

        entityManager.persist(institution);
        entityManager.flush();

        User institutionUser =
                User.create(
                        "institution-pending@test.com",
                        passwordEncoder.encode("institution-password"),
                        "기관 담당자",
                        "기관담당",
                        "0311234567",
                        UserGender.FEMALE
                );

        ReflectionTestUtils.setField(
                institutionUser,
                "role",
                UserRole.INSTITUTION
        );

        ReflectionTestUtils.setField(
                institutionUser,
                "institution",
                institution
        );

        institutionUser =
                userRepository.saveAndFlush(
                        institutionUser
                );

        String rawPassword =
                "test-password";

        User user =
                User.create(
                        "withdraw-pending@test.com",
                        passwordEncoder.encode(rawPassword),
                        "탈퇴 정책 사용자",
                        "탈퇴정책",
                        "01012345678",
                        UserGender.MALE
                );

        user =
                userRepository.saveAndFlush(
                        user
                );

        Long userId =
                user.getId();

        CareRecipient recipient =
                CareRecipient.create(
                        institution,
                        "테스트 대상자",
                        UserGender.FEMALE,
                        1950,
                        "01099998888",
                        "경기도 평택시 테스트 주소",
                        null,
                        null,
                        null,
                        ConsentStatus.AGREED
                );

        recipient =
                careRecipientRepository.saveAndFlush(
                        recipient
                );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        institution,
                        institutionUser,
                        LocalDateTime.now().plusDays(1),
                        1,
                        GenderCondition.NONE
                );

        activity =
                careActivityRepository.saveAndFlush(
                        activity
                );

        /*
         * createDirect()는 기본적으로 status=PENDING으로 생성합니다.
         *
         * 이번 테스트에서는 approve()를 호출하지 않고
         * 신청 대기 상태 그대로 저장합니다.
         */
        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        user
                );

        activityApplicationRepository.saveAndFlush(
                application
        );

        entityManager.clear();

        // when & then
        assertThatThrownBy(
                () -> userProfileService.withdraw(
                        userId,
                        new WithdrawRequest(rawPassword)
                )
        )
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> {
                    CustomException customException =
                            (CustomException) exception;

                    assertThat(customException.getErrorCode())
                            .isEqualTo(ErrorCode.WITHDRAWAL_BLOCKED);
                });

        /*
         * 탈퇴가 차단되었으므로 계정 상태는 그대로 ACTIVE여야 합니다.
         */
        entityManager.clear();

        User activeUser =
                userRepository.findById(
                                userId
                        )
                        .orElseThrow();

        assertThat(activeUser.getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        assertThat(activeUser.getDeleted())
                .isFalse();

        assertThat(activeUser.getDeletedAt())
                .isNull();
    }
}