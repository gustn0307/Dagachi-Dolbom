import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

function AdminHeader() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="admin-header">
      <div className="admin-mobile-brand">
        ◆ 서비스 관리자
      </div>

      <div className="admin-global-search">
        <span>⌕</span>
        <input
          aria-label="통합 검색"
          placeholder="사용자 또는 기관 통합 검색"
        />
      </div>

      <button
        className="admin-alert"
        type="button"
        aria-label="알림"
      >
        ♢
        <i />
      </button>

      <div className="admin-user">
        <span>A</span>

        <div>
          <strong>서비스 관리자</strong>
          <small>superadmin</small>
        </div>

        <button
          type="button"
          onClick={handleLogout}
        >
          로그아웃
        </button>
      </div>
    </header>
  );
}

export default AdminHeader;