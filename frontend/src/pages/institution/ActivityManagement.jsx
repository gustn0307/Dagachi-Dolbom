import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";
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

  const [page, setPage] = useState(0);
  const [status, setStatus] = useState("");

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
   * 백엔드는 배열이 아니라 PageResponse를 반환한다.
   * 실제 활동 배열은 data.content에 들어 있다.
   */
  const activities =
    Array.isArray(data?.content)
      ? data.content
      : [];

  const recruitingCount =
    activities.filter(
      (activity) =>
        activity.status === "RECRUITING",
    ).length;

  const completedCount =
    activities.filter(
      (activity) =>
        activity.status === "COMPLETED",
    ).length;

  const handleStatusChange = (event) => {
    setStatus(event.target.value);
    setPage(0);
  };

  const openDetail = (activityId) => {
    navigate(
      `/institution/activities/${activityId}`,
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
        >
          ＋ 활동 등록
        </button>
      </div>

      <section className="activity-summary">
        <article>
          <div>
            <span>조회된 전체 활동</span>

            <strong>
              {data?.totalElements ?? 0}건
            </strong>
          </div>
        </article>

        <article>
          <div>
            <span>현재 페이지 모집 중</span>

            <strong>
              {recruitingCount}건
            </strong>
          </div>
        </article>

        <article>
          <div>
            <span>현재 페이지 완료</span>

            <strong>
              {completedCount}건
            </strong>
          </div>
        </article>
      </section>

      <section className="panel table-panel">
        <div className="panel-title activity-title">
          <div>
            <h2>기관 활동 목록</h2>

            <p>
              활동 일정과 모집 상태를 확인하세요.
            </p>
          </div>

          <select
            value={status}
            onChange={handleStatusChange}
            aria-label="활동 상태 선택"
          >
            <option value="">
              전체 상태
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
                  scheduledDate.toLocaleDateString(
                    "ko-KR",
                  );

                const timeText =
                  scheduledDate.toLocaleTimeString(
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
                      <small>{timeText}</small>
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
                        필요 인원{" "}
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

        {data?.totalPages > 1 && (
          <div className="table-pagination">
            <button
              type="button"
              disabled={data.first}
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

            <span>
              {data.page + 1}
              {" / "}
              {data.totalPages}
            </span>

            <button
              type="button"
              disabled={data.last}
              onClick={() =>
                setPage(
                  (current) =>
                    current + 1,
                )
              }
            >
              다음
            </button>
          </div>
        )}
      </section>
    </div>
  );
}

export default ActivityManagement;