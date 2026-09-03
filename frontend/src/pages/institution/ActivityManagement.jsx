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
   * 전체 데이터 기준으로
   * 활동 상태별 건수를 조회한다.
   *
   * 목록 API의 totalElements만 사용하므로
   * size는 1로 요청한다.
   */
  useEffect(() => {
    let ignore = false;

    const loadActivityCounts = async () => {
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
            totalResponse?.totalElements ??
            0,

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
   * 활동 상태 필터 변경
   */
  const handleStatusChange = (event) => {
    setStatus(event.target.value);
    setPage(0);
  };

  /*
   * 활동 상세 페이지 이동
   */
  const openDetail = (activityId) => {
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
  const handleCreate = async (request) => {
    setSubmitting(true);
    setSubmitError("");

    try {
      await institutionApi.createActivity(
        request,
      );

      setShowCreateForm(false);

      /*
       * 첫 페이지가 아니면 첫 페이지로 이동한다.
       * 이미 첫 페이지라면 목록을 즉시 다시 조회한다.
       *
       * 목록이 갱신되면 useEffect가 실행되어
       * 위쪽 활동 건수도 다시 조회된다.
       */
      if (page === 0) {
        await reload();
      } else {
        setPage(0);
      }
    } catch (requestError) {
      setSubmitError(
        requestError?.response?.data?.message ??
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
            돌봄 활동과 일정을 관리하세요
          </h1>

          <span>
            기관의 돌봄 활동과 신청 인원을
            확인합니다.
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
              {activityCounts.recruiting}건
            </strong>
          </div>
        </article>

        <article>
          <div>
            <span>완료된 활동</span>

            <strong>
              {activityCounts.completed}건
            </strong>
          </div>
        </article>
      </section>

      <section className="panel table-panel">
        <div className="panel-title activity-title">
          <div>
            <h2>기관 활동 목록</h2>

            <p>
              활동 일정과 모집 상태를
              확인하세요.
            </p>
          </div>

          <label className="activity-status-filter">
            <span>상태별 조회</span>

            <select
              value={status}
              aria-label="활동 상태 선택"
              onChange={handleStatusChange}
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
              (activity, index) => {
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
                        hour: "2-digit",
                        minute: "2-digit",
                      },
                    );

                const statusLabel =
                  STATUS_LABELS[
                    activity.status
                  ] ?? activity.status;

                const statusClass =
                  statusLabel.replace(
                    " ",
                    "-",
                  );

                return (
                  <article
                    key={activity.activityId}
                  >
                    <time>
                      <b>{dateText}</b>

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
                        {activity.recipientName}
                        {" "}
                        돌봄 활동
                      </h3>

                      <p>
                        모집{" "}
                        {activity.requiredPeople}명
                        {" · "}
                        승인{" "}
                        {activity.approvedCount}명
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
                          activity.activityId,
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

      {/* 기관 활동 등록 모달 */}
      {showCreateForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            /*
             * 모달 바깥 영역을 눌렀을 때만
             * 모달을 닫는다.
             */
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
            aria-labelledby="activity-create-title"
          >
            <div className="care-modal-header">
              <div>
                <p>활동 관리</p>

                <h2 id="activity-create-title">
                  신규 활동 등록
                </h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                disabled={submitting}
                onClick={closeCreateForm}
              >
                ×
              </button>
            </div>

            {submitError && (
              <div className="care-form-error">
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