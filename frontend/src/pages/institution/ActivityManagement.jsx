import { institutionApi } from "../../api/institutionApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";
// BACKEND: 활동 생성/배정/완료 처리는 /api/institution/activities에 연결하고,
// 상태 변경 후 목록과 통계 데이터를 함께 다시 조회합니다.
function ActivityManagement() {
  const {
    data = [],
    loading,
    error,
    reload,
  } = useInstitutionData(institutionApi.getActivities, []);
  if (loading || error)
    return (
      <div className="institution-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>활동 관리</p>
          <h1>돌봄 활동과 일정을 관리하세요</h1>
          <span>예정된 지원 활동을 배정하고 완료 내용을 기록합니다.</span>
        </div>
        <button className="orange-action">＋ 활동 등록</button>
      </div>
      <section className="activity-summary">
        <article>
          <div>
            <span>이번 주 활동</span>
            <strong>{data.length}건</strong>
          </div>
          <b>67%</b>
          <progress value="67" max="100" />
        </article>
        <article>
          <div>
            <span>봉사자 배정 완료</span>
            <strong>
              {data.filter((x) => x.status !== "모집 중").length}건
            </strong>
          </div>
          <b>78%</b>
          <progress value="78" max="100" />
        </article>
        <article>
          <div>
            <span>완료된 활동</span>
            <strong>9건</strong>
          </div>
          <b>50%</b>
          <progress value="50" max="100" />
        </article>
      </section>
      <section className="panel table-panel">
        <div className="panel-title activity-title">
          <div>
            <h2>다가오는 활동</h2>
            <p>일정과 담당자를 확인해 주세요.</p>
          </div>
          <div className="view-switch">
            <button className="active">목록</button>
            <button>달력</button>
          </div>
        </div>
        <div className="activity-list">
          {data.map((item, i) => (
            <article key={item.id}>
              <time>
                <b>{item.date}</b>
                <small>{item.time}</small>
              </time>
              <span className={`activity-dot dot-${i}`}>✓</span>
              <div>
                <h3>{item.title}</h3>
                <p>
                  대상자 {item.target} · {item.assignee}
                </p>
              </div>
              <i className={`table-status ${item.status.replace(" ", "-")}`}>
                {item.status}
              </i>
              <button>상세 보기</button>
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
export default ActivityManagement;
