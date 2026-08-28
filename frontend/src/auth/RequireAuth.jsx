import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "./AuthContext";

function RequireAuth({ children }) {
  const { isAuthenticated, loading } = useAuth();
  const location = useLocation();

  // 새로고침 직후에는 AuthContext가 /api/auth/me로
  // 인증 상태를 복원 중이므로 판단이 끝날 때까지 기다립니다.
  if (loading) {
    return null;
  }

  if (!isAuthenticated) {
    // 로그인 성공 후 원래 접근하려던 페이지로 돌아갈 수 있도록
    // 현재 경로를 redirect query parameter로 전달합니다.
    const redirect = `${location.pathname}${location.search}`;

    return (
      <Navigate
        to={`/login?redirect=${encodeURIComponent(redirect)}`}
        replace
      />
    );
  }

  return children;
}

export default RequireAuth;