import { institutionMockData } from "../data/institutionMockData";
import api, { unwrapData } from "./api";

const USE_MOCK =
  import.meta.env.VITE_USE_MOCK_API === "true";

const clone = (value) =>
  new Promise((resolve) =>
    setTimeout(() => resolve(structuredClone(value)), 180),
  );

export const getCareRecipients = (params = {}) =>
  unwrapData(
    api.get("/api/institution/care-recipients", { params }),
  );

export const getCareRecipient = (recipientId) =>
  unwrapData(
    api.get(`/api/institution/care-recipients/${recipientId}`),
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
      { consentStatus },
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
export const reopenCareRecipient = (
  recipientId,
) =>
  unwrapData(
    api.post(
      `/api/institution/care-recipients/${recipientId}/reopen`,
    ),
  );

export const getDashboard = () =>
  USE_MOCK
    ? clone(institutionMockData.dashboard)
    : unwrapData(api.get("/api/institution/dashboard"));

export const getReports = (params = {}) =>
  USE_MOCK
    ? clone(institutionMockData.reports)
    : unwrapData(api.get("/api/institution/reports", { params }));

export const getCareTargets = (params = {}) =>
  USE_MOCK
    ? clone(institutionMockData.careTargets)
    : getCareRecipients(params);

export const getVolunteers = (params = {}) =>
  USE_MOCK
    ? clone(institutionMockData.volunteers)
    : unwrapData(api.get("/api/institution/volunteers", { params }));

export const getActivities = (params = {}) =>
  USE_MOCK
    ? clone(institutionMockData.activities)
    : unwrapData(api.get("/api/institution/activities", { params }));

export const getStatistics = (period = "6months") =>
  USE_MOCK
    ? clone(institutionMockData.statistics)
    : unwrapData(api.get("/api/institution/statistics", { params: { period } }));

export const updateReportStatus = (id, status) =>
  USE_MOCK
    ? clone({ id, status })
    : unwrapData(
      api.patch(`/api/institution/reports/${id}/status`, { status }),
    );

export const institutionApi = {
  getCareRecipients,
  getCareRecipient,

  // CARE-03~07 돌봄 대상자 관리 API
  createCareRecipient,
  updateCareRecipient,
  updateCareRecipientConsent,
  closeCareRecipient,
  reopenCareRecipient,


  getDashboard,
  getReports,
  getCareTargets,
  getVolunteers,
  getActivities,
  getStatistics,
  updateReportStatus,
};
