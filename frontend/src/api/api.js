import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: {
    Accept: "application/json",
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config) => {
    const accessToken = localStorage.getItem("accessToken");

    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => Promise.reject(error),
);

// TODO: 인증 상태와 역할별 이동 정책이 확정되면 401/403 공통 처리를 추가합니다.

// 역할별 API와 기존 Mock 화면 모두 실제 payload만 받도록 반환 형식을 통일합니다.
export const unwrapData = (request) =>
  request.then((response) => response.data.data);

export default api;
