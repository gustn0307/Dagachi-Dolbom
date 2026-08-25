import { useState } from "react";

const EMPTY_FORM = {
  name: "",
  gender: "FEMALE",
  birthYear: "",
  phone: "",
  address: "",
  detailAddress: "",
  consentStatus: "PENDING",
};

function CareRecipientForm({
  mode = "create",
  initialValues,
  submitting = false,
  onSubmit,
  onCancel,
}) {
  const [form, setForm] = useState({
    ...EMPTY_FORM,
    ...initialValues,
    birthYear: initialValues?.birthYear ?? "",
  });

  const handleChange = (event) => {
    const { name, value } = event.target;

    setForm((previous) => ({
      ...previous,
      [name]: value,
    }));
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    const request = {
      name: form.name.trim(),
      gender: form.gender,
      birthYear: form.birthYear === "" ? null : Number(form.birthYear),
      phone: form.phone.trim(),
      address: form.address.trim(),
      detailAddress: form.detailAddress.trim(),
    };

    // 동의 상태는 신규 등록할 때만 함께 전송한다.
    // 수정 화면에서는 CARE-05 API로 별도 변경한다.
    if (mode === "create") {
      request.consentStatus = form.consentStatus;
    }

    await onSubmit(request);
  };

  return (
    <form className="care-recipient-form" onSubmit={handleSubmit}>
      <div className="care-form-grid">
        <label>
          <span>이름 *</span>
          <input
            name="name"
            value={form.name}
            onChange={handleChange}
            maxLength={100}
            required
          />
        </label>

        <label>
          <span>성별 *</span>
          <select
            name="gender"
            value={form.gender}
            onChange={handleChange}
            required
          >
            <option value="FEMALE">여성</option>
            <option value="MALE">남성</option>
          </select>
        </label>

        <label>
          <span>출생연도</span>
          <input
            type="number"
            name="birthYear"
            value={form.birthYear}
            onChange={handleChange}
            min={1900}
            max={2100}
            placeholder="예: 1948"
          />
        </label>

        <label>
          <span>전화번호</span>
          <input
            type="tel"
            name="phone"
            value={form.phone}
            onChange={handleChange}
            maxLength={30}
            placeholder="010-0000-0000"
          />
        </label>

        <label className="care-form-wide">
          <span>주소 *</span>
          <input
            name="address"
            value={form.address}
            onChange={handleChange}
            maxLength={255}
            required
          />
        </label>

        <label className="care-form-wide">
          <span>상세 주소</span>
          <input
            name="detailAddress"
            value={form.detailAddress}
            onChange={handleChange}
            maxLength={255}
          />
        </label>

        

        {mode === "create" && (
          <label className="care-form-wide">
            <span>동의 상태 *</span>
            <select
              name="consentStatus"
              value={form.consentStatus}
              onChange={handleChange}
              required
            >
              <option value="PENDING">동의 대기</option>
              <option value="AGREED">동의 완료</option>
              <option value="WITHDRAWN">동의 철회</option>
            </select>
          </label>
        )}
      </div>

      <div className="care-form-actions">
        <button
          type="button"
          className="care-form-cancel"
          onClick={onCancel}
          disabled={submitting}
        >
          취소
        </button>

        <button type="submit" className="orange-action" disabled={submitting}>
          {submitting
            ? "저장 중..."
            : mode === "create"
              ? "대상자 등록"
              : "정보 수정"}
        </button>
      </div>
    </form>
  );
}

export default CareRecipientForm;
