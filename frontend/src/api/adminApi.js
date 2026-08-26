import { adminMockData } from "../data/adminMockData";
import api, { unwrapData } from "./api";

const USE_MOCK =
  import.meta.env.VITE_USE_MOCK_API !== "false" ||
  !import.meta.env.VITE_API_BASE_URL;
const clone = (value) =>
  new Promise((resolve) =>
    setTimeout(() => resolve(structuredClone(value)), 180),
  );

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

export const getUsers = (params = {}) =>
  USE_MOCK
    ? clone(adminMockData.users)
    : unwrapData(api.get("/api/admin/users", { params }));

export const updateUserStatus = (id, status) =>
  USE_MOCK
    ? clone({ id, status })
    : unwrapData(api.patch(`/api/admin/users/${id}/status`, { status }));

export const getInstitutions = (params = {}) =>
  USE_MOCK
    ? clone(adminMockData.institutions)
    : unwrapData(api.get("/api/admin/institutions", { params }));

export const updateInstitutionStatus = (id, status) =>
  USE_MOCK
    ? clone({ id, status })
    : unwrapData(
        api.patch(`/api/admin/institutions/${id}/status`, { status }),
      );

export const adminApi = {
  getAdminNotices,
  createAdminNotice,
  updateAdminNotice,
  deleteAdminNotice,
  getUsers,
  updateUserStatus,
  getInstitutions,
  updateInstitutionStatus,
};
