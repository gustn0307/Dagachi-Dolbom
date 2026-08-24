import axios from "axios";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000,
  headers: {
    Accept: "application/json",
  },
});

// 저장된 Access Token이 있으면 모든 인증 API 요청에
// Authorization: Bearer {accessToken} 헤더를 자동으로 추가합니다.
api.interceptors.request.use(
  (config) => {
    const accessToken = sessionStorage.getItem("accessToken");

    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }

    return config;
  },
  (error) => Promise.reject(error),
);

// 401은 인증 정보가 없거나 Access Token이 만료·위조된 경우이므로
// 저장된 토큰을 제거하고 로그인 페이지로 이동합니다.
//
// 403은 인증 자체는 유효하지만 Role·데이터 접근 권한이 부족한 경우이므로
// 여기서 로그아웃시키지 않고 각 화면/도메인에서 처리합니다.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error?.response?.status === 401) {
      sessionStorage.removeItem("accessToken");

      if (window.location.pathname !== "/login") {
        window.location.href = "/login";
      }
    }

    return Promise.reject(error);
  },
);

// Backend의 공통 ApiResponse<T>에서 실제 화면이 사용할 data만 반환합니다.
// 각 API 함수가 response.data.data를 반복해서 처리하지 않도록 형식을 통일합니다.
export const unwrapData = (request) =>
  request.then((response) => response.data.data);

export default api;