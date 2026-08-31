import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";

import UserLayout from "./layouts/UserLayout";
import InstitutionLayout from "./layouts/InstitutionLayout";
import AdminLayout from "./layouts/AdminLayout";

import PublicLanding from "./pages/landing/PublicLanding";
import Home from "./pages/home/Home";
import Report from "./pages/report/Report";
import Volunteer from "./pages/volunteer/Volunteer";
import MyPage from "./pages/mypage/MyPage";
import Notice from "./pages/notice/Notice";

import InstitutionDashboard from "./pages/institution/Dashboard";
import ReportManagement from "./pages/institution/ReportManagement";
import CareTargetManagement from "./pages/institution/CareTargetManagement";
import CareTargetDetail from "./pages/institution/CareTargetDetail";
import VolunteerManagement from "./pages/institution/VolunteerManagement";
import VolunteerDetail from "./pages/institution/VolunteerDetail";
import ActivityManagement from "./pages/institution/ActivityManagement";
import InstitutionStatistics from "./pages/institution/Statistics";

import UserManagement from "./pages/admin/UserManagement";
import InstitutionManagement from "./pages/admin/InstitutionManagement";

import Login from "./auth/login";
import Join from "./auth/join";
import RequireRole from "./auth/RequireRole";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 비회원도 접근 가능한 공개 랜딩 페이지 */}
        <Route path="/" element={<PublicLanding />} />

        {/* 일반 사용자 영역 */}
        <Route element={<UserLayout />}>
          <Route
            path="/home"
            element={
              <RequireRole allowedRoles={["USER"]}>
                <Home />
              </RequireRole>
            }
          />

          {/* 제보 접수는 회원/비회원 모두 사용할 수 있으므로 공개합니다. */}
          <Route path="/report" element={<Report />} />
          <Route
            path="/report/new"
            element={<Navigate to="/report" replace />}
          />

          <Route
            path="/volunteer"
            element={
              <RequireRole allowedRoles={["USER"]}>
                <Volunteer />
              </RequireRole>
            }
          />

          <Route
            path="/mypage"
            element={
              <RequireRole allowedRoles={["USER"]}>
                <MyPage />
              </RequireRole>
            }
          />

          {/* 공개된 공지는 로그인하지 않아도 조회할 수 있습니다. */}
          <Route path="/notice" element={<Notice />} />

          <Route path="/login" element={<Login />} />
          <Route path="/join" element={<Join />} />
        </Route>

        {/* 기관 담당자 전용 영역 */}
        <Route
          path="/institution"
          element={
            <RequireRole allowedRoles={["INSTITUTION"]}>
              <InstitutionLayout />
            </RequireRole>
          }
        >
          <Route index element={<InstitutionDashboard />} />
          <Route path="reports" element={<ReportManagement />} />
          <Route path="care-targets" element={<CareTargetManagement />} />
          <Route
            path="care-targets/:recipientId"
            element={<CareTargetDetail />}
          />
          <Route path="volunteers" element={<VolunteerManagement />} />
          <Route path="volunteers/:volunteerId" element={<VolunteerDetail />} />

          <Route path="activities" element={<ActivityManagement />} />
          <Route path="statistics" element={<InstitutionStatistics />} />
        </Route>

        {/* 관리자 전용 영역 */}
        <Route
          path="/admin"
          element={
            <RequireRole allowedRoles={["ADMIN"]}>
              <AdminLayout />
            </RequireRole>
          }
        >
          <Route index element={<Navigate to="users" replace />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="institutions" element={<InstitutionManagement />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
