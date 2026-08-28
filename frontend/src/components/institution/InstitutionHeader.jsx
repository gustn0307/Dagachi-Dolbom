function InstitutionHeader() {
  // BACKEND: 기관명과 담당자 정보는 GET /api/auth/me 또는 인증 컨텍스트에서 받아 표시합니다.
  return (
    <header className="institution-header">
      <div className="institution-mobile-brand">
        <span>♥</span> 다같이 돌봄
      </div>
      <div className="header-search">
        <span>⌕</span>
        <input aria-label="통합 검색" placeholder="이름, 제보 번호로 검색" />
      </div>
      <div className="institution-header-user">
        <button className="notification" type="button" aria-label="알림">
          ♢<i />
        </button>
        <span className="user-avatar">김</span>
        <span>
          <strong>김담당</strong>
          <small>행복복지관</small>
        </span>
        <button className="header-more" type="button" aria-label="사용자 메뉴">
          ⌄
        </button>
      </div>
    </header>
  );
}

export default InstitutionHeader;
