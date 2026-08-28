import { adminMockData } from "../data/adminMockData";
import api, { unwrapData } from "./api";

const USE_MOCK =
  import.meta.env.VITE_USE_MOCK_API === "true";

const clone = (value) =>
  new Promise((resolve) =>
    setTimeout(() => resolve(structuredClone(value)), 180),
  );

export const getAdminNotices = (params = {}) =>
  unwrapData(api.get("/api/admin/notices", { params }));

export const createAdminNotice = (payload) =>
  unwrapData(api.post("/api/admin/notices", payload));

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
  getUsers,
  updateUserStatus,
  getInstitutions,
  updateInstitutionStatus,
};
