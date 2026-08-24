import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

function RequireRole({ allowedRoles, children }) {
  const { user, isAuthenticated, loading } = useAuth();
  const location = useLocation();

  // 새로고침 직후 인증 상태 복원이 끝나기 전에
  // 비로그인 또는 권한 없음으로 잘못 판단하지 않도록 기다립니다.
  if (loading) {
    return null;
  }

  if (!isAuthenticated) {
    // 로그인 성공 후 원래 접근하려던 페이지로 복귀할 수 있도록
    // 현재 경로를 로그인 페이지에 전달합니다.
    const redirect = `${location.pathname}${location.search}`;

    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(redirect)}`}
        replace
      />
    );
  }

  if (!allowedRoles.includes(user?.role)) {
    // 로그인은 되어 있지만 해당 화면에 접근할 Role이 없으면
    // 현재 사용자의 Role에 맞는 기본 화면으로 돌려보냅니다.
    if (user?.role === "ADMIN") {
      return <Navigate to="/admin" replace />;
    }

    if (user?.role === "INSTITUTION") {
      return <Navigate to="/institution" replace />;
    }

    return <Navigate to="/home" replace />;
  }

  return children;
}

export default RequireRole;