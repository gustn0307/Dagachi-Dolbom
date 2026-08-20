import { useMemo, useState } from "react";
import { adminApi } from "../../api/adminApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

function InstitutionManagement() {
  // BACKEND: GET /api/admin/institutions 응답을 조회합니다.
  // 기관 목록이 커지면 query/filter/page를 adminApi.getInstitutions 인자로 전달하세요.
  const { data, loading, error, reload, setData } = useInstitutionData(
    adminApi.getInstitutions,
    [],
  );
  const safeData = Array.isArray(data) ? data : [];
  const [query, setQuery] = useState("");
  const [filter, setFilter] = useState("전체");
  const [updating, setUpdating] = useState("");
  const visible = useMemo(
    () =>
      safeData.filter(
        (item) =>
          (filter === "전체" || item.status === filter) &&
          `${item.name}${item.manager}${item.id}`
            .toLowerCase()
            .includes(query.toLowerCase()),
      ),
    [safeData, query, filter],
  );
  // BACKEND: 승인/반려/정지는 PATCH /api/admin/institutions/{id}/status로 저장됩니다.
  // 반려 사유가 필요하면 모달에서 reason을 입력받아 { status, reason }을 전송하세요.
  const updateStatus = async (item, status) => {
    setUpdating(item.id);
    try {
      await adminApi.updateInstitutionStatus(item.id, status);
      setData((list) =>
        list.map((row) => (row.id === item.id ? { ...row, status } : row)),
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
          <p>INSTITUTION MANAGEMENT</p>
          <h1>기관 관리</h1>
          <span>가입 기관을 검토하고 서비스 이용 권한을 관리합니다.</span>
        </div>
        <button className="admin-primary">＋ 기관 직접 등록</button>
      </div>
      <section className="admin-kpis">
        <article>
          <span>전체 기관</span>
          <strong>
            {data.length}
            <small>개</small>
          </strong>
          <em>전국 운영 기관</em>
        </article>
        <article>
          <span>운영 중</span>
          <strong>
            {data.filter((x) => x.status === "승인").length}
            <small>개</small>
          </strong>
          <em>정상 이용</em>
        </article>
        <article className="pending">
          <span>승인 대기</span>
          <strong>
            {
              data.filter((x) => ["승인 대기", "서류 검토"].includes(x.status))
                .length
            }
            <small>개</small>
          </strong>
          <em>검토가 필요합니다</em>
        </article>
        <article>
          <span>운영 정지</span>
          <strong>
            {data.filter((x) => x.status === "운영 정지").length}
            <small>개</small>
          </strong>
          <em>관리자 조치</em>
        </article>
      </section>
      <section className="admin-panel">
        <div className="admin-toolbar">
          <div className="admin-tabs">
            {["전체", "승인 대기", "서류 검토", "승인", "운영 정지"].map(
              (item) => (
                <button
                  key={item}
                  className={filter === item ? "active" : ""}
                  onClick={() => setFilter(item)}
                >
                  {item}
                  {item === "승인 대기" && (
                    <em>
                      {data.filter((x) => x.status === "승인 대기").length}
                    </em>
                  )}
                </button>
              ),
            )}
          </div>
          <label className="admin-search">
            <span>⌕</span>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="기관명 또는 담당자 검색"
            />
          </label>
        </div>
        <div className="institution-cards">
          {visible.map((item, i) => (
            <article key={item.id}>
              <div className={`institution-mark m${i}`}>▣</div>
              <div className="institution-card-main">
                <div>
                  <span
                    className={`admin-status ${item.status.replace(" ", "-")}`}
                  >
                    {item.status}
                  </span>
                  <h2>{item.name}</h2>
                  <p>
                    {item.id} · {item.type}
                  </p>
                </div>
                <dl>
                  <div>
                    <dt>담당자</dt>
                    <dd>{item.manager}</dd>
                  </div>
                  <div>
                    <dt>연락처</dt>
                    <dd>{item.phone}</dd>
                  </div>
                  <div>
                    <dt>지역</dt>
                    <dd>{item.area}</dd>
                  </div>
                  <div>
                    <dt>가입 신청</dt>
                    <dd>{item.joinedAt}</dd>
                  </div>
                </dl>
              </div>
              <div className="institution-actions">
                {["승인 대기", "서류 검토"].includes(item.status) ? (
                  <>
                    <button
                      className="reject"
                      disabled={updating === item.id}
                      onClick={() => updateStatus(item, "운영 정지")}
                    >
                      반려
                    </button>
                    <button
                      className="approve"
                      disabled={updating === item.id}
                      onClick={() => updateStatus(item, "승인")}
                    >
                      {updating === item.id ? "처리 중" : "승인"}
                    </button>
                  </>
                ) : (
                  <button className="details">상세 관리</button>
                )}
              </div>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
export default InstitutionManagement;
