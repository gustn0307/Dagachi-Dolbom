import { Link } from "react-router-dom";
import heroImage from "../../assets/public-hero-care.png";

function PublicLanding() {
  return (
    <main className="public-landing">
      <header className="public-header">
        <Link className="public-brand" to="/">
          <span>♥</span>
          <div>
            <strong>다함께 돌봄</strong>
            <small>함께 만드는 따뜻한 우리 동네</small>
          </div>
        </Link>
        <div className="public-auth-actions">
          <Link to="/login">로그인</Link>
          <Link className="public-join" to="/join">
            회원가입
          </Link>
        </div>
      </header>
      <section
        className="public-hero"
        style={{
          backgroundImage: `linear-gradient(90deg,rgba(255,250,239,.98) 0%,rgba(255,250,239,.92) 29%,rgba(255,250,239,.15) 57%,transparent 100%), url(${heroImage})`,
        }}
      >
        <div className="public-hero-copy">
          <span>우리 동네를 잇는 작은 관심</span>
          <h1>
            당신의 관심이
            <br />
            누군가의 오늘을
            <br />
            <em>지켜냅니다</em>
          </h1>
          <p>
            도움이 필요한 이웃을 발견하셨나요?
            <br />
            로그인 없이도 안전하게 제보할 수 있습니다.
          </p>
          <div>
            <Link
              className="public-report-button"
              to="/report"
            >
              로그인 없이 제보하기 <b>→</b>
            </Link>
            <a className="public-learn-link" href="#report-guide">
              제보 과정 알아보기
            </a>
          </div>
        </div>
      </section>
      <section className="public-report-section" id="report-guide">
        <div className="public-report-copy">
          <span>NEIGHBOR REPORT</span>
          <h2>
            작은 제보가
            <br />큰 변화를 만듭니다
          </h2>
          <p>
            정확한 이름이나 주소를 몰라도 괜찮습니다. 발견한 위치와 상황을
            알려주시면 가까운 지역 기관이 확인하고 필요한 도움을 연결합니다.
          </p>
          <ul>
            <li>
              <b>01</b>
              <span>
                <strong>간편하게 작성</strong>약 2분이면 제보할 수 있어요.
              </span>
            </li>
            <li>
              <b>02</b>
              <span>
                <strong>안전하게 전달</strong>정보는 담당 기관만 확인합니다.
              </span>
            </li>
            <li>
              <b>03</b>
              <span>
                <strong>신속하게 연결</strong>지역 기관이 도움을 확인합니다.
              </span>
            </li>
          </ul>
        </div>
        <div className="public-report-card">
          <span className="report-card-icon">⚑</span>
          <h2>
            도움이 필요한 이웃을
            <br />
            알려주세요
          </h2>
          <p>회원가입 없이 바로 작성할 수 있습니다.</p>
          <Link to="/report">
            비회원 제보 시작하기 <b>→</b>
          </Link>
          <small>
            생명이나 안전이 위급하면 112 또는 119에 먼저 연락해 주세요.
          </small>
        </div>
      </section>
      <footer className="public-footer">
        © 2026 다함께 돌봄 · 함께 만드는 따뜻한 우리 동네
      </footer>
    </main>
  );
}
export default PublicLanding;
