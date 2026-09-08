import api, { unwrapData } from "./api";

/* =====================================================
 * 돌봄 대상자 API
 * ===================================================== */

// CARE-01 돌봄 대상자 목록 조회
export const getCareRecipients = (params = {}) =>
  unwrapData(
    api.get("/api/institution/care-recipients", {
      params,
    }),
  );

// CARE-02 돌봄 대상자 상세 조회
export const getCareRecipient = (recipientId) =>
  unwrapData(
    api.get(
      `/api/institution/care-recipients/${recipientId}`,
    ),
  );

// CARE-03 돌봄 대상자 등록
export const createCareRecipient = (request) =>
  unwrapData(
    api.post(
      "/api/institution/care-recipients",
      request,
    ),
  );

// CARE-04 돌봄 대상자 기본정보 수정
export const updateCareRecipient = (
  recipientId,
  request,
) =>
  unwrapData(
    api.patch(
      `/api/institution/care-recipients/${recipientId}`,
      request,
    ),
  );

// CARE-05 돌봄 대상자 동의 상태 변경
export const updateCareRecipientConsent = (
  recipientId,
  consentStatus,
) =>
  unwrapData(
    api.patch(
      `/api/institution/care-recipients/${recipientId}/consent`,
      {
        consentStatus,
      },
    ),
  );

// CARE-06 돌봄 대상자 관리 종료
export const closeCareRecipient = (recipientId) =>
  unwrapData(
    api.post(
      `/api/institution/care-recipients/${recipientId}/close`,
    ),
  );

// CARE-07 돌봄 대상자 관리 재개
export const reopenCareRecipient = (recipientId) =>
  unwrapData(
    api.post(
      `/api/institution/care-recipients/${recipientId}/reopen`,
    ),
  );

// 기존 화면에서 사용하는 돌봄 대상자 목록 함수
export const getCareTargets = (params = {}) =>
  getCareRecipients(params);

/* =====================================================
 * 기관 대시보드 API
 * ===================================================== */

export const getDashboard = () =>
  unwrapData(
    api.get("/api/institution/dashboard"),
  );

/* =====================================================
 * 기관 제보 API
 * ===================================================== */

// REPORT-01 미배정 제보 목록 조회
export const getUnassignedReports = (
  params = {},
) =>
  unwrapData(
    api.get(
      "/api/institution/reports/unassigned",
      {
        params,
      },
    ),
  );

// REPORT-02 미배정 제보 관할 지정
export const assignReport = (reportId) =>
  unwrapData(
    api.patch(
      `/api/institution/reports/${reportId}/assignment`,
    ),
  );

// REPORT-03 내 기관 제보 목록 조회
export const getReports = (params = {}) =>
  unwrapData(
    api.get("/api/institution/reports", {
      params,
    }),
  );

// REPORT-04 내 기관 제보 상세 조회
export const getReport = (reportId) =>
  unwrapData(
    api.get(
      `/api/institution/reports/${reportId}`,
    ),
  );

// REPORT-05 제보 상태 변경
export const updateReportStatus = (
  reportId,
  status,
) =>
  unwrapData(
    api.patch(
      `/api/institution/reports/${reportId}/status`,
      {
        status,
      },
    ),
  );

// REPORT-06 기존 돌봄 대상자 연결
export const linkReportCareRecipient = (
  reportId,
  careRecipientId,
) =>
  unwrapData(
    api.put(
      `/api/institution/reports/${reportId}/care-recipient`,
      {
        careRecipientId,
      },
    ),
  );

// REPORT-07 신규 돌봄 대상자 등록 및 제보 연결
export const createAndLinkReportCareRecipient = (
  reportId,
  request,
) =>
  unwrapData(
    api.post(
      `/api/institution/reports/${reportId}/care-recipient`,
      request,
    ),
  );

// 제보 AI 요약 생성
export const createReportAiSummary = (
  reportId,
) =>
  unwrapData(
    api.post(
      `/api/institution/reports/${reportId}/ai-analyses`,
    ),
  );

// 제보 최신 AI 요약 조회
export const getLatestReportAiSummary = (
  reportId,
) =>
  unwrapData(
    api.get(
      `/api/institution/reports/${reportId}/ai-analyses`,
    ),
  );

/* =====================================================
 * 기관 봉사자 API
 * ===================================================== */

// VOL-01~03 봉사자 목록·검색·정렬
export const getVolunteers = (params = {}) =>
  unwrapData(
    api.get("/api/institution/volunteers", {
      params,
    }),
  );

// VOL-05 기관 봉사자 현황 요약
export const getVolunteerOverview = () =>
  unwrapData(
    api.get(
      "/api/institution/volunteers/summary",
    ),
  );

// VOL-06 기관 봉사자 기본 상세 조회
export const getVolunteer = (volunteerId) =>
  unwrapData(
    api.get(
      `/api/institution/volunteers/${volunteerId}`,
    ),
  );

// VOL-07 기관별 봉사자 활동 이력 조회
export const getVolunteerActivities = (
  volunteerId,
  params = {},
) =>
  unwrapData(
    api.get(
      `/api/institution/volunteers/${volunteerId}/activities`,
      {
        params,
      },
    ),
  );

/* =====================================================
 * 기관 활동 API
 * ===================================================== */

// 기관 활동 목록 조회
export const getActivities = (params = {}) =>
  unwrapData(
    api.get("/api/institution/activities", {
      params,
    }),
  );

  // 승인 대기 봉사 신청 현황 조회
export const getPendingApplicationSummary = () =>
  unwrapData(
    api.get(
      "/api/institution/activities/pending-applications/summary",
    ),
  );

// 기관 활동 상세 조회
export const getActivity = (activityId) =>
  unwrapData(
    api.get(
      `/api/institution/activities/${activityId}`,
    ),
  );

// 기관 활동 등록
export const createActivity = (request) =>
  unwrapData(
    api.post(
      "/api/institution/activities",
      request,
    ),
  );

// 기관 활동 정보 수정
export const updateActivity = (
  activityId,
  request,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}`,
      request,
    ),
  );

// 기관 활동 상태 변경
export const updateActivityStatus = (
  activityId,
  status,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}/status`,
      {
        status,
      },
    ),
  );

// 기관 활동 신청자 목록 조회
export const getActivityApplications = (
  activityId,
  params = {},
) =>
  unwrapData(
    api.get(
      `/api/institution/activities/${activityId}/applications`,
      {
        params,
      },
    ),
  );

// 기관 담당자의 봉사 신청 승인
export const approveActivityApplication = (
  activityId,
  applicationId,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}/applications/${applicationId}/approve`,
    ),
  );

// 기관 담당자의 봉사 신청 반려
export const rejectActivityApplication = (
  activityId,
  applicationId,
  reason,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}/applications/${applicationId}/reject`,
      {
        reason,
      },
    ),
  );

// 기관 담당자의 활동기록 상세 조회
export const getInstitutionActivityRecord = (
  activityId,
) =>
  unwrapData(
    api.get(
      `/api/institution/activities/${activityId}/record`,
    ),
  );

// 기관 담당자의 활동기록 승인
export const approveInstitutionActivityRecord = (
  activityId,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}/record/approve`,
    ),
  );

// 기관 담당자의 활동기록 보완 요청
export const requestInstitutionActivityRecordRevision = (
  activityId,
  reviewNote,
) =>
  unwrapData(
    api.patch(
      `/api/institution/activities/${activityId}/record/revision`,
      {
        reviewNote,
      },
    ),
  );

/* =====================================================
 * 기관 통계 API
 * ===================================================== */

export const getStatistics = (
  period = "6months",
) =>
  unwrapData(
    api.get("/api/institution/statistics", {
      params: {
        period,
      },
    }),
  );

/* =====================================================
 * 기관 화면 API 모음
 * ===================================================== */

export const institutionApi = {
  // 돌봄 대상자
  getCareRecipients,
  getCareRecipient,
  createCareRecipient,
  updateCareRecipient,
  updateCareRecipientConsent,
  closeCareRecipient,
  reopenCareRecipient,
  getCareTargets,

  // 대시보드
  getDashboard,

  // 제보
  getUnassignedReports,
  assignReport,
  getReports,
  getReport,
  updateReportStatus,
  linkReportCareRecipient,
  createAndLinkReportCareRecipient,
  createReportAiSummary,
  getLatestReportAiSummary,

  // 봉사자
  getVolunteers,
  getVolunteerOverview,
  getVolunteer,
  getVolunteerActivities,

  // 활동
  getActivities,
  getPendingApplicationSummary,
  getActivity,
  createActivity,
  updateActivity,
  updateActivityStatus,
  getActivityApplications,
  approveActivityApplication,
  rejectActivityApplication,
  getInstitutionActivityRecord,
  approveInstitutionActivityRecord,
  requestInstitutionActivityRecordRevision,

  // 통계
  getStatistics,
};