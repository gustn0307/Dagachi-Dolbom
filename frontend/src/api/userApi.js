import api, { unwrapData } from "./api";

export const getActivities = (params = {}) =>
  unwrapData(api.get("/api/activities", { params }));

export const getActivity = (activityId) =>
  unwrapData(api.get(`/api/activities/${activityId}`));

// CHECK-01 해당 활동기록 버전의 체크리스트 문항을 조회합니다.
export const getActivityChecklist = (recordId) =>
  unwrapData(api.get(`/api/activity-records/${recordId}/checklist`));

// RECORD-02 현재 공동 ActivityRecord Draft를 조회합니다.
export const getActivityRecord = (recordId) =>
  unwrapData(api.get(`/api/activity-records/${recordId}`));

// RECORD-03 현재 공동 Draft 전체를 저장합니다.
export const saveActivityRecordDraft = (recordId, request) =>
  unwrapData(api.put(`/api/activity-records/${recordId}/draft`, request));

// RECORD-04 대상자 서명 이미지를 업로드합니다.
export const uploadActivityRecordSignature = (recordId, signature) => {
  const formData = new FormData();

  // 백엔드 multipart part명이 "signature"로 확정되어 있습니다.
  formData.append("signature", signature);

  return unwrapData(
    api.post(`/api/activity-records/${recordId}/signature`, formData),
  );
};

// RECORD-05 활동기록을 최종 제출합니다.
// Submit API는 별도의 Request Body를 받지 않습니다.
export const submitActivityRecord = (recordId) =>
  unwrapData(api.post(`/api/activity-records/${recordId}/submit`));

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

// 공개 공지 목록 조회 API
export const getNotices = (params = {}) =>
  unwrapData(api.get("/api/notices", { params }));

// 공개 공지 상세 조회 API
export const getNotice = (noticeId) =>
  unwrapData(api.get(`/api/notices/${noticeId}`));

export const userApi = {
  getActivities,
  getActivity,

  // 활동기록 API
  getActivityChecklist,
  getActivityRecord,
  saveActivityRecordDraft,
  uploadActivityRecordSignature,
  submitActivityRecord,

  createReport,
  getMyReports,
  getNotices,
  getNotice,
};

// 돌봄 대상자 리스트 목록 조회
// 지역/연령대/성별/거리순 필터링 포함
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
  const response = await api.get(
    `/api/activities/${activityId}/execution-details`,
  );
  return response.data.data;
};

// 활동 신청 API
export const applyForActivity = (activityId) =>
  unwrapData(api.post(`/api/activities/${activityId}/applications`));

// APP-02 (1단계) 자동배정 후보 조회 - 신청 생성 안 함
export const fetchAutoMatchCandidate = async ({ latitude, longitude, excludeActivityIds } = {}) => {
  const params = {};
  if (latitude != null && longitude != null) {
    params.latitude = latitude;
    params.longitude = longitude;
  }
  if (excludeActivityIds && excludeActivityIds.length > 0) {
    params.excludeActivityIds = excludeActivityIds;
  }

  const response = await api.get("/api/activity-applications/auto-match/candidate", { params });
  return response.data.data;
};

// APP-02 (2단계) 자동배정 신청 확정
export const applyAutoMatch = (activityId) =>
  unwrapData(api.post("/api/activity-applications/auto-match", { activityId }));

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

  const response = await api.get("/api/users/me/activity-applications", {
    params,
  });
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

// 활동 시작 (RECORD-01)
export const startActivity = (activityId) =>
  unwrapData(api.post(`/api/activities/${activityId}/start`));

// USER-01 내 프로필 조회
export const getMyProfile = () => unwrapData(api.get("/api/users/me"));

// USER-02 내 프로필 수정
export const updateMyProfile = (payload) =>
  unwrapData(api.patch("/api/users/me", payload));

// 비밀번호 변경
export const changePassword = (currentPassword, newPassword) =>
  unwrapData(
    api.patch("/api/users/me/password", { currentPassword, newPassword }),
  );

// USER-03 회원 탈퇴 (본인확인용 비밀번호 필요)
export const withdrawUser = (password) =>
  unwrapData(api.delete("/api/users/me", { data: { password } }));
