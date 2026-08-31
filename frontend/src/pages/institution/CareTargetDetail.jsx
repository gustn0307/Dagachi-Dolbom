import { useCallback, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { institutionApi } from "../../api/institutionApi";
import CareRecipientForm from "../../components/institution/CareRecipientForm";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

const GENDER_LABEL = {
  MALE: "남",
  FEMALE: "여",
};

const STATUS_LABEL = {
  ACTIVE: "관리 중",
  INACTIVE: "관리 종료",
};

const CONSENT_STATUS_LABEL = {
  PENDING: "동의 대기",
  AGREED: "동의 완료",
  WITHDRAWN: "동의 철회",
};

const display = (value, fallback = "미등록") =>
  value === null || value === undefined || value === "" ? fallback : value;

const formatDateTime = (value, fallback = "기록 없음") => {
  if (!value) return fallback;

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return fallback;
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
};

function CareTargetDetail() {
  const { recipientId } = useParams();
  const navigate = useNavigate();

  // CARE-04 정보 수정 모달
  const [showEditForm, setShowEditForm] = useState(false);

  // CARE-05 동의 상태 변경 모달
  const [showConsentForm, setShowConsentForm] = useState(false);

  const [selectedConsentStatus, setSelectedConsentStatus] = useState("PENDING");

  const [submitting, setSubmitting] = useState(false);

  const [actionError, setActionError] = useState("");

  const loader = useCallback(
    () => institutionApi.getCareRecipient(recipientId),
    [recipientId],
  );

  const {
    data: recipient,
    loading,
    error,
    reload,
  } = useInstitutionData(loader, [loader]);

  if (loading || error) {
    return (
      <div className="institution-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  }

  /**
   * CARE-04 대상자 기본정보 수정.
   */
  const handleUpdate = async (request) => {
    setSubmitting(true);
    setActionError("");

    try {
      await institutionApi.updateCareRecipient(recipientId, request);

      setShowEditForm(false);

      // 수정된 상세정보 다시 조회
      await reload();
    } catch (reason) {
      setActionError(
        reason?.response?.data?.message ?? "대상자 정보 수정에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  /**
   * CARE-05 동의 상태 변경.
   */
  const handleConsentUpdate = async () => {
    setSubmitting(true);
    setActionError("");

    try {
      await institutionApi.updateCareRecipientConsent(
        recipientId,
        selectedConsentStatus,
      );

      setShowConsentForm(false);

      // 변경된 동의 정보 다시 조회
      await reload();
    } catch (reason) {
      setActionError(
        reason?.response?.data?.message ?? "동의 상태 변경에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const handleCloseManagement = async () => {
    const confirmed = window.confirm(
      "이 돌봄 대상자의 관리를 종료하시겠습니까?",
    );

    if (!confirmed) return;

    setSubmitting(true);
    setActionError("");

    try {
      await institutionApi.closeCareRecipient(recipientId);
      await reload();
    } catch (closeError) {
      setActionError(
        closeError?.response?.data?.message ??
          "관리 종료 중 오류가 발생했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };
  /**
   * CARE-07 종료된 돌봄 대상자의 관리를 재개한다.
   */
  const handleReopenManagement = async () => {
    const confirmed = window.confirm(
      "이 돌봄 대상자의 관리를 다시 시작하시겠습니까?",
    );

    if (!confirmed) return;

    setSubmitting(true);
    setActionError("");

    try {
      await institutionApi.reopenCareRecipient(recipientId);

      // 변경된 관리 상태를 다시 조회한다.
      await reload();
    } catch (reason) {
      setActionError(
        reason?.response?.data?.message ?? "관리 재개 중 오류가 발생했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };
  const reportCount = recipient?.reportCount ?? 0;

  const activityCount = recipient?.activityCount ?? 0;

  return (
    <div className="institution-page care-detail-page">
      <button
        className="detail-back"
        type="button"
        onClick={() => navigate("/institution/care-targets")}
      >
        ← 목록으로
      </button>

      <div className="page-title-row compact">
        <div>
          <p>돌봄 대상자 상세</p>

          <h1>{display(recipient?.name, "이름 미등록")}</h1>

          <span>대상자의 기본 정보와 돌봄 현황을 확인합니다.</span>
        </div>

        <div className="detail-title-actions">
          <div className="detail-badges">
            <span className={`care-tag ${recipient?.status}`}>
              {STATUS_LABEL[recipient?.status] ?? display(recipient?.status)}
            </span>

            <span className={`care-tag ${recipient?.consentStatus}`}>
              {CONSENT_STATUS_LABEL[recipient?.consentStatus] ??
                display(recipient?.consentStatus)}
            </span>
          </div>

          <button
            type="button"
            className="orange-action"
            onClick={() => {
              setActionError("");
              setShowEditForm(true);
            }}
          >
            정보 수정
          </button>

          {/* 관리 중인 대상자에게만 관리 종료 버튼을 표시한다. */}
          {recipient?.status === "ACTIVE" && (
            <button
              type="button"
              className="close-care-action"
              onClick={handleCloseManagement}
              disabled={submitting}
            >
              {submitting ? "처리 중..." : "관리 종료"}
            </button>
          )}

          {/* 관리 종료된 대상자에게만 관리 재개 버튼을 표시한다. */}
          {recipient?.status === "INACTIVE" && (
            <button
              type="button"
              className="reopen-care-action"
              onClick={handleReopenManagement}
              disabled={submitting}
            >
              {submitting ? "처리 중..." : "관리 재개"}
            </button>
          )}
        </div>
      </div>
      {/* 관리 종료·재개 요청 오류 */}
      {actionError && !showEditForm && !showConsentForm && (
        <div className="care-form-error detail-action-error">{actionError}</div>
      )}

      <section className="detail-summary-grid">
        <article className="panel">
          <span>최근 확인일</span>

          <strong>{formatDateTime(recipient?.lastCheckedAt)}</strong>
        </article>

        <article className="panel">
          <span>연결 제보</span>
          <strong>{reportCount}건</strong>
        </article>

        <article className="panel">
          <span>전체 활동</span>
          <strong>{activityCount}건</strong>
        </article>
      </section>

      <div className="care-detail-grid">
        <section className="panel detail-section">
          <div className="panel-title">
            <div>
              <h2>기본 정보</h2>
              <p>등록된 인적 사항과 연락처입니다.</p>
            </div>
          </div>

          <dl className="detail-info-list">
            <div>
              <dt>성별</dt>
              <dd>
                {GENDER_LABEL[recipient?.gender] ?? display(recipient?.gender)}
              </dd>
            </div>

            <div>
              <dt>출생연도</dt>
              <dd>
                {recipient?.birthYear ? `${recipient.birthYear}년` : "미등록"}
              </dd>
            </div>

            <div>
              <dt>전화번호</dt>
              <dd>{display(recipient?.phone)}</dd>
            </div>

            <div>
              <dt>주소</dt>
              <dd>{display(recipient?.address)}</dd>
            </div>

            <div>
              <dt>상세 주소</dt>
              <dd>{display(recipient?.detailAddress)}</dd>
            </div>

            <div>
              <dt>대상자 번호</dt>
              <dd>{display(recipient?.recipientId)}</dd>
            </div>
          </dl>
        </section>

        <section className="panel detail-section">
          <div className="panel-title">
            <div>
              <h2>동의 정보</h2>
              <p>개인정보 및 돌봄 서비스 동의 현황입니다.</p>
            </div>

            <button
              type="button"
              className="filter-button"
              onClick={() => {
                setActionError("");

                setSelectedConsentStatus(recipient?.consentStatus ?? "PENDING");

                setShowConsentForm(true);
              }}
            >
              동의 상태 변경
            </button>
          </div>

          <dl className="detail-info-list">
            <div>
              <dt>동의 상태</dt>
              <dd>
                {CONSENT_STATUS_LABEL[recipient?.consentStatus] ??
                  display(recipient?.consentStatus)}
              </dd>
            </div>

            <div>
              <dt>동의 일시</dt>
              <dd>{formatDateTime(recipient?.consentAt, "미동의")}</dd>
            </div>

            <div>
              <dt>철회 일시</dt>
              <dd>
                {formatDateTime(
                  recipient?.consentWithdrawnAt,
                  "철회 기록 없음",
                )}
              </dd>
            </div>
          </dl>
        </section>

        <section className="panel detail-section detail-wide">
          <div className="panel-title">
            <div>
              <h2>제보·활동 요약</h2>
              <p>대상자와 연결된 돌봄 진행 현황입니다.</p>
            </div>
          </div>

          <div className="linked-summary">
            <article>
              <span>제보</span>
              <strong>{reportCount}건</strong>
              <p>대상자와 연결된 전체 제보</p>
            </article>

            <article>
              <span>활동</span>
              <strong>{activityCount}건</strong>
              <p>대상자와 연결된 전체 활동</p>
            </article>
          </div>
        </section>
      </div>

      {/* CARE-04 정보 수정 모달 */}
      {showEditForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowEditForm(false);
            }
          }}
        >
          <section
            className="care-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="care-edit-title"
          >
            <div className="care-modal-header">
              <div>
                <p>돌봄 대상자</p>
                <h2 id="care-edit-title">대상자 정보 수정</h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                onClick={() => setShowEditForm(false)}
                disabled={submitting}
              >
                ×
              </button>
            </div>

            {actionError && (
              <div className="care-form-error">{actionError}</div>
            )}

            <CareRecipientForm
              mode="edit"
              initialValues={recipient}
              submitting={submitting}
              onSubmit={handleUpdate}
              onCancel={() => setShowEditForm(false)}
            />
          </section>
        </div>
      )}

      {/* CARE-05 동의 상태 변경 모달 */}
      {showConsentForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) {
              setShowConsentForm(false);
            }
          }}
        >
          <section
            className="care-modal care-consent-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="care-consent-title"
          >
            <div className="care-modal-header">
              <div>
                <p>돌봄 대상자</p>

                <h2 id="care-consent-title">동의 상태 변경</h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                onClick={() => setShowConsentForm(false)}
                disabled={submitting}
              >
                ×
              </button>
            </div>

            <div className="care-consent-body">
              <label>
                <span>동의 상태</span>

                <select
                  value={selectedConsentStatus}
                  onChange={(event) =>
                    setSelectedConsentStatus(event.target.value)
                  }
                  disabled={submitting}
                >
                  <option value="PENDING">동의 대기</option>

                  <option value="AGREED">동의 완료</option>

                  <option value="WITHDRAWN">동의 철회</option>
                </select>
              </label>

              {actionError && (
                <div className="care-form-error consent-error">
                  {actionError}
                </div>
              )}

              <div className="care-form-actions">
                <button
                  type="button"
                  className="care-form-cancel"
                  onClick={() => setShowConsentForm(false)}
                  disabled={submitting}
                >
                  취소
                </button>

                <button
                  type="button"
                  className="orange-action"
                  onClick={handleConsentUpdate}
                  disabled={submitting}
                >
                  {submitting ? "변경 중..." : "동의 상태 변경"}
                </button>
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

export default CareTargetDetail;
