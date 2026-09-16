import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import { institutionApi } from "../../api/institutionApi";
import { DataState } from "../../hooks/useInstitutionData";
import { usePolling } from "../../hooks/usePolling";

const PERIODS = [
  { key: "DAILY", label: "일" },
  { key: "MONTHLY", label: "월" },
  { key: "YEARLY", label: "년" },
];

const STATUS_LABEL = {
  RECRUITING: "모집 중",
  READY: "모집 완료",
  IN_PROGRESS: "진행 중",
  COMPLETED: "완료",
  CANCELED: "취소",
};

const formatDate = (value) =>
  new Intl.DateTimeFormat("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));

function Dashboard() {
  const [period, setPeriod] = useState("MONTHLY");
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [carePriority, setCarePriority] = useState(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiError, setAiError] = useState("");
  const [showAllPriorities, setShowAllPriorities] = useState(false);

  const loadDashboard = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      setData(await institutionApi.getDashboard(period));
    } catch (requestError) {
      setError(requestError);
    } finally {
      setLoading(false);
    }
  }, [period]);

  useEffect(() => {
    loadDashboard();
  }, [loadDashboard]);
    /*
   * 현재 선택한 기간의 대시보드 현황을 조용히 갱신합니다.
   * AI 돌봄 우선순위 분석은 자동으로 다시 실행하지 않습니다.
   */
  const pollDashboard = useCallback(async () => {
    try {
      const latestData = await institutionApi.getDashboard(period);
      setData(latestData);
    } catch {
      // 폴링 실패 시 기존 화면 데이터를 유지합니다.
    }
  }, [period]);

  usePolling(pollDashboard, {
    interval: 10000,
    enabled: Boolean(data) && !loading && !error,
    immediate: false,
    refreshOnFocus: true,
  });

  const analyzeCarePriority = async () => {
    try {
      setAiLoading(true);
      setAiError("");
      setCarePriority(await institutionApi.getCarePriorities());
      setShowAllPriorities(false);
    } catch (requestError) {
      setAiError(
        requestError?.response?.data?.message ??
          "AI 분석 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해주세요.",
      );
    } finally {
      setAiLoading(false);
    }
  };

  if (loading || error || !data) {
    return (
      <div className="institution-page">
        <DataState loading={loading} error={error} onRetry={loadDashboard} />
      </div>
    );
  }

  const { summary } = data;
  const priorityItems = carePriority?.items ?? [];
  const visiblePriorityItems = showAllPriorities
    ? priorityItems
    : priorityItems.slice(0, 3);
  const metrics = [
    {
      key: "volunteers",
      label: "참여 봉사자",
      value: summary.volunteerCount,
      icon: "♧",
      tone: "orange",
      link: "/institution/volunteers",
    },
    {
      key: "recipients",
      label: "돌봄 대상자",
      value: summary.careRecipientCount,
      icon: "♡",
      tone: "green",
      link: "/institution/care-targets",
    },
    {
      key: "activities",
      label: "등록 활동",
      value: summary.totalActivityCount,
      icon: "✓",
      tone: "blue",
      link: "/institution/activities",
    },
    {
      key: "applications",
      label: "승인 대기 신청",
      value: summary.pendingApplicationCount,
      icon: "!",
      tone: "purple",
      link: "/institution/activities",
    },
    {
      key: "inProgress",
      label: "진행 중 돌봄",
      value: summary.inProgressActivityCount,
      icon: "▶",
      tone: "orange",
      link: "/institution/activities",
    },
    {
      key: "completed",
      label: "완료된 돌봄",
      value: summary.completedActivityCount,
      icon: "●",
      tone: "green",
      link: "/institution/activities",
    },
  ];

  return (
    <div className="institution-page dashboard-page">
      <div className="page-title-row">
        <div>
          <p>
            {new Intl.DateTimeFormat("ko-KR", { dateStyle: "full" }).format(
              new Date(),
            )}
          </p>
          <h1>안녕하세요, {data.managerName}님 👋</h1>
          <span>오늘 확인할 돌봄 현황과 업무를 한눈에 확인하세요.</span>
        </div>
        <Link className="orange-action" to="/institution/reports">
          신규 제보 확인 <b>→</b>
        </Link>
      </div>
      <section className="panel dashboard-ai-priority">
        <div className="panel-title">
          <div>
            <h2>AI 돌봄 우선 확인</h2>
            <p>
              활동 이력과 체크리스트를 바탕으로 먼저 확인할 대상자를 추천합니다.
            </p>
          </div>
          <button
            type="button"
            onClick={analyzeCarePriority}
            disabled={aiLoading}
          >
            {aiLoading
              ? "분석 중..."
              : carePriority
                ? "다시 분석"
                : "AI 분석하기"}
          </button>
        </div>

        {aiError && <p className="dashboard-ai-error">{aiError}</p>}

        {!carePriority && !aiError && (
          <p className="dashboard-ai-empty">
            버튼을 눌러 돌봄 공백 우선순위를 확인하세요.
          </p>
        )}

        {carePriority?.items?.length === 0 && (
          <p className="dashboard-ai-empty">
            현재 우선 확인이 필요한 대상자가 없습니다.
          </p>
        )}

        {carePriority?.items?.length > 0 && (
          <div className="dashboard-ai-list">
            {visiblePriorityItems.map((item) => (
              <article key={item.recipientId}>
                <div className="dashboard-ai-person">
                  <span className={`risk-${item.riskLevel.toLowerCase()}`}>
                    {item.riskLevel}
                  </span>
                  <div>
                    <strong>{item.recipientName}</strong>
                    <small>우선순위 점수 {item.score}점</small>
                  </div>
                </div>
                <ul>
                  {item.reasons.map((reason) => (
                    <li key={reason}>{reason}</li>
                  ))}
                </ul>
                <p>
                  <b>권장 조치</b>
                  {item.recommendedAction}
                </p>
                <Link to={`/institution/care-targets/${item.recipientId}`}>
                  대상자 상세 보기 →
                </Link>
              </article>
            ))}
          </div>
        )}

        {priorityItems.length > 3 && (
          <button
            className="dashboard-ai-toggle"
            type="button"
            onClick={() => setShowAllPriorities((current) => !current)}
          >
            {showAllPriorities
              ? "상위 3명만 보기"
              : `전체 추천 ${priorityItems.length}명 보기`}
          </button>
        )}

        {carePriority?.model && (
          <small className="dashboard-ai-notice">
            AI 분석은 참고용이며 최종 판단은 기관 담당자가 수행합니다.
          </small>
        )}
      </section>

      <section className="dashboard-metric-grid">
        {metrics.map((item) => (
          <Link
            className={`metric-card ${item.tone}`}
            to={item.link}
            key={item.key}
          >
            <div className="metric-icon">{item.icon}</div>
            <div>
              <span>{item.label}</span>
              <strong>
                {item.value}
                <small>건</small>
              </strong>
            </div>
          </Link>
        ))}
      </section>

      <section className="panel dashboard-chart-panel">
        <div className="panel-title">
          <div>
            <h2>돌봄 완료 추이</h2>
            <p>기관에서 최종 승인한 활동기록을 기준으로 집계합니다.</p>
          </div>
          <div className="dashboard-period-tabs" aria-label="그래프 집계 기간">
            {PERIODS.map((item) => (
              <button
                className={period === item.key ? "active" : ""}
                key={item.key}
                onClick={() => setPeriod(item.key)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </div>
        </div>
        <div className="dashboard-chart">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={data.completedTrend}
              margin={{ top: 12, right: 22, left: -18, bottom: 0 }}
            >
              <CartesianGrid
                stroke="#eee9e4"
                strokeDasharray="4 4"
                vertical={false}
              />
              <XAxis
                dataKey="label"
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#8b837c", fontSize: 11 }}
              />
              <YAxis
                allowDecimals={false}
                axisLine={false}
                tickLine={false}
                tick={{ fill: "#8b837c", fontSize: 11 }}
              />
              <Tooltip formatter={(value) => [`${value}건`, "완료 활동"]} />
              <Line
                type="monotone"
                dataKey="count"
                stroke="#f36f2b"
                strokeWidth={3}
                dot={{ r: 3, fill: "#f36f2b" }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </section>

      <div className="dashboard-bottom-grid">
        <section className="panel dashboard-tasks">
          <div className="panel-title">
            <div>
              <h2>지금 처리할 업무</h2>
              <p>대기 중인 업무부터 확인하세요.</p>
            </div>
          </div>
          <Link to="/institution/reports">
            <span>미배정 제보</span>
            <strong>{summary.unassignedReportCount}건</strong>
            <b>→</b>
          </Link>
          <Link to="/institution/activities">
            <span>승인 대기 신청</span>
            <strong>{summary.pendingApplicationCount}건</strong>
            <b>→</b>
          </Link>
          <Link to="/institution/activities">
            <span>활동 인증 대기</span>
            <strong>{summary.pendingRecordReviewCount}건</strong>
            <b>→</b>
          </Link>
        </section>

        <section className="panel dashboard-upcoming">
          <div className="panel-title">
            <div>
              <h2>다가오는 돌봄 일정</h2>
              <p>모집 중이거나 모집 완료된 최근 일정입니다.</p>
            </div>
            <Link to="/institution/activities">전체 보기 →</Link>
          </div>
          {data.upcomingActivities.length === 0 ? (
            <p className="dashboard-empty">예정된 돌봄 활동이 없습니다.</p>
          ) : (
            data.upcomingActivities.map((activity) => (
              <Link
                to={`/institution/activities/${activity.activityId}`}
                key={activity.activityId}
              >
                <time>{formatDate(activity.scheduledAt)}</time>
                <div>
                  <strong>{activity.recipientName} 돌봄 활동</strong>
                  <span>
                    승인 {activity.approvedPeople}/{activity.requiredPeople}명
                  </span>
                </div>
                <em>{STATUS_LABEL[activity.status] ?? activity.status}</em>
              </Link>
            ))
          )}
        </section>
      </div>

      
    </div>
  );
}

export default Dashboard;
