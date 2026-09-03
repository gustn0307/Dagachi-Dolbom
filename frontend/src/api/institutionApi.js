import { institutionMockData } from "../data/institutionMockData";
import api, { unwrapData } from "./api";

const USE_MOCK =
  import.meta.env.VITE_USE_MOCK_API === "true";

const clone = (value) =>
  new Promise((resolve) =>
    setTimeout(
      () =>
        resolve(
          structuredClone(value),
        ),
      180,
    ),
  );

/**
 * CARE-01 돌봄 대상자 목록 조회.
 */
export const getCareRecipients = (
  params = {},
) =>
  unwrapData(
    api.get(
      "/api/institution/care-recipients",
      { params },
    ),
  );

/**
 * CARE-02 돌봄 대상자 상세 조회.
 */
export const getCareRecipient = (
  recipientId,
) =>
  unwrapData(
    api.get(
      `/api/institution/care-recipients/${recipientId}`,
    ),
  );

/**
 * CARE-03 돌봄 대상자 등록.
 */
export const createCareRecipient = (
  request,
) =>
  unwrapData(
    api.post(
      "/api/institution/care-recipients",
      request,
    ),
  );

/**
 * CARE-04 돌봄 대상자 기본정보 수정.
 */
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

/**
 * CARE-05 돌봄 대상자 동의 상태 변경.
 */
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

/**
 * CARE-06 돌봄 대상자 관리 종료.
 */
export const closeCareRecipient = (
  recipientId,
) =>
  unwrapData(
    api.post(
      `/api/institution/care-recipients/${recipientId}/close`,
    ),
  );

/**
 * CARE-07 돌봄 대상자 관리 재개.
 */
export const reopenCareRecipient = (
  recipientId,
) =>
  unwrapData(
    api.post(
      `/api/institution/care-recipients/${recipientId}/reopen`,
    ),
  );

/**
 * 기관 대시보드 조회.
 */
export const getDashboard = () =>
  USE_MOCK
    ? clone(
        institutionMockData.dashboard,
      )
    : unwrapData(
        api.get(
          "/api/institution/dashboard",
        ),
      );

/**
 * 기관 제보 목록 조회.
 */
export const getReports = (
  params = {},
) =>
  USE_MOCK
    ? clone(
        institutionMockData.reports,
      )
    : unwrapData(
        api.get(
          "/api/institution/reports",
          { params },
        ),
      );

/**
 * 기존 화면 호환용 돌봄 대상자 목록 조회.
 */
export const getCareTargets = (
  params = {},
) =>
  USE_MOCK
    ? clone(
        institutionMockData.careTargets,
      )
    : getCareRecipients(
        params,
      );

/**
 * VOL-01~03 기관 봉사자 목록, 검색 및 정렬.
 */
export const getVolunteers = (
  params = {},
) =>
  unwrapData(
    api.get(
      "/api/institution/volunteers",
      { params },
    ),
  );

/**
 * VOL-05 기관 봉사자 현황 요약.
 */
export const getVolunteerOverview = () =>
  unwrapData(
    api.get(
      "/api/institution/volunteers/summary",
    ),
  );

/**
 * VOL-06 기관 봉사자 기본 상세 조회.
 */
export const getVolunteer = (
  volunteerId,
) =>
  unwrapData(
    api.get(
      `/api/institution/volunteers/${volunteerId}`,
    ),
  );

/**
 * VOL-06 기관별 봉사자 활동 이력 조회.
 */
export const getVolunteerActivities = (
  volunteerId,
  params = {},
) =>
  unwrapData(
    api.get(
      `/api/institution/volunteers/${volunteerId}/activities`,
      { params },
    ),
  );

/**
 * 기관 활동 목록 조회.
 */
export const getActivities = (
  params = {},
) =>
  USE_MOCK
    ? clone(
        institutionMockData.activities,
      )
    : unwrapData(
        api.get(
          "/api/institution/activities",
          { params },
        ),
      );
      
/**
 * 기관 활동 상세 조회.
 */
export const getActivity = (
  activityId,
) =>
  unwrapData(
    api.get(
      `/api/institution/activities/${activityId}`,
    ),
  );

/**
 * 기관 활동 등록.
 */
export const createActivity = (
  request,
) =>
  unwrapData(
    api.post(
      "/api/institution/activities",
      request,
    ),
  );

/**
 * 기관 활동 정보 수정.
 */
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

/**
 * 기관 활동 상태 변경.
 */
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

/**
 * 기관 활동 신청자 목록 조회.
 */
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

/**
 * 기관 통계 조회.
 */
export const getStatistics = (
  period = "6months",
) =>
  USE_MOCK
    ? clone(
        institutionMockData.statistics,
      )
    : unwrapData(
        api.get(
          "/api/institution/statistics",
          {
            params: {
              period,
            },
          },
        ),
      );

/**
 * 제보 상태 변경.
 */
export const updateReportStatus = (
  id,
  status,
) =>
  USE_MOCK
    ? clone({
        id,
        status,
      })
    : unwrapData(
      api.patch(`/api/institution/reports/${id}/status`, { status }),
    );

/**
 * 기관 화면에서 사용하는 API 모음.
 */
export const institutionApi = {
  // 돌봄 대상자 API
  getCareRecipients,
  getCareRecipient,
  createCareRecipient,
  updateCareRecipient,
  updateCareRecipientConsent,
  closeCareRecipient,
  reopenCareRecipient,

  // 대시보드 및 제보 API
  getDashboard,
  getReports,
  getCareTargets,

  // VOL-01~07 기관 봉사자 관리 API
  getVolunteers,
  getVolunteerOverview,
  getVolunteer,
  getVolunteerActivities,

  // 기관 활동 API
  getActivities,
  getActivity,
  createActivity,
  updateActivity,
  updateActivityStatus,
  getActivityApplications,

  // 기관 활동 및 통계 API
  getStatistics,

  // 제보 상태 변경 API
  updateReportStatus,
};