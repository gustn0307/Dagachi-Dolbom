import { useEffect, useState } from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";

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

const REVIEW_STATUS_LABELS = {
  DRAFT: "작성 중",
  SUBMITTED: "기관 검토 대기",
  APPROVED: "활동 인증 완료",
  NEEDS_REVISION: "보완 필요",
  REJECTED: "인증 반려",
};

const VISIT_RESULT_LABELS = {
  MET: "대상자를 만남",
  NOT_MET: "대상자를 만나지 못함",
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

  return new Date(value).toLocaleString(
    "ko-KR",
    {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    },
  );
}

function getErrorMessage(
  error,
  fallbackMessage,
) {
  return (
    error?.response?.data?.message ||
    error?.message ||
    fallbackMessage
  );
}

function ActivityDetail() {
  const navigate = useNavigate();
  const { activityId } = useParams();

  const [activity, setActivity] =
    useState(null);

  const [activityRecord, setActivityRecord] =
    useState(null);

  const [applications, setApplications] =
    useState([]);

  const [
    applicationPage,
    setApplicationPage,
  ] = useState(0);

  const [
    applicationTotalPages,
    setApplicationTotalPages,
  ] = useState(0);

  const [
    applicationStatus,
    setApplicationStatus,
  ] = useState("");

  const [loading, setLoading] =
    useState(true);

  const [
    recordLoading,
    setRecordLoading,
  ] = useState(false);

  const [
    applicationsLoading,
    setApplicationsLoading,
  ] = useState(true);

  const [error, setError] =
    useState("");

  const [actionError, setActionError] =
    useState("");

  const [processing, setProcessing] =
    useState(false);

  const [
    recordProcessing,
    setRecordProcessing,
  ] = useState(false);

  const [
    processingApplicationId,
    setProcessingApplicationId,
  ] = useState(null);

  const [
    showEditForm,
    setShowEditForm,
  ] = useState(false);

  const [editForm, setEditForm] =
    useState({
      scheduledAt: "",
      requiredPeople: 2,
    });

  const loadActivity = async () => {
    const response =
      await institutionApi.getActivity(
        activityId,
      );

    setActivity(response);

    return response;
  };

  const loadActivityRecord = async () => {
    setRecordLoading(true);

    try {
      const response =
        await institutionApi
          .getInstitutionActivityRecord(
            activityId,
          );

      setActivityRecord(response);
    } catch (requestError) {
      if (
        requestError?.response?.status === 404
      ) {
        setActivityRecord(null);
        return;
      }

      setActionError(
        getErrorMessage(
          requestError,
          "활동기록을 불러오지 못했습니다.",
        ),
      );
    } finally {
      setRecordLoading(false);
    }
  };

  const loadApplications = async () => {
    setApplicationsLoading(true);

    try {
      const response =
        await institutionApi
          .getActivityApplications(
            activityId,
            {
              page: applicationPage,
              size: 20,
              status:
                applicationStatus ||
                undefined,
            },
          );

      setApplications(
        Array.isArray(response?.content)
          ? response.content
          : [],
      );

      setApplicationTotalPages(
        response?.totalPages ?? 0,
      );
    } catch (requestError) {
      setActionError(
        getErrorMessage(
          requestError,
          "신청자 목록을 불러오지 못했습니다.",
        ),
      );
    } finally {
      setApplicationsLoading(false);
    }
  };

  useEffect(() => {
    let ignore = false;

    const initialize = async () => {
      setLoading(true);
      setError("");

      try {
        const response =
          await institutionApi.getActivity(
            activityId,
          );

        if (!ignore) {
          setActivity(response);
        }
      } catch (requestError) {
        if (!ignore) {
          setError(
            getErrorMessage(
              requestError,
              "활동 정보를 불러오지 못했습니다.",
            ),
          );
        }
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };

    initialize();

    return () => {
      ignore = true;
    };
  }, [activityId]);

  useEffect(() => {
    if (!activity?.hasRecord) {
      setActivityRecord(null);
      return;
    }

    loadActivityRecord();
  }, [
    activityId,
    activity?.hasRecord,
  ]);

  useEffect(() => {
    loadApplications();
  }, [
    activityId,
    applicationPage,
    applicationStatus,
  ]);

  const reloadAfterApplicationProcess =
    async () => {
      const [
        activityResponse,
        applicationResponse,
      ] = await Promise.all([
        institutionApi.getActivity(
          activityId,
        ),

        institutionApi
          .getActivityApplications(
            activityId,
            {
              page: applicationPage,
              size: 20,
              status:
                applicationStatus ||
                undefined,
            },
          ),
      ]);

      setActivity(activityResponse);

      setApplications(
        Array.isArray(
          applicationResponse?.content,
        )
          ? applicationResponse.content
          : [],
      );

      setApplicationTotalPages(
        applicationResponse?.totalPages ??
          0,
      );
    };

  const openEditForm = () => {
    setActionError("");

    setEditForm({
      scheduledAt:
        activity.scheduledAt
          ? activity.scheduledAt.slice(
              0,
              16,
            )
          : "",

      requiredPeople:
        activity.requiredPeople,
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
    const { name, value } =
      event.target;

    setEditForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const handleUpdate = async (
    event,
  ) => {
    event.preventDefault();

    setProcessing(true);
    setActionError("");

    try {
      const response =
        await institutionApi
          .updateActivity(
            activityId,
            {
              scheduledAt:
                editForm.scheduledAt,

              requiredPeople: Number(
                editForm.requiredPeople,
              ),
            },
          );

      setActivity(response);
      setShowEditForm(false);
    } catch (requestError) {
      setActionError(
        getErrorMessage(
          requestError,
          "활동 정보 수정에 실패했습니다.",
        ),
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleStatusChange = async (
    newStatus,
  ) => {
    const statusLabel =
      STATUS_LABELS[newStatus] ??
      newStatus;

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
        await institutionApi
          .updateActivityStatus(
            activityId,
            newStatus,
          );

      setActivity(response);
    } catch (requestError) {
      setActionError(
        getErrorMessage(
          requestError,
          "활동 상태 변경에 실패했습니다.",
        ),
      );
    } finally {
      setProcessing(false);
    }
  };

  const handleApproveApplication =
    async (application) => {
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
        await institutionApi
          .approveActivityApplication(
            activityId,
            application.applicationId,
          );

        await reloadAfterApplicationProcess();
      } catch (requestError) {
        setActionError(
          getErrorMessage(
            requestError,
            "봉사 신청 승인에 실패했습니다.",
          ),
        );
      } finally {
        setProcessingApplicationId(
          null,
        );
      }
    };

  const handleRejectApplication =
    async (application) => {
      const reason = window.prompt(
        `${application.name}님의 신청을 반려하는 이유를 입력하세요.`,
      );

      if (reason === null) {
        return;
      }

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
        await institutionApi
          .rejectActivityApplication(
            activityId,
            application.applicationId,
            reason.trim(),
          );

        await reloadAfterApplicationProcess();
      } catch (requestError) {
        setActionError(
          getErrorMessage(
            requestError,
            "봉사 신청 반려에 실패했습니다.",
          ),
        );
      } finally {
        setProcessingApplicationId(
          null,
        );
      }
    };

  const handleApproveRecord =
    async () => {
      const confirmed = window.confirm(
        "제출된 체크리스트를 승인하고 활동을 완료하시겠습니까?",
      );

      if (!confirmed) {
        return;
      }

      setRecordProcessing(true);
      setActionError("");

      try {
        const response =
          await institutionApi
            .approveInstitutionActivityRecord(
              activityId,
            );

        setActivityRecord(response);

        const activityResponse =
          await loadActivity();

        setActivity(activityResponse);

        window.alert(
          "활동기록을 승인하고 활동을 완료했습니다.",
        );
      } catch (requestError) {
        setActionError(
          getErrorMessage(
            requestError,
            "활동기록 승인에 실패했습니다.",
          ),
        );
      } finally {
        setRecordProcessing(false);
      }
    };

  const handleRequestRevision =
    async () => {
      const reviewNote = window.prompt(
        "봉사자에게 전달할 보완 요청 내용을 입력하세요.",
      );

      if (reviewNote === null) {
        return;
      }

      if (!reviewNote.trim()) {
        setActionError(
          "보완 요청 내용을 입력해야 합니다.",
        );
        return;
      }

      if (
        reviewNote.trim().length > 500
      ) {
        setActionError(
          "보완 요청 내용은 500자 이하여야 합니다.",
        );
        return;
      }

      setRecordProcessing(true);
      setActionError("");

      try {
        const response =
          await institutionApi
            .requestInstitutionActivityRecordRevision(
              activityId,
              reviewNote.trim(),
            );

        setActivityRecord(response);

        const activityResponse =
          await loadActivity();

        setActivity(activityResponse);

        window.alert(
          "봉사자에게 보완을 요청했습니다.",
        );
      } catch (requestError) {
        setActionError(
          getErrorMessage(
            requestError,
            "활동기록 보완 요청에 실패했습니다.",
          ),
        );
      } finally {
        setRecordProcessing(false);
      }
    };

  const handleApplicationStatusChange = (
    event,
  ) => {
    setApplicationStatus(
      event.target.value,
    );

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
            navigate(
              "/institution/activities",
            )
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

  
  const canReviewRecord =
    activityRecord?.reviewStatus ===
    "SUBMITTED";

  return (
    <div className="institution-page care-detail-page">
      <button
        type="button"
        className="detail-back"
        onClick={() =>
          navigate(
            "/institution/activities",
          )
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
            활동 정보와 신청자, 제출된
            활동기록을 확인합니다.
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

          {activity.status ===
            "RECRUITING" && (
            <button
              type="button"
              className="care-form-cancel"
              disabled={processing}
              onClick={() =>
                handleStatusChange(
                  "CANCELED",
                )
              }
            >
              활동 취소
            </button>
          )}

          {activity.status ===
            "READY" && (
            <>
              <button
                type="button"
                className="care-form-cancel"
                disabled={processing}
                onClick={() =>
                  handleStatusChange(
                    "RECRUITING",
                  )
                }
              >
                다시 모집
              </button>

              <button
                type="button"
                className="care-form-cancel"
                disabled={processing}
                onClick={() =>
                  handleStatusChange(
                    "CANCELED",
                  )
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
            {STATUS_LABELS[
              activity.status
            ] ?? activity.status}
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
          <span>활동 인증</span>

          <strong>
            {activity.hasRecord
              ? REVIEW_STATUS_LABELS[
                  activity.reviewStatus
                ] ??
                activity.reviewStatus
              : "기록 없음"}
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
              <dd>
                {activity.createdByName}
              </dd>
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
                  ? REVIEW_STATUS_LABELS[
                      activity.reviewStatus
                    ] ??
                    activity.reviewStatus
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
              <dd>
                {activity.recipientId}
              </dd>
            </div>

            <div>
              <dt>이름</dt>
              <dd>
                {activity.recipientName}
              </dd>
            </div>

            <div>
              <dt>전화번호</dt>
              <dd>
                {activity.recipientPhone ||
                  "-"}
              </dd>
            </div>

            <div>
              <dt>주소</dt>
              <dd>
                {activity.recipientAddress ||
                  "-"}{" "}
                {activity.recipientDetailAddress ||
                  ""}
              </dd>
            </div>
          </dl>
        </section>

        <section className="panel detail-section detail-wide">
          <div className="panel-title activity-title">
            <div>
              <h2>활동 신청자</h2>
              <p>
                활동을 신청한 봉사자를
                확인합니다.
              </p>
            </div>

            <select
              value={applicationStatus}
              aria-label="신청 상태 선택"
              onChange={
                handleApplicationStatusChange
              }
            >
              <option value="">
                전체 상태
              </option>

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
            ) : applications.length ===
              0 ? (
              <div className="data-state">
                신청자가 없습니다.
              </div>
            ) : (
              applications.map(
                (
                  application,
                  index,
                ) => (
                  <article
                    key={
                      application.applicationId
                    }
                  >
                    <span
                      className={`activity-dot dot-${
                        index % 4
                      }`}
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
                disabled={
                  applicationPage === 0
                }
                onClick={() =>
                  setApplicationPage(
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

        <section className="panel detail-section detail-wide">
          <div className="panel-title activity-title">
            <div>
              <h2>활동 인증</h2>

              <p>
                봉사자가 제출한 활동 결과와
                체크리스트를 확인하세요.
              </p>
            </div>

            {activityRecord && (
              <i className="table-status">
                {REVIEW_STATUS_LABELS[
                  activityRecord.reviewStatus
                ] ??
                  activityRecord.reviewStatus}
              </i>
            )}
          </div>

          {recordLoading ? (
            <div className="data-state">
              활동기록을 불러오고 있습니다.
            </div>
          ) : !activity.hasRecord ||
            !activityRecord ? (
            <div className="data-state">
              아직 봉사자가 제출한
              활동기록이 없습니다.
            </div>
          ) : (
            <>
              <dl className="detail-info-list">
                <div>
                  <dt>기록 번호</dt>
                  <dd>
                    {activityRecord.recordId}
                  </dd>
                </div>

                <div>
                  <dt>제출자</dt>
                  <dd>
                    {activityRecord.submittedByName ||
                      "아직 제출되지 않음"}
                  </dd>
                </div>

                <div>
                  <dt>방문 결과</dt>
                  <dd>
                    {VISIT_RESULT_LABELS[
                      activityRecord.visitResult
                    ] ??
                      activityRecord.visitResult ??
                      "-"}
                  </dd>
                </div>

                <div>
                  <dt>활동 시작</dt>
                  <dd>
                    {formatDateTime(
                      activityRecord.startedAt,
                    )}
                  </dd>
                </div>

                <div>
                  <dt>활동 완료</dt>
                  <dd>
                    {formatDateTime(
                      activityRecord.completedAt,
                    )}
                  </dd>
                </div>

                <div>
                  <dt>서명 등록</dt>
                  <dd>
                    {activityRecord.signatureUploaded
                      ? "등록 완료"
                      : "등록되지 않음"}
                  </dd>
                </div>

                <div>
                  <dt>특이사항</dt>
                  <dd>
                    {activityRecord.specialNote ||
                      "특이사항 없음"}
                  </dd>
                </div>

                <div>
                  <dt>검토 담당자</dt>
                  <dd>
                    {activityRecord.reviewedByName ||
                      "-"}
                  </dd>
                </div>

                <div>
                  <dt>검토 일시</dt>
                  <dd>
                    {formatDateTime(
                      activityRecord.reviewedAt,
                    )}
                  </dd>
                </div>
              </dl>

              {activityRecord.reviewNote && (
                <div className="care-form-error">
                  보완 요청 내용:{" "}
                  {activityRecord.reviewNote}
                </div>
              )}

              <div className="activity-list">
                <div className="panel-title">
                  <div>
                    <h2>안부 체크리스트</h2>
                    <p>
                      봉사자가 제출한 답변입니다.
                    </p>
                  </div>
                </div>

                {Array.isArray(
                  activityRecord.responses,
                ) &&
                activityRecord.responses.length >
                  0 ? (
                  activityRecord.responses.map(
                    (response, index) => (
                      <article
                        key={
                          response.checklistItemId
                        }
                      >
                        <span className="activity-dot">
                          {index + 1}
                        </span>

                        <div>
                          <h3>
                            {response.question}
                          </h3>

                          <p>
                            답변:{" "}
                            {response.selectedValue ||
                              response.textValue ||
                              "응답 없음"}
                          </p>
                        </div>
                      </article>
                    ),
                  )
                ) : (
                  <div className="data-state">
                    저장된 체크리스트 답변이
                    없습니다.
                  </div>
                )}
              </div>

              {canReviewRecord && (
                <div className="care-form-actions">
                  <button
                    type="button"
                    className="care-form-cancel"
                    disabled={recordProcessing}
                    onClick={
                      handleRequestRevision
                    }
                  >
                    {recordProcessing
                      ? "처리 중..."
                      : "보완 요청"}
                  </button>

                  <button
                    type="button"
                    className="orange-action"
                    disabled={recordProcessing}
                    onClick={
                      handleApproveRecord
                    }
                  >
                    {recordProcessing
                      ? "처리 중..."
                      : "활동 인증"}
                  </button>
                </div>
              )}

              {activityRecord.reviewStatus ===
                "APPROVED" && (
                <div className="data-state">
                  기관에서 인증을 완료한
                  활동입니다.
                </div>
              )}

              {activityRecord.reviewStatus ===
                "NEEDS_REVISION" && (
                <div className="data-state">
                  봉사자의 수정 및 재제출을
                  기다리고 있습니다.
                </div>
              )}

              {activityRecord.reviewStatus ===
                "DRAFT" && (
                <div className="data-state">
                  봉사자가 활동기록을 작성
                  중입니다.
                </div>
              )}
            </>
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
                  <span>
                    활동 예정 일시
                  </span>

                  <input
                    type="datetime-local"
                    name="scheduledAt"
                    value={
                      editForm.scheduledAt
                    }
                    disabled={processing}
                    required
                    onChange={
                      handleEditChange
                    }
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
                      2,
                    )}
                    disabled={processing}
                    required
                    onChange={
                      handleEditChange
                    }
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