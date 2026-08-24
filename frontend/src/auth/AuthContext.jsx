import { createContext, useContext, useEffect, useState } from "react";
import { getMe } from "../api/authApi";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  // user 정보가 존재하면 로그인된 상태로 판단합니다.
  const isAuthenticated = Boolean(user);

  const logout = () => {
    // 현재 브라우저 세션의 Access Token을 제거하고
    // React의 인증 상태도 즉시 비로그인 상태로 변경합니다.
    sessionStorage.removeItem("accessToken");
    setUser(null);
  };

  useEffect(() => {
    const restoreAuth = async () => {
      const accessToken = sessionStorage.getItem("accessToken");

      // 저장된 토큰이 없다면 서버 확인 없이 비로그인 상태로 확정합니다.
      if (!accessToken) {
        setLoading(false);
        return;
      }

      try {
        // 새로고침 시 메모리의 user 상태는 사라지므로,
        // 저장된 Access Token으로 /api/auth/me를 호출해 사용자 정보를 복원합니다.
        const currentUser = await getMe();
        setUser(currentUser);
      } catch {
        // 만료되었거나 유효하지 않은 토큰이면 인증 정보를 모두 정리합니다.
        // api.js의 401 interceptor에서도 토큰을 제거하지만,
        // AuthContext 자체도 독립적으로 안전한 상태를 보장합니다.
        sessionStorage.removeItem("accessToken");
        setUser(null);
      } finally {
        // 인증 복원이 끝난 뒤 Route Guard가 최종 인증 상태를 판단할 수 있게 합니다.
        setLoading(false);
      }
    };

    restoreAuth();
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        setUser,
        isAuthenticated,
        loading,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth는 AuthProvider 내부에서 사용해야 합니다.");
  }

  return context;
}