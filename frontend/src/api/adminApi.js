import api, { unwrapData } from "./api";

export const getAdminNotices = (params = {}) =>
  unwrapData(api.get("/api/admin/notices", { params }));

export const createAdminNotice = (payload) =>
  unwrapData(api.post("/api/admin/notices", payload));

// 관리자 공지 수정 및 상태 변경 API
export const updateAdminNotice = (noticeId, payload) =>
  unwrapData(api.patch(`/api/admin/notices/${noticeId}`, payload));

// 관리자 공지 Soft Delete API
export const deleteAdminNotice = (noticeId) =>
  unwrapData(api.delete(`/api/admin/notices/${noticeId}`));

export const adminApi = {
  getAdminNotices,
  createAdminNotice,
  updateAdminNotice,
  deleteAdminNotice,
};