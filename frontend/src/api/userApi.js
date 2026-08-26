import api, { unwrapData } from "./api";

export const getActivities = (params = {}) =>
  unwrapData(api.get("/api/activities", { params }));

export const getActivity = (activityId) =>
  unwrapData(api.get(`/api/activities/${activityId}`));

// 공개 공지 목록 조회 API
export const getNotices = (params = {}) =>
  unwrapData(api.get("/api/notices", { params }));

// 공개 공지 상세 조회 API
export const getNotice = (noticeId) =>
  unwrapData(api.get(`/api/notices/${noticeId}`));

export const userApi = {
  getActivities,
  getActivity,
  getNotices,
  getNotice,
};
