import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import UserLayout from "./layouts/UserLayout";
import Home from "./pages/home/Home";
import Report from "./pages/report/Report";
import Volunteer from "./pages/volunteer/Volunteer";
import MyPage from "./pages/mypage/MyPage";
import Notice from "./pages/notice/Notice";
import InstitutionLayout from "./layouts/InstitutionLayout";
import InstitutionDashboard from "./pages/institution/Dashboard";
import ReportManagement from "./pages/institution/ReportManagement";
import CareTargetManagement from "./pages/institution/CareTargetManagement";
import VolunteerManagement from "./pages/institution/VolunteerManagement";
import ActivityManagement from "./pages/institution/ActivityManagement";
import InstitutionStatistics from "./pages/institution/Statistics";
import AdminLayout from "./layouts/AdminLayout";
import UserManagement from "./pages/admin/UserManagement";
import InstitutionManagement from "./pages/admin/InstitutionManagement";
import Login from "./auth/login";
import PublicLanding from "./pages/landing/PublicLanding";

function App() {
  // BACKEND: 로그인 API 연결 후 UserLayout/InstitutionLayout/AdminLayout 앞에
  // RequireAuth 같은 라우트 가드를 추가해 역할(USER, INSTITUTION, ADMIN)을 검사하세요.
  // 실제 권한 검증은 프론트뿐 아니라 각 백엔드 API에서도 반드시 수행해야 합니다.
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<PublicLanding />} />
        <Route element={<UserLayout />}>
          <Route path="/home" element={<Home />} />
          <Route path="/report" element={<Report />} />
          <Route path="/report/new" element={<Report />} />
          <Route path="/volunteer" element={<Volunteer />} />
          <Route path="/mypage" element={<MyPage />} />
          <Route path="/notice" element={<Notice />} />
          <Route path="/login" element={<Login />} />
        </Route>
        <Route path="/institution" element={<InstitutionLayout />}>
          <Route index element={<InstitutionDashboard />} />
          <Route path="reports" element={<ReportManagement />} />
          <Route path="care-targets" element={<CareTargetManagement />} />
          <Route path="volunteers" element={<VolunteerManagement />} />
          <Route path="activities" element={<ActivityManagement />} />
          <Route path="statistics" element={<InstitutionStatistics />} />
        </Route>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="users" replace />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="institutions" element={<InstitutionManagement />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
