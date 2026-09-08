import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { userApi } from "../../api/userApi";

const OPTION_LABELS = {
  YES: "예",
  NO: "아니오",
  UNKNOWN: "확인 어려움",
};

const REVIEW_STATUS_LABELS = {
  DRAFT: "작성 중",
  SUBMITTED: "제출 완료",
  APPROVED: "승인 완료",
  NEEDS_REVISION: "보완 요청",
  REJECTED: "반려",
};

/*
 * 서버의 LocalDateTime 값을
 * datetime-local input에서 사용할 형식으로 변환합니다.
 */
function toInputDateTime(value) {
  if (!value) {
    return "";
  }

  return value.slice(0, 16);
}

/*
 * datetime-local input 값을
 * Backend LocalDateTime 요청값으로 변환합니다.
 */
function toApiDateTime(value) {
  if (!value) {
    return null;
  }

  return value.length === 16 ? `${value}:00` : value;
}

/*
 * 날짜/시간을 화면 표시용 한국어 형식으로 변환합니다.
 */
function formatDateTime(value) {
  if (!value) {
    return "-";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return date.toLocaleString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function ActivityRecord() {
  const navigate = useNavigate();
  const { recordId } = useParams();

  /*
   * RECORD-02에서 받은 ActivityRecord 전체 상태입니다.
   */
  const [record, setRecord] = useState(null);

  /*
   * CHECK-01에서 받은 체크리스트 문항입니다.
   * selectedValue도 각 문항 안에서 함께 관리합니다.
   */
  const [checklistItems, setChecklistItems] = useState([]);

  /*
   * RECORD-03에서 저장할 ActivityRecord 기본 입력값입니다.
   */
  const [form, setForm] = useState({
    visitResult: "",
    completedAt: "",
    specialNote: "",
  });

  /*
   * RECORD-04에서 업로드할 대상자 서명 파일입니다.
   */
  const [signatureFile, setSignatureFile] = useState(null);

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [actionError, setActionError] = useState("");

  const [successMessage, setSuccessMessage] = useState("");

  /*
   * 성공/오류 메시지를 사용자가 실행한 기능 가까이에 표시하기 위해
   * 메시지가 어느 영역에서 발생했는지 구분합니다.
   * "signature" = 서명 영역
   * "actions" = 임시저장 / 최종 제출 영역
   */
  const [messageTarget, setMessageTarget] = useState("");

  const [saving, setSaving] = useState(false);
  const [uploadingSignature, setUploadingSignature] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  /*
   * DRAFT와 NEEDS_REVISION 상태에서만
   * 활동기록을 수정할 수 있습니다.
   */
  const editable =
    record?.reviewStatus === "DRAFT" ||
    record?.reviewStatus === "NEEDS_REVISION";

  /*
   * CHECK-01과 RECORD-02를 동시에 조회합니다.
   *
   * CHECK-01:
   * - 체크리스트 문항
   * - 선택지
   * - 기존 체크리스트 응답
   *
   * RECORD-02:
   * - 방문 결과
   * - 활동 시작/완료 시각
   * - 특이사항
   * - 서명 여부
   * - 검토 상태
   */
  useEffect(() => {
    let ignore = false;

    const loadActivityRecord = async () => {
      setLoading(true);
      setLoadError("");

      try {
        const [checklistResponse, recordResponse] = await Promise.all([
          userApi.getActivityChecklist(recordId),
          userApi.getActivityRecord(recordId),
        ]);

        if (ignore) {
          return;
        }

        setRecord(recordResponse);

        setChecklistItems(
          Array.isArray(checklistResponse?.items)
            ? checklistResponse.items
            : [],
        );

        setForm({
          visitResult: recordResponse?.visitResult ?? "",

          completedAt: toInputDateTime(recordResponse?.completedAt),

          specialNote: recordResponse?.specialNote ?? "",
        });
      } catch (error) {
        if (ignore) {
          return;
        }

        setLoadError(
          error?.response?.data?.message ?? "활동기록을 불러오지 못했습니다.",
        );
      } finally {
        if (!ignore) {
          setLoading(false);
        }
      }
    };

    loadActivityRecord();

    return () => {
      ignore = true;
    };
  }, [recordId]);

  /*
   * 방문 결과를 변경합니다.
   *
   * NOT_MET이면 체크리스트를 사용하지 않으므로
   * 현재 화면의 체크리스트 답변도 모두 비웁니다.
   */
  const handleVisitResultChange = (event) => {
    const value = event.target.value;

    setActionError("");
    setSuccessMessage("");

    setForm((current) => ({
      ...current,
      visitResult: value,
    }));

    if (value === "NOT_MET") {
      setChecklistItems((items) =>
        items.map((item) => ({
          ...item,
          selectedValue: null,
        })),
      );
    }
  };

  /*
   * 완료 시각과 특이사항 입력값을 변경합니다.
   */
  const handleFormChange = (event) => {
    const { name, value } = event.target;

    setActionError("");
    setSuccessMessage("");

    setForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  /*
   * CHECK-01 문항의 선택값을 변경합니다.
   */
  const handleChecklistChange = (itemId, selectedValue) => {
    setActionError("");
    setSuccessMessage("");

    setChecklistItems((items) =>
      items.map((item) =>
        item.id === itemId
          ? {
              ...item,
              selectedValue,
            }
          : item,
      ),
    );
  };

  /*
   * 현재 화면 상태를 RECORD-03 요청 DTO 구조로 변환합니다.
   *
   * MET:
   * 답변한 문항만 responses에 포함합니다.
   *
   * NOT_MET:
   * Backend 정책에 따라 responses는 빈 배열입니다.
   */
  const createDraftRequest = () => {
    const responses =
      form.visitResult === "MET"
        ? checklistItems
            .filter(
              (item) => item.selectedValue != null && item.selectedValue !== "",
            )
            .map((item) => ({
              itemId: item.id,
              selectedValue: item.selectedValue,
              textValue: null,
            }))
        : [];

    return {
      visitResult: form.visitResult || null,

      completedAt: toApiDateTime(form.completedAt),

      specialNote: form.specialNote,

      responses,
    };
  };

  /*
   * RECORD-03 공동 Draft를 저장합니다.
   */
  const handleSaveDraft = async () => {
    setMessageTarget("actions");

    setSaving(true);
    setActionError("");
    setSuccessMessage("");

    try {
      const response = await userApi.saveActivityRecordDraft(
        recordId,
        createDraftRequest(),
      );

      setRecord(response);

      /*
       * Backend에서 공백 특이사항 등을 정리했을 수 있으므로
       * 저장된 최신 값을 화면에도 반영합니다.
       */
      setForm((current) => ({
        ...current,

        visitResult: response?.visitResult ?? "",

        completedAt: toInputDateTime(response?.completedAt),

        specialNote: response?.specialNote ?? "",
      }));

      setSuccessMessage("임시저장되었습니다.");
    } catch (error) {
      setActionError(
        error?.response?.data?.message ?? "활동기록 저장에 실패했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  /*
   * RECORD-04 대상자 서명을 업로드합니다.
   *
   * 서명 API는 서버에 저장된 visitResult가 MET인 경우에만
   * 사용할 수 있으므로 먼저 Draft 저장이 필요합니다.
   */
  const handleSignatureUpload = async () => {
    setMessageTarget("signature");

    if (!signatureFile) {
      setActionError("업로드할 서명 이미지를 선택해주세요.");
      return;
    }

    if (form.visitResult !== "MET" || record?.visitResult !== "MET") {
      setActionError("대상자를 만남으로 선택한 뒤 먼저 임시저장해주세요.");
      return;
    }

    setUploadingSignature(true);
    setActionError("");
    setSuccessMessage("");

    try {
      const response = await userApi.uploadActivityRecordSignature(
        recordId,
        signatureFile,
      );

      setRecord((current) => ({
        ...current,
        signatureUploaded: response?.signatureUploaded ?? true,
      }));

      setSignatureFile(null);

      setSuccessMessage("서명이 등록되었습니다.");
    } catch (error) {
      setActionError(
        error?.response?.data?.message ?? "서명 업로드에 실패했습니다.",
      );
    } finally {
      setUploadingSignature(false);
    }
  };

  /*
   * RECORD-05 최종 제출입니다.
   *
   * RECORD-05는 Request Body를 받지 않고
   * DB에 저장되어 있는 Draft를 검증하기 때문에,
   * 먼저 현재 화면 내용을 RECORD-03으로 저장한 뒤 제출합니다.
   */
  const handleSubmit = async () => {
    const confirmed = window.confirm("활동기록을 최종 제출하시겠습니까?");

    if (!confirmed) {
      return;
    }

    setMessageTarget("actions");

    setSubmitting(true);
    setActionError("");
    setSuccessMessage("");

    try {
      /*
       * 사용자가 마지막으로 수정한 화면 값을
       * 먼저 공동 Draft에 저장합니다.
       */
      const savedRecord = await userApi.saveActivityRecordDraft(
        recordId,
        createDraftRequest(),
      );

      setRecord(savedRecord);

      /*
       * 저장된 Draft를 RECORD-05로 최종 제출합니다.
       */
      const submittedRecord = await userApi.submitActivityRecord(recordId);

      setRecord(submittedRecord);

      setSuccessMessage("활동기록이 제출되었습니다.");
    } catch (error) {
      setActionError(
        error?.response?.data?.message ?? "활동기록 제출에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <main className="activity-record-page">
        <div className="activity-record-state">
          활동기록을 불러오고 있습니다.
        </div>
      </main>
    );
  }

  if (loadError || !record) {
    return (
      <main className="activity-record-page">
        <div className="activity-record-state error">
          {loadError || "활동기록을 찾을 수 없습니다."}
        </div>

        <button type="button" onClick={() => navigate("/volunteer")}>
          봉사 활동으로 돌아가기
        </button>
      </main>
    );
  }

  return (
    <main className="activity-record-page">
      <button
        type="button"
        className="activity-record-back"
        onClick={() => navigate("/volunteer")}
      >
        ← 봉사 활동으로
      </button>

      <header className="activity-record-header">
        <p>ACTIVITY RECORD</p>

        <h1>활동기록 작성</h1>

        <span>
          활동 #{record.activityId}
          {" · "}
          기록 #{record.recordId}
        </span>
      </header>

      <section className="activity-record-summary">
        <article>
          <span>기록 상태</span>

          <strong>
            {REVIEW_STATUS_LABELS[record.reviewStatus] ?? record.reviewStatus}
          </strong>
        </article>

        <article>
          <span>활동 시작</span>

          <strong>{formatDateTime(record.startedAt)}</strong>
        </article>

        <article>
          <span>서명</span>

          <strong>{record.signatureUploaded ? "등록 완료" : "미등록"}</strong>
        </article>
      </section>

      {record.reviewStatus === "NEEDS_REVISION" && record.reviewNote && (
        <section className="activity-record-review-note">
          <strong>기관 보완 요청</strong>

          <p>{record.reviewNote}</p>
        </section>
      )}

      <section className="activity-record-section">
        <div className="activity-record-section-title">
          <span>1</span>

          <div>
            <h2>방문 결과</h2>

            <p>돌봄 대상자를 직접 만났는지 선택해주세요.</p>
          </div>
        </div>

        <div className="activity-record-visit-options">
          <label>
            <input
              type="radio"
              name="visitResult"
              value="MET"
              checked={form.visitResult === "MET"}
              disabled={!editable}
              onChange={handleVisitResultChange}
            />

            <strong>만남</strong>
            <span>대상자를 만나 안부를 확인했습니다.</span>
          </label>

          <label>
            <input
              type="radio"
              name="visitResult"
              value="NOT_MET"
              checked={form.visitResult === "NOT_MET"}
              disabled={!editable || record.signatureUploaded}
              onChange={handleVisitResultChange}
            />

            <strong>미만남</strong>
            <span>방문했지만 대상자를 만나지 못했습니다.</span>
          </label>
        </div>

        {record.signatureUploaded && form.visitResult === "MET" && editable && (
          <p className="activity-record-help">
            서명이 등록된 기록은 미만남으로 변경할 수 없습니다.
          </p>
        )}
      </section>

      {form.visitResult === "MET" && (
        <section className="activity-record-section">
          <div className="activity-record-section-title">
            <span>2</span>

            <div>
              <h2>안부 체크리스트</h2>

              <p>대상자의 현재 상태를 확인해주세요.</p>
            </div>
          </div>

          <div className="activity-record-checklist">
            {checklistItems.map((item, index) => (
              <article key={item.id}>
                <div className="activity-record-question">
                  <span>{index + 1}</span>

                  <strong>{item.question}</strong>

                  {item.required && <em>필수</em>}
                </div>

                <div className="activity-record-answer-options">
                  {(item.options ?? []).map((option) => (
                    <label key={option}>
                      <input
                        type="radio"
                        name={`checklist-${item.id}`}
                        value={option}
                        checked={item.selectedValue === option}
                        disabled={!editable}
                        onChange={() => handleChecklistChange(item.id, option)}
                      />

                      <span>{OPTION_LABELS[option] ?? option}</span>
                    </label>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
      )}

      <section className="activity-record-section">
        <div className="activity-record-section-title">
          <span>{form.visitResult === "MET" ? "3" : "2"}</span>

          <div>
            <h2>활동 결과</h2>

            <p>활동 종료 시각과 특이사항을 기록해주세요.</p>
          </div>
        </div>

        <div className="activity-record-fields">
          <label>
            <span>활동 완료 시각</span>

            <input
              type="datetime-local"
              name="completedAt"
              value={form.completedAt}
              disabled={!editable}
              onChange={handleFormChange}
            />
          </label>

          <label>
            <span>
              특이사항
              {form.visitResult === "NOT_MET" && <em> 필수</em>}
            </span>

            <textarea
              name="specialNote"
              value={form.specialNote}
              disabled={!editable}
              rows={5}
              placeholder={
                form.visitResult === "NOT_MET"
                  ? "대상자를 만나지 못한 상황을 입력해주세요."
                  : "특이사항이 있다면 입력해주세요."
              }
              onChange={handleFormChange}
            />
          </label>
        </div>
      </section>

      {form.visitResult === "MET" && (
        <section className="activity-record-section">
          <div className="activity-record-section-title">
            <span>4</span>

            <div>
              <h2>대상자 서명</h2>

              <p>활동 내용을 확인한 대상자의 서명 이미지를 등록합니다.</p>
            </div>
          </div>

          {record.signatureUploaded ? (
            <div className="activity-record-signature-status">
              ✓ 서명이 등록되어 있습니다.
              {editable && (
                <span>잘못 등록한 경우 새 이미지로 교체할 수 있습니다.</span>
              )}
            </div>
          ) : (
            <div className="activity-record-signature-status">
              아직 서명이 등록되지 않았습니다.
            </div>
          )}

          {editable && (
            <div className="activity-record-signature-upload">
              <input
                type="file"
                accept="image/jpeg,image/png"
                onChange={(event) =>
                  setSignatureFile(event.target.files?.[0] ?? null)
                }
              />

              <button
                type="button"
                disabled={uploadingSignature || !signatureFile}
                onClick={handleSignatureUpload}
              >
                {uploadingSignature
                  ? "업로드 중..."
                  : record.signatureUploaded
                    ? "서명 교체"
                    : "서명 등록"}
              </button>

              {/* 서명 등록/교체 결과는 서명 영역 바로 아래에 표시합니다. */}
              {messageTarget === "signature" && actionError && (
                <div className="activity-record-message error">
                  {actionError}
                </div>
              )}

              {messageTarget === "signature" && successMessage && (
                <div className="activity-record-message success">
                  {successMessage}
                </div>
              )}

              <small>
                JPEG 또는 PNG 이미지를 사용할 수 있습니다. 방문 결과를 먼저
                임시저장한 뒤 등록해주세요.
              </small>
            </div>
          )}
        </section>
      )}

      {editable ? (
        <section className="activity-record-actions">
          {/* 임시저장 / 최종 제출 결과는 하단 버튼 영역에 표시합니다. */}
          {messageTarget === "actions" && actionError && (
            <div className="activity-record-message error">{actionError}</div>
          )}

          {messageTarget === "actions" && successMessage && (
            <div className="activity-record-message success">
              {successMessage}
            </div>
          )}

          <button
            type="button"
            disabled={saving || submitting || uploadingSignature}
            onClick={handleSaveDraft}
          >
            {saving ? "저장 중..." : "임시저장"}
          </button>

          <button
            type="button"
            className="activity-record-submit"
            disabled={saving || submitting || uploadingSignature}
            onClick={handleSubmit}
          >
            {submitting
              ? "제출 중..."
              : record.reviewStatus === "NEEDS_REVISION"
                ? "수정 내용 재제출"
                : "최종 제출"}
          </button>
        </section>
      ) : (
        <section className="activity-record-readonly">
          <strong>
            {REVIEW_STATUS_LABELS[record.reviewStatus] ?? record.reviewStatus}
          </strong>

          <p>제출된 활동기록은 조회만 할 수 있습니다.</p>
        </section>
      )}
    </main>
  );
}

export default ActivityRecord;