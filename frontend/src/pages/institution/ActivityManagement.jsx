import {
  useEffect,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";
import ActivityForm
  from "../../components/institution/ActivityForm";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

const STATUS_LABELS = {
  RECRUITING: "모집 중",
  READY: "진행 예정",
  IN_PROGRESS: "진행 중",
  COMPLETED: "완료",
  CANCELED: "취소",
};

function ActivityManagement() {
  const navigate = useNavigate();

  const [page, setPage] =
    useState(0);

  const [status, setStatus] =
    useState("");

  const [
    showCreateForm,
    setShowCreateForm,
  ] = useState(false);

  const [submitting, setSubmitting] =
    useState(false);

  const [submitError, setSubmitError] =
    useState("");

  /*
   * 전체 활동, 모집 중 활동, 완료된 활동 건수
   */
  const [
    activityCounts,
    setActivityCounts,
  ] = useState({
    total: 0,
    recruiting: 0,
    completed: 0,
  });

  /*
   * 기관의 승인 대기 봉사 신청 현황
   */
  const [
    pendingSummary,
    setPendingSummary,
  ] = useState({
    totalPendingCount: 0,
    activities: [],
  });

  /*
   * 현재 선택된 조건의 활동 목록 조회
   */
  const {
    data,
    loading,
    error,
    reload,
  } = useInstitutionData(
    () =>
      institutionApi.getActivities({
        page,
        size: 20,
        status: status || undefined,
      }),
    [
      page,
      status,
    ],
  );

  /*
   * 백엔드는 PageResponse를 반환한다.
   * 실제 활동 목록은 data.content에 들어 있다.
   */
  const activities =
    Array.isArray(data?.content)
      ? data.content
      : [];

  /*
   * 현재 목록의 페이지 정보
   */
  const totalElements =
    data?.totalElements ?? 0;

  const totalPages =
    data?.totalPages ?? 0;

  const isFirst =
    data?.first ?? true;

  const isLast =
    data?.last ?? true;

  /*
   * 활동 ID별 승인 대기 신청 인원을 저장한다.
   */
  const pendingCountByActivityId =
    new Map(
      (
        pendingSummary.activities ?? []
      ).map((activity) => [
        Number(activity.activityId),
        Number(
          activity.pendingCount ?? 0,
        ),
      ]),
    );

  /*
   * 전체 데이터 기준으로
   * 활동 상태별 건수를 조회한다.
   */
  useEffect(() => {
    let ignore = false;

    const loadActivityCounts =
      async () => {
        try {
          const [
            totalResponse,
            recruitingResponse,
            completedResponse,
          ] = await Promise.all([
            institutionApi.getActivities({
              page: 0,
              size: 1,
            }),

            institutionApi.getActivities({
              page: 0,
              size: 1,
              status: "RECRUITING",
            }),

            institutionApi.getActivities({
              page: 0,
              size: 1,
              status: "COMPLETED",
            }),
          ]);

          if (ignore) {
            return;
          }

          setActivityCounts({
            total:
              totalResponse
                ?.totalElements ?? 0,

            recruiting:
              recruitingResponse
                ?.totalElements ?? 0,

            completed:
              completedResponse
                ?.totalElements ?? 0,
          });
        } catch {
          if (!ignore) {
            setActivityCounts({
              total: 0,
              recruiting: 0,
              completed: 0,
            });
          }
        }
      };

    loadActivityCounts();

    return () => {
      ignore = true;
    };
  }, [data]);

  /*
   * 승인 대기 봉사 신청 현황을 조회한다.
   */
  useEffect(() => {
    let ignore = false;

    const loadPendingSummary =
      async () => {
        try {
          const response =
            await institutionApi
              .getPendingApplicationSummary();

          if (ignore) {
            return;
          }

          setPendingSummary({
            totalPendingCount:
              Number(
                response
                  ?.totalPendingCount ??
                  0,
              ),

            activities:
              Array.isArray(
                response?.activities,
              )
                ? response.activities
                : [],
          });
        } catch {
          if (!ignore) {
            setPendingSummary({
              totalPendingCount: 0,
              activities: [],
            });
          }
        }
      };

    loadPendingSummary();

    return () => {
      ignore = true;
    };
  }, [data]);

  /*
   * 활동 상태 필터 변경
   */
  const handleStatusChange = (
    event,
  ) => {
    setStatus(event.target.value);
    setPage(0);
  };

  /*
   * 활동 상세 페이지 이동
   */
  const openDetail = (
    activityId,
  ) => {
    navigate(
      `/institution/activities/${activityId}`,
    );
  };

  /*
   * 활동 등록 모달 열기
   */
  const openCreateForm = () => {
    setSubmitError("");
    setShowCreateForm(true);
  };

  /*
   * 활동 등록 모달 닫기
   */
  const closeCreateForm = () => {
    if (submitting) {
      return;
    }

    setShowCreateForm(false);
    setSubmitError("");
  };

  /*
   * 기관 활동 등록
   */
  const handleCreate = async (
    request,
  ) => {
    setSubmitting(true);
    setSubmitError("");

    try {
      await institutionApi
        .createActivity(request);

      setShowCreateForm(false);

      if (page === 0) {
        await reload();
      } else {
        setPage(0);
      }
    } catch (requestError) {
      setSubmitError(
        requestError?.response?.data
          ?.message ??
          "활동 등록에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
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
          <p>활동 관리</p>

          <h1>
            돌봄 활동과 일정을
            관리하세요
          </h1>

          <span>
            기관의 돌봄 활동과 신청
            인원을 확인합니다.
          </span>
        </div>

        <button
          type="button"
          className="orange-action"
          onClick={openCreateForm}
        >
          ＋ 활동 등록
        </button>
      </div>

      <section className="activity-summary">
        <article>
          <div>
            <span>전체 활동</span>

            <strong>
              {activityCounts.total}건
            </strong>
          </div>
        </article>

        <article>
          <div>
            <span>모집 중 활동</span>

            <strong>
              {
                activityCounts
                  .recruiting
              }
              건
            </strong>
          </div>
        </article>

        <article>
          <div>
            <span>완료된 활동</span>

            <strong>
              {
                activityCounts
                  .completed
              }
              건
            </strong>
          </div>
        </article>
      </section>
            {pendingSummary.activities.length > 0 && (
        <section className="panel pending-application-panel">
          <div className="pending-application-header">
            <div>
              <h2>승인 대기 신청</h2>

              <p>
                봉사 신청이 들어온 돌봄 활동을 확인하세요.
              </p>
            </div>

            <strong>
              총 {pendingSummary.totalPendingCount}명
            </strong>
          </div>

          <div className="pending-application-list">
            {pendingSummary.activities.map(
              (pendingActivity) => {
                const scheduledDate = new Date(
                  pendingActivity.scheduledAt,
                );

                const scheduledText =
                  Number.isNaN(scheduledDate.getTime())
                    ? ""
                    : scheduledDate.toLocaleString(
                        "ko-KR",
                        {
                          year: "numeric",
                          month: "long",
                          day: "numeric",
                          hour: "2-digit",
                          minute: "2-digit",
                        },
                      );

                return (
                  <article
                    key={pendingActivity.activityId}
                    className="pending-application-item"
                  >
                    <div className="pending-application-icon">
                      !
                    </div>

                    <div className="pending-application-content">
                      <h3>
                        {pendingActivity.recipientName}
                        {" "}
                        돌봄 활동
                      </h3>

                      <p>
                        {scheduledText}
                      </p>
                    </div>

                    <span className="pending-application-count">
                      신청 {pendingActivity.pendingCount}명
                    </span>

                    <button
                      type="button"
                      onClick={() =>
                        openDetail(
                          pendingActivity.activityId,
                        )
                      }
                    >
                      신청 확인
                    </button>
                  </article>
                );
              },
            )}
          </div>
        </section>
      )}

      <section className="panel table-panel">
        <div className="panel-title activity-title">
          <div>
            <h2>기관 활동 목록</h2>

            <p>
              활동 일정과 모집 상태를
              확인하세요.

              {pendingSummary
                .totalPendingCount >
                0 && (
                <>
                  {" · "}

                  <strong className="activity-pending-text">
                    승인 대기 신청{" "}
                    {
                      pendingSummary
                        .totalPendingCount
                    }
                    명
                  </strong>
                </>
              )}
            </p>
          </div>

          <label className="activity-status-filter">
            <span>상태별 조회</span>

            <select
              value={status}
              aria-label="활동 상태 선택"
              onChange={
                handleStatusChange
              }
            >
              <option value="">
                전체
              </option>

              <option value="RECRUITING">
                모집 중
              </option>

              <option value="READY">
                진행 예정
              </option>

              <option value="IN_PROGRESS">
                진행 중
              </option>

              <option value="COMPLETED">
                완료
              </option>

              <option value="CANCELED">
                취소
              </option>
            </select>
          </label>
        </div>

        <div className="activity-list">
          {activities.length === 0 ? (
            <div className="data-state">
              조회된 활동이 없습니다.
            </div>
          ) : (
            activities.map(
              (
                activity,
                index,
              ) => {
                const scheduledDate =
                  new Date(
                    activity.scheduledAt,
                  );

                const dateText =
                  scheduledDate
                    .toLocaleDateString(
                      "ko-KR",
                    );

                const timeText =
                  scheduledDate
                    .toLocaleTimeString(
                      "ko-KR",
                      {
                        hour:
                          "2-digit",
                        minute:
                          "2-digit",
                      },
                    );

                const statusLabel =
                  STATUS_LABELS[
                    activity.status
                  ] ??
                  activity.status;

                const statusClass =
                  statusLabel.replace(
                    " ",
                    "-",
                  );

                const pendingCount =
                  pendingCountByActivityId
                    .get(
                      Number(
                        activity
                          .activityId,
                      ),
                    ) ?? 0;

                return (
                  <article
                    key={
                      activity
                        .activityId
                    }
                  >
                    <time>
                      <b>
                        {dateText}
                      </b>

                      <small>
                        {timeText}
                      </small>
                    </time>

                    <span
                      className={
                        `activity-dot dot-${
                          index % 4
                        }`
                      }
                    >
                      ✓
                    </span>

                    <div>
                      <h3>
                        {
                          activity
                            .recipientName
                        }{" "}
                        돌봄 활동

                        {pendingCount >
                          0 && (
                          <span className="activity-pending-badge">
                            신청{" "}
                            {
                              pendingCount
                            }
                            명
                          </span>
                        )}
                      </h3>

                      <p>
                        모집{" "}
                        {
                          activity
                            .requiredPeople
                        }
                        명
                        {" · "}
                        승인{" "}
                        {
                          activity
                            .approvedCount
                        }
                        명

                        {pendingCount >
                          0 && (
                          <>
                            {" · "}

                            <strong className="activity-pending-text">
                              승인 대기{" "}
                              {
                                pendingCount
                              }
                              명
                            </strong>
                          </>
                        )}
                      </p>
                    </div>

                    <i
                      className={
                        `table-status ${statusClass}`
                      }
                    >
                      {statusLabel}
                    </i>

                    <button
                      type="button"
                      onClick={() =>
                        openDetail(
                          activity
                            .activityId,
                        )
                      }
                    >
                      상세 보기
                    </button>
                  </article>
                );
              },
            )
          )}
        </div>

        {totalPages > 0 && (
          <div className="table-footer care-pagination">
            <span>
              전체 {totalElements}건 ·{" "}
              {page + 1}/{totalPages}{" "}
              페이지
            </span>

            <div>
              <button
                type="button"
                disabled={isFirst}
                onClick={() =>
                  setPage(
                    (current) =>
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
                  length:
                    totalPages,
                },
                (_, index) =>
                  index,
              ).map(
                (pageNumber) => (
                  <button
                    type="button"
                    key={pageNumber}
                    className={
                      pageNumber ===
                      page
                        ? "active"
                        : ""
                    }
                    aria-current={
                      pageNumber ===
                      page
                        ? "page"
                        : undefined
                    }
                    onClick={() =>
                      setPage(
                        pageNumber,
                      )
                    }
                  >
                    {pageNumber + 1}
                  </button>
                ),
              )}

              <button
                type="button"
                disabled={isLast}
                onClick={() =>
                  setPage(
                    (current) =>
                      Math.min(
                        current + 1,
                        totalPages -
                          1,
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

      {showCreateForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (
              event.target ===
              event.currentTarget
            ) {
              closeCreateForm();
            }
          }}
        >
          <section
            className="care-modal"
            role="dialog"
            aria-modal="true"
            aria-label="활동 등록"
          >
            
            <div>
              <h2>활동 등록</h2>

              <p>
                돌봄 대상자와 활동
                일정을 입력해 주세요.
              </p>
            </div>

            {submitError && (
              <div className="data-state error">
                {submitError}
              </div>
            )}

            <ActivityForm
              submitting={submitting}
              onSubmit={handleCreate}
              onCancel={closeCreateForm}
            />
          </section>
        </div>
      )}
    </div>
  );
}

export default ActivityManagement;