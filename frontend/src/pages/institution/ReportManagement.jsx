import { useCallback, useState } from "react";
import { useNavigate } from "react-router-dom";
import { usePolling } from "../../hooks/usePolling";

import { institutionApi } from "../../api/institutionApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

const STATUS_OPTIONS = [
  {
    value: "",
    label: "전체 상태",
  },
  {
    value: "SUBMITTED",
    label: "접수",
  },
  {
    value: "REVIEWING",
    label: "검토 중",
  },
  {
    value: "NEED_MORE_INFO",
    label: "추가 정보 필요",
  },
  {
    value: "ACCEPTED",
    label: "접수 승인",
  },
  {
    value: "REJECTED",
    label: "반려",
  },
  {
    value: "CLOSED",
    label: "종결",
  },
];

const STATUS_LABELS = {
  SUBMITTED: "접수",
  REVIEWING: "검토 중",
  NEED_MORE_INFO: "추가 정보 필요",
  ACCEPTED: "접수 승인",
  REJECTED: "반려",
  CLOSED: "종결",
};

function formatDate(value) {
  if (!value) {
    return "-";
  }

  return new Date(value).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

function formatDistance(value) {
  if (value === null || value === undefined) {
    return "거리 정보 없음";
  }

  return `약 ${Number(value).toFixed(1)}km`;
}

function getErrorMessage(error) {
  return (
    error?.response?.data?.message ||
    error?.message ||
    "요청을 처리하지 못했습니다."
  );
}

function ReportManagement() {
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState("unassigned");

  const [page, setPage] = useState(0);

  const [status, setStatus] = useState("");

  const [from, setFrom] = useState("");

  const [to, setTo] = useState("");

  const [assigningReportId, setAssigningReportId] = useState(null);

  const [analyzingReportId, setAnalyzingReportId] = useState(null);

  const [duplicateAnalysis, setDuplicateAnalysis] = useState(null);

  const { data, loading, error, reload, setData } = useInstitutionData(() => {
    const params = {
      page,
      size: 20,
      status: status || undefined,
      from: from || undefined,
      to: to || undefined,
    };

    if (activeTab === "unassigned") {
      return institutionApi.getUnassignedReports(params);
    }

    return institutionApi.getReports(params);
  }, [activeTab, page, status, from, to]);

  const [retrying, setRetrying] = useState(false);
  const [retryMessage, setRetryMessage] = useState("");

  const pollReports = useCallback(async () => {
    try {
      const params = {
        page,
        size: 20,
        status: status || undefined,
        from: from || undefined,
        to: to || undefined,
      };

      const latestData =
        activeTab === "unassigned"
          ? await institutionApi.getUnassignedReports(params)
          : await institutionApi.getReports(params);

      setData(latestData);
    } catch {
      // 폴링 실패 시 기존 목록을 유지합니다.
    }
  }, [activeTab, from, page, setData, status, to]);

  usePolling(pollReports, {
    interval: 5000,
    enabled: !loading && !error && assigningReportId === null && !retrying,
    immediate: false,
    refreshOnFocus: true,
  });

  const reports = Array.isArray(data?.content) ? data.content : [];

  const totalElements = data?.totalElements ?? 0;

  const totalPages = data?.totalPages ?? 0;

  const isFirst = data?.first ?? true;

  const isLast = data?.last ?? true;

  const changeTab = (tab) => {
    setActiveTab(tab);
    setPage(0);
    setStatus("");
    setFrom("");
    setTo("");
  };

  const handleStatusChange = (event) => {
    setStatus(event.target.value);
    setPage(0);
  };

  const handleFromChange = (event) => {
    setFrom(event.target.value);
    setPage(0);
  };

  const handleToChange = (event) => {
    setTo(event.target.value);
    setPage(0);
  };

  const resetFilters = () => {
    setStatus("");
    setFrom("");
    setTo("");
    setPage(0);
  };

  const handleAssignReport = async (reportId) => {
    const confirmed = window.confirm(
      `제보 #${reportId}을(를) 우리 기관의 관할로 지정하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      setAssigningReportId(reportId);

      await institutionApi.assignReport(reportId);

      window.dispatchEvent(new Event("institution-report-count-changed"));

      window.alert("제보를 우리 기관의 관할로 지정했습니다.");

      if (reports.length === 1 && page > 0) {
        setPage((current) => Math.max(current - 1, 0));
      } else {
        await reload();
      }
    } catch (assignError) {
      const statusCode = assignError?.response?.status;

      const errorCode = assignError?.response?.data?.code;

      if (statusCode === 409 || errorCode === "REPORT_409_ALREADY_ASSIGNED") {
        window.alert(
          "다른 기관이 먼저 관할로 지정한 제보입니다. 목록을 다시 불러옵니다.",
        );

        await reload();

        window.dispatchEvent(new Event("institution-report-count-changed"));

        return;
      }

      window.alert(getErrorMessage(assignError));
    } finally {
      setAssigningReportId(null);
    }
  };

  const handleOpenDetail = (reportId) => {
    navigate(`/institution/reports/${reportId}`);
  };

  const handleDuplicateAnalysis = async (reportId) => {
    try {
      setAnalyzingReportId(reportId);
      setDuplicateAnalysis(null);

      const result = await institutionApi.analyzeDuplicateReport(reportId);

      setDuplicateAnalysis({
        reportId,
        candidates: Array.isArray(result?.candidates) ? result.candidates : [],
      });
    } catch (analysisError) {
      window.alert(getErrorMessage(analysisError));
    } finally {
      setAnalyzingReportId(null);
    }
  };

  const handleRetryMissingTitles = async () => {
    const scope = activeTab === "unassigned" ? "UNASSIGNED" : "MY_INSTITUTION";

    setRetrying(true);
    setRetryMessage("");

    try {
      const result = await institutionApi.retryMissingReportAiTitles(scope);

      const base = `요약 ${result.targetCount}건 중 ${result.succeededCount}건 완료`;
      const fail =
        result.failedCount > 0 ? ` (${result.failedCount}건 실패)` : "";
      const more = result.hasMore
        ? " · 남은 건이 있어요, 한 번 더 눌러주세요."
        : "";

      setRetryMessage(base + fail + more);

      await reload();
    } catch (retryError) {
      window.alert(getErrorMessage(retryError));
    } finally {
      setRetrying(false);
    }
  };

  if (loading || error) {
    return (
      <div className="institution-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>제보 관리</p>

          <h1>접수된 제보를 확인하세요</h1>

          <span>미배정 제보를 확인하고 기관에 배정된 제보를 관리합니다.</span>
        </div>
      </div>

      <section className="panel table-panel">
        <div className="report-main-tabs">
          <button
            type="button"
            className={activeTab === "unassigned" ? "active" : ""}
            onClick={() => changeTab("unassigned")}
          >
            미배정 제보
          </button>

          <button
            type="button"
            className={activeTab === "assigned" ? "active" : ""}
            onClick={() => changeTab("assigned")}
          >
            내 기관 제보
          </button>
        </div>

        <div className="report-filter-bar">
          <select
            value={status}
            aria-label="제보 상태"
            onChange={handleStatusChange}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>

          <label>
            <span>시작일</span>

            <input
              type="date"
              value={from}
              max={to || undefined}
              onChange={handleFromChange}
            />
          </label>

          <label>
            <span>종료일</span>

            <input
              type="date"
              value={to}
              min={from || undefined}
              onChange={handleToChange}
            />
          </label>

          <button
            type="button"
            className="report-filter-reset"
            onClick={resetFilters}
          >
            초기화
          </button>
          <button
            type="button"
            className="report-filter-reset"
            disabled={retrying}
            onClick={handleRetryMissingTitles}
          >
            {retrying ? "재생성 중..." : "AI 요약 재생성"}
          </button>
          {retryMessage && (
            <div className="report-retry-message">{retryMessage}</div>
          )}
        </div>

        <div
          className={
            activeTab === "unassigned"
              ? "report-management-table unassigned"
              : "report-management-table assigned"
          }
        >
          {activeTab === "unassigned" ? (
            <div className="report-table-head">
              <span>제보 번호</span>
              <span>제보 내용</span>
              <span>지역</span>
              <span>거리</span>
              <span>접수일</span>
              <span>상태</span>
              <span></span>
            </div>
          ) : (
            <div className="report-table-head">
              <span>제보 번호</span>
              <span>제보 내용</span>
              <span>주소</span>
              <span>접수일</span>
              <span>상태</span>
              <span></span>
            </div>
          )}

          {reports.length === 0 ? (
            <div className="report-empty-state">
              {activeTab === "unassigned"
                ? "현재 미배정 제보가 없습니다."
                : "내 기관에 배정된 제보가 없습니다."}
            </div>
          ) : activeTab === "unassigned" ? (
            reports.map((report) => {
              const statusLabel = STATUS_LABELS[report.status] ?? report.status;

              const isAssigning = assigningReportId === report.reportId;

              return (
                <article key={report.reportId}>
                  <span className="id-cell">#{report.reportId}</span>

                  <span className="main-cell">
                    <strong>{report.aiSummary ?? report.contentPreview}</strong>
                  </span>

                  <span>{report.region || "-"}</span>

                  <span>{formatDistance(report.distanceKm)}</span>

                  <span>{formatDate(report.createdAt)}</span>

                  <span>
                    <i className="table-status">{statusLabel}</i>
                  </span>

                  <button
  type="button"
  className="report-assign-button"
  disabled={assigningReportId !== null}
  onClick={() => handleAssignReport(report.reportId)}
>
  {isAssigning ? "처리 중" : "관할 지정"}
</button>
                </article>
              );
            })
          ) : (
            reports.map((report) => {
              const statusLabel = STATUS_LABELS[report.status] ?? report.status;

              return (
                <article key={report.reportId}>
                  <span className="id-cell">#{report.reportId}</span>

                  <span className="main-cell">
                    <strong>{report.aiSummary ?? report.content}</strong>
                  </span>

                  <span>{report.address || "-"}</span>

                  <span>{formatDate(report.createdAt)}</span>

                  <span>
                    <i className="table-status">{statusLabel}</i>
                  </span>

                 <div className="report-row-actions">
  <button
    type="button"
    className="report-detail-button"
    disabled={analyzingReportId !== null}
    onClick={() =>
      handleDuplicateAnalysis(report.reportId)
    }
  >
    {analyzingReportId === report.reportId
      ? "분석 중..."
      : "유사 제보"}
  </button>

  <button
    type="button"
    className="report-detail-button"
    onClick={() =>
      handleOpenDetail(report.reportId)
    }
  >
    상세 보기
  </button>
</div>
                </article>
              );
            })
          )}
        </div>

        {/* 바로 여기에 유사 제보 결과 코드 추가 */}
        {duplicateAnalysis && (
          <div className="duplicate-analysis-result">
            <div className="duplicate-analysis-header">
              <div>
                <strong>
                  제보 #{duplicateAnalysis.reportId} 유사 제보 분석
                </strong>
                <p>최근 접수된 제보 중 내용이 비슷한 결과입니다.</p>
              </div>

              <button type="button" onClick={() => setDuplicateAnalysis(null)}>
                닫기
              </button>
            </div>

            {duplicateAnalysis.candidates.length === 0 ? (
              <div className="report-empty-state">유사한 제보가 없습니다.</div>
            ) : (
              <div className="duplicate-candidate-list">
                {duplicateAnalysis.candidates.map((candidate) => (
                  <div
                    className="duplicate-candidate-item"
                    key={candidate.reportId}
                    >
                    <span>제보 #{candidate.reportId}</span>

                    <strong>
                      {candidate.contentPreview || "제보 내용이 없습니다."}
                    </strong>

                    <span>
                      유사도{" "}
                      {Math.round(Number(candidate.similarity ?? 0) * 100)}%
                    </span>

                    <span>{formatDistance(candidate.distanceKm)}</span>

                    <span>{formatDate(candidate.createdAt)}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {totalPages > 0 && (
          <div className="table-footer care-pagination">
            <span>
              전체 {totalElements}건 · {page + 1}/{totalPages} 페이지
            </span>

            <div>
              <button
                type="button"
                disabled={isFirst}
                onClick={() => setPage((current) => Math.max(current - 1, 0))}
              >
                이전
              </button>

              {Array.from(
                {
                  length: totalPages,
                },
                (_, index) => index,
              ).map((pageNumber) => (
                <button
                  type="button"
                  key={pageNumber}
                  className={pageNumber === page ? "active" : ""}
                  aria-current={pageNumber === page ? "page" : undefined}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber + 1}
                </button>
              ))}

              <button
                type="button"
                disabled={isLast}
                onClick={() =>
                  setPage((current) => Math.min(current + 1, totalPages - 1))
                }
              >
                다음
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

export default ReportManagement;
