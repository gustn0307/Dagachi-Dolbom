import api, { unwrapData } from "./api";

// 인증 관련 API는 Backend의 ApiResponse<T>에서 data만 반환하도록 통일합니다.
export const signup = (payload) =>
  unwrapData(api.post("/api/auth/signup", payload));

export const login = (payload) =>
  unwrapData(api.post("/api/auth/login", payload));

export const getMe = () =>
  unwrapData(api.get("/api/auth/me"));

export const authApi = {
  signup,
  login,
  getMe,
};