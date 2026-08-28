import { useState } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";

const links = [
  { label: "홈", path: "/home" },
  { label: "이웃 제보", path: "/report" },
  { label: "봉사 참여", path: "/volunteer" },
  { label: "마이페이지", path: "/mypage" },
  { label: "공지 · FAQ", path: "/notice" },
];

function UserHeader() {
  const [menuOpen, setMenuOpen] = useState(false);

  const navigate = useNavigate();

  const { isAuthenticated, logout } = useAuth();

  const homePath = isAuthenticated ? "/home" : "/";

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  return (
    <header className="topbar">
      <Link to={homePath} className="brand" aria-label="다함께 돌봄 홈">
        <span className="brand-symbol" aria-hidden="true">
          ♥
        </span>
        <span>
          <b>다함께 돌봄</b>
          <small>함께 만드는 따뜻한 우리 동네</small>
        </span>
      </Link>
      <button
        className="mobile-menu"
        type="button"
        aria-label="메뉴 열기"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        {menuOpen ? "×" : "☰"}
      </button>
      <nav className={menuOpen ? "open" : ""} aria-label="주요 메뉴">
        {links.map((link) => {
          const path = link.path === "/home" ? homePath : link.path;

          return (
            <NavLink
              key={link.path}
              to={path}
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                isActive ? "nav-link active" : "nav-link"
              }
            >
              {link.label}
            </NavLink>
          );
        })}
      </nav>
      <div className="header-tools">
        <button
          className="font-control"
          type="button"
          aria-label="큰 글씨 사용"
        >
          글자 크기 <i />
          <b>A</b>
        </button>

        {isAuthenticated ? (
          <button type="button" className="login" onClick={handleLogout}>
            로그아웃
          </button>
        ) : (
          <>
            <Link to="/login" className="login">
              로그인
            </Link>

            <Link to="/join" className="join">
              회원가입
            </Link>
          </>
        )}
      </div>
    </header>
  );
}

export default UserHeader;
