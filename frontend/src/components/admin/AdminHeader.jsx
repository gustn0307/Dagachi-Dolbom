import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const ROLE_LABEL = {
  ADMIN: "관리자",
  INSTITUTION: "기관 담당자",
  USER: "일반 회원",
};

function AdminHeader() {
  const navigate = useNavigate();
  const { user, logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  const avatarInitial = user?.name ? user.name.charAt(0) : "?";
  const roleLabel = ROLE_LABEL[user?.role] ?? "";

  return (
    <header className="admin-header">
      <div className="admin-mobile-brand">◆ 서비스 관리자</div>

      <div className="admin-global-search">
        <span>⌕</span>
        <input aria-label="통합 검색" placeholder="사용자 또는 기관 통합 검색" />
      </div>

      <div className="admin-user">
        <span>{avatarInitial}</span>

        <div>
          <strong>{user?.name ?? "이름"}</strong>
          <small>{roleLabel}</small>
        </div>

        <button type="button" onClick={handleLogout}>
          로그아웃
        </button>
      </div>
    </header>
  );
}

export default AdminHeader;