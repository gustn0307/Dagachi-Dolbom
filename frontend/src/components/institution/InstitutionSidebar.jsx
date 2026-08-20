import { NavLink } from "react-router-dom";

const Icon = ({ children }) => <span className="side-icon" aria-hidden="true">{children}</span>;

function InstitutionSidebar() {
  return (
    <aside className="institution-sidebar">
      <div className="institution-logo">
        <span className="institution-logo-mark">♥</span>
        <div><h2>이웃을 잇다</h2><p>기관 파트너</p></div>
      </div>
      <nav>
        <NavLink to="/institution" end><Icon>▦</Icon><span>대시보드</span></NavLink>
        <NavLink to="/institution/reports"><Icon>⌕</Icon><span>제보 관리</span><em>12</em></NavLink>
        <NavLink to="/institution/care-targets"><Icon>♡</Icon><span>돌봄 대상자</span></NavLink>
        <NavLink to="/institution/volunteers"><Icon>♧</Icon><span>봉사자 관리</span></NavLink>
        <NavLink to="/institution/activities"><Icon>✓</Icon><span>활동 관리</span></NavLink>
        <NavLink to="/institution/statistics"><Icon>⌁</Icon><span>통계</span></NavLink>
      </nav>
      <div className="institution-help"><span>?</span><p><strong>도움이 필요하신가요?</strong>기관 전용 고객센터<br />02-1234-5678</p></div>
    </aside>
  );
}

export default InstitutionSidebar;
