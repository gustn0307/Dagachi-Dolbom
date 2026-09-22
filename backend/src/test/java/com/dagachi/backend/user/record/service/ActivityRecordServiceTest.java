package com.dagachi.backend.user.record.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.*;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ActivityStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.CareActivityRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.user.record.dto.ActivityRecordDraftRequest;
import com.dagachi.backend.domain.enums.ChecklistItemType;
import com.dagachi.backend.domain.enums.ConsentStatus;
import com.dagachi.backend.domain.enums.GenderCondition;
import com.dagachi.backend.domain.enums.UserGender;
import com.dagachi.backend.user.record.dto.ActivityRecordDetailResponse;
import com.dagachi.backend.user.record.dto.ActivityRecordSignatureResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.dagachi.backend.domain.enums.VisitResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    private static final Long EXTRA_RECORD_ID = 1L;
    private static final Long EXTRA_ACTIVITY_ID = 100L;
    private static final Long EXTRA_USER_ID = 10L;
    private static final Integer EXTRA_CHECKLIST_VERSION = 1;

    private static final LocalDateTime EXTRA_STARTED_AT =
            LocalDateTime.of(
                    2026,
                    9,
                    8,
                    9,
                    0
            );

    private static final LocalDateTime EXTRA_COMPLETED_AT =
            LocalDateTime.of(
                    2026,
                    9,
                    8,
                    10,
                    0
            );

    /*
     * 추가 정상 흐름 테스트에서는 Mock ActivityRecord 대신
     * 실제 Entity 상태 변경까지 함께 검증합니다.
     */
    private ActivityRecord buildRealActivityRecord() {

        CareRecipient recipient =
                CareRecipient.create(
                        null,
                        "테스트 대상자",
                        UserGender.FEMALE,
                        1950,
                        "010-1111-2222",
                        "서울시 강남구 역삼동 123",
                        null,
                        null,
                        null,
                        ConsentStatus.AGREED
                );

        CareActivity activity =
                CareActivity.create(
                        recipient,
                        null,
                        null,
                        EXTRA_STARTED_AT.plusDays(1),
                        2,
                        GenderCondition.NONE
                );

        ReflectionTestUtils.setField(
                activity,
                "id",
                EXTRA_ACTIVITY_ID
        );

        ActivityRecord record =
                ActivityRecord.createDraft(
                        activity,
                        EXTRA_CHECKLIST_VERSION,
                        EXTRA_STARTED_AT
                );

        ReflectionTestUtils.setField(
                record,
                "id",
                EXTRA_RECORD_ID
        );

        return record;
    }

    private User buildRealUser() {

        User user = User.create(
                "record@test.com",
                "encoded-pw",
                "테스트 사용자",
                "테스터",
                "010-0000-0000",
                UserGender.MALE
        );

        ReflectionTestUtils.setField(
                user,
                "id",
                EXTRA_USER_ID
        );

        return user;
    }

    private ChecklistItem buildRealChecklistItem(
            Long itemId,
            String code,
            int sortOrder,
            boolean required
    ) {
        return buildRealChecklistItem(
                itemId,
                code,
                sortOrder,
                required,
                ChecklistItemType.SINGLE_CHOICE
        );
    }

    private ChecklistItem buildRealChecklistItem(
            Long itemId,
            String code,
            int sortOrder,
            boolean required,
            ChecklistItemType itemType
    ) {
        try {
            var constructor =
                    ChecklistItem.class.getDeclaredConstructor();

            constructor.setAccessible(true);

            ChecklistItem item =
                    constructor.newInstance();

            ReflectionTestUtils.setField(
                    item,
                    "id",
                    itemId
            );

            ReflectionTestUtils.setField(
                    item,
                    "version",
                    EXTRA_CHECKLIST_VERSION
            );

            ReflectionTestUtils.setField(
                    item,
                    "code",
                    code
            );

            ReflectionTestUtils.setField(
                    item,
                    "question",
                    "테스트 질문 " + itemId
            );

            ReflectionTestUtils.setField(
                    item,
                    "itemType",
                    itemType
            );

            /*
             * REQ-REC-07:
             * SINGLE_CHOICE만 optionsJson을 사용합니다.
             *
             * REQ-REC-08:
             * TEXT는 optionsJson을 사용하지 않습니다.
             */
            if (itemType == ChecklistItemType.SINGLE_CHOICE) {
                ReflectionTestUtils.setField(
                        item,
                        "optionsJson",
                        new ObjectMapper()
                                .createArrayNode()
                                .add("YES")
                                .add("NO")
                                .add("UNKNOWN")
                );
            }

            ReflectionTestUtils.setField(
                    item,
                    "required",
                    required
            );

            ReflectionTestUtils.setField(
                    item,
                    "sortOrder",
                    sortOrder
            );

            ReflectionTestUtils.setField(
                    item,
                    "active",
                    true
            );

            return item;

        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ChecklistResponse buildRealChecklistResponse(
            ActivityRecord record,
            ChecklistItem item,
            String selectedValue
    ) {
        return ChecklistResponse.create(
                record,
                item,
                selectedValue,
                null
        );
    }

    private void givenApprovedRecordForRead(
            ActivityRecord record
    ) {
        when(
                activityRecordRepository.findById(
                        EXTRA_RECORD_ID
                )
        ).thenReturn(
                Optional.of(record)
        );

        when(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                EXTRA_ACTIVITY_ID,
                                EXTRA_USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).thenReturn(
                true
        );
    }

    private void givenApprovedRecordForUpdate(
            ActivityRecord record
    ) {
        when(
                activityRecordRepository.findByIdForUpdate(
                        EXTRA_RECORD_ID
                )
        ).thenReturn(
                Optional.of(record)
        );

        when(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                EXTRA_ACTIVITY_ID,
                                EXTRA_USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).thenReturn(
                true
        );
    }

    /*
     * buildActivityRecordResponse()가 응답 List를 정렬하므로
     * Repository Mock은 호출마다 수정 가능한 새 List를 반환합니다.
     */
    private void givenChecklistResponses(
            List<ChecklistResponse> responses
    ) {
        when(
                checklistResponseRepository
                        .findByActivityRecordId(
                                EXTRA_RECORD_ID
                        )
        ).thenAnswer(
                invocation ->
                        new ArrayList<>(
                                responses
                        )
        );
    }

    /*
     * 실제 Spring Transaction 없이도
     * ActivityRecordService가 등록한 commit/rollback callback을
     * 단위 테스트에서 실행하기 위한 helper입니다.
     */
    private void completeSynchronization(
            int status
    ) {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(
                        TransactionSynchronizationManager
                                .getSynchronizations()
                );

        if (status ==
                TransactionSynchronization.STATUS_COMMITTED) {

            for (TransactionSynchronization synchronization
                    : synchronizations) {
                synchronization.afterCommit();
            }
        }

        for (TransactionSynchronization synchronization
                : synchronizations) {
            synchronization.afterCompletion(
                    status
            );
        }
    }

    @Test
    @DisplayName(
            "[REQ-REC-01][REQ-REC-02] 다른 승인 참여자가 이미 시작한 활동은 기존 공동 ActivityRecord를 반환한다"
    )
    void startActivity_기존공동기록이있으면_새로생성하지않고_반환한다() {

        Long activityId = 100L;
        Long userId = 10L;

        CareActivity activity =
                org.mockito.Mockito.mock(
                        CareActivity.class
                );

        ActivityApplication application =
                org.mockito.Mockito.mock(
                        ActivityApplication.class
                );

        ActivityRecord existingRecord =
                org.mockito.Mockito.mock(
                        ActivityRecord.class
                );

        when(
                careActivityRepository.findByIdForUpdate(
                        activityId
                )
        ).thenReturn(
                Optional.of(activity)
        );

        when(
                activityApplicationRepository
                        .findByActivity_IdAndUser_Id(
                                activityId,
                                userId
                        )
        ).thenReturn(
                Optional.of(application)
        );

        when(application.getStatus())
                .thenReturn(
                        ApplicationStatus.APPROVED
                );

        when(
                activityRecordRepository.findByActivity_Id(
                        activityId
                )
        ).thenReturn(
                Optional.of(existingRecord)
        );

        when(activity.getStatus())
                .thenReturn(
                        ActivityStatus.IN_PROGRESS
                );

        when(activity.getId())
                .thenReturn(activityId);

        when(existingRecord.getId())
                .thenReturn(1L);

        when(existingRecord.getActivity())
                .thenReturn(activity);

        when(existingRecord.getReviewStatus())
                .thenReturn(
                        ActivityReviewStatus.DRAFT
                );

        when(existingRecord.getChecklistVersion())
                .thenReturn(1);

        when(existingRecord.getStartedAt())
                .thenReturn(EXTRA_STARTED_AT);


        var result =
                activityRecordService.startActivity(
                        activityId,
                        userId
                );


        assertEquals(
                1L,
                result.activityRecordId()
        );

        assertEquals(
                activityId,
                result.activityId()
        );

        org.mockito.Mockito.verify(
                activityRecordRepository,
                org.mockito.Mockito.never()
        ).save(
                org.mockito.ArgumentMatchers.any(
                        ActivityRecord.class
                )
        );

        org.mockito.Mockito.verify(
                checklistItemRepository,
                org.mockito.Mockito.never()
        ).findCurrentActiveVersion();
    }

    @Test
    @DisplayName(
            "[REQ-REC-01][REQ-REC-05][REQ-REC-06] 최초 시작은 active 체크리스트 버전으로 공동 DRAFT를 생성하고 활동을 IN_PROGRESS로 변경한다"
    )
    void startActivity_최초시작이면_공동DRAFT를_생성한다() {

        Long activityId = 100L;
        Long userId = 10L;

        CareActivity activity =
                org.mockito.Mockito.mock(
                        CareActivity.class
                );

        ActivityApplication application =
                org.mockito.Mockito.mock(
                        ActivityApplication.class
                );

        when(
                careActivityRepository.findByIdForUpdate(
                        activityId
                )
        ).thenReturn(
                Optional.of(activity)
        );

        when(
                activityApplicationRepository
                        .findByActivity_IdAndUser_Id(
                                activityId,
                                userId
                        )
        ).thenReturn(
                Optional.of(application)
        );

        when(application.getStatus())
                .thenReturn(
                        ApplicationStatus.APPROVED
                );

        when(
                activityRecordRepository.findByActivity_Id(
                        activityId
                )
        ).thenReturn(
                Optional.empty()
        );

        when(activity.getStatus())
                .thenReturn(
                        ActivityStatus.READY
                );

        when(activity.getId())
                .thenReturn(activityId);

        when(activity.getRequiredPeople())
                .thenReturn(1);

        when(activity.getGenderCondition())
                .thenReturn(
                        GenderCondition.NONE
                );

        when(
                activityApplicationRepository
                        .countApprovedMap(
                                List.of(activityId)
                        )
        ).thenReturn(
                java.util.Map.of(
                        activityId,
                        1L
                )
        );

        when(
                checklistItemRepository
                        .findCurrentActiveVersion()
        ).thenReturn(
                Optional.of(2)
        );

        when(
                activityRecordRepository.save(
                        org.mockito.ArgumentMatchers.any(
                                ActivityRecord.class
                        )
                )
        ).thenAnswer(invocation -> {

            ActivityRecord saved =
                    invocation.getArgument(0);

            ReflectionTestUtils.setField(
                    saved,
                    "id",
                    1L
            );

            return saved;
        });


        var result =
                activityRecordService.startActivity(
                        activityId,
                        userId
                );


        assertEquals(
                1L,
                result.activityRecordId()
        );

        assertEquals(
                2,
                result.checklistVersion()
        );

        assertEquals(
                ActivityReviewStatus.DRAFT,
                result.reviewStatus()
        );

        org.mockito.Mockito.verify(
                activity
        ).changeStatus(
                ActivityStatus.IN_PROGRESS
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-08] TEXT 문항은 selectedValue 없이 textValue에 직접 확인한 사실을 저장한다"
    )
    void saveDraft_TEXT문항은_textValue를_정상저장한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        ChecklistItem textItem =
                buildRealChecklistItem(
                        4L,
                        "DIRECT_OBSERVATION",
                        4,
                        false,
                        ChecklistItemType.TEXT
                );

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository.findById(
                        4L
                )
        ).thenReturn(
                Optional.of(textItem)
        );

        givenChecklistResponses(
                List.of()
        );

        ActivityRecordDraftRequest request =
                new ActivityRecordDraftRequest(
                        VisitResult.MET,
                        EXTRA_COMPLETED_AT,
                        null,
                        List.of(
                                new ActivityRecordDraftRequest
                                        .ChecklistAnswerRequest(
                                        4L,
                                        null,
                                        "대상자가 직접 식사를 했다고 답변함"
                                )
                        )
                );


        activityRecordService.saveDraft(
                EXTRA_RECORD_ID,
                EXTRA_USER_ID,
                request
        );


        org.mockito.Mockito.verify(
                checklistResponseRepository
        ).saveAll(
                org.mockito.ArgumentMatchers.argThat(
                        responses -> {

                            ChecklistResponse saved =
                                    responses.iterator()
                                            .next();

                            return saved.getSelectedValue()
                                    == null
                                    && "대상자가 직접 식사를 했다고 답변함"
                                    .equals(
                                            saved.getTextValue()
                                    );
                        }
                )
        );
    }

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

    // ===============================================================
    // 기존 테스트에 없는 RECORD-02~05 추가 검증
    // ===============================================================

    @Test
    @DisplayName(
            "[REQ-REC-02] SUBMITTED 이후에도 승인 참여자는 공동 ActivityRecord를 조회할 수 있다"
    )
    void getActivityRecord_SUBMITTED이후에도_승인참여자는_조회할수있다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.NOT_MET,
                EXTRA_COMPLETED_AT,
                "대상자 부재"
        );

        record.submit(
                buildRealUser()
        );

        givenApprovedRecordForRead(
                record
        );

        givenChecklistResponses(
                List.of()
        );

        ActivityRecordDetailResponse response =
                activityRecordService
                        .getActivityRecord(
                                EXTRA_RECORD_ID,
                                EXTRA_USER_ID
                        );

        assertEquals(
                ActivityReviewStatus.SUBMITTED,
                response.reviewStatus()
        );

        assertEquals(
                VisitResult.NOT_MET,
                response.visitResult()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-04][REQ-REC-07] MET Draft의 허용된 객관식 응답을 정상 저장한다"
    )
    void saveDraft_MET_허용된_객관식응답을_정상저장한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        ChecklistItem item =
                buildRealChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1,
                        true
                );

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository.findById(
                        1L
                )
        ).thenReturn(
                Optional.of(item)
        );

        givenChecklistResponses(
                List.of()
        );

        ActivityRecordDraftRequest request =
                new ActivityRecordDraftRequest(
                        VisitResult.MET,
                        EXTRA_COMPLETED_AT,
                        "직접 확인",
                        List.of(
                                new ActivityRecordDraftRequest
                                        .ChecklistAnswerRequest(
                                        1L,
                                        "YES",
                                        null
                                )
                        )
                );

        ActivityRecordDetailResponse response =
                activityRecordService.saveDraft(
                        EXTRA_RECORD_ID,
                        EXTRA_USER_ID,
                        request
                );

        assertEquals(
                VisitResult.MET,
                response.visitResult()
        );

        assertEquals(
                EXTRA_COMPLETED_AT,
                response.completedAt()
        );

        assertEquals(
                "직접 확인",
                response.specialNote()
        );

        verify(
                checklistResponseRepository
        ).saveAll(
                anyList()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-07] options_json에 없는 selectedValue는 Draft에 저장할 수 없다"
    )
    void saveDraft_허용되지않은_selectedValue면_예외가발생한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        ChecklistItem item =
                buildRealChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1,
                        true
                );

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository.findById(
                        1L
                )
        ).thenReturn(
                Optional.of(item)
        );

        ActivityRecordDraftRequest request =
                new ActivityRecordDraftRequest(
                        VisitResult.MET,
                        EXTRA_COMPLETED_AT,
                        null,
                        List.of(
                                new ActivityRecordDraftRequest
                                        .ChecklistAnswerRequest(
                                        1L,
                                        "INVALID",
                                        null
                                )
                        )
                );

        CustomException exception =
                assertThrows(
                        CustomException.class,
                        () ->
                                activityRecordService.saveDraft(
                                        EXTRA_RECORD_ID,
                                        EXTRA_USER_ID,
                                        request
                                )
                );

        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-12][REQ-REC-13] MET 기록에 새 서명을 정상 업로드한다"
    )
    void uploadSignature_MET기록이면_새서명을_정상업로드한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                null
        );

        givenApprovedRecordForUpdate(
                record
        );

        MockMultipartFile signature =
                new MockMultipartFile(
                        "signature",
                        "signature.png",
                        "image/png",
                        new byte[]{1}
                );

        String newKey =
                "signatures/new.png";

        when(
                s3StorageService.upload(
                        eq(signature),
                        eq("signatures"),
                        anyString()
                )
        ).thenReturn(
                newKey
        );

        TransactionSynchronizationManager
                .initSynchronization();

        try {
            ActivityRecordSignatureResponse response =
                    activityRecordService
                            .uploadSignature(
                                    EXTRA_RECORD_ID,
                                    EXTRA_USER_ID,
                                    signature
                            );

            completeSynchronization(
                    TransactionSynchronization
                            .STATUS_COMMITTED
            );

            assertTrue(
                    response.signatureUploaded()
            );

            assertNotNull(
                    response.signedAt()
            );

            assertEquals(
                    newKey,
                    record.getSignatureS3Key()
            );

            assertNotNull(
                    record.getSignedAt()
            );

            verify(
                    s3StorageService,
                    never()
            ).delete(
                    newKey
            );

        } finally {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }

    @Test
    @DisplayName(
            "[REQ-REC-12][REQ-REC-13] DB rollback이면 새 서명을 S3에서 보상 삭제하고 기존 서명은 유지한다"
    )
    void uploadSignature_rollback이면_새서명만_보상삭제한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                null
        );

        String oldKey =
                "signatures/old.png";

        String newKey =
                "signatures/new.png";

        record.updateSignature(
                oldKey,
                EXTRA_COMPLETED_AT
        );

        givenApprovedRecordForUpdate(
                record
        );

        MockMultipartFile signature =
                new MockMultipartFile(
                        "signature",
                        "signature.png",
                        "image/png",
                        new byte[]{1}
                );

        when(
                s3StorageService.upload(
                        eq(signature),
                        eq("signatures"),
                        anyString()
                )
        ).thenReturn(
                newKey
        );

        TransactionSynchronizationManager
                .initSynchronization();

        try {
            activityRecordService
                    .uploadSignature(
                            EXTRA_RECORD_ID,
                            EXTRA_USER_ID,
                            signature
                    );

            /*
             * 실제 DB rollback 상황을 callback으로 재현합니다.
             */
            completeSynchronization(
                    TransactionSynchronization
                            .STATUS_ROLLED_BACK
            );

            verify(
                    s3StorageService
            ).delete(
                    newKey
            );

            verify(
                    s3StorageService,
                    never()
            ).delete(
                    oldKey
            );

        } finally {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }

    @Test
    @DisplayName(
            "[REQ-REC-12][REQ-REC-13] 서명 교체 commit 후 기존 S3 서명을 삭제한다"
    )
    void uploadSignature_commit이면_기존서명을_삭제한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                null
        );

        String oldKey =
                "signatures/old.png";

        String newKey =
                "signatures/new.png";

        record.updateSignature(
                oldKey,
                EXTRA_COMPLETED_AT
        );

        givenApprovedRecordForUpdate(
                record
        );

        MockMultipartFile signature =
                new MockMultipartFile(
                        "signature",
                        "signature.png",
                        "image/png",
                        new byte[]{1}
                );

        when(
                s3StorageService.upload(
                        eq(signature),
                        eq("signatures"),
                        anyString()
                )
        ).thenReturn(
                newKey
        );

        TransactionSynchronizationManager
                .initSynchronization();

        try {
            activityRecordService
                    .uploadSignature(
                            EXTRA_RECORD_ID,
                            EXTRA_USER_ID,
                            signature
                    );

            /*
             * 실제 DB commit 성공 상황을 callback으로 재현합니다.
             */
            completeSynchronization(
                    TransactionSynchronization
                            .STATUS_COMMITTED
            );

            verify(
                    s3StorageService
            ).delete(
                    oldKey
            );

            verify(
                    s3StorageService,
                    never()
            ).delete(
                    newKey
            );

        } finally {
            TransactionSynchronizationManager
                    .clearSynchronization();
        }
    }

    @Test
    @DisplayName(
            "[REQ-REC-03][REQ-REC-09][REQ-REC-12][REQ-REC-15] MET 완료조건을 모두 충족하면 SUBMITTED로 전환한다"
    )
    void submit_MET_완료조건을_충족하면_정상제출한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                "직접 확인"
        );

        record.updateSignature(
                "signatures/final.png",
                EXTRA_COMPLETED_AT
        );

        ChecklistItem item1 =
                buildRealChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1,
                        true
                );

        ChecklistItem item2 =
                buildRealChecklistItem(
                        2L,
                        "HEALTH_CONDITION",
                        2,
                        true
                );

        ChecklistItem item3 =
                buildRealChecklistItem(
                        3L,
                        "SUPPORT_NEEDED",
                        3,
                        true
                );

        ChecklistResponse response1 =
                buildRealChecklistResponse(
                        record,
                        item1,
                        "YES"
                );

        ChecklistResponse response2 =
                buildRealChecklistResponse(
                        record,
                        item2,
                        "NO"
                );

        ChecklistResponse response3 =
                buildRealChecklistResponse(
                        record,
                        item3,
                        "YES"
                );

        User submitter =
                buildRealUser();

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                EXTRA_CHECKLIST_VERSION
                        )
        ).thenReturn(
                List.of(
                        item1,
                        item2,
                        item3
                )
        );

        /*
         * Repository 순서와 상관없이 응답은 sortOrder 순으로 정렬되어야 합니다.
         */
        givenChecklistResponses(
                List.of(
                        response3,
                        response1,
                        response2
                )
        );

        when(
                userRepository.findByIdAndDeletedFalse(
                        EXTRA_USER_ID
                )
        ).thenReturn(
                Optional.of(submitter)
        );

        ActivityRecordDetailResponse result =
                activityRecordService.submit(
                        EXTRA_RECORD_ID,
                        EXTRA_USER_ID
                );

        assertEquals(
                ActivityReviewStatus.SUBMITTED,
                result.reviewStatus()
        );

        assertSame(
                submitter,
                record.getSubmittedBy()
        );

        assertTrue(
                result.signatureUploaded()
        );

        assertEquals(
                List.of(
                        1L,
                        2L,
                        3L
                ),
                result.responses()
                        .stream()
                        .map(
                                ActivityRecordDetailResponse
                                        .ChecklistAnswerResponse
                                        ::checklistItemId
                        )
                        .toList()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-09][REQ-REC-15] MET 필수 체크리스트 문항이 누락되면 제출할 수 없다"
    )
    void submit_MET_필수문항이_누락되면_예외가발생한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                null
        );

        record.updateSignature(
                "signatures/final.png",
                EXTRA_COMPLETED_AT
        );

        ChecklistItem item1 =
                buildRealChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1,
                        true
                );

        ChecklistItem item2 =
                buildRealChecklistItem(
                        2L,
                        "HEALTH_CONDITION",
                        2,
                        true
                );

        ChecklistResponse response1 =
                buildRealChecklistResponse(
                        record,
                        item1,
                        "YES"
                );

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                EXTRA_CHECKLIST_VERSION
                        )
        ).thenReturn(
                List.of(
                        item1,
                        item2
                )
        );

        givenChecklistResponses(
                List.of(
                        response1
                )
        );

        CustomException exception =
                assertThrows(
                        CustomException.class,
                        () ->
                                activityRecordService.submit(
                                        EXTRA_RECORD_ID,
                                        EXTRA_USER_ID
                                )
                );

        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-12][REQ-REC-15] MET 기록에 서명이 없으면 제출할 수 없다"
    )
    void submit_MET_서명이_없으면_예외가발생한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.MET,
                EXTRA_COMPLETED_AT,
                null
        );

        ChecklistItem item =
                buildRealChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1,
                        true
                );

        ChecklistResponse response =
                buildRealChecklistResponse(
                        record,
                        item,
                        "YES"
                );

        givenApprovedRecordForUpdate(
                record
        );

        when(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                EXTRA_CHECKLIST_VERSION
                        )
        ).thenReturn(
                List.of(item)
        );

        givenChecklistResponses(
                List.of(response)
        );

        CustomException exception =
                assertThrows(
                        CustomException.class,
                        () ->
                                activityRecordService.submit(
                                        EXTRA_RECORD_ID,
                                        EXTRA_USER_ID
                                )
                );

        assertEquals(
                ErrorCode.INVALID_INPUT_VALUE,
                exception.getErrorCode()
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-14][REQ-REC-19] NEEDS_REVISION의 NOT_MET 기록은 조건 충족 후 재제출할 수 있다"
    )
    void submit_NEEDS_REVISION_NOT_MET은_정상재제출한다() {

        ActivityRecord record =
                buildRealActivityRecord();

        record.updateDraft(
                VisitResult.NOT_MET,
                EXTRA_COMPLETED_AT,
                "대상자 부재"
        );

        ReflectionTestUtils.setField(
                record,
                "reviewStatus",
                ActivityReviewStatus.NEEDS_REVISION
        );

        User submitter =
                buildRealUser();

        givenApprovedRecordForUpdate(
                record
        );

        givenChecklistResponses(
                List.of()
        );

        when(
                userRepository.findByIdAndDeletedFalse(
                        EXTRA_USER_ID
                )
        ).thenReturn(
                Optional.of(submitter)
        );

        ActivityRecordDetailResponse result =
                activityRecordService.submit(
                        EXTRA_RECORD_ID,
                        EXTRA_USER_ID
                );

        assertEquals(
                ActivityReviewStatus.SUBMITTED,
                result.reviewStatus()
        );

        assertEquals(
                VisitResult.NOT_MET,
                result.visitResult()
        );

        assertSame(
                submitter,
                record.getSubmittedBy()
        );
    }
}