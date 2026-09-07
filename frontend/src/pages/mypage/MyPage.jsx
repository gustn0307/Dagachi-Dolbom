import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import PageHeader from "../../components/common/PageHeader";
import {
  getMyReports,
  getMyProfile,
  updateMyProfile,
  changePassword,
  withdrawUser,
} from "../../api/userApi";

const STATUS_LABELS = {
  SUBMITTED: "접수 완료",
  REVIEWING: "검토 중",
  NEED_MORE_INFO: "추가 정보 필요",
  ACCEPTED: "접수 승인",
  REJECTED: "반려",
  CLOSED: "처리 완료",
};

const GENDER_LABELS = {
  MALE: "남성",
  FEMALE: "여성",
};

// 모달 오버레이는 새 클래스를 만들지 않고 인라인 style로만 처리합니다.
const overlayStyle = {
  position: "fixed",
  inset: 0,
  background: "rgba(52, 44, 38, 0.45)",
  display: "grid",
  placeItems: "center",
  zIndex: 50,
  padding: "20px",
};

function MyPage() {
  const navigate = useNavigate();

  // ---- 최근 제보 ----
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // ---- 내 정보 (USER-01, USER-02) ----
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState("");
  const [isEditing, setIsEditing] = useState(false);
  const [editForm, setEditForm] = useState({ nickname: "", phone: "" });
  const [saving, setSaving] = useState(false);

  // ---- 비밀번호 변경 ----
  const [showPasswordModal, setShowPasswordModal] = useState(false);
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: "",
    newPassword: "",
    newPasswordConfirm: "",
  });
  const [passwordError, setPasswordError] = useState("");
  const [changingPassword, setChangingPassword] = useState(false);

  // ---- 회원 탈퇴 (USER-03) ----
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  const [withdrawPassword, setWithdrawPassword] = useState("");
  const [withdrawConfirmed, setWithdrawConfirmed] = useState(false);
  const [withdrawError, setWithdrawError] = useState("");
  const [withdrawing, setWithdrawing] = useState(false);

  useEffect(() => {
    const loadReports = async () => {
      try {
        setLoading(true);
        setError("");

        // 로그인한 사용자의 제보를 최신순으로 조회합니다.
        const data = await getMyReports({
          page: 0,
          size: 5,
          sort: "createdAt,desc",
        });

        setReports(data.content ?? []);
      } catch (requestError) {
        setError(
          requestError?.response?.data?.message ??
            "제보 내역을 불러오지 못했습니다.",
        );
      } finally {
        setLoading(false);
      }
    };

    const loadProfile = async () => {
      try {
        setProfileLoading(true);
        setProfileError("");

        const data = await getMyProfile();
        setProfile(data);
      } catch (requestError) {
        setProfileError(
          requestError?.response?.data?.message ??
            "내 정보를 불러오지 못했습니다.",
        );
      } finally {
        setProfileLoading(false);
      }
    };

    loadReports();
    loadProfile();
  }, []);

  // ---- 내 정보 수정 핸들러 ----

  const handleEditStart = () => {
    setEditForm({
      nickname: profile?.nickname ?? "",
      phone: profile?.phone ?? "",
    });
    setProfileError("");
    setIsEditing(true);
  };

  const handleEditChange = (event) => {
    const { name, value } = event.target;
    setEditForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleEditCancel = () => {
    setIsEditing(false);
  };

  const handleEditSave = async (event) => {
    event.preventDefault();

    try {
      setSaving(true);
      setProfileError("");

      const updated = await updateMyProfile({
        nickname: editForm.nickname,
        phone: editForm.phone,
      });

      setProfile(updated);
      setIsEditing(false);
    } catch (requestError) {
      setProfileError(
        requestError?.response?.data?.message ?? "정보 수정에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  // ---- 비밀번호 변경 핸들러 ----

  const openPasswordModal = () => {
    setPasswordForm({
      currentPassword: "",
      newPassword: "",
      newPasswordConfirm: "",
    });
    setPasswordError("");
    setShowPasswordModal(true);
  };

  const handlePasswordFormChange = (event) => {
    const { name, value } = event.target;
    setPasswordForm((prev) => ({ ...prev, [name]: value }));
  };

  const handleChangePassword = async (event) => {
    event.preventDefault();

    // 비밀번호 확인은 프론트에서만 검증합니다 (회원가입 화면과 동일한 방식).
    if (passwordForm.newPassword !== passwordForm.newPasswordConfirm) {
      setPasswordError("새 비밀번호가 서로 일치하지 않습니다.");
      return;
    }

    try {
      setChangingPassword(true);
      setPasswordError("");

      await changePassword(
        passwordForm.currentPassword,
        passwordForm.newPassword,
      );

      setShowPasswordModal(false);
    } catch (requestError) {
      const code = requestError?.response?.data?.code;

      if (code === "USER_400_PASSWORD_MISMATCH") {
        setPasswordError("현재 비밀번호가 일치하지 않습니다.");
      } else if (code === "USER_400_PASSWORD_SAME_AS_CURRENT") {
        setPasswordError("새 비밀번호는 현재 비밀번호와 달라야 합니다.");
      } else {
        setPasswordError(
          requestError?.response?.data?.message ??
            "비밀번호 변경에 실패했습니다.",
        );
      }
    } finally {
      setChangingPassword(false);
    }
  };

  // ---- 회원 탈퇴 핸들러 ----

  const openWithdrawModal = () => {
    setWithdrawPassword("");
    setWithdrawConfirmed(false);
    setWithdrawError("");
    setShowWithdrawModal(true);
  };

  const handleWithdraw = async (event) => {
    event.preventDefault();

    if (!withdrawConfirmed) {
      setWithdrawError("탈퇴에 동의한다는 확인이 필요합니다.");
      return;
    }

    try {
      setWithdrawing(true);
      setWithdrawError("");

      await withdrawUser(withdrawPassword);

      // 탈퇴 완료 후 로그인 화면으로 이동합니다.
      // 실제 로그아웃(토큰 제거)은 프로젝트의 AuthContext.logout()과
      // 연결해 주세요.
      navigate("/login", { replace: true });
    } catch (requestError) {
      const code = requestError?.response?.data?.code;

      if (code === "USER_409_WITHDRAWAL_BLOCKED") {
        setWithdrawError(
          "대기중이거나 승인된 신청/활동이 있어 탈퇴할 수 없어요. " +
            "봉사 참여 > 내 신청 현황에서 먼저 취소해 주세요.",
        );
      } else if (code === "USER_400_PASSWORD_MISMATCH") {
        setWithdrawError("비밀번호가 일치하지 않습니다.");
      } else {
        setWithdrawError(
          requestError?.response?.data?.message ??
            "탈퇴 처리 중 오류가 발생했습니다.",
        );
      }
    } finally {
      setWithdrawing(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow="마이페이지"
        title="반가워요"
        text="당신의 관심을 통해 이웃의 오늘이 더 안전해지고 있어요."
      />

      {/* ---- 내 정보 (USER-01, USER-02) - 기존 .recent 카드 스타일 재사용 ---- */}
      <section className="recent">
        <h2>내 정보</h2>

        {profileLoading && (
          <p style={{ color: "#897e75" }}>내 정보를 불러오는 중입니다.</p>
        )}

        {!profileLoading && profileError && !isEditing && (
          <p style={{ color: "#d9534f" }}>{profileError}</p>
        )}

        {!profileLoading && profile && !isEditing && (
          <>
            {[
              ["이름", profile.name],
              ["닉네임", profile.nickname || "설정 안 함"],
              ["이메일", profile.email],
              ["전화번호", profile.phone],
              ["성별", GENDER_LABELS[profile.gender] ?? profile.gender],
            ].map(([label, value]) => (
              <p key={label} style={{ display: "flex", gap: "16px" }}>
                <span style={{ color: "#897e75", minWidth: "72px" }}>
                  {label}
                </span>
                <span>{value}</span>
              </p>
            ))}

            <div style={{ display: "flex", gap: "8px" }}>
              <button
                type="button"
                className="secondary-action"
                onClick={handleEditStart}
              >
                정보 수정
              </button>
              <button
                type="button"
                className="secondary-action"
                onClick={openPasswordModal}
              >
                비밀번호 변경
              </button>
            </div>
          </>
        )}

        {!profileLoading && profile && isEditing && (
          <div
            className="form-card"
            style={{ margin: "18px 0 0", boxShadow: "none" }}
          >
            <form onSubmit={handleEditSave}>
              <label>
                닉네임
                <input
                  type="text"
                  name="nickname"
                  maxLength={100}
                  value={editForm.nickname}
                  onChange={handleEditChange}
                />
              </label>

              <label>
                전화번호
                <input
                  type="tel"
                  name="phone"
                  maxLength={30}
                  value={editForm.phone}
                  onChange={handleEditChange}
                />
              </label>

              <label>
                이메일 (변경 불가)
                <input
                  type="email"
                  value={profile.email}
                  disabled
                  style={{ background: "#f7f4f0", color: "#948a82" }}
                />
              </label>

              <label>
                성별 (변경 불가)
                <input
                  type="text"
                  value={GENDER_LABELS[profile.gender] ?? profile.gender}
                  disabled
                  style={{ background: "#f7f4f0", color: "#948a82" }}
                />
              </label>

              {profileError && (
                <p style={{ color: "#d9534f" }}>{profileError}</p>
              )}

              <div style={{ display: "flex", gap: "10px" }}>
                <button type="submit" className="submit" disabled={saving}>
                  {saving ? "저장 중..." : "저장"}
                </button>
                <button
                  type="button"
                  className="secondary-action"
                  onClick={handleEditCancel}
                  disabled={saving}
                >
                  취소
                </button>
              </div>
            </form>
          </div>
        )}
      </section>

      {/* ---- 비밀번호 변경 모달 - 기존 .simple-auth-card 재사용 ---- */}
      {showPasswordModal && (
        <div style={overlayStyle} role="dialog" aria-modal="true">
          <form
            className="simple-auth-card"
            style={{ width: "min(420px, 100%)" }}
            onSubmit={handleChangePassword}
          >
            <p>비밀번호 변경</p>
            <h1>비밀번호를 변경할게요</h1>

            <label>
              현재 비밀번호
              <input
                type="password"
                name="currentPassword"
                required
                value={passwordForm.currentPassword}
                onChange={handlePasswordFormChange}
              />
            </label>

            <label>
              새 비밀번호
              <input
                type="password"
                name="newPassword"
                required
                minLength={8}
                placeholder="8자 이상 입력하세요"
                value={passwordForm.newPassword}
                onChange={handlePasswordFormChange}
              />
            </label>

            <label>
              새 비밀번호 확인
              <input
                type="password"
                name="newPasswordConfirm"
                required
                minLength={8}
                value={passwordForm.newPasswordConfirm}
                onChange={handlePasswordFormChange}
              />
              {passwordForm.newPasswordConfirm &&
                passwordForm.newPassword !==
                  passwordForm.newPasswordConfirm && (
                  <span className="auth-error">
                    비밀번호가 일치하지 않습니다.
                  </span>
                )}
            </label>

            {passwordError && (
              <span className="auth-error">{passwordError}</span>
            )}

            <button type="submit" disabled={changingPassword}>
              {changingPassword ? "변경 중..." : "변경하기"}
            </button>
            <button
              type="button"
              className="secondary-action"
              style={{ marginTop: "10px" }}
              onClick={() => setShowPasswordModal(false)}
              disabled={changingPassword}
            >
              취소
            </button>
          </form>
        </div>
      )}

      {/* ---- 마일리지/통계 (기존 그대로 - 이번 범위 아님) ---- */}
      <section className="profile-grid">
        <article className="mileage">
          <span>✦</span>
          <p>나의 마일리지</p>
          <strong>
            - <small>점</small>
          </strong>
          <button type="button">내역 보기 ›</button>
        </article>

        <article className="profile-stat">
          <strong>
            -<span>회</span>
          </strong>
          <p>완료한 안부 확인</p>
        </article>

        <article className="profile-stat">
          <strong>
            -<span>명</span>
          </strong>
          <p>함께한 이웃</p>
        </article>
      </section>

      {/* ---- 최근 제보 ---- */}
      <section className="recent">
        <h2>최근 제보</h2>

        {loading && (
          <p style={{ color: "#897e75" }}>제보 내역을 불러오는 중입니다.</p>
        )}

        {!loading && error && <p style={{ color: "#d9534f" }}>{error}</p>}

        {!loading && !error && reports.length === 0 && (
          <p style={{ color: "#897e75" }}>접수한 제보가 없습니다.</p>
        )}

        {!loading &&
          !error &&
          reports.map((report) => (
            <article
              key={report.reportId}
              style={{ padding: "18px 0", borderBottom: "1px solid #eee5dc" }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: "16px",
                }}
              >
                <strong>제보 #{report.reportId}</strong>
                <span>{STATUS_LABELS[report.status] ?? report.status}</span>
              </div>

              <p style={{ margin: "8px 0 4px", color: "#4f463f" }}>
                {report.content}
              </p>

              {report.address && (
                <small style={{ color: "#897e75" }}>{report.address}</small>
              )}
            </article>
          ))}
      </section>

      {/* ---- 회원 탈퇴 ---- */}
      <section className="recent">
        <button
          type="button"
          className="secondary-action"
          onClick={openWithdrawModal}
        >
          회원 탈퇴
        </button>
      </section>

      {showWithdrawModal && (
        <div style={overlayStyle} role="dialog" aria-modal="true">
          <form
            className="simple-auth-card"
            style={{ width: "min(420px, 100%)" }}
            onSubmit={handleWithdraw}
          >
            <p>회원 탈퇴</p>
            <h1>정말 탈퇴하시겠어요?</h1>

            <span
              style={{
                display: "block",
                marginBottom: "16px",
                color: "#70665d",
                fontSize: "13px",
                lineHeight: 1.6,
              }}
            >
              탈퇴하면 계정 정보와 활동 이력에 다시 접근할 수 없습니다. 진행
              중인 신청이나 승인된 활동이 있다면 먼저 취소해야 탈퇴할 수 있어요.
            </span>

            <label>
              비밀번호 확인
              <input
                type="password"
                required
                value={withdrawPassword}
                onChange={(event) => setWithdrawPassword(event.target.value)}
              />
            </label>

            <label
              style={{
                display: "flex",
                alignItems: "flex-start",
                gap: "8px",
                fontWeight: 400,
                fontSize: "13px",
              }}
            >
              <input
                type="checkbox"
                style={{ width: "auto", marginTop: "3px" }}
                checked={withdrawConfirmed}
                onChange={(event) => setWithdrawConfirmed(event.target.checked)}
              />
              위 내용을 확인했으며 탈퇴에 동의합니다.
            </label>

            {withdrawError && (
              <span className="auth-error">{withdrawError}</span>
            )}

            <button type="submit" disabled={withdrawing}>
              {withdrawing ? "처리 중..." : "탈퇴하기"}
            </button>
            <button
              type="button"
              className="secondary-action"
              style={{ marginTop: "10px" }}
              onClick={() => setShowWithdrawModal(false)}
              disabled={withdrawing}
            >
              취소
            </button>
          </form>
        </div>
      )}
    </>
  );
}

export default MyPage;
