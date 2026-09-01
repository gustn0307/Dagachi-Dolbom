import { useState } from "react";
import { institutionApi } from "../../api/institutionApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

function Statistics() {
  const [period, setPeriod] = useState("6months");
  // BACKEND: period 변경 시 GET /api/institution/statistics?period=...를 다시 호출합니다.
  // 서버는 로그인한 기관 범위로 집계하고 monthlyReports/categories/statusCounts를 반환합니다.
  const { data, loading, error, reload } = useInstitutionData(
    () => institutionApi.getStatistics(period),
    [period],
  );
  if (loading || error)
    return (
      <div className="institution-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  const max = Math.max(...data.monthlyReports.map((item) => item.received));
  const total = data.categories.reduce((sum, item) => sum + item.value, 0);
  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>통계</p>
          <h1>기관 운영 현황을 확인하세요</h1>
          <span>제보 접수부터 돌봄 연계까지 주요 지표를 분석합니다.</span>
        </div>
        <select
          className="period-select"
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
        >
          <option value="6months">최근 6개월</option>
          <option value="year">최근 1년</option>
        </select>
      </div>
      <section className="stats-summary">
        <article>
          <span>누적 제보</span>
          <strong>
            {data.summary.totalReports}
            <small>건</small>
          </strong>
          <em>↗ 18.2%</em>
        </article>
        <article>
          <span>처리 완료</span>
          <strong>
            {data.summary.completedReports}
            <small>건</small>
          </strong>
          <em>↗ 12.4%</em>
        </article>
        <article>
          <span>완료율</span>
          <strong>
            {data.summary.completionRate}
            <small>%</small>
          </strong>
          <em>목표 85%</em>
        </article>
        <article>
          <span>평균 초기 대응</span>
          <strong>
            {data.summary.averageResponseHours}
            <small>시간</small>
          </strong>
          <em>↓ 0.8시간</em>
        </article>
      </section>
      <div className="stats-grid">
        <section className="panel chart-panel">
          <div className="panel-title">
            <div>
              <h2>월별 제보 처리 현황</h2>
              <p>접수 건수와 완료 건수를 비교합니다.</p>
            </div>
            <div className="chart-legend">
              <span className="received">접수</span>
              <span className="completed">완료</span>
            </div>
          </div>
          <div className="bar-chart">
            {data.monthlyReports.map((item) => (
              <div className="bar-group" key={item.month}>
                <div className="bars">
                  <i
                    style={{ height: `${(item.received / max) * 100}%` }}
                    title={`접수 ${item.received}건`}
                  ></i>
                  <i
                    style={{ height: `${(item.completed / max) * 100}%` }}
                    title={`완료 ${item.completed}건`}
                  ></i>
                </div>
                <span>{item.month}</span>
              </div>
            ))}
          </div>
        </section>
        <section className="panel category-panel">
          <div className="panel-title">
            <div>
              <h2>지원 유형 분포</h2>
              <p>누적 제보 {total}건 기준</p>
            </div>
          </div>
          <div className="donut-wrap">
            <div
              className="donut"
              style={{
                background: `conic-gradient(${data.categories.map((item, index) => `${item.color} ${(data.categories.slice(0, index).reduce((s, x) => s + x.value, 0) / total) * 100}% ${(data.categories.slice(0, index + 1).reduce((s, x) => s + x.value, 0) / total) * 100}%`).join(",")})`,
              }}
            >
              <span>
                <strong>{total}</strong>전체 제보
              </span>
            </div>
            <div className="category-legend">
              {data.categories.map((item) => (
                <p key={item.label}>
                  <i style={{ background: item.color }}></i>
                  <span>{item.label}</span>
                  <b>{Math.round((item.value / total) * 100)}%</b>
                </p>
              ))}
            </div>
          </div>
        </section>
      </div>
      <section className="panel status-overview">
        <div className="panel-title">
          <div>
            <h2>현재 제보 처리 단계</h2>
            <p>실시간 업무 적재 현황입니다.</p>
          </div>
        </div>
        <div>
          {data.statusCounts.map((item, index) => (
            <article key={item.label}>
              <span>{item.label}</span>
              <strong>
                {item.value}
                <small>건</small>
              </strong>
              {index < data.statusCounts.length - 1 && <b>→</b>}
            </article>
          ))}
        </div>
      </section>
    </div>
  );
}
export default Statistics;
