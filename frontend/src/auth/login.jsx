import { Link, useNavigate, useSearchParams } from "react-router-dom";

function Login() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirect = params.get("redirect") || "/home";
  const handleSubmit = (event) => {
    event.preventDefault();
    // BACKEND: POST /api/auth/login에 이메일과 비밀번호를 전송하고 인증 성공 후 이동합니다.
    navigate(redirect, { replace: true });
  };
  return (
    <main className="simple-auth-page">
      <form className="simple-auth-card" onSubmit={handleSubmit}>
        <span className="auth-mark">♥</span>
        <p>이웃을 잇다</p>
        <h1>로그인</h1>
        <label>
          이메일
          <input type="email" required placeholder="example@email.com" />
        </label>
        <label>
          비밀번호
          <input type="password" required placeholder="비밀번호를 입력하세요" />
        </label>
        <button type="submit">로그인하고 계속하기</button>
        <small>
          아직 회원이 아니신가요? <Link to="/join">회원가입</Link>
        </small>
      </form>
    </main>
  );
}
export default Login;
