package com.dagachi.backend.institution.dashboard.service;

import com.dagachi.backend.common.exception.CustomException;
import com.dagachi.backend.domain.entity.Institution;
import com.dagachi.backend.domain.entity.User;
import com.dagachi.backend.domain.repository.UserRepository;
import com.dagachi.backend.institution.dashboard.dto.DashboardPeriod;
import com.dagachi.backend.institution.dashboard.dto.InstitutionDashboardResponse;
import com.dagachi.backend.institution.dashboard.repository.InstitutionDashboardRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstitutionDashboardServiceTest {

    @Mock UserRepository userRepository;
    @Mock InstitutionDashboardRepository dashboardRepository;
    @Mock User manager;
    @Mock Institution institution;

    private InstitutionDashboardService service;

    @BeforeEach
    void setUp() {
        service = new InstitutionDashboardService(userRepository, dashboardRepository);
    }

    @Test
    @DisplayName("REQ-DASH-01, REQ-DASH-02 로그인 담당자의 기관 데이터로 대시보드를 집계한다")
    void returnsDashboardForAuthenticatedInstitution() {
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(manager.getInstitution()).thenReturn(institution);
        when(manager.getName()).thenReturn("강남기관담당자");
        when(institution.getId()).thenReturn(1L);
        when(dashboardRepository.countParticipants(1L)).thenReturn(3L);
        when(dashboardRepository.countRecipients(1L)).thenReturn(2L);
        when(dashboardRepository.countActivities(1L)).thenReturn(8L);
        when(dashboardRepository.countPendingApplications(1L)).thenReturn(4L);
        when(dashboardRepository.countActivitiesByStatus(1L, "IN_PROGRESS")).thenReturn(1L);
        when(dashboardRepository.countActivitiesByStatus(1L, "COMPLETED")).thenReturn(5L);
        when(dashboardRepository.countUnassignedReports()).thenReturn(2L);
        when(dashboardRepository.countPendingRecordReviews(1L)).thenReturn(1L);
        when(dashboardRepository.findUpcomingActivities(1L)).thenReturn(List.of());
        when(dashboardRepository.findCompletedTrend(eq(1L), eq("month"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        InstitutionDashboardResponse response = service.getDashboard(10L, DashboardPeriod.MONTHLY);

        assertThat(response.managerName()).isEqualTo("강남기관담당자");
        assertThat(response.summary().volunteerCount()).isEqualTo(3L);
        assertThat(response.summary().careRecipientCount()).isEqualTo(2L);
        assertThat(response.summary().totalActivityCount()).isEqualTo(8L);
        assertThat(response.summary().pendingApplicationCount()).isEqualTo(4L);
        assertThat(response.summary().inProgressActivityCount()).isEqualTo(1L);
        assertThat(response.summary().completedActivityCount()).isEqualTo(5L);
        assertThat(response.summary().unassignedReportCount()).isEqualTo(2L);
        assertThat(response.summary().pendingRecordReviewCount()).isEqualTo(1L);
        assertThat(response.completedTrend()).hasSize(12);
    }

    @Test
    @DisplayName("REQ-AUTH-09 기관 소속이 없는 사용자는 대시보드를 조회할 수 없다")
    void rejectsUserWithoutInstitution() {
        when(userRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(manager));
        when(manager.getInstitution()).thenReturn(null);

        assertThatThrownBy(() -> service.getDashboard(10L, DashboardPeriod.MONTHLY))
                .isInstanceOf(CustomException.class)
                .hasMessage("접근 권한이 없습니다.");
    }
}
