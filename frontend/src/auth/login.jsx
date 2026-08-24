import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { getMe, login } from "../api/authApi";
import { useAuth } from "./AuthContext";

function Login() {
  const navigate = useNavigate();
  const [params] = useSearchParams();

  const { setUser } = useAuth();

  // RequireAuth에서 로그인 페이지로 보낸 경우,
  // 로그인 성공 후 원래 접근하려던 페이지로 복귀하기 위한 경로입니다.
  const redirect = params.get("redirect");

  const [form, setForm] = useState({
    email: "",
    password: "",
  });

  const [errorMessage, setErrorMessage] = useState("");

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((prev) => ({
      ...prev,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    setErrorMessage("");

    try {
      // 로그인 API에서 발급받은 Access Token을 현재 브라우저 세션에 저장합니다.
      // api.js의 Axios interceptor가 이후 요청마다 이 토큰을 Authorization 헤더에 첨부합니다.
      const response = await login(form);
      sessionStorage.setItem("accessToken", response.accessToken);

      // 토큰 저장 직후 /api/auth/me를 호출해
      // 실제 인증된 사용자 정보를 AuthContext에 반영합니다.
      const user = await getMe();
      setUser(user);

      // 인증이 필요한 페이지에서 로그인 화면으로 이동한 경우
      // 로그인 성공 후 해당 페이지로 다시 돌아갑니다.
      if (redirect) {
        navigate(redirect, { replace: true });
        return;
      }

      // 직접 로그인한 경우에는 사용자 역할에 맞는 기본 화면으로 이동합니다.
      if (user.role === "ADMIN") {
        navigate("/admin", { replace: true });
        return;
      }

      if (user.role === "INSTITUTION") {
        navigate("/institution", { replace: true });
        return;
      }

      navigate("/home", { replace: true });
    } catch (error) {
      // 백엔드의 공통 에러 응답 message가 있으면 그대로 표시합니다.
      const message =
        error?.response?.data?.message ||
        "로그인에 실패했습니다.";

      setErrorMessage(message);
    }
  };

  return (
    <main className="simple-auth-page">
      <form className="simple-auth-card" onSubmit={handleSubmit}>
        <span className="auth-mark">♥</span>
        <p>이웃을 잇다</p>
        <h1>로그인</h1>

        <label>
          이메일
          <input
            type="email"
            name="email"
            required
            placeholder="example@email.com"
            value={form.email}
            onChange={handleChange}
          />
        </label>

        <label>
          비밀번호
          <input
            type="password"
            name="password"
            required
            placeholder="비밀번호를 입력하세요"
            value={form.password}
            onChange={handleChange}
          />
        </label>

        {errorMessage && (
          <p className="auth-error">
            {errorMessage}
          </p>
        )}

        <button type="submit">
          로그인하고 계속하기
        </button>

        <small>
          아직 회원이 아니신가요? <Link to="/join">회원가입</Link>
        </small>
      </form>
    </main>
  );
}

export default Login;