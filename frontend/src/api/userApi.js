import api, { unwrapData } from "./api";

export const getActivities = (params = {}) =>
  unwrapData(api.get("/api/activities", { params }));

export const getActivity = (activityId) =>
  unwrapData(api.get(`/api/activities/${activityId}`));

// 회원/비회원 공통 제보 등록
// FormData의 request에는 JSON Blob,
// images에는 최대 3장의 이미지 파일을 담아 전송합니다.
// 대용량 이미지 업로드는 일반 API보다 시간이 오래 걸릴 수 있으므로
// 제보 등록 요청에만 60초 timeout을 적용합니다.
export const createReport = (formData) =>
  unwrapData(
    api.post("/api/reports", formData, {
      timeout: 60000,
    }),
  );

// 로그인 사용자의 제보 목록 조회
export const getMyReports = (params = {}) =>
  unwrapData(api.get("/api/users/me/reports", { params }));

export const userApi = {
  getActivities,
  getActivity,
  createReport,
  getMyReports,
};

// 돌봄 대상자 리스트 목록 조회 (지역/연령대/성별/거리순 필터링 포함)
export const fetchActivities = async ({
  page = 0,
  size = 20,
  region,
  ageGroups,
  gender,
  latitude,
  longitude,
} = {}) => {
  const params = { page, size };

  if (region) {
    params.region = region;
  }
  if (ageGroups && ageGroups.length > 0) {
    params.ageGroups = ageGroups;
  }
  if (gender) {
    params.gender = gender;
  }
  if (latitude != null && longitude != null) {
    params.latitude = latitude;
    params.longitude = longitude;
  }

  const response = await api.get("/api/activities", { params });
  return response.data.data;
};

export const fetchActivityDetail = async (activityId) => {
  const response = await api.get(`/api/activities/${activityId}`);
  return response.data.data;
};

export const fetchExecutionDetail = async (activityId) => {
  const response = await api.get(`/api/activities/${activityId}/execution-details`);
  return response.data.data;
};

// 신청 버튼 활성화
export const applyForActivity = (activityId) =>
  unwrapData(api.post(`/api/activities/${activityId}/applications`));

// 내 신청 목록 조회 (APP-03)
export const fetchMyApplications = async ({
  page = 0,
  size = 10,
  status,
  applicationType,
} = {}) => {
  const params = { page, size };
  if (status) params.status = status;
  if (applicationType) params.applicationType = applicationType;

  const response = await api.get("/api/users/me/activity-applications", { params });
  return response.data.data;
};

// 내 활동 목록 조회 (APP-04)
export const fetchMyActivities = async ({
  page = 0,
  size = 10,
  activityStatus,
} = {}) => {
  const params = { page, size };
  if (activityStatus) params.activityStatus = activityStatus;

  const response = await api.get("/api/users/me/activities", { params });
  return response.data.data;
};

// 신청 취소 (APP-05)
export const cancelApplication = (applicationId) =>
  unwrapData(api.post(`/api/activity-applications/${applicationId}/cancel`));