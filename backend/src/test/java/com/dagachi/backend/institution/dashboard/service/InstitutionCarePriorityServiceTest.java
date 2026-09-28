package com.dagachi.backend.institution.dashboard.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiCarePriorityRequest;
import com.dagachi.backend.common.ai.dto.AiCarePriorityResponse;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.dashboard.dto.CarePriorityResponse;
import com.dagachi.backend.institution.dashboard.repository.InstitutionDashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionCarePriorityServiceTest {

    @Mock UserRepository userRepository;
    @Mock InstitutionDashboardRepository dashboardRepository;
    @Mock AiServiceClient aiServiceClient;
    @Mock User manager;
    @Mock Institution institution;

    private InstitutionCarePriorityService service;

    @BeforeEach
    void setUp() {
        service = new InstitutionCarePriorityService(
                userRepository,
                dashboardRepository,
                aiServiceClient
        );
    }

    @Test
    @DisplayName("REQ-AI-09, REQ-AI-10 개인정보를 제외한 지표로 우선 확인 대상을 분석한다")
    void analyzesPseudonymousCandidateAndMapsRecipient() {
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(manager.getInstitution()).thenReturn(institution);
        when(institution.getId()).thenReturn(1L);
        when(dashboardRepository.findCarePriorityCandidates(1L)).thenReturn(List.of(
                new InstitutionDashboardRepository.CarePriorityCandidateRow(
                        22L, "김영희", 21, 0, 2, 1, 1, false
                )
        ));
        when(aiServiceClient.analyzeCarePriority(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AiCarePriorityResponse(
                        List.of(new AiCarePriorityResponse.Recommendation(
                                "recipient-22", "HIGH", 88,
                                List.of("마지막 안부 확인 후 21일 경과"),
                                "빠른 안부 확인"
                        )),
                        "test-model"
                ));

        CarePriorityResponse response = service.analyze(10L);

        ArgumentCaptor<AiCarePriorityRequest> requestCaptor =
                ArgumentCaptor.forClass(AiCarePriorityRequest.class);
        verify(aiServiceClient).analyzeCarePriority(requestCaptor.capture());

        AiCarePriorityRequest.Candidate sent = requestCaptor.getValue().candidates().getFirst();
        assertThat(sent.candidateKey()).isEqualTo("recipient-22");
        assertThat(sent.daysSinceLastCheck()).isEqualTo(21);
        assertThat(response.items().getFirst().recipientId()).isEqualTo(22L);
        assertThat(response.items().getFirst().recipientName()).isEqualTo("김영희");
        assertThat(response.items().getFirst().riskLevel()).isEqualTo("HIGH");
    }
}
