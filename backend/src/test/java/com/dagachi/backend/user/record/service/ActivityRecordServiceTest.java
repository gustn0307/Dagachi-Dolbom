package com.dagachi.backend.user.record.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.record.dto.ActivityRecordDraftRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.dagachi.backend.domain.enums.VisitResult;
import org.springframework.web.multipart.MultipartFile;
import com.dagachi.backend.domain.entity.ChecklistItem;
import com.dagachi.backend.domain.entity.ChecklistResponse;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityRecordServiceTest {

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

    @Test
    void saveDraft_제출된기록이면_상태충돌예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));

        when(activityRecord.getActivity())
                .thenReturn(careActivity);

        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.SUBMITTED);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                )
        );

        // then
        assertEquals(
                ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_승인된참여자가아니면_접근거부예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));

        when(activityRecord.getActivity())
                .thenReturn(careActivity);

        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(false);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                )
        );

        // then
        assertEquals(
                ErrorCode.FORBIDDEN,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_완료시각이시작시각보다이르면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 9, 8, 10, 0);

        LocalDateTime completedAt =
                LocalDateTime.of(2026, 9, 8, 9, 0);

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));

        when(activityRecord.getActivity())
                .thenReturn(careActivity);

        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(activityRecord.getStartedAt())
                .thenReturn(startedAt);

        when(request.completedAt())
                .thenReturn(completedAt);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                )
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_NOT_MET인데_체크리스트응답이있으면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);
        ActivityRecordDraftRequest.ChecklistAnswerRequest response =
                mock(ActivityRecordDraftRequest.ChecklistAnswerRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(request.visitResult())
                .thenReturn(VisitResult.NOT_MET);
        when(request.responses())
                .thenReturn(List.of(response));

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(recordId, userId, request)
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_같은체크리스트문항이중복되면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        ActivityRecordDraftRequest.ChecklistAnswerRequest response1 =
                mock(ActivityRecordDraftRequest.ChecklistAnswerRequest.class);
        ActivityRecordDraftRequest.ChecklistAnswerRequest response2 =
                mock(ActivityRecordDraftRequest.ChecklistAnswerRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(request.visitResult())
                .thenReturn(VisitResult.MET);
        when(request.responses())
                .thenReturn(List.of(response1, response2));

        when(response1.itemId())
                .thenReturn(1L);
        when(response2.itemId())
                .thenReturn(1L);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(recordId, userId, request)
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_서명된MET기록을_NOT_MET으로변경하면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(activityRecord.getVisitResult())
                .thenReturn(VisitResult.MET);
        when(activityRecord.getSignatureS3Key())
                .thenReturn("signatures/test.png");

        when(request.visitResult())
                .thenReturn(VisitResult.NOT_MET);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.saveDraft(recordId, userId, request)
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void uploadSignature_NOT_MET기록이면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        MultipartFile signature = mock(MultipartFile.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(activityRecord.getVisitResult())
                .thenReturn(VisitResult.NOT_MET);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.uploadSignature(
                        recordId,
                        userId,
                        signature
                )
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void submit_이미제출된기록이면_상태충돌예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.SUBMITTED);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.submit(recordId, userId)
        );

        // then
        assertEquals(
                ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT,
                exception.getErrorCode()
        );
    }

    @Test
    void submit_NOT_MET인데_특이사항이없으면_잘못된입력예외가발생한다() {

        // given
        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 9, 8, 9, 0);
        LocalDateTime completedAt =
                LocalDateTime.of(2026, 9, 8, 10, 0);

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId,
                userId,
                ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(activityRecord.getVisitResult())
                .thenReturn(VisitResult.NOT_MET);

        when(activityRecord.getStartedAt())
                .thenReturn(startedAt);
        when(activityRecord.getCompletedAt())
                .thenReturn(completedAt);

        when(activityRecord.getId())
                .thenReturn(recordId);

        when(checklistResponseRepository.findByActivityRecordId(recordId))
                .thenReturn(List.of());

        when(activityRecord.getSpecialNote())
                .thenReturn(null);

        // when
        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.submit(recordId, userId)
        );

        // then
        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void submit_방문결과가없으면_잘못된입력예외가발생한다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);

        when(activityRecord.getVisitResult())
                .thenReturn(null);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.submit(recordId, userId)
        );

        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void submit_MET인데_체크리스트가없으면_체크리스트없음예외가발생한다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 9, 8, 9, 0);
        LocalDateTime completedAt =
                LocalDateTime.of(2026, 9, 8, 10, 0);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);
        when(activityRecord.getVisitResult())
                .thenReturn(VisitResult.MET);
        when(activityRecord.getStartedAt())
                .thenReturn(startedAt);
        when(activityRecord.getCompletedAt())
                .thenReturn(completedAt);
        when(activityRecord.getChecklistVersion())
                .thenReturn(1);

        when(checklistItemRepository.findByVersionOrderBySortOrderAsc(1))
                .thenReturn(List.of());

        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.submit(recordId, userId)
        );

        assertEquals(
                ErrorCode.CHECKLIST_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void submit_NOT_MET인데_체크리스트응답이남아있으면_잘못된입력예외가발생한다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ChecklistResponse response = mock(ChecklistResponse.class);

        LocalDateTime startedAt =
                LocalDateTime.of(2026, 9, 8, 9, 0);
        LocalDateTime completedAt =
                LocalDateTime.of(2026, 9, 8, 10, 0);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.DRAFT);
        when(activityRecord.getVisitResult())
                .thenReturn(VisitResult.NOT_MET);
        when(activityRecord.getStartedAt())
                .thenReturn(startedAt);
        when(activityRecord.getCompletedAt())
                .thenReturn(completedAt);
        when(activityRecord.getId())
                .thenReturn(recordId);

        when(checklistResponseRepository.findByActivityRecordId(recordId))
                .thenReturn(List.of(response));

        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.submit(recordId, userId)
        );

        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    void uploadSignature_제출된기록이면_상태충돌예외가발생한다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        MultipartFile signature = mock(MultipartFile.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.SUBMITTED);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.uploadSignature(
                        recordId,
                        userId,
                        signature
                )
        );

        assertEquals(
                ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT,
                exception.getErrorCode()
        );
    }

    @Test
    void getActivityRecord_승인된참여자가아니면_접근거부예외가발생한다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);

        when(activityRecordRepository.findById(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(false);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> activityRecordService.getActivityRecord(
                        recordId,
                        userId
                )
        );

        assertEquals(
                ErrorCode.FORBIDDEN,
                exception.getErrorCode()
        );
    }

    @Test
    void saveDraft_NEEDS_REVISION상태이면_저장할수있다() {

        Long recordId = 1L;
        Long userId = 10L;
        Long activityId = 100L;

        ActivityRecord activityRecord = mock(ActivityRecord.class);
        CareActivity careActivity = mock(CareActivity.class);
        ActivityRecordDraftRequest request = mock(ActivityRecordDraftRequest.class);

        when(activityRecordRepository.findByIdForUpdate(recordId))
                .thenReturn(Optional.of(activityRecord));
        when(activityRecord.getActivity())
                .thenReturn(careActivity);
        when(careActivity.getId())
                .thenReturn(activityId);
        when(activityRecord.getId())
                .thenReturn(recordId);

        when(activityApplicationRepository.existsByActivityIdAndUserIdAndStatus(
                activityId, userId, ApplicationStatus.APPROVED
        )).thenReturn(true);

        when(activityRecord.getReviewStatus())
                .thenReturn(ActivityReviewStatus.NEEDS_REVISION);

        when(request.responses())
                .thenReturn(List.of());

        when(checklistResponseRepository.findByActivityRecordId(recordId))
                .thenReturn(new java.util.ArrayList<>());

        assertDoesNotThrow(
                () -> activityRecordService.saveDraft(
                        recordId,
                        userId,
                        request
                )
        );
    }
}