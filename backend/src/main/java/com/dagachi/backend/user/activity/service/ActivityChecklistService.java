package com.dagachi.backend.user.activity.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.ChecklistItem;
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.user.activity.dto.ActivityChecklistResponse;
import com.dagachi.backend.user.activity.dto.ChecklistItemResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityChecklistService {

    private final ActivityRecordRepository activityRecordRepository;
    private final ActivityApplicationRepository activityApplicationRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistResponseRepository checklistResponseRepository;

    public ActivityChecklistService(
            ActivityRecordRepository activityRecordRepository,
            ActivityApplicationRepository activityApplicationRepository,
            ChecklistItemRepository checklistItemRepository,
            ChecklistResponseRepository checklistResponseRepository
    ) {
        this.activityRecordRepository = activityRecordRepository;
        this.activityApplicationRepository = activityApplicationRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistResponseRepository = checklistResponseRepository;
    }

    // CHECK-01 활동 기록의 체크리스트 문항과 기존 응답을 조회한다.
    @Transactional(readOnly = true)
    public ActivityChecklistResponse getChecklist(
            Long recordId,
            Long userId
    ) {
        // 1. recordId에 해당하는 ActivityRecord 조회
        ActivityRecord activityRecord = activityRecordRepository.findById(recordId)
                .orElseThrow(() ->
                        new CustomException(ErrorCode.RESOURCE_NOT_FOUND)
                );

        // 2. ActivityRecord가 속한 활동 ID 조회
        Long activityId = activityRecord.getActivity().getId();

        // 3. 현재 사용자가 해당 활동의 APPROVED 참여자인지 확인
        boolean approved = activityApplicationRepository
                .existsByActivityIdAndUserIdAndStatus(
                        activityId,
                        userId,
                        ApplicationStatus.APPROVED
                );

        // 4. 승인된 참여자가 아니면 체크리스트 조회를 중단
        if (!approved) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        // 5. ActivityRecord에 저장된 체크리스트 버전의 문항 조회
        Integer checklistVersion = activityRecord.getChecklistVersion();

        List<ChecklistItem> checklistItems =
                checklistItemRepository.findByVersionOrderBySortOrderAsc(
                        checklistVersion
                );

        // 해당 버전의 체크리스트 문항이 없으면 404 처리
        if (checklistItems.isEmpty()) {
            throw new CustomException(ErrorCode.CHECKLIST_NOT_FOUND);
        }

        // 6. 현재 ActivityRecord에 이미 저장된 기존 체크리스트 응답 조회
        List<ChecklistResponse> checklistResponses =
                checklistResponseRepository.findByActivityRecordId(
                        recordId
                );

        // 7. 체크리스트 문항과 기존 응답을 합쳐 프론트 반환용 DTO 생성
        List<ChecklistItemResponse> itemResponses = new ArrayList<>();

        for (ChecklistItem item : checklistItems) {

            // 아직 응답하지 않은 문항은 null로 반환
            String selectedValue = null;
            String textValue = null;

            // 현재 문항 ID와 일치하는 기존 응답 탐색
            for (ChecklistResponse response : checklistResponses) {

                if (response.getChecklistItem().getId().equals(item.getId())) {
                    selectedValue = response.getSelectedValue();
                    textValue = response.getTextValue();
                    break;
                }
            }

            // 문항 정보와 기존 응답을 하나의 DTO로 변환
            ChecklistItemResponse itemResponse =
                    new ChecklistItemResponse(
                            item.getId(),
                            item.getCode(),
                            item.getQuestion(),
                            item.getItemType(),
                            convertOptions(item),
                            item.getRequired(),
                            item.getSortOrder(),
                            selectedValue,
                            textValue
                    );

            itemResponses.add(itemResponse);
        }

        // 체크리스트 버전과 문항 목록을 최종 응답 DTO로 반환
        return new ActivityChecklistResponse(
                checklistVersion,
                itemResponses
        );
    }

    // 체크리스트의 optionsJson 배열을 프론트에 전달할 문자열 목록으로 변환합니다.
    private List<String> convertOptions(ChecklistItem checklistItem) {
        List<String> options = new ArrayList<>();

        if (checklistItem.getOptionsJson() == null
                || !checklistItem.getOptionsJson().isArray()) {
            return options;
        }

        for (var option : checklistItem.getOptionsJson()) {
            options.add(option.asText());
        }

        return options;
    }
}