package com.dagachi.backend.institution.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.domain.entity.ActivityApplication;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.CareRecipient;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.CareRecipientStatus;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.CareRecipientRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.InstitutionActivityRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityCreateRequest;
import com.dagachi.backend.institution.activity.dto.InstitutionActivityStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionActivityServiceTest {

    @Mock UserRepository userRepository;
    @Mock InstitutionActivityRepository institutionActivityRepository;
    @Mock ActivityApplicationRepository activityApplicationRepository;
    @Mock CareActivityRepository careActivityRepository;
    @Mock CareRecipientRepository careRecipientRepository;
    @Mock ChecklistResponseRepository checklistResponseRepository;
    @Mock User manager;
    @Mock User volunteer;
    @Mock Institution institution;
    @Mock CareRecipient recipient;

    private InstitutionActivityService service;

    @BeforeEach
    void setUp() {
        service = new InstitutionActivityService(
                userRepository,
                institutionActivityRepository,
                activityApplicationRepository,
                careActivityRepository,
                careRecipientRepository,
                checklistResponseRepository
        );

        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(manager.getInstitution()).thenReturn(institution);
        when(institution.getId()).thenReturn(1L);
    }

    @Test
    @DisplayName("동의하지 않은 대상자는 활동을 등록할 수 없다")
    void createActivityRejectsRecipientWithoutConsent() {
        when(careRecipientRepository.findByIdAndInstitution_IdAndDeletedFalse(20L, 1L))
                .thenReturn(Optional.of(recipient));
        when(recipient.getStatus()).thenReturn(CareRecipientStatus.ACTIVE);
        when(recipient.getConsentStatus()).thenReturn(ConsentStatus.PENDING);

        InstitutionActivityCreateRequest request = new InstitutionActivityCreateRequest(
                20L,
                LocalDateTime.now().plusDays(1),
                2,
                GenderCondition.NONE
        );

        assertThatThrownBy(() -> service.createInstitutionActivity(10L, request))
                .isInstanceOf(CustomException.class)
                .hasMessage("입력값이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("마지막 봉사자를 승인하면 활동이 자동으로 READY가 된다")
    void approvingLastRequiredVolunteerChangesActivityToReady() {
        CareActivity activity = CareActivity.create(
                recipient,
                institution,
                manager,
                LocalDateTime.now().plusDays(1),
                2,
                GenderCondition.NONE
        );
        ActivityApplication application = ActivityApplication.createDirect(activity, volunteer);

        when(institutionActivityRepository.findDetailActivity(1L, 30L)).thenReturn(Optional.of(activity));
        when(careActivityRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(activity));
        when(institutionActivityRepository.findActivityApplication(1L, 30L, 40L))
                .thenReturn(Optional.of(application));
        when(institutionActivityRepository.countApplications(30L, ApplicationStatus.APPROVED))
                .thenReturn(1L);

        service.approveActivityApplication(10L, 30L, 40L);

        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.READY);
    }

    @Test
    @DisplayName("같은 성별 봉사자가 없으면 마지막 승인을 차단한다")
    void approvingLastVolunteerRejectsUnmetGenderCondition() {
        CareActivity activity = CareActivity.create(
                recipient,
                institution,
                manager,
                LocalDateTime.now().plusDays(1),
                2,
                GenderCondition.SAME_GENDER_ONE
        );

        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        volunteer
                );

        when(recipient.getGender())
                .thenReturn(UserGender.FEMALE);

        when(volunteer.getGender())
                .thenReturn(UserGender.MALE);

        when(
                institutionActivityRepository
                        .findDetailActivity(
                                1L,
                                30L
                        )
        ).thenReturn(Optional.of(activity));

        when(
                careActivityRepository
                        .findByIdForUpdate(
                                30L
                        )
        ).thenReturn(Optional.of(activity));

        when(
                institutionActivityRepository
                        .findActivityApplication(
                                1L,
                                30L,
                                40L
                        )
        ).thenReturn(Optional.of(application));

        when(
                institutionActivityRepository
                        .countApplications(
                                30L,
                                ApplicationStatus.APPROVED
                        )
        ).thenReturn(1L);

        when(
                activityApplicationRepository
                        .findApprovedUserGenders(
                                30L
                        )
        ).thenReturn(
                List.of(UserGender.MALE)
        );

        assertThatThrownBy(
                () ->
                        service.approveActivityApplication(
                                10L,
                                30L,
                                40L
                        )
        ).isInstanceOf(CustomException.class);

        assertThat(application.getStatus())
                .isEqualTo(ApplicationStatus.PENDING);

        assertThat(activity.getStatus())
                .isEqualTo(ActivityStatus.RECRUITING);
    }

    @Test
    @DisplayName("같은 성별 봉사자가 있으면 마지막 승인 후 READY가 된다")
    void approvingLastVolunteerSucceedsWhenGenderConditionIsMet() {
        CareActivity activity = CareActivity.create(
                recipient,
                institution,
                manager,
                LocalDateTime.now().plusDays(1),
                2,
                GenderCondition.SAME_GENDER_ONE
        );

        ActivityApplication application =
                ActivityApplication.createDirect(
                        activity,
                        volunteer
                );

        when(recipient.getGender())
                .thenReturn(UserGender.FEMALE);

        /*
         * 이번에 승인할 봉사자는 남성이지만,
         * 기존 승인자 중 여성 봉사자가 있는 상황이다.
         */
        when(volunteer.getGender())
                .thenReturn(UserGender.MALE);

        when(
                institutionActivityRepository
                        .findDetailActivity(
                                1L,
                                30L
                        )
        ).thenReturn(Optional.of(activity));

        when(
                careActivityRepository
                        .findByIdForUpdate(
                                30L
                        )
        ).thenReturn(Optional.of(activity));

        when(
                institutionActivityRepository
                        .findActivityApplication(
                                1L,
                                30L,
                                40L
                        )
        ).thenReturn(Optional.of(application));

        when(
                institutionActivityRepository
                        .countApplications(
                                30L,
                                ApplicationStatus.APPROVED
                        )
        ).thenReturn(1L);

        when(
                activityApplicationRepository
                        .findApprovedUserGenders(
                                30L
                        )
        ).thenReturn(
                List.of(UserGender.FEMALE)
        );

        service.approveActivityApplication(
                10L,
                30L,
                40L
        );

        assertThat(application.getStatus())
                .isEqualTo(ApplicationStatus.APPROVED);

        assertThat(activity.getStatus())
                .isEqualTo(ActivityStatus.READY);
    }

    @Test
    @DisplayName("일반 상태 변경으로 진행 중 활동을 완료할 수 없다")
    void cannotCompleteActivityThroughStatusChange() {
        CareActivity activity = CareActivity.create(
                recipient,
                institution,
                manager,
                LocalDateTime.now().minusHours(1),
                2,
                GenderCondition.NONE
        );

        activity.changeStatus(
                ActivityStatus.IN_PROGRESS
        );

        when(
                institutionActivityRepository
                        .findDetailActivity(
                                1L,
                                30L
                        )
        ).thenReturn(Optional.of(activity));

        InstitutionActivityStatusRequest request =
                new InstitutionActivityStatusRequest(
                        ActivityStatus.COMPLETED
                );

        assertThatThrownBy(
                () ->
                        service.changeInstitutionActivityStatus(
                                10L,
                                30L,
                                request
                        )
        )
                .isInstanceOf(CustomException.class)
                .hasMessage("입력값이 올바르지 않습니다.");

        assertThat(activity.getStatus())
                .isEqualTo(ActivityStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("만남 기록을 승인하면 활동을 완료하고 마지막 안부 확인 시간을 갱신한다")
    void approvingMetRecordCompletesActivityAndUpdatesLastCheckedAt() {
        LocalDateTime completedAt = LocalDateTime.of(2026, 9, 15, 11, 30);
        CareActivity activity = CareActivity.create(
                recipient,
                institution,
                manager,
                LocalDateTime.now().minusHours(2),
                2,
                GenderCondition.NONE
        );
        activity.changeStatus(ActivityStatus.IN_PROGRESS);

        ActivityRecord record = org.mockito.Mockito.mock(ActivityRecord.class);
        when(record.getId()).thenReturn(50L);
        when(record.getActivity()).thenReturn(activity);
        when(record.getReviewStatus()).thenReturn(ActivityReviewStatus.SUBMITTED);
        when(record.getVisitResult()).thenReturn(VisitResult.MET);
        when(record.getCompletedAt()).thenReturn(completedAt);
        when(record.getReviewedBy()).thenReturn(manager);

        when(institutionActivityRepository.findDetailActivity(1L, 30L)).thenReturn(Optional.of(activity));
        when(institutionActivityRepository.findActivityRecord(30L)).thenReturn(Optional.of(record));
        when(checklistResponseRepository.findByActivityRecordId(50L)).thenReturn(List.of());

        service.approveInstitutionActivityRecord(10L, 30L);

        verify(record).approveReview(manager);
        verify(recipient).updateLastCheckedAt(completedAt);
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.COMPLETED);
    }
}
