import { institutionMockData } from "../data/institutionMockData";
import api, { unwrapData } from "./api";

const USE_MOCK =
  import.meta.env.VITE_USE_MOCK_API !== "false" ||
  !import.meta.env.VITE_API_BASE_URL;
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
  getDashboard,
  getReports,
  getCareTargets,
  getVolunteers,
  getActivities,
  getStatistics,
  updateReportStatus,
};
