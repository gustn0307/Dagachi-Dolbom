import { Link } from "react-router-dom";

function ShieldIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 3 20 6v5c0 5.2-3.4 8.7-8 10-4.6-1.3-8-4.8-8-10V6l8-3Z" />
      <path d="m8.5 12 2.2 2.2 4.8-5" />
    </svg>
  );
}

function ReportEntry() {
  return (
    <main className="report-entry-page">
      <section className="report-entry-hero">
        <span className="entry-eyebrow">이웃을 위한 따뜻한 관심</span>
        <h1>
          로그인하지 않아도
          <br />
          <em>안전하게 제보할 수 있어요</em>
        </h1>
        <p>
          도움이 필요해 보이는 이웃을 발견하셨나요?
          <br />
          간단한 정보만으로 담당 기관에 상황을 전달할 수 있습니다.
        </p>
      </section>

      <section className="report-entry-content">
        <div className="entry-choice-grid">
          <article className="entry-choice anonymous-choice">
            <span className="choice-label">빠른 제보</span>
            <div className="choice-icon">
              <ShieldIcon />
            </div>
            <h2>비회원으로 제보하기</h2>
            <p>
              회원가입이나 로그인 없이 바로 제보할 수 있어요. 입력한 정보는 제보
              확인 목적으로만 사용됩니다.
            </p>
            <ul>
              <li>약 2분이면 작성 완료</li>
              <li>개인정보 최소 수집</li>
              <li>사진 첨부 가능</li>
            </ul>
            <Link
              className="entry-primary"
              to="/report/new"
              state={{ guest: true }}
            >
              로그인 없이 제보하기 <b>→</b>
            </Link>
          </article>

          <article className="entry-choice member-choice">
            <span className="choice-label">회원 제보</span>
            <div className="choice-icon member-icon">♡</div>
            <h2>로그인하고 제보하기</h2>
            <p>
              로그인하면 접수한 제보의 처리 상태와 이전 제보 내역을
              마이페이지에서 확인할 수 있어요.
            </p>
            <ul>
              <li>제보 처리 상태 확인</li>
              <li>이전 제보 내역 관리</li>
              <li>담당 기관 답변 확인</li>
            </ul>
            {/* BACKEND: 로그인 완료 후 redirect=/report/new로 이동하도록 로그인 페이지에 연결합니다. */}
            <Link className="entry-secondary" to="/login?redirect=/report/new">
              로그인 후 제보하기 <b>→</b>
            </Link>
          </article>
        </div>

        <div className="entry-safety-note">
          <span>!</span>
          <p>
            <strong>생명이나 안전이 위급한 상황인가요?</strong>제보 작성보다{" "}
            <b>112 또는 119</b>에 먼저 연락해 주세요.
          </p>
        </div>

        <div className="entry-process">
          <h2>제보는 이렇게 처리됩니다</h2>
          <div>
            <article>
              <b>01</b>
              <strong>내용 작성</strong>
              <span>위치와 상황을 알려주세요.</span>
            </article>
            <i>→</i>
            <article>
              <b>02</b>
              <strong>담당 기관 확인</strong>
              <span>지역 기관이 내용을 검토합니다.</span>
            </article>
            <i>→</i>
            <article>
              <b>03</b>
              <strong>지원 연결</strong>
              <span>필요한 도움을 신속히 연결합니다.</span>
            </article>
          </div>
        </div>
      </section>
    </main>
  );
}

export default ReportEntry;
