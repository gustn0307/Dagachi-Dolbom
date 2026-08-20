# Frontend 공통 API 사용 가이드

이 문서는 다같이돌봄 Frontend에서 Backend API를 호출하는 현재 표준과 사용법을 설명한다.

페이지 컴포넌트에서는 `axios`나 `fetch`를 직접 사용하지 않는다. 반드시 역할별 API 모듈을 호출한다.

## 1. 구조

```text
Page / Hook
    ↓
역할별 API 모듈
  ├─ authApi.js
  ├─ userApi.js
  ├─ institutionApi.js
  └─ adminApi.js
    ↓
공통 api.js (Axios instance 1개)
    ↓
Backend http://localhost:8080
```

파일 위치:

```text
src/api/
  api.js
  authApi.js
  userApi.js
  institutionApi.js
  adminApi.js
```

기존 `src/services`는 위 역할별 API 모듈로 통합되어 삭제되었다.

## 2. 환경변수와 실행

로컬 실제 Backend 연결은 `frontend/.env.local`에 설정한다.

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK_API=false
```

기존 관리자·기관 화면을 Mock으로 실행하려면 다음과 같이 설정한다.

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCK_API=true
```

```bash
npm install
npm run dev
```

- Frontend: `http://localhost:3000`
- Backend: `http://localhost:8080`
- `VITE_API_BASE_URL`에는 `/api`를 붙이지 않는다.
- `.env.local` 변경 후 Vite를 다시 시작한다.
- `.env.local`은 Git에 Commit하지 않는다.
- `VITE_` 변수에 비밀번호, JWT Secret, AWS Key를 넣지 않는다.

## 3. 공통 Axios 동작

`src/api/api.js`에는 프로젝트에서 유일한 Axios instance가 있다.

- `baseURL`: `import.meta.env.VITE_API_BASE_URL`
- timeout: 15초
- JSON 요청 Header 설정
- access token 자동 첨부

`localStorage`에 토큰이 있으면 모든 요청에 다음 Header가 자동으로 추가된다.

```http
Authorization: Bearer {accessToken}
```

페이지나 역할별 API 함수에서 Authorization Header를 반복해서 만들지 않는다.

## 4. 응답 반환 규칙

Backend 공통 응답:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "요청이 성공했습니다.",
  "data": {}
}
```

API 함수는 공통 `unwrapData()`로 `response.data.data`의 실제 payload만 반환한다.

목록 API는 다음과 같은 `PageResponse`를 반환한다.

```js
{
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true
}
```

따라서 목록은 다음처럼 사용한다.

```js
const page = await userApi.getActivities({ page: 0, size: 20 });
const activities = page.content;
```

## 5. Auth API

```js
import { authApi } from "../api/authApi";
```

### 회원가입

```js
const user = await authApi.signup({
  email: "user@example.com",
  password: "password123!",
  name: "홍길동",
  nickname: "길동",
  phone: "010-1234-5678",
  gender: "MALE",
});
```

- API: `POST /api/auth/signup`
- `gender`: `MALE` 또는 `FEMALE`

### 로그인과 토큰 저장

```js
const loginResult = await authApi.login({
  email: "user@example.com",
  password: "password123!",
});

localStorage.setItem("accessToken", loginResult.accessToken);
```

- API: `POST /api/auth/login`
- API 모듈은 토큰을 자동 저장하지 않는다.
- 로그인 UI가 성공 결과를 확인한 뒤 명시적으로 저장해야 한다.

### 현재 사용자 조회

```js
const me = await authApi.getMe();
```

- API: `GET /api/auth/me`
- 공통 interceptor가 저장된 토큰을 자동 첨부한다.

### 현재 최소 로그아웃

Backend 로그아웃 정책은 아직 확정되지 않았다. 현재는 저장된 토큰을 제거한다.

```js
localStorage.removeItem("accessToken");
```

## 6. USER API

```js
import { userApi } from "../api/userApi";
```

### 활동 목록

```js
const page = await userApi.getActivities({
  page: 0,
  size: 20,
});

const activities = page.content;
```

- API: `GET /api/activities?page=0&size=20`
- `page`: 0-based
- `size`: 기본 20, 최대 100
- 검색·필터가 추가되면 같은 `params` 객체에 전달한다.

### 활동 상세

```js
const activity = await userApi.getActivity(activityId);
```

- API: `GET /api/activities/{activityId}`

## 7. INSTITUTION API

```js
import { institutionApi } from "../api/institutionApi";
```

### CARE-01 대상자 목록

```js
const page = await institutionApi.getCareRecipients({
  page: 0,
  size: 20,
});
```

- API: `GET /api/institution/care-recipients`

### CARE-02 대상자 상세

```js
const recipient = await institutionApi.getCareRecipient(recipientId);
```

- API: `GET /api/institution/care-recipients/{recipientId}`

### 기존 기관 화면용 함수

```js
await institutionApi.getDashboard();
await institutionApi.getReports({ page: 0, size: 20 });
await institutionApi.getCareTargets({ page: 0, size: 20 });
await institutionApi.getVolunteers({ page: 0, size: 20 });
await institutionApi.getActivities({ page: 0, size: 20 });
await institutionApi.getStatistics("6months");
await institutionApi.updateReportStatus(reportId, status);
```

이 함수들은 기존 기관 화면의 Mock 기능을 보존하기 위해 통합한 함수다.

- Mock 모드에서는 `institutionMockData`를 반환한다.
- `getCareTargets()`는 기존 화면 이름과의 호환 함수다.
- 실제 모드의 `getCareTargets()`는 최신 명세의 `getCareRecipients()`를 호출한다.
- 신규 코드에서는 `getCareRecipients()`를 사용한다.
- 대시보드·제보·봉사자·통계 등의 실제 계약은 Backend Controller 구현 시 명세와 다시 대조한다.

## 8. ADMIN API

```js
import { adminApi } from "../api/adminApi";
```

### NOTICE-03 공지 목록

```js
const page = await adminApi.getAdminNotices({
  page: 0,
  size: 20,
});
```

- API: `GET /api/admin/notices`

### NOTICE-04 공지 등록

```js
const notice = await adminApi.createAdminNotice({
  title: "공지 제목",
  content: "공지 내용",
});
```

- API: `POST /api/admin/notices`
- payload는 최종 Backend Request DTO 필드에 맞춘다.

### 기존 관리자 화면용 함수

```js
await adminApi.getUsers({ page: 0, size: 20 });
await adminApi.updateUserStatus(userId, status);
await adminApi.getInstitutions({ page: 0, size: 20 });
await adminApi.updateInstitutionStatus(institutionId, status);
```

이 함수들은 기존 관리자 화면의 Mock 기능을 보존하기 위해 통합했다. 실제 Endpoint와 상태값은 관련 Backend 계약 확정 시 다시 검증한다.

## 9. React 페이지 사용 예시

```jsx
import { useEffect, useState } from "react";
import { userApi } from "../../api/userApi";

function ActivityList() {
  const [activities, setActivities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    userApi
      .getActivities({ page: 0, size: 20 })
      .then((page) => setActivities(page.content))
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>불러오는 중...</p>;
  if (error) return <p>목록을 불러오지 못했습니다.</p>;

  return activities.map((activity) => (
    <article key={activity.activityId}>{activity.title}</article>
  ));
}
```

기존 기관·관리자 화면은 공통 조회 Hook도 사용한다.

```jsx
const { data, loading, error, reload } = useInstitutionData(
  institutionApi.getDashboard,
  [],
);
```

## 10. 오류 처리

현재 전역 401/403 redirect는 구현하지 않았다. 각 화면에서 Axios 오류를 처리한다.

```js
try {
  const me = await authApi.getMe();
  setUser(me);
} catch (error) {
  const status = error.response?.status;
  const serverError = error.response?.data;

  if (status === 401) {
    localStorage.removeItem("accessToken");
  }

  console.error(serverError?.code, serverError?.message);
}
```

주요 상태코드:

- 400: 입력값 검증 실패
- 401: 인증 필요 또는 토큰 오류
- 403: 역할 권한 부족 또는 정지 계정
- 404: 데이터 또는 API 없음
- 409: 중복 이메일 등 충돌
- 500: 서버 오류

로그인 이동과 역할별 redirect 정책 확정 전에는 interceptor에서 강제 이동시키지 않는다.

## 11. Mock과 실제 API

`VITE_USE_MOCK_API=true`:

- 기존 관리자·기관 화면용 함수 일부가 로컬 Mock 데이터를 반환한다.
- Backend 없이 해당 UI를 확인할 수 있다.

`VITE_USE_MOCK_API=false`:

- 역할별 함수가 공통 Axios로 Backend를 호출한다.
- Backend와 PostgreSQL이 실행 중이어야 한다.
- JWT API는 `localStorage.accessToken`이 필요하다.

Auth, USER 활동, CARE-01/02, 관리자 공지 API는 Mock 분기 없이 실제 Backend를 호출한다.

## 12. 새 API 추가 규칙

역할에 맞는 기존 파일에 함수만 추가한다.

```js
// src/api/userApi.js
export const applyActivity = (activityId, payload) =>
  unwrapData(
    api.post(`/api/activities/${activityId}/applications`, payload),
  );

export const userApi = {
  // 기존 함수들
  applyActivity,
};
```

규칙:

1. 페이지에서 `axios`나 `fetch`를 직접 사용하지 않는다.
2. 새 `axios.create()`를 만들지 않는다.
3. Backend 주소를 코드에 하드코딩하지 않는다.
4. Endpoint에는 `/api`를 포함하고 환경변수에는 포함하지 않는다.
5. 목록 Query는 `{ params }`로 전달한다.
6. Body는 Axios의 두 번째 인자로 전달한다.
7. 응답은 `unwrapData()`로 payload만 반환한다.
8. Public·multipart·S3 API는 계약 확정 전에 구현하지 않는다.

```js
unwrapData(api.get("/api/path", { params }));
unwrapData(api.post("/api/path", payload));
unwrapData(api.patch("/api/path/1", payload));
unwrapData(api.delete("/api/path/1"));
```

## 13. 연결 확인

1. Backend가 `http://localhost:8080`에서 실행 중인지 확인한다.
2. Frontend가 `http://localhost:3000`에서 실행 중인지 확인한다.
3. `.env.local` 값을 확인하고 Vite를 재시작한다.
4. 브라우저 Network 탭에서 URL과 상태코드를 확인한다.
5. 인증 요청의 Header에 `Authorization: Bearer ...`가 있는지 확인한다.
6. 오류 응답의 `code`와 `message`를 확인한다.
7. 변경 후 `npm run build`를 실행한다.

## 14. 아직 구현하지 않는 범위

- 전역 401/403 자동 redirect
- Refresh Token과 자동 재발급
- Backend 로그아웃 또는 Token blacklist
- 공개 공지와 비회원 제보의 최종 Public 정책
- 이메일 사용 가능 여부 API
- multipart 및 S3 공통 업로드
- AI Service 직접 호출

Frontend는 FastAPI를 직접 호출하지 않고 Spring Boot Backend를 통해 AI 기능을 사용한다.
