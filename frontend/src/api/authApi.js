import api, { unwrapData } from "./api";

export const signup = (payload) =>
  unwrapData(api.post("/api/auth/signup", payload));

export const login = (payload) =>
  unwrapData(api.post("/api/auth/login", payload));

export const getMe = () => unwrapData(api.get("/api/auth/me"));

export const authApi = {
  signup,
  login,
  getMe,
};
