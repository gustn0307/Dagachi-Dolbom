// BACKEND: 관리자 프로필은 GET /api/auth/me로 조회하고, 로그아웃은
// POST /api/auth/logout 성공 후 로그인 라우트로 이동하도록 연결합니다.
function AdminHeader(){return <header className="admin-header"><div className="admin-mobile-brand">◆ 서비스 관리자</div><div className="admin-global-search"><span>⌕</span><input aria-label="통합 검색" placeholder="사용자 또는 기관 통합 검색" /></div><button className="admin-alert" aria-label="알림">♢<i /></button><div className="admin-user"><span>A</span><div><strong>서비스 관리자</strong><small>superadmin</small></div><button>⌄</button></div></header>}
export default AdminHeader;
