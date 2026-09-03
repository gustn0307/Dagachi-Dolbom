package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.common.storage.S3StorageService;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.ChecklistItem;
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.enums.ActivityReviewStatus;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ChecklistItemType;
import com.dagachi.backend.domain.enums.VisitResult;
import com.dagachi.backend.user.activity.dto.ActivityRecordDraftRequest;
import com.dagachi.backend.user.activity.dto.ActivityRecordResponse;
import com.dagachi.backend.user.activity.dto.ActivityRecordSignatureResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ActivityRecordService {

    // 활동기록(ActivityRecord)을 조회하고 Draft 수정/서명/제출 상태를 변경할 때 사용합니다.
    private final ActivityRecordRepository activityRecordRepository;

    // 현재 로그인한 사용자가 해당 활동의 APPROVED 참여자인지 확인할 때 사용합니다.
    private final ActivityApplicationRepository activityApplicationRepository;

    // ActivityRecord의 checklistVersion에 해당하는 체크리스트 문항을 조회하고 검증할 때 사용합니다.
    private final ChecklistItemRepository checklistItemRepository;

    // ActivityRecord에 저장된 체크리스트 응답을 조회하고 생성/수정/삭제할 때 사용합니다.
    private final ChecklistResponseRepository checklistResponseRepository;

    // 대상자 서명 이미지를 S3에 업로드하고 기존 서명 파일을 삭제할 때 사용합니다.
    private final S3StorageService s3StorageService;

    // Submit을 실제로 호출한 사용자를 조회하여 submittedBy에 저장할 때 사용합니다.
    private final UserRepository userRepository;

    public ActivityRecordService(
            ActivityRecordRepository activityRecordRepository,
            ActivityApplicationRepository activityApplicationRepository,
            ChecklistItemRepository checklistItemRepository,
            ChecklistResponseRepository checklistResponseRepository,
            S3StorageService s3StorageService,
            UserRepository userRepository
    ) {
        this.activityRecordRepository = activityRecordRepository;
        this.activityApplicationRepository = activityApplicationRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistResponseRepository = checklistResponseRepository;
        this.s3StorageService = s3StorageService;
        this.userRepository = userRepository;
    }

    // 현재 사용자가 해당 활동의 APPROVED 참여자인지 확인합니다.
    private void validateApprovedParticipant(
            ActivityRecord activityRecord,
            Long userId
    ) {
        Long activityId = activityRecord.getActivity().getId();

        boolean approved = activityApplicationRepository
                .existsByActivityIdAndUserIdAndStatus(
                        activityId,
                        userId,
                        ApplicationStatus.APPROVED
                );

        if (!approved) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    // 수정/서명/제출할 ActivityRecord를 잠금 상태로 조회합니다.
    private ActivityRecord getRecordForUpdate(Long recordId) {
        return activityRecordRepository.findByIdForUpdate(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // 특이사항의 앞뒤 공백을 제거하고, 빈 내용은 null로 정리합니다.
    private String normalizeSpecialNote(String specialNote) {

        if (specialNote == null) {
            return null;
        }

        String trimmedNote = specialNote.trim();

        if (trimmedNote.isEmpty()) {
            return null;
        }

        return trimmedNote;
    }

    // 같은 체크리스트 문항이 요청에 중복으로 들어왔는지 확인합니다.
    private void validateDuplicateItemIds(
            List<ActivityRecordDraftRequest.ChecklistAnswerRequest> responses
    ) {
        Set<Long> itemIds = new HashSet<>();

        for (ActivityRecordDraftRequest.ChecklistAnswerRequest response : responses) {

            if (!itemIds.add(response.itemId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    // 활동 완료 시각이 시작 시각보다 빠르지 않은지 확인합니다.
    private void validateCompletedAt(
            ActivityRecord activityRecord,
            LocalDateTime completedAt
    ) {
        if (completedAt == null) {
            return;
        }

        LocalDateTime startedAt = activityRecord.getStartedAt();

        if (startedAt != null && completedAt.isBefore(startedAt)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // MET 상태가 아닌데 체크리스트 응답이 포함되어 있는지 확인합니다.
    private void validateResponsesByVisitResult(
            VisitResult visitResult,
            List<ActivityRecordDraftRequest.ChecklistAnswerRequest> responses
    ) {
        if (visitResult != VisitResult.MET && !responses.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 요청한 체크리스트 문항이 존재하고, 현재 ActivityRecord의 체크리스트 버전에 속하는지 확인합니다.
    private ChecklistItem getValidChecklistItem(
            ActivityRecord activityRecord,
            Long itemId
    ) {
        ChecklistItem checklistItem = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE));

        if (!checklistItem.getVersion().equals(activityRecord.getChecklistVersion())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return checklistItem;
    }

    // 체크리스트 문항 타입에 맞는 응답값이 입력되었는지 확인합니다.
    private void validateChecklistAnswer(
            ChecklistItem checklistItem,
            ActivityRecordDraftRequest.ChecklistAnswerRequest response
    ) {
        // v1 체크리스트는 SINGLE_CHOICE 문항만 사용합니다.
        if (checklistItem.getItemType() != ChecklistItemType.SINGLE_CHOICE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 선택형 문항은 selectedValue가 반드시 있어야 합니다.
        if (response.selectedValue() == null || response.selectedValue().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // v1에서는 textValue를 사용하지 않습니다.
        if (response.textValue() != null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 선택형 문항에는 optionsJson 배열이 존재해야 합니다.
        if (checklistItem.getOptionsJson() == null
                || !checklistItem.getOptionsJson().isArray()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // selectedValue가 해당 문항의 허용된 선택지인지 확인합니다.
        boolean validOption = false;

        for (var option : checklistItem.getOptionsJson()) {
            if (response.selectedValue().equals(option.asText())) {
                validOption = true;
                break;
            }
        }

        if (!validOption) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 요청된 체크리스트 응답을 검증하고, 문항 ID별 ChecklistItem을 반환합니다.
    private Map<Long, ChecklistItem> validateChecklistResponses(
            ActivityRecord activityRecord,
            List<ActivityRecordDraftRequest.ChecklistAnswerRequest> responses
    ) {
        Map<Long, ChecklistItem> checklistItems = new HashMap<>();

        for (ActivityRecordDraftRequest.ChecklistAnswerRequest response : responses) {

            ChecklistItem checklistItem = getValidChecklistItem(
                    activityRecord,
                    response.itemId()
            );

            validateChecklistAnswer(checklistItem, response);

            checklistItems.put(response.itemId(), checklistItem);
        }

        return checklistItems;
    }

    // Draft 요청을 기준으로 기존 체크리스트 응답을 생성/수정/삭제하여 동기화합니다.
    private void syncChecklistResponses(
            ActivityRecord activityRecord,
            List<ActivityRecordDraftRequest.ChecklistAnswerRequest> responses,
            Map<Long, ChecklistItem> checklistItems
    ) {
        List<ChecklistResponse> existingResponses =
                checklistResponseRepository.findByActivityRecordId(activityRecord.getId());

        Map<Long, ChecklistResponse> existingResponseMap = new HashMap<>();

        for (ChecklistResponse existingResponse : existingResponses) {
            existingResponseMap.put(
                    existingResponse.getChecklistItem().getId(),
                    existingResponse
            );
        }

        List<ChecklistResponse> responsesToSave = new ArrayList<>();
        Set<Long> requestedItemIds = new HashSet<>();

        for (ActivityRecordDraftRequest.ChecklistAnswerRequest response : responses) {
            Long itemId = response.itemId();
            requestedItemIds.add(itemId);

            ChecklistResponse existingResponse = existingResponseMap.get(itemId);

            if (existingResponse != null) {
                existingResponse.updateAnswer(
                        response.selectedValue(),
                        response.textValue()
                );

                responsesToSave.add(existingResponse);

            } else {
                ChecklistResponse newResponse = ChecklistResponse.create(
                        activityRecord,
                        checklistItems.get(itemId),
                        response.selectedValue(),
                        response.textValue()
                );

                responsesToSave.add(newResponse);
            }
        }

        List<ChecklistResponse> responsesToDelete = new ArrayList<>();

        for (ChecklistResponse existingResponse : existingResponses) {
            Long itemId = existingResponse.getChecklistItem().getId();

            if (!requestedItemIds.contains(itemId)) {
                responsesToDelete.add(existingResponse);
            }
        }

        if (!responsesToDelete.isEmpty()) {
            checklistResponseRepository.deleteAll(responsesToDelete);
        }

        if (!responsesToSave.isEmpty()) {
            checklistResponseRepository.saveAll(responsesToSave);
        }
    }

    // Draft 저장 전에 시간, 응답 조합, 중복 문항, 체크리스트 응답값을 검증합니다.
    private Map<Long, ChecklistItem> validateDraftRequest(
            ActivityRecord activityRecord,
            ActivityRecordDraftRequest request
    ) {
        validateCompletedAt(
                activityRecord,
                request.completedAt()
        );

        validateResponsesByVisitResult(
                request.visitResult(),
                request.responses()
        );

        validateDuplicateItemIds(
                request.responses()
        );

        return validateChecklistResponses(
                activityRecord,
                request.responses()
        );
    }

    // 공동 Draft의 활동 결과와 체크리스트 응답을 저장하고 최신 상태를 반환합니다.
    @Transactional
    public ActivityRecordResponse saveDraft(
            Long recordId,
            Long userId,
            ActivityRecordDraftRequest request
    ) {
        // 수정할 ActivityRecord를 잠금 상태로 조회합니다.
        ActivityRecord activityRecord = getRecordForUpdate(recordId);

        // 해당 활동의 APPROVED 참여자인지 확인합니다.
        validateApprovedParticipant(activityRecord, userId);

        // DRAFT / NEEDS_REVISION 상태에서만 Draft를 저장할 수 있습니다.
        validateDraftEditableStatus(activityRecord);

        /*
         * TODO: 완료
         * DRAFT / NEEDS_REVISION 상태에서만 저장할 수 있도록 검사합니다.
         * 상태충돌 409 ErrorCode는 팀장 확인 후 추가합니다.
         */

        // 이미 서명이 등록된 MET 기록은 MET 이외의 상태로 변경할 수 없습니다.
        if (activityRecord.getVisitResult() == VisitResult.MET
                && request.visitResult() != VisitResult.MET
                && activityRecord.getSignatureS3Key() != null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // Draft 요청값과 체크리스트 응답을 검증합니다.
        Map<Long, ChecklistItem> checklistItems =
                validateDraftRequest(activityRecord, request);

        // 특이사항의 공백을 정리합니다.
        String normalizedSpecialNote =
                normalizeSpecialNote(request.specialNote());

        // ActivityRecord의 Draft 내용을 변경합니다.
        activityRecord.updateDraft(
                request.visitResult(),
                request.completedAt(),
                normalizedSpecialNote
        );

        // 요청 전체를 기준으로 체크리스트 응답을 생성/수정/삭제합니다.
        syncChecklistResponses(
                activityRecord,
                request.responses(),
                checklistItems
        );

        // 변경사항을 DB에 반영하여 updatedAt이 최신 시각으로 갱신되도록 합니다.
        activityRecordRepository.flush();

        return buildActivityRecordResponse(activityRecord);
    }


    // 대상자를 실제로 만난 MET 기록인지 확인합니다.
    private void validateSignatureVisitResult(ActivityRecord activityRecord) {
        if (activityRecord.getVisitResult() != VisitResult.MET) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // DB 트랜잭션이 실패하면 이번 요청에서 새로 업로드한 서명 파일을 S3에서 보상 삭제합니다.
    private void registerSignatureRollbackCleanup(String uploadedKey) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "서명 S3 보상 처리를 위한 트랜잭션이 활성화되어 있지 않습니다."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {

                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            try {
                                s3StorageService.delete(uploadedKey);
                            } catch (RuntimeException e) {
                                log.error(
                                        "서명 S3 보상 삭제에 실패했습니다. key={}",
                                        uploadedKey,
                                        e
                                );
                            }
                        }
                    }
                }
        );
    }

    // ActivityRecord별 서명 파일 저장용 S3 prefix를 생성합니다.
    private String buildSignaturePrefix(Long recordId) {
        return "signatures/activity-records/" + recordId;
    }

    // Submit할 활동기록의 시작 시각과 완료 시각이 유효한지 확인합니다.
    private void validateSubmitTimes(ActivityRecord activityRecord) {

        LocalDateTime startedAt = activityRecord.getStartedAt();
        LocalDateTime completedAt = activityRecord.getCompletedAt();

        if (startedAt == null || completedAt == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (completedAt.isBefore(startedAt)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // Submit할 활동기록에 방문 결과가 입력되어 있는지 확인합니다.
    private void validateSubmitVisitResult(ActivityRecord activityRecord) {
        if (activityRecord.getVisitResult() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // NOT_MET 제출 시 체크리스트, 서명, 특이사항 조건을 확인합니다.
    private void validateNotMetSubmit(ActivityRecord activityRecord) {

        List<ChecklistResponse> responses =
                checklistResponseRepository.findByActivityRecordId(activityRecord.getId());

        if (!responses.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (activityRecord.getSignatureS3Key() != null
                || activityRecord.getSignedAt() != null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        if (activityRecord.getSpecialNote() == null
                || activityRecord.getSpecialNote().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // Draft 저장이 가능한 상태인지 확인합니다.
    private void validateDraftEditableStatus(ActivityRecord activityRecord) {
        if (activityRecord.getReviewStatus() != ActivityReviewStatus.DRAFT
                && activityRecord.getReviewStatus() != ActivityReviewStatus.NEEDS_REVISION) {
            throw new CustomException(ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT);
        }
    }

    // 기존 서명 파일 삭제가 실패하면 최대 2회 추가 재시도하고, 최종 실패 시 로그를 남깁니다.
    private void deleteOldSignatureWithRetry(String oldSignatureKey) {

        if (oldSignatureKey == null) {
            return;
        }

        int maxAttempts = 3; // 최초 1회 + 추가 재시도 2회

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                s3StorageService.delete(oldSignatureKey);
                return;

            } catch (RuntimeException e) {

                if (attempt == maxAttempts) {
                    log.error(
                            "기존 서명 S3 삭제에 최종 실패했습니다. 수동 정리가 필요합니다. key={}, attempts={}",
                            oldSignatureKey,
                            maxAttempts,
                            e
                    );
                    return;
                }

                log.warn(
                        "기존 서명 S3 삭제에 실패하여 재시도합니다. key={}, attempt={}",
                        oldSignatureKey,
                        attempt,
                        e
                );
            }
        }
    }

    // ActivityRecord와 저장된 체크리스트 응답을 공통 응답 DTO로 변환합니다.
    private ActivityRecordResponse buildActivityRecordResponse(
            ActivityRecord activityRecord
    ) {
        List<ChecklistResponse> checklistResponses =
                checklistResponseRepository.findByActivityRecordId(activityRecord.getId());

        // 체크리스트 문항의 표시 순서에 맞게 응답을 정렬합니다.
        checklistResponses.sort(
                Comparator.comparing(
                        response -> response.getChecklistItem().getSortOrder()
                )
        );

        List<ActivityRecordResponse.ChecklistAnswerResponse> responses = new ArrayList<>();

        for (ChecklistResponse checklistResponse : checklistResponses) {
            ChecklistItem checklistItem = checklistResponse.getChecklistItem();

            responses.add(
                    new ActivityRecordResponse.ChecklistAnswerResponse(
                            checklistItem.getId(),
                            checklistItem.getQuestion(),
                            checklistResponse.getSelectedValue(),
                            checklistResponse.getTextValue()
                    )
            );
        }

        boolean signatureUploaded =
                activityRecord.getSignatureS3Key() != null
                        && activityRecord.getSignedAt() != null;

        return new ActivityRecordResponse(
                activityRecord.getId(),
                activityRecord.getActivity().getId(),
                activityRecord.getChecklistVersion(),
                activityRecord.getVisitResult(),
                activityRecord.getStartedAt(),
                activityRecord.getCompletedAt(),
                activityRecord.getSpecialNote(),
                activityRecord.getUpdatedAt(),
                responses,
                signatureUploaded,
                activityRecord.getReviewStatus(),
                activityRecord.getReviewNote()
        );
    }

    // APPROVED 참여자가 활동기록의 현재 저장 상태를 조회합니다.
    @Transactional(readOnly = true)
    public ActivityRecordResponse getActivityRecord(
            Long recordId,
            Long userId
    ) {
        ActivityRecord activityRecord = activityRecordRepository.findById(recordId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        // 해당 활동의 APPROVED 참여자인지 확인합니다.
        validateApprovedParticipant(activityRecord, userId);

        return buildActivityRecordResponse(activityRecord);
    }

    // DB 트랜잭션이 성공한 뒤 기존 서명 파일을 S3에서 삭제합니다.
    private void registerOldSignatureCleanupAfterCommit(String oldSignatureKey) {

        if (oldSignatureKey == null) {
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "기존 서명 S3 정리를 위한 트랜잭션이 활성화되어 있지 않습니다."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deleteOldSignatureWithRetry(oldSignatureKey);
                    }
                }
        );
    }

    // APPROVED 참여자가 MET 활동기록에 대상자 서명을 업로드하거나 교체합니다.
    @Transactional
    public ActivityRecordSignatureResponse uploadSignature(
            Long recordId,
            Long userId,
            MultipartFile signature
    ) {
        // 수정할 ActivityRecord를 잠금 상태로 조회합니다.
        ActivityRecord activityRecord = getRecordForUpdate(recordId);

        // 해당 활동의 APPROVED 참여자인지 확인합니다.
        validateApprovedParticipant(activityRecord, userId);

        // DRAFT / NEEDS_REVISION 상태에서만 서명을 변경할 수 있습니다.
        validateDraftEditableStatus(activityRecord);

        // 대상자를 실제로 만난 MET 기록에서만 서명을 등록할 수 있습니다.
        validateSignatureVisitResult(activityRecord);

        // 교체 전 기존 서명 S3 Key를 보관합니다.
        String oldSignatureKey = activityRecord.getSignatureS3Key();

        // 새 서명 이미지를 S3에 먼저 업로드합니다.
        String newSignatureKey = s3StorageService.upload(
                signature,
                buildSignaturePrefix(recordId)
        );

        // 이후 DB 트랜잭션이 실패하면 새로 올린 서명을 S3에서 삭제합니다.
        registerSignatureRollbackCleanup(newSignatureKey);

        // 서버 기준으로 현재 서명 등록 시각을 기록합니다.
        LocalDateTime signedAt = LocalDateTime.now();

        // ActivityRecord가 새 서명 파일과 서명 시각을 가리키도록 변경합니다.
        activityRecord.updateSignature(
                newSignatureKey,
                signedAt
        );

        // DB commit 성공 후 기존 서명 파일을 삭제합니다.
        registerOldSignatureCleanupAfterCommit(oldSignatureKey);

        // 사용자에게는 서명 등록 여부와 등록 시각만 반환합니다.
        return new ActivityRecordSignatureResponse(
                true,
                signedAt
        );
    }

    // Submit할 때 DB에 저장된 체크리스트 응답이 현재 버전과 문항 규칙에 맞는지 다시 확인합니다.
    private void validateStoredChecklistResponse(
            ActivityRecord activityRecord,
            ChecklistResponse response
    ) {
        ChecklistItem checklistItem = response.getChecklistItem();

        // 현재 ActivityRecord가 사용한 체크리스트 버전의 문항인지 확인합니다.
        if (!checklistItem.getVersion().equals(activityRecord.getChecklistVersion())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // v1에서는 SINGLE_CHOICE 문항만 사용합니다.
        if (checklistItem.getItemType() != ChecklistItemType.SINGLE_CHOICE) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 선택형 문항은 selectedValue가 반드시 있어야 합니다.
        if (response.getSelectedValue() == null
                || response.getSelectedValue().isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // v1에서는 textValue를 사용하지 않습니다.
        if (response.getTextValue() != null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 선택 가능한 optionsJson 배열이 정상적으로 존재해야 합니다.
        if (checklistItem.getOptionsJson() == null
                || !checklistItem.getOptionsJson().isArray()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        boolean validOption = false;

        for (var option : checklistItem.getOptionsJson()) {
            if (response.getSelectedValue().equals(option.asText())) {
                validOption = true;
                break;
            }
        }

        // 저장된 값이 해당 문항의 허용된 선택지가 아니면 제출할 수 없습니다.
        if (!validOption) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // MET 제출 시 필수 체크리스트 응답과 서명 등록 여부를 확인합니다.
    private void validateMetSubmit(ActivityRecord activityRecord) {

        List<ChecklistItem> checklistItems =
                checklistItemRepository.findByVersionOrderBySortOrderAsc(
                        activityRecord.getChecklistVersion()
                );

        // MET인데 해당 버전의 체크리스트 자체가 없으면 제출할 수 없습니다.
        if (checklistItems.isEmpty()) {
            throw new CustomException(ErrorCode.CHECKLIST_NOT_FOUND);
        }

        List<ChecklistResponse> responses =
                checklistResponseRepository.findByActivityRecordId(activityRecord.getId());

        // DB에 저장된 응답값을 Submit 시 다시 검증합니다.
        for (ChecklistResponse response : responses) {
            validateStoredChecklistResponse(activityRecord, response);
        }

        Set<Long> answeredItemIds = new HashSet<>();

        for (ChecklistResponse response : responses) {
            answeredItemIds.add(response.getChecklistItem().getId());
        }

        // 현재 버전의 필수 문항이 모두 응답되어 있는지 확인합니다.
        for (ChecklistItem checklistItem : checklistItems) {
            if (checklistItem.getRequired()
                    && !answeredItemIds.contains(checklistItem.getId())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // MET 제출에는 서명 파일과 서명 시각이 모두 필요합니다.
        if (activityRecord.getSignatureS3Key() == null
                || activityRecord.getSignedAt() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // Submit 전에 방문 결과, 시간, MET/NOT_MET별 완료 조건을 최종 검증합니다.
    private void validateSubmitRequirements(ActivityRecord activityRecord) {

        // 방문 결과가 입력되어 있는지 확인합니다.
        validateSubmitVisitResult(activityRecord);

        // 시작 시각과 완료 시각이 유효한지 확인합니다.
        validateSubmitTimes(activityRecord);

        // 방문 결과에 따라 최종 제출 조건을 검증합니다.
        if (activityRecord.getVisitResult() == VisitResult.MET) {
            validateMetSubmit(activityRecord);
            return;
        }

        validateNotMetSubmit(activityRecord);
    }

    // Submit을 실제로 호출한 사용자를 조회합니다.
    private User getSubmitter(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    // Submit이 가능한 상태인지 확인합니다.
    private void validateSubmitStatus(ActivityRecord activityRecord) {
        if (activityRecord.getReviewStatus() != ActivityReviewStatus.DRAFT
                && activityRecord.getReviewStatus() != ActivityReviewStatus.NEEDS_REVISION) {
            throw new CustomException(ErrorCode.ACTIVITY_RECORD_STATE_CONFLICT);
        }
    }

    // APPROVED 참여자가 활동기록의 최종 제출 조건을 검증하고 SUBMITTED 상태로 변경합니다.
    @Transactional
    public ActivityRecordResponse submit(
            Long recordId,
            Long userId
    ) {
        // 제출할 ActivityRecord를 잠금 상태로 조회합니다.
        ActivityRecord activityRecord = getRecordForUpdate(recordId);

        // 해당 활동의 APPROVED 참여자인지 먼저 확인합니다.
        validateApprovedParticipant(activityRecord, userId);

        // DRAFT / NEEDS_REVISION 상태에서만 제출할 수 있습니다.
        validateSubmitStatus(activityRecord);

        // 방문 결과, 시간, 체크리스트, 서명 등 최종 제출 조건을 검증합니다.
        validateSubmitRequirements(activityRecord);

        // 실제 Submit API를 호출한 사용자를 조회합니다.
        User submitter = getSubmitter(userId);

        // submittedBy를 제출자로 갱신하고 reviewStatus를 SUBMITTED로 변경합니다.
        activityRecord.submit(submitter);

        // 변경사항을 DB에 반영하여 updatedAt이 최신 시각으로 갱신되도록 합니다.
        activityRecordRepository.flush();

        // 제출 완료된 최신 활동기록 상태를 반환합니다.
        return buildActivityRecordResponse(activityRecord);
    }
}