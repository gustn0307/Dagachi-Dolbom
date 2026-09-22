package com.dagachi.backend.user.record.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.ActivityRecord;
import com.dagachi.backend.domain.entity.CareActivity;
import com.dagachi.backend.domain.entity.ChecklistItem;
import com.dagachi.backend.domain.entity.ChecklistResponse;
import com.dagachi.backend.domain.enums.ApplicationStatus;
import com.dagachi.backend.domain.enums.ChecklistItemType;
import com.dagachi.backend.domain.repository.ActivityApplicationRepository;
import com.dagachi.backend.domain.repository.ActivityRecordRepository;
import com.dagachi.backend.domain.repository.ChecklistItemRepository;
import com.dagachi.backend.domain.repository.ChecklistResponseRepository;
import com.dagachi.backend.user.record.dto.ActivityChecklistResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActivityChecklistServiceTest {

    @Mock
    private ActivityRecordRepository activityRecordRepository;

    @Mock
    private ActivityApplicationRepository activityApplicationRepository;

    @Mock
    private ChecklistItemRepository checklistItemRepository;

    @Mock
    private ChecklistResponseRepository checklistResponseRepository;

    @InjectMocks
    private ActivityChecklistService service;

    private static final Long RECORD_ID = 10L;
    private static final Long ACTIVITY_ID = 20L;
    private static final Long USER_ID = 100L;
    private static final Integer CHECKLIST_VERSION = 1;

    private ActivityRecord buildRecord() {
        ActivityRecord record =
                mock(ActivityRecord.class);

        CareActivity activity =
                mock(CareActivity.class);

        given(record.getActivity())
                .willReturn(activity);

        given(activity.getId())
                .willReturn(ACTIVITY_ID);

        return record;
    }

    private ChecklistItem buildChecklistItem(
            Long itemId,
            String code,
            int sortOrder
    ) {
        ChecklistItem item =
                mock(ChecklistItem.class);

        given(item.getId())
                .willReturn(itemId);

        given(item.getCode())
                .willReturn(code);

        given(item.getQuestion())
                .willReturn("테스트 질문 " + itemId);

        given(item.getItemType())
                .willReturn(
                        ChecklistItemType.SINGLE_CHOICE
                );

        given(item.getOptionsJson())
                .willReturn(
                        new ObjectMapper()
                                .createArrayNode()
                                .add("YES")
                                .add("NO")
                                .add("UNKNOWN")
                );

        given(item.getRequired())
                .willReturn(true);

        given(item.getSortOrder())
                .willReturn(sortOrder);

        return item;
    }

    // ---------------------------------------------------------------
    // CHECK-01
    // ---------------------------------------------------------------

    @Test
    @DisplayName(
            "[CHECK-01] 활동 기록이 없으면 RESOURCE_NOT_FOUND"
    )
    void getChecklist_기록이_없으면_RESOURCE_NOT_FOUND() {

        given(
                activityRecordRepository.findById(
                        RECORD_ID
                )
        ).willReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        service.getChecklist(
                                RECORD_ID,
                                USER_ID
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.RESOURCE_NOT_FOUND
                );

        verifyNoInteractions(
                checklistItemRepository,
                checklistResponseRepository
        );
    }

    @Test
    @DisplayName(
            "[CHECK-01] APPROVED 참여자가 아니면 FORBIDDEN"
    )
    void getChecklist_승인참여자가_아니면_FORBIDDEN() {

        ActivityRecord record =
                buildRecord();

        given(
                activityRecordRepository.findById(
                        RECORD_ID
                )
        ).willReturn(
                Optional.of(record)
        );

        given(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                ACTIVITY_ID,
                                USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).willReturn(
                false
        );

        assertThatThrownBy(
                () ->
                        service.getChecklist(
                                RECORD_ID,
                                USER_ID
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.FORBIDDEN
                );

        verifyNoInteractions(
                checklistItemRepository,
                checklistResponseRepository
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-06] ActivityRecord에 고정된 checklistVersion의 문항이 없으면 CHECKLIST_NOT_FOUND"
    )
    void getChecklist_저장버전의_문항이_없으면_CHECKLIST_NOT_FOUND() {

        ActivityRecord record =
                buildRecord();

        given(
                activityRecordRepository.findById(
                        RECORD_ID
                )
        ).willReturn(
                Optional.of(record)
        );

        given(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                ACTIVITY_ID,
                                USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).willReturn(
                true
        );

        given(
                record.getChecklistVersion()
        ).willReturn(
                CHECKLIST_VERSION
        );

        given(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                CHECKLIST_VERSION
                        )
        ).willReturn(
                List.of()
        );

        assertThatThrownBy(
                () ->
                        service.getChecklist(
                                RECORD_ID,
                                USER_ID
                        )
        )
                .isInstanceOf(
                        CustomException.class
                )
                .extracting(
                        "errorCode"
                )
                .isEqualTo(
                        ErrorCode.CHECKLIST_NOT_FOUND
                );

        verify(
                checklistItemRepository
        ).findByVersionOrderBySortOrderAsc(
                CHECKLIST_VERSION
        );

        verifyNoInteractions(
                checklistResponseRepository
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-06] ActivityRecord에 고정된 checklistVersion 기준으로 문항과 options를 sortOrder 순으로 반환한다"
    )
    void getChecklist_저장버전_문항을_정상반환한다() {

        ActivityRecord record =
                buildRecord();

        ChecklistItem item1 =
                buildChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1
                );

        ChecklistItem item2 =
                buildChecklistItem(
                        2L,
                        "HEALTH_CONDITION",
                        2
                );

        given(
                activityRecordRepository.findById(
                        RECORD_ID
                )
        ).willReturn(
                Optional.of(record)
        );

        given(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                ACTIVITY_ID,
                                USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).willReturn(
                true
        );

        given(
                record.getChecklistVersion()
        ).willReturn(
                CHECKLIST_VERSION
        );

        given(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                CHECKLIST_VERSION
                        )
        ).willReturn(
                List.of(
                        item1,
                        item2
                )
        );

        given(
                checklistResponseRepository
                        .findByActivityRecordId(
                                RECORD_ID
                        )
        ).willReturn(
                List.of()
        );

        ActivityChecklistResponse response =
                service.getChecklist(
                        RECORD_ID,
                        USER_ID
                );

        assertThat(
                response.checklistVersion()
        ).isEqualTo(
                CHECKLIST_VERSION
        );

        assertThat(
                response.items()
        ).hasSize(2);

        assertThat(
                response.items()
                        .get(0)
                        .code()
        ).isEqualTo(
                "MEAL_STATUS"
        );

        assertThat(
                response.items()
                        .get(0)
                        .options()
        ).containsExactly(
                "YES",
                "NO",
                "UNKNOWN"
        );

        assertThat(
                response.items()
                        .get(0)
                        .selectedValue()
        ).isNull();

        assertThat(
                response.items()
                        .get(0)
                        .textValue()
        ).isNull();

        assertThat(
                response.items()
                        .get(1)
                        .sortOrder()
        ).isEqualTo(
                2
        );
    }

    @Test
    @DisplayName(
            "[REQ-REC-07][REQ-REC-08] 기존 체크리스트 응답의 selectedValue와 textValue를 각 문항에 매핑한다"
    )
    void getChecklist_기존응답을_문항에_매핑한다() {

        ActivityRecord record =
                buildRecord();

        ChecklistItem item =
                buildChecklistItem(
                        1L,
                        "MEAL_STATUS",
                        1
                );

        ChecklistResponse savedResponse =
                mock(ChecklistResponse.class);

        given(
                savedResponse.getChecklistItem()
        ).willReturn(
                item
        );

        given(
                savedResponse.getSelectedValue()
        ).willReturn(
                "YES"
        );

        given(
                savedResponse.getTextValue()
        ).willReturn(
                null
        );

        given(
                activityRecordRepository.findById(
                        RECORD_ID
                )
        ).willReturn(
                Optional.of(record)
        );

        given(
                activityApplicationRepository
                        .existsByActivityIdAndUserIdAndStatus(
                                ACTIVITY_ID,
                                USER_ID,
                                ApplicationStatus.APPROVED
                        )
        ).willReturn(
                true
        );

        given(
                record.getChecklistVersion()
        ).willReturn(
                CHECKLIST_VERSION
        );

        given(
                checklistItemRepository
                        .findByVersionOrderBySortOrderAsc(
                                CHECKLIST_VERSION
                        )
        ).willReturn(
                List.of(item)
        );

        given(
                checklistResponseRepository
                        .findByActivityRecordId(
                                RECORD_ID
                        )
        ).willReturn(
                List.of(savedResponse)
        );

        ActivityChecklistResponse response =
                service.getChecklist(
                        RECORD_ID,
                        USER_ID
                );

        assertThat(
                response.items()
                        .get(0)
                        .selectedValue()
        ).isEqualTo(
                "YES"
        );

        assertThat(
                response.items()
                        .get(0)
                        .textValue()
        ).isNull();
    }
}