import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

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

  return new Date(value).toLocaleDateString(
    "ko-KR",
    {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    },
  );
}

function formatDistance(value) {
  if (
    value === null ||
    value === undefined
  ) {
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

  const [activeTab, setActiveTab] =
    useState("unassigned");

  const [page, setPage] =
    useState(0);

  const [status, setStatus] =
    useState("");

  const [from, setFrom] =
    useState("");

  const [to, setTo] =
    useState("");

  const [
    assigningReportId,
    setAssigningReportId,
  ] = useState(null);

  const {
    data,
    loading,
    error,
    reload,
  } = useInstitutionData(
    () => {
      const params = {
        page,
        size: 20,
        status: status || undefined,
        from: from || undefined,
        to: to || undefined,
      };

      if (activeTab === "unassigned") {
        return institutionApi
          .getUnassignedReports(params);
      }

      return institutionApi.getReports(
        params,
      );
    },
    [
      activeTab,
      page,
      status,
      from,
      to,
    ],
  );

  const reports =
    Array.isArray(data?.content)
      ? data.content
      : [];

  const totalElements =
    data?.totalElements ?? 0;

  const totalPages =
    data?.totalPages ?? 0;

  const isFirst =
    data?.first ?? true;

  const isLast =
    data?.last ?? true;

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

  const handleAssignReport = async (
  reportId,
) => {
  const confirmed = window.confirm(
    `제보 #${reportId}을(를) 우리 기관의 관할로 지정하시겠습니까?`,
  );

  if (!confirmed) {
    return;
  }

  try {
    setAssigningReportId(reportId);

    await institutionApi.assignReport(
      reportId,
    );

    window.dispatchEvent(
      new Event(
        "institution-report-count-changed",
      ),
    );

    window.alert(
      "제보를 우리 기관의 관할로 지정했습니다.",
    );

    if (
      reports.length === 1 &&
      page > 0
    ) {
      setPage((current) =>
        Math.max(current - 1, 0),
      );
    } else {
      await reload();
    }
  } catch (assignError) {
    const statusCode =
      assignError?.response?.status;

    const errorCode =
      assignError?.response?.data?.code;

    if (
      statusCode === 409 ||
      errorCode ===
        "REPORT_409_ALREADY_ASSIGNED"
    ) {
      window.alert(
        "다른 기관이 먼저 관할로 지정한 제보입니다. 목록을 다시 불러옵니다.",
      );

      await reload();

      window.dispatchEvent(
        new Event(
          "institution-report-count-changed",
        ),
      );

      return;
    }

    window.alert(
      getErrorMessage(assignError),
    );
  } finally {
    setAssigningReportId(null);
  }
};

  const handleOpenDetail = (reportId) => {
    navigate(
      `/institution/reports/${reportId}`,
    );
  };

  if (loading || error) {
    return (
      <div className="institution-page">
        <DataState
          loading={loading}
          error={error}
          onRetry={reload}
        />
      </div>
    );
  }

  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>제보 관리</p>

          <h1>
            접수된 제보를 확인하세요
          </h1>

          <span>
            미배정 제보를 확인하고 기관에
            배정된 제보를 관리합니다.
          </span>
        </div>
      </div>

      <section className="panel table-panel">
        <div className="report-main-tabs">
          <button
            type="button"
            className={
              activeTab === "unassigned"
                ? "active"
                : ""
            }
            onClick={() =>
              changeTab("unassigned")
            }
          >
            미배정 제보
          </button>

          <button
            type="button"
            className={
              activeTab === "assigned"
                ? "active"
                : ""
            }
            onClick={() =>
              changeTab("assigned")
            }
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
              <option
                key={option.value}
                value={option.value}
              >
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
              const statusLabel =
                STATUS_LABELS[report.status] ??
                report.status;

              const isAssigning =
                assigningReportId ===
                report.reportId;

              return (
                <article
                  key={report.reportId}
                >
                  <span className="id-cell">
                    #{report.reportId}
                  </span>

                  <span className="main-cell">
                    <strong>
                      {report.contentPreview}
                    </strong>
                  </span>

                  <span>
                    {report.region || "-"}
                  </span>

                  <span>
                    {formatDistance(
                      report.distanceKm,
                    )}
                  </span>

                  <span>
                    {formatDate(
                      report.createdAt,
                    )}
                  </span>

                  <span>
                    <i className="table-status">
                      {statusLabel}
                    </i>
                  </span>

                  <button
                    type="button"
                    className="report-assign-button"
                    disabled={
                      assigningReportId !== null
                    }
                    onClick={() =>
                      handleAssignReport(
                        report.reportId,
                      )
                    }
                  >
                    {isAssigning
                      ? "처리 중"
                      : "관할 지정"}
                  </button>
                </article>
              );
            })
          ) : (
            reports.map((report) => {
              const statusLabel =
                STATUS_LABELS[report.status] ??
                report.status;

              return (
                <article
                  key={report.reportId}
                >
                  <span className="id-cell">
                    #{report.reportId}
                  </span>

                  <span className="main-cell">
                    <strong>
                      {report.content}
                    </strong>
                  </span>

                  <span>
                    {report.address || "-"}
                  </span>

                  <span>
                    {formatDate(
                      report.createdAt,
                    )}
                  </span>

                  <span>
                    <i className="table-status">
                      {statusLabel}
                    </i>
                  </span>

                  <button
                    type="button"
                    className="report-detail-button"
                    onClick={() =>
                      handleOpenDetail(
                        report.reportId,
                      )
                    }
                  >
                    상세 보기
                  </button>
                </article>
              );
            })
          )}
        </div>

        {totalPages > 0 && (
          <div className="table-footer care-pagination">
            <span>
              전체 {totalElements}건 ·{" "}
              {page + 1}/{totalPages} 페이지
            </span>

            <div>
              <button
                type="button"
                disabled={isFirst}
                onClick={() =>
                  setPage((current) =>
                    Math.max(
                      current - 1,
                      0,
                    ),
                  )
                }
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
                  className={
                    pageNumber === page
                      ? "active"
                      : ""
                  }
                  aria-current={
                    pageNumber === page
                      ? "page"
                      : undefined
                  }
                  onClick={() =>
                    setPage(pageNumber)
                  }
                >
                  {pageNumber + 1}
                </button>
              ))}

              <button
                type="button"
                disabled={isLast}
                onClick={() =>
                  setPage((current) =>
                    Math.min(
                      current + 1,
                      totalPages - 1,
                    ),
                  )
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