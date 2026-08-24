import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { signup } from "../api/authApi";

function Join() {
  const navigate = useNavigate();

  const [errorMessage, setErrorMessage] = useState("");

  const [form, setForm] = useState({
    email: "",
    password: "",
    passwordConfirm: "",
    name: "",
    nickname: "",
    phone: "",
    gender: "",
  });

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

    // 비밀번호 확인 값은 프론트에서만 검증합니다.
    // 두 값이 다르면 회원가입 API를 호출하지 않습니다.
    if (form.password !== form.passwordConfirm) {
      return;
    }

    try {
      // passwordConfirm은 백엔드 SignupRequest 필드가 아니므로
      // 실제 회원가입 요청 payload에서는 제외합니다.
      const {
        passwordConfirm,
        ...signupPayload
      } = form;

      await signup(signupPayload);

      // 회원가입 성공 후 로그인 화면으로 이동합니다.
      navigate("/login");
    } catch (error) {
      // 백엔드의 공통 에러 응답 message가 있으면 그대로 표시합니다.
      const message =
        error?.response?.data?.message ||
        "회원가입에 실패했습니다.";

      setErrorMessage(message);
    }
  };

  return (
    <main className="simple-auth-page">
      <form className="simple-auth-card" onSubmit={handleSubmit}>
        <span className="auth-mark">♥</span>
        <p>이웃을 잇다</p>
        <h1>회원가입</h1>

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
            minLength={8}
            placeholder="8자 이상 입력하세요"
            value={form.password}
            onChange={handleChange}
          />
        </label>

        <label>
          비밀번호 확인
          <input
            type="password"
            name="passwordConfirm"
            required
            minLength={8}
            placeholder="비밀번호를 한 번 더 입력하세요"
            value={form.passwordConfirm}
            onChange={handleChange}
          />

          {form.passwordConfirm &&
            form.password !== form.passwordConfirm && (
              <span className="auth-error">
                비밀번호가 일치하지 않습니다.
              </span>
            )}
        </label>

        <label>
          이름
          <input
            type="text"
            name="name"
            required
            placeholder="이름을 입력하세요"
            value={form.name}
            onChange={handleChange}
          />
        </label>

        <label>
          닉네임
          <input
            type="text"
            name="nickname"
            placeholder="닉네임을 입력하세요"
            value={form.nickname}
            onChange={handleChange}
          />
        </label>

        <label>
          전화번호
          <input
            type="tel"
            name="phone"
            required
            placeholder="010-1234-5678"
            value={form.phone}
            onChange={handleChange}
          />
        </label>

        <label>
          성별
          <select
            name="gender"
            required
            value={form.gender}
            onChange={handleChange}
          >
            <option value="">선택해주세요</option>
            <option value="MALE">남성</option>
            <option value="FEMALE">여성</option>
          </select>
        </label>

        {errorMessage && (
          <p className="auth-error">
            {errorMessage}
          </p>
        )}

        <button type="submit">
          회원가입
        </button>

        <small>
          이미 회원이신가요? <Link to="/login">로그인</Link>
        </small>
      </form>
    </main>
  );
}

export default Join;