package com.dagachi.backend.institution.dashboard.service;

import com.dagachi.backend.common.ai.client.AiServiceClient;
import com.dagachi.backend.common.ai.dto.AiCarePriorityRequest;
import com.dagachi.backend.common.ai.dto.AiCarePriorityResponse;
import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.common.exception.ErrorCode;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.dashboard.dto.CarePriorityResponse;
import com.dagachi.backend.institution.dashboard.repository.InstitutionDashboardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InstitutionCarePriorityService {

    private final UserRepository userRepository;
    private final InstitutionDashboardRepository dashboardRepository;
    private final AiServiceClient aiServiceClient;

    public InstitutionCarePriorityService(
            UserRepository userRepository,
            InstitutionDashboardRepository dashboardRepository,
            AiServiceClient aiServiceClient
    ) {
        this.userRepository = userRepository;
        this.dashboardRepository = dashboardRepository;
        this.aiServiceClient = aiServiceClient;
    }

    public CarePriorityResponse analyze(Long userId) {
        User manager = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Institution institution = manager.getInstitution();
        if (institution == null) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<InstitutionDashboardRepository.CarePriorityCandidateRow> candidates =
                dashboardRepository.findCarePriorityCandidates(institution.getId());

        if (candidates.isEmpty()) {
            return new CarePriorityResponse(LocalDateTime.now(), null, List.of());
        }

        Map<String, InstitutionDashboardRepository.CarePriorityCandidateRow> candidateMap =
                candidates.stream().collect(Collectors.toMap(
                        row -> candidateKey(row.recipientId()),
                        Function.identity()
                ));

        AiCarePriorityRequest request = new AiCarePriorityRequest(
                candidates.stream()
                        .map(row -> new AiCarePriorityRequest.Candidate(
                                candidateKey(row.recipientId()),
                                row.daysSinceLastCheck(),
                                row.recentActivityCount(),
                                row.mealConcernCount(),
                                row.healthConcernCount(),
                                row.supportNeededCount(),
                                row.hasUpcomingActivity()
                        ))
                        .toList()
        );

        AiCarePriorityResponse aiResponse = aiServiceClient.analyzeCarePriority(request);
        List<CarePriorityResponse.Item> items = aiResponse.recommendations().stream()
                .filter(item -> candidateMap.containsKey(item.candidateKey()))
                .map(item -> {
                    InstitutionDashboardRepository.CarePriorityCandidateRow row =
                            candidateMap.get(item.candidateKey());
                    return new CarePriorityResponse.Item(
                            row.recipientId(),
                            row.recipientName(),
                            item.riskLevel(),
                            item.score(),
                            item.reasons(),
                            item.recommendedAction()
                    );
                })
                .sorted(Comparator.comparingInt(CarePriorityResponse.Item::score).reversed())
                .toList();

        return new CarePriorityResponse(LocalDateTime.now(), aiResponse.model(), items);
    }

    private String candidateKey(Long recipientId) {
        return "recipient-" + recipientId;
    }
}
