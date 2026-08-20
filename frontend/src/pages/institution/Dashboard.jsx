import { Link } from "react-router-dom";
import { institutionApi } from "../../api/institutionApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

function Dashboard() {
  // BACKEND: 기관 식별자는 로그인 세션에서 서버가 결정하도록 하고,
  // 클라이언트가 임의의 institutionId를 보내 권한 범위를 선택하지 않게 합니다.
  const { data, loading, error, reload } = useInstitutionData(institutionApi.getDashboard, []);
  if (loading || error) return <div className="institution-page"><DataState loading={loading} error={error} onRetry={reload} /></div>;
  return <div className="institution-page">
    <div className="page-title-row"><div><p>2026년 8월 12일 수요일</p><h1>안녕하세요, 김담당님 👋</h1><span>오늘도 따뜻한 이웃 돌봄을 함께 시작해 볼까요?</span></div><Link className="orange-action" to="/institution/reports">신규 제보 확인 <b>→</b></Link></div>
    <section className="metric-grid">{data.metrics.map(item=><article className={`metric-card ${item.tone}`} key={item.key}><div className="metric-icon">{item.icon}</div><div><span>{item.label}</span><strong>{item.value}<small>{item.unit}</small></strong><p>{item.note}</p></div></article>)}</section>
    <div className="dashboard-grid"><section className="panel recent-reports"><div className="panel-title"><div><h2>최근 접수 제보</h2><p>우선 확인이 필요한 제보입니다.</p></div><Link to="/institution/reports">전체 보기 →</Link></div><div className="report-list">{data.recentReports.map(item=><article key={item.id}><span className="report-category">{item.category}</span><div><strong>{item.title}</strong><p>{item.id} · {item.place}</p></div><time>{item.receivedAt}</time><span className={`table-status ${item.status.replace(' ','-')}`}>{item.status}</span></article>)}</div></section>
      <section className="panel schedule-panel"><div className="panel-title"><div><h2>오늘의 일정</h2><p>8월 12일</p></div><button>＋</button></div><div className="schedule-list">{data.schedules.map(item=><article key={item.id}><time>{item.time}</time><div><b>{item.title}</b><p>{item.detail}</p></div></article>)}</div></section></div>
  </div>;
}
export default Dashboard;
