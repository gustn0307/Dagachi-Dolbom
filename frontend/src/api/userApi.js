import api, { unwrapData } from "./api";

export const getActivities = (params = {}) =>
  unwrapData(api.get("/api/activities", { params }));

export const getActivity = (activityId) =>
  unwrapData(api.get(`/api/activities/${activityId}`));

// 회원/비회원 공통 제보 등록
// FormData의 request에는 JSON Blob,
// images에는 최대 3장의 이미지 파일을 담아 전송합니다.
export const createReport = (formData) =>
  unwrapData(api.post("/api/reports", formData));

// 로그인 사용자의 제보 목록 조회
export const getMyReports = (params = {}) =>
  unwrapData(api.get("/api/users/me/reports", { params }));

export const userApi = {
  getActivities,
  getActivity,
  createReport,
  getMyReports,
};