import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";

const STATUS_LABELS = {
  RECRUITING: "모집 중",
  READY: "진행 예정",
  IN_PROGRESS: "진행 중",
  COMPLETED: "완료",
  CANCELED: "취소",
};

const APPLICATION_STATUS_LABELS = {
  PENDING: "승인 대기",
  APPROVED: "승인",
  REJECTED: "반려",
  CANCELED: "신청 취소",
};

const GENDER_LABELS = {
  MALE: "남성",
  FEMALE: "여성",
};

const GENDER_CONDITION_LABELS = {
  NONE: "성별 제한 없음",
  SAME_GENDER_ONE: "대상자와 같은 성별 최소 1명",
};

function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  return new Date(value).toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function ActivityDetail() {
  const navigate = useNavigate();
  const { activityId } = useParams();

  const [activity, setActivity] = useState(null);
  const [applications, setApplications] = useState([]);

  const [applicationPage, setApplicationPage] = useState(0);
  const [applicationTotalPages, setApplicationTotalPages] =
    useState(0);
  const [applicationStatus, setApplicationStatus] = useState("");

  const [loading, setLoading] = useState(true);
  const [applicationsLoading, setApplicationsLoading] =
    useState(true);

  const [error, setError] = useState("");
  const [actionError, setActionError] = useState("");

  const [processing, setProcessing] = useState(false);
  const [
    processingApplicationId,
    setProcessingApplicationId,
  ] = useState(null);

  const [showEditForm, setShowEditForm] = useState(false);

  const [editForm, setEditForm] = useState({
    scheduledAt: "",
    requiredPeople: 1,
  });

  /**
   * 활동 상세정보 조회.
   */
  useEffect(() => {
    let ignore = false;

    const loadActivity = async () => {
      setLoading(true);
      setError("");

      try {
        const response =
          await institutionApi.getActivity(activityId);

        if (!ignore) {
          setActivity(response);
        }
      } catch (requestError) {
        if (!ignore) {
          setError(
            requestError?.response?.data?.message ??
              "활동 정보를 불러오지 못했습니다.",
          );
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };

    loadActivity();

    return () => {
      ignore = true;
    };
  }, [activityId]);

  /**
   * 활동 신청자 목록 조회.
   */
  useEffect(() => {
    let ignore = false;

    const loadApplications = async () => {
      setApplicationsLoading(true);

      try {
        const response =
          await institutionApi.getActivityApplications(
            activityId,
            {
              page: applicationPage,
              size: 20,
              status: applicationStatus || undefined,
            },
          );

        if (!ignore) {
          setApplications(
            Array.isArray(response?.content)
              ? response.content
              : [],
          );

          setApplicationTotalPages(
            response?.totalPages ?? 0,
          );
        }
      } catch (requestError) {
        if (!ignore) {
          setActionError(
            requestError?.response?.data?.message ??
              "신청자 목록을 불러오지 못했습니다.",
          );
        }
      } finally {
        if (!ignore) {
          setApplicationsLoading(false);
        }
      }
    };

    loadApplications();

    return () => {
      ignore = true;
    };
  }, [
    activityId,
    applicationPage,
    applicationStatus,
  ]);

  /**
   * 신청 처리 후 활동 상세와 신청자 목록을 다시 조회한다.
   */
  const reloadAfterApplicationProcess = async () => {
    const [
      activityResponse,
      applicationResponse,
    ] = await Promise.all([
      institutionApi.getActivity(activityId),

      institutionApi.getActivityApplications(
        activityId,
        {
          page: applicationPage,
          size: 20,
          status: applicationStatus || undefined,
        },
      ),
    ]);

    setActivity(activityResponse);

    setApplications(
      Array.isArray(applicationResponse?.content)
        ? applicationResponse.content
        : [],
    );

    setApplicationTotalPages(
      applicationResponse?.totalPages ?? 0,
    );
  };

  const openEditForm = () => {
    setActionError("");

    setEditForm({
      scheduledAt: activity.scheduledAt
        ? activity.scheduledAt.slice(0, 16)
        : "",

      requiredPeople: activity.requiredPeople,
    });

    setShowEditForm(true);
  };

  const closeEditForm = () => {
    if (processing) {
      return;
    }

    setShowEditForm(false);
  };

  const handleEditChange = (event) => {
    const { name, value } = event.target;

    setEditForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  /**
   * 활동 일정과 필요 인원 수정.
   */
  const handleUpdate = async (event) => {
    event.preventDefault();

    setProcessing(true);
    setActionError("");

    try {
      const response =
        await institutionApi.updateActivity(
          activityId,
          {
            scheduledAt: editForm.scheduledAt,
            requiredPeople: Number(
              editForm.requiredPeople,
            ),
          },
        );

      setActivity(response);
      setShowEditForm(false);
    } catch (requestError) {
      setActionError(
        requestError?.response?.data?.message ??
          "활동 정보 수정에 실패했습니다.",
      );
    } finally {
      setProcessing(false);
    }
  };

  /**
   * 활동 상태 변경.
   */
  const handleStatusChange = async (newStatus) => {
    const statusLabel =
      STATUS_LABELS[newStatus] ?? newStatus;

    const confirmed = window.confirm(
      `활동 상태를 '${statusLabel}' 상태로 변경하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    setProcessing(true);
    setActionError("");

    try {
      const response =
        await institutionApi.updateActivityStatus(
          activityId,
          newStatus,
        );

      setActivity(response);
    } catch (requestError) {
      setActionError(
        requestError?.response?.data?.message ??
          "활동 상태 변경에 실패했습니다.",
      );
    } finally {
      setProcessing(false);
    }
  };

  /**
   * 봉사 신청 승인.
   */
  const handleApproveApplication = async (
    application,
  ) => {
    const confirmed = window.confirm(
      `${application.name}님의 봉사 신청을 승인하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    setProcessingApplicationId(
      application.applicationId,
    );
    setActionError("");

    try {
      await institutionApi.approveActivityApplication(
        activityId,
        application.applicationId,
      );

      await reloadAfterApplicationProcess();
    } catch (requestError) {
      setActionError(
        requestError?.response?.data?.message ??
          "봉사 신청 승인에 실패했습니다.",
      );
    } finally {
      setProcessingApplicationId(null);
    }
  };

  /**
   * 봉사 신청 반려.
   */
  const handleRejectApplication = async (
    application,
  ) => {
    const reason = window.prompt(
      `${application.name}님의 신청을 반려하는 이유를 입력하세요.`,
    );

    // 취소 버튼을 누른 경우
    if (reason === null) {
      return;
    }

    // 내용 없이 확인을 누른 경우
    if (!reason.trim()) {
      setActionError(
        "반려 사유를 입력해야 합니다.",
      );
      return;
    }

    setProcessingApplicationId(
      application.applicationId,
    );
    setActionError("");

    try {
      await institutionApi.rejectActivityApplication(
        activityId,
        application.applicationId,
        reason.trim(),
      );

      await reloadAfterApplicationProcess();
    } catch (requestError) {
      setActionError(
        requestError?.response?.data?.message ??
          "봉사 신청 반려에 실패했습니다.",
      );
    } finally {
      setProcessingApplicationId(null);
    }
  };

  const handleApplicationStatusChange = (event) => {
    setApplicationStatus(event.target.value);
    setApplicationPage(0);
  };

  if (loading) {
    return (
      <div className="institution-page">
        <div className="data-state">
          활동 정보를 불러오고 있습니다.
        </div>
      </div>
    );
  }

  if (error || !activity) {
    return (
      <div className="institution-page">
        <button
          type="button"
          className="detail-back"
          onClick={() =>
            navigate("/institution/activities")
          }
        >
          ← 활동 목록
        </button>

        <div className="data-state error">
          {error ||
            "활동 정보를 찾을 수 없습니다."}
        </div>
      </div>
    );
  }

  const canEdit =
    activity.status === "RECRUITING" ||
    activity.status === "READY";

  const canBecomeReady =
    activity.status === "RECRUITING" &&
    activity.approvedCount >=
      activity.requiredPeople;

  return (
    <div className="institution-page care-detail-page">
      <button
        type="button"
        className="detail-back"
        onClick={() =>
          navigate("/institution/activities")
        }
      >
        ← 활동 목록
      </button>

      <div className="page-title-row compact">
        <div>
          <p>활동 상세</p>

          <h1>
            {activity.recipientName} 돌봄 활동
          </h1>

          <span>
            활동 정보와 신청자를 확인합니다.
          </span>
        </div>

        <div className="detail-title-actions">
          {canEdit && (
            <button
              type="button"
              className="care-form-cancel"
              disabled={processing}
              onClick={openEditForm}
            >
              정보 수정
            </button>
          )}

          {activity.status === "RECRUITING" && (
            <>
              <button
                type="button"
                className="orange-action"
                disabled={
                  processing ||
                  !canBecomeReady
                }
                onClick={() =>
                  handleStatusChange("READY")
                }
              >
                모집 완료
              </button>

              <button
                type="button"
                className="care-form-cancel"
                disabled={processing}
                onClick={() =>
                  handleStatusChange("CANCELED")
                }
              >
                활동 취소
              </button>
            </>
          )}

          {activity.status === "READY" && (
            <>
              <button
                type="button"
                className="care-form-cancel"
                disabled={processing}
                onClick={() =>
                  handleStatusChange("RECRUITING")
                }
              >
                다시 모집
              </button>

              <button
                type="button"
                className="orange-action"
                disabled={processing}
                onClick={() =>
                  handleStatusChange("IN_PROGRESS")
                }
              >
                활동 시작
              </button>

              <button
                type="button"
                className="care-form-cancel"
                disabled={processing}
                onClick={() =>
                  handleStatusChange("CANCELED")
                }
              >
                활동 취소
              </button>
            </>
          )}
        </div>
      </div>

      {actionError && (
        <div className="care-form-error">
          {actionError}
        </div>
      )}

      <section className="detail-summary-grid">
        <article>
          <span>현재 상태</span>
          <strong>
            {STATUS_LABELS[activity.status] ??
              activity.status}
          </strong>
        </article>

        <article>
          <span>필요 인원</span>
          <strong>
            {activity.requiredPeople}명
          </strong>
        </article>

        <article>
          <span>승인 인원</span>
          <strong>
            {activity.approvedCount}명
          </strong>
        </article>

        <article>
          <span>승인 대기</span>
          <strong>
            {activity.pendingCount}명
          </strong>
        </article>
      </section>

      <div className="care-detail-grid">
        <section className="panel detail-section">
          <div className="panel-title">
            <div>
              <h2>활동 정보</h2>
              <p>활동 일정과 조건입니다.</p>
            </div>
          </div>

          <dl className="detail-info-list">
            <div>
              <dt>활동 번호</dt>
              <dd>{activity.activityId}</dd>
            </div>

            <div>
              <dt>활동 예정일</dt>
              <dd>
                {formatDateTime(
                  activity.scheduledAt,
                )}
              </dd>
            </div>

            <div>
              <dt>성별 조건</dt>
              <dd>
                {GENDER_CONDITION_LABELS[
                  activity.genderCondition
                ] ??
                  activity.genderCondition}
              </dd>
            </div>

            <div>
              <dt>등록 담당자</dt>
              <dd>{activity.createdByName}</dd>
            </div>

            <div>
              <dt>등록일</dt>
              <dd>
                {formatDateTime(
                  activity.createdAt,
                )}
              </dd>
            </div>

            <div>
              <dt>활동 결과</dt>
              <dd>
                {activity.hasRecord
                  ? activity.reviewStatus ??
                    "결과 작성됨"
                  : "결과 없음"}
              </dd>
            </div>
          </dl>
        </section>

        <section className="panel detail-section">
          <div className="panel-title">
            <div>
              <h2>돌봄 대상자</h2>
              <p>활동 대상자 정보입니다.</p>
            </div>
          </div>

          <dl className="detail-info-list">
            <div>
              <dt>대상자 번호</dt>
              <dd>{activity.recipientId}</dd>
            </div>

            <div>
              <dt>이름</dt>
              <dd>{activity.recipientName}</dd>
            </div>

            <div>
              <dt>전화번호</dt>
              <dd>
                {activity.recipientPhone || "-"}
              </dd>
            </div>

            <div>
              <dt>주소</dt>
              <dd>
                {activity.recipientAddress}{" "}
                {activity.recipientDetailAddress}
              </dd>
            </div>
          </dl>
        </section>

        <section className="panel detail-section detail-wide">
          <div className="panel-title activity-title">
            <div>
              <h2>활동 신청자</h2>
              <p>
                활동을 신청한 봉사자를 확인합니다.
              </p>
            </div>

            <select
              value={applicationStatus}
              aria-label="신청 상태 선택"
              onChange={
                handleApplicationStatusChange
              }
            >
              <option value="">전체 상태</option>
              <option value="PENDING">
                승인 대기
              </option>
              <option value="APPROVED">
                승인
              </option>
              <option value="REJECTED">
                반려
              </option>
              <option value="CANCELED">
                신청 취소
              </option>
            </select>
          </div>

          <div className="activity-list">
            {applicationsLoading ? (
              <div className="data-state">
                신청자를 불러오고 있습니다.
              </div>
            ) : applications.length === 0 ? (
              <div className="data-state">
                신청자가 없습니다.
              </div>
            ) : (
              applications.map(
                (application, index) => (
                  <article
                    key={
                      application.applicationId
                    }
                  >
                    <span
                      className={
                        `activity-dot dot-${
                          index % 4
                        }`
                      }
                    >
                      {application.name?.[0] ??
                        "봉"}
                    </span>

                    <div>
                      <h3>
                        {application.name}
                        {" · "}
                        {application.nickname ||
                          "닉네임 없음"}
                      </h3>

                      <p>
                        {application.phone}
                        {" · "}
                        {GENDER_LABELS[
                          application.gender
                        ] ??
                          application.gender}
                        {" · 신청 "}
                        {formatDateTime(
                          application.appliedAt,
                        )}
                      </p>
                    </div>

                    <i className="table-status">
                      {APPLICATION_STATUS_LABELS[
                        application.status
                      ] ??
                        application.status}
                    </i>

                    {application.status ===
                      "PENDING" &&
                      activity.status ===
                        "RECRUITING" && (
                        <div className="application-actions">
                          <button
                            type="button"
                            disabled={
                              processingApplicationId ===
                              application.applicationId
                            }
                            onClick={() =>
                              handleApproveApplication(
                                application,
                              )
                            }
                          >
                            승인
                          </button>

                          <button
                            type="button"
                            className="reject"
                            disabled={
                              processingApplicationId ===
                              application.applicationId
                            }
                            onClick={() =>
                              handleRejectApplication(
                                application,
                              )
                            }
                          >
                            반려
                          </button>
                        </div>
                      )}
                  </article>
                ),
              )
            )}
          </div>

          {applicationTotalPages > 1 && (
            <div className="table-pagination">
              <button
                type="button"
                disabled={applicationPage === 0}
                onClick={() =>
                  setApplicationPage(
                    (current) =>
                      Math.max(current - 1, 0),
                  )
                }
              >
                이전
              </button>

              <span>
                {applicationPage + 1}
                {" / "}
                {applicationTotalPages}
              </span>

              <button
                type="button"
                disabled={
                  applicationPage + 1 >=
                  applicationTotalPages
                }
                onClick={() =>
                  setApplicationPage(
                    (current) => current + 1,
                  )
                }
              >
                다음
              </button>
            </div>
          )}
        </section>
      </div>

      {showEditForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (
              event.target ===
              event.currentTarget
            ) {
              closeEditForm();
            }
          }}
        >
          <section
            className="care-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="activity-edit-title"
          >
            <div className="care-modal-header">
              <div>
                <p>활동 관리</p>
                <h2 id="activity-edit-title">
                  활동 정보 수정
                </h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                disabled={processing}
                onClick={closeEditForm}
              >
                ×
              </button>
            </div>

            {actionError && (
              <div className="care-form-error">
                {actionError}
              </div>
            )}

            <form
              className="care-recipient-form"
              onSubmit={handleUpdate}
            >
              <div className="care-form-grid">
                <label>
                  <span>활동 예정 일시</span>

                  <input
                    type="datetime-local"
                    name="scheduledAt"
                    value={editForm.scheduledAt}
                    disabled={processing}
                    required
                    onChange={handleEditChange}
                  />
                </label>

                <label>
                  <span>필요 인원</span>

                  <input
                    type="number"
                    name="requiredPeople"
                    value={
                      editForm.requiredPeople
                    }
                    min={Math.max(
                      activity.approvedCount,
                      1,
                    )}
                    disabled={processing}
                    required
                    onChange={handleEditChange}
                  />
                </label>
              </div>

              <div className="care-form-actions">
                <button
                  type="button"
                  className="care-form-cancel"
                  disabled={processing}
                  onClick={closeEditForm}
                >
                  취소
                </button>

                <button
                  type="submit"
                  className="orange-action"
                  disabled={processing}
                >
                  {processing
                    ? "수정 중..."
                    : "수정 저장"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

export default ActivityDetail;