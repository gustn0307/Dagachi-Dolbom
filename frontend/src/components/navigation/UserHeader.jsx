import { useState } from "react";
import { Link, NavLink } from "react-router-dom";

const links = [
  { label: "홈", path: "/home" },
  { label: "이웃 제보", path: "/report" },
  { label: "봉사 참여", path: "/volunteer" },
  { label: "마이페이지", path: "/mypage" },
  { label: "공지 · FAQ", path: "/notice" },
];

function UserHeader() {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <header className="topbar">
      <Link to="/home" className="brand" aria-label="이웃을 잇다 홈">
        <span className="brand-symbol" aria-hidden="true">♥</span>
        <span><b>다같이 돌봄</b><small>함께 만드는 따뜻한 우리 동네</small></span>
      </Link>
      <button className="mobile-menu" type="button" aria-label="메뉴 열기" aria-expanded={menuOpen} onClick={() => setMenuOpen((open) => !open)}>
        {menuOpen ? "×" : "☰"}
      </button>
      <nav className={menuOpen ? "open" : ""} aria-label="주요 메뉴">
        {links.map((link) => (
          <NavLink key={link.path} to={link.path} onClick={() => setMenuOpen(false)} className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}>
            {link.label}
          </NavLink>
        ))}
      </nav>
      <div className="header-tools">
        <button className="font-control" type="button" aria-label="큰 글씨 사용">글자 크기 <i /><b>A</b></button>
        <Link to="/login" className="login">로그인</Link>
        <Link to="/join" className="join">회원가입</Link>
      </div>
    </header>
  );
}

export default UserHeader;
