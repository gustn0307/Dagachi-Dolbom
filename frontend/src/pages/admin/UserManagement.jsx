import { useMemo, useState } from "react";
import { adminApi } from "../../api/adminApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

function UserManagement() {
  // BACKEND: adminApi.getUsers가 GET /api/admin/users 결과를 가져옵니다.
  // 운영 데이터가 많아지면 query/status/page를 API 파라미터로 넘겨 서버 검색으로 바꾸세요.
  const { data, loading, error, reload, setData } = useInstitutionData(
    adminApi.getUsers,
    [],
  );
  const safeData = Array.isArray(data) ? data : [];
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("전체");
  const [updating, setUpdating] = useState("");
  const visible = useMemo(
    () =>
      safeData.filter(
        (user) =>
          (status === "전체" || user.status === status) &&
          `${user.name}${user.email}${user.id}`
            .toLowerCase()
            .includes(query.toLowerCase()),
      ),
    [safeData, query, status],
  );
  // BACKEND: 상태 변경 성공 후 현재 목록만 낙관적으로 갱신합니다.
  // 서버가 변경된 사용자 전체 객체를 반환한다면 그 응답으로 item을 교체하세요.
  const toggleStatus = async (user) => {
    setUpdating(user.id);
    try {
      const next = user.status === "활성" ? "정지" : "활성";
      await adminApi.updateUserStatus(user.id, next);
      setData((list) =>
        list.map((item) =>
          item.id === user.id ? { ...item, status: next } : item,
        ),
      );
    } finally {
      setUpdating("");
    }
  };
  if (loading || error)
    return (
      <div className="admin-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  return (
    <div className="admin-page">
      <div className="admin-page-title">
        <div>
          <p>USER MANAGEMENT</p>
          <h1>일반 사용자 관리</h1>
          <span>가입 사용자와 서비스 이용 상태를 관리합니다.</span>
        </div>
        <button className="admin-export">⇩ 목록 내보내기</button>
      </div>
      <section className="admin-kpis">
        <article>
          <span>전체 사용자</span>
          <strong>
            {data.length}
            <small>명</small>
          </strong>
          <em>이번 달 +128</em>
        </article>
        <article>
          <span>활성 사용자</span>
          <strong>
            {data.filter((x) => x.status === "활성").length}
            <small>명</small>
          </strong>
          <em>최근 30일 기준</em>
        </article>
        <article>
          <span>신규 가입</span>
          <strong>
            42<small>명</small>
          </strong>
          <em>이번 주</em>
        </article>
        <article>
          <span>이용 정지</span>
          <strong>
            {data.filter((x) => x.status === "정지").length}
            <small>명</small>
          </strong>
          <em>검토 필요</em>
        </article>
      </section>
      <section className="admin-panel">
        <div className="admin-toolbar">
          <div className="admin-tabs">
            {["전체", "활성", "정지"].map((item) => (
              <button
                key={item}
                className={status === item ? "active" : ""}
                onClick={() => setStatus(item)}
              >
                {item}
              </button>
            ))}
          </div>
          <label className="admin-search">
            <span>⌕</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="이름, 이메일, 사용자 번호 검색"
            />
          </label>
          <button className="admin-filter">☷ 필터</button>
        </div>
        <div className="admin-user-table">
          <div className="admin-table-head">
            <span>사용자</span>
            <span>연락처</span>
            <span>가입일</span>
            <span>제보</span>
            <span>봉사</span>
            <span>상태</span>
            <span></span>
          </div>
          {visible.map((user, i) => (
            <article key={user.id}>
              <div className="admin-user-identity">
                <div className={`admin-avatar a${i}`}>{user.name[0]}</div>
                <div className="admin-user-info">
                  <strong>{user.name}</strong>
                  <small>
                    {user.id} · {user.email}
                  </small>
                </div>
              </div>
              <span className="admin-phone">{user.phone}</span>
              <time>{user.joinedAt}</time>
              <b>{user.reports}건</b>
              <b>{user.volunteerHours}시간</b>
              <i className={`admin-status ${user.status}`}>{user.status}</i>
              <button
                className="row-menu"
                onClick={() => toggleStatus(user)}
                disabled={updating === user.id}
              >
                {updating === user.id
                  ? "…"
                  : user.status === "활성"
                    ? "정지"
                    : "해제"}
              </button>
            </article>
          ))}
        </div>
        <div className="admin-table-footer">
          <span>총 {visible.length}명의 사용자</span>
          <div>
            <button>‹</button>
            <button className="active">1</button>
            <button>2</button>
            <button>›</button>
          </div>
        </div>
      </section>
    </div>
  );
}
export default UserManagement;
