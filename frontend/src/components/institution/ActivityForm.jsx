import { useEffect, useState } from "react";

import { institutionApi } from "../../api/institutionApi";

const INITIAL_FORM = {
  recipientId: "",
  scheduledAt: "",
  requiredPeople: 2,
  genderCondition: "NONE",
};

function ActivityForm({
  submitting = false,
  onSubmit,
  onCancel,
}) {
  const [form, setForm] =
    useState(INITIAL_FORM);

  const [recipients, setRecipients] =
    useState([]);

  const [loadingRecipients, setLoadingRecipients] =
    useState(true);

  const [recipientError, setRecipientError] =
    useState("");

  /*
   * 활동 등록에 사용할 수 있는
   * 현재 관리 중인 돌봄 대상자를 불러온다.
   */
  useEffect(() => {
    let ignore = false;

    const loadRecipients = async () => {
      setLoadingRecipients(true);
      setRecipientError("");

      try {
        const data =
          await institutionApi.getCareRecipients({
            status: "ACTIVE",
            page: 0,
            size: 100,
          });

        if (ignore) {
          return;
        }

        setRecipients(
          Array.isArray(data?.content)
            ? data.content
            : [],
        );
      } catch (error) {
        if (ignore) {
          return;
        }

        setRecipientError(
          error?.response?.data?.message ??
            "돌봄 대상자 목록을 불러오지 못했습니다.",
        );
      } finally {
        if (!ignore) {
          setLoadingRecipients(false);
        }
      }
    };

    loadRecipients();

    return () => {
      ignore = true;
    };
  }, []);

  const handleChange = (event) => {
    const {
      name,
      value,
    } = event.target;

    setForm(
      (current) => ({
        ...current,
        [name]: value,
      }),
    );
  };

  const handleSubmit = (event) => {
  event.preventDefault();

  const requiredPeople =
    Number(form.requiredPeople);

  if (requiredPeople < 2) {
    alert("모집 인원은 최소 2명이어야 합니다.");
    return;
  }

  onSubmit({
    recipientId:
      Number(form.recipientId),

    scheduledAt:
      form.scheduledAt,

    requiredPeople,

    genderCondition:
      form.genderCondition,
  });
};

  return (
    <form
      className="care-recipient-form"
      onSubmit={handleSubmit}
    >
      {recipientError && (
        <div className="care-form-error">
          {recipientError}
        </div>
      )}

      <div className="care-form-grid">
        <label className="care-form-wide">
          <span>돌봄 대상자</span>

          <select
            name="recipientId"
            value={form.recipientId}
            disabled={
              submitting ||
              loadingRecipients
            }
            required
            onChange={handleChange}
          >
            <option value="">
              {loadingRecipients
                ? "대상자를 불러오는 중입니다."
                : "돌봄 대상자를 선택하세요."}
            </option>

            {recipients.map(
              (recipient) => (
                <option
                  key={recipient.recipientId}
                  value={recipient.recipientId}
                >
                  {recipient.name}
                  {" · "}
                  {recipient.address}
                </option>
              ),
            )}
          </select>
        </label>

        <label>
          <span>활동 예정 일시</span>

          <input
            type="datetime-local"
            name="scheduledAt"
            value={form.scheduledAt}
            disabled={submitting}
            required
            onChange={handleChange}
          />
        </label>

        <label>
          <span>필요 인원</span>

          <input
            type="number"
            name="requiredPeople"
            value={form.requiredPeople}
            min="2"
            disabled={submitting}
            required
            onChange={handleChange}
          />
        </label>

        <label className="care-form-wide">
          <span>성별 조건</span>

          <select
            name="genderCondition"
            value={form.genderCondition}
            disabled={submitting}
            required
            onChange={handleChange}
          >
            <option value="NONE">
              성별 제한 없음
            </option>

            <option value="SAME_GENDER_ONE">
              대상자와 같은 성별 최소 1명
            </option>
          </select>
        </label>
      </div>

      <div className="care-form-actions">
        <button
          type="button"
          className="care-form-cancel"
          disabled={submitting}
          onClick={onCancel}
        >
          취소
        </button>

        <button
          type="submit"
          className="orange-action"
          disabled={
            submitting ||
            loadingRecipients ||
            recipients.length === 0
          }
        >
          {submitting
            ? "등록 중..."
            : "활동 등록"}
        </button>
      </div>
    </form>
  );
}

export default ActivityForm;