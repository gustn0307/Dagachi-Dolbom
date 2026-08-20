import { useEffect, useRef, useState } from "react";
import PageHeader from "../../components/common/PageHeader";

const CameraIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M8.2 5.5 9.4 4h5.2l1.2 1.5H19a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-10a2 2 0 0 1 2-2h3.2Z" />
    <circle cx="12" cy="12.5" r="3.5" />
  </svg>
);

const LocationIcon = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M20 10c0 5-8 11-8 11S4 15 4 10a8 8 0 1 1 16 0Z" />
    <circle cx="12" cy="10" r="2.5" />
  </svg>
);

function Report() {
  const fileRef = useRef(null);
  const [description, setDescription] = useState("");
  const [preview, setPreview] = useState("");
  const [fileName, setFileName] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => () => preview && URL.revokeObjectURL(preview), [preview]);

  const handleFile = (file) => {
    if (!file || !file.type.startsWith("image/")) return;
    if (preview) URL.revokeObjectURL(preview);
    setPreview(URL.createObjectURL(file));
    setFileName(file.name);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    // BACKEND: FormData를 만들어 POST /api/reports로 전송할 위치입니다.
    // 사진 파일이 있으므로 JSON 대신 multipart/form-data를 사용하고,
    // 서버가 반환한 접수 번호를 state에 저장해 완료 화면에 표시하세요.
    // 예: const result = await reportApi.create(new FormData(event.currentTarget));
    setSubmitted(true);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  if (submitted) {
    return (
      <main className="report-page report-complete">
        <section className="success-panel" aria-live="polite">
          <span className="success-check">✓</span>
          <p className="success-kicker">접수 완료</p>
          <h1>소중한 제보가 접수되었어요</h1>
          <p className="success-copy">
            담당 기관에서 내용을 확인한 뒤 도움이 필요한 이웃에게 신속하게
            연락하겠습니다.
          </p>
          <div className="receipt-card">
            <div>
              <span>접수 번호</span>
              {/* BACKEND: 신고 등록 API가 반환하는 reportNumber 값으로 교체합니다. */}
              <strong>CARE-260812-0142</strong>
            </div>
            <div>
              <span>처리 상태</span>
              <strong className="status-pill">확인 대기</strong>
            </div>
          </div>
          <button className="secondary-action" type="button" onClick={() => setSubmitted(false)}>
            새로운 제보 작성하기
          </button>
        </section>
      </main>
    );
  }

  return (
    <main className="report-page">
      <PageHeader
        eyebrow="우리 동네의 따뜻한 관심"
        title="도움이 필요한 이웃을 알려주세요"
        text="작은 관심이 누군가에게는 큰 도움이 됩니다. 입력한 정보는 지원을 위해서만 안전하게 사용됩니다."
      />

      <div className="report-layout">
        <aside className="report-guide" aria-label="제보 안내">
          <span className="guide-badge">안심 제보</span>
          <h2>제보 전 확인해 주세요</h2>
          <ol>
            <li><b>01</b><span><strong>위치를 알려주세요</strong>정확하지 않아도 괜찮아요.</span></li>
            <li><b>02</b><span><strong>상황을 설명해 주세요</strong>직접 본 내용을 중심으로 적어주세요.</span></li>
            <li><b>03</b><span><strong>안전하게 전달해요</strong>담당 기관만 내용을 확인합니다.</span></li>
          </ol>
          <div className="emergency-note">
            <span>긴급한 상황인가요?</span>
            생명이나 안전이 위급한 경우에는 <strong>112 또는 119</strong>로 먼저 연락해 주세요.
          </div>
        </aside>

        <form className="form-card report-form" onSubmit={handleSubmit}>
          <div className="form-intro">
            <div><span>제보서</span><h2>이웃의 상황을 알려주세요</h2></div>
            <small><i>*</i> 필수 입력</small>
          </div>

          <div className="field-group">
            <label htmlFor="report-location">발견 위치 <em>*</em></label>
            <p>건물명이나 주변의 눈에 띄는 장소를 함께 적어주세요.</p>
            <div className="input-with-icon">
              <LocationIcon />
              <input id="report-location" name="location" type="text" required placeholder="예: 행복구 한마음로 123, 온누리 약국 앞" />
            </div>
          </div>

          <div className="field-group">
            <div className="label-row">
              <label htmlFor="report-description">상황 설명 <em>*</em></label>
              <span>{description.length}/500</span>
            </div>
            <p>도움이 필요해 보인 이유와 현재 상황을 구체적으로 적어주세요.</p>
            <textarea id="report-description" name="description" rows="6" maxLength="500" required value={description} onChange={(event) => setDescription(event.target.value)} placeholder="예: 며칠째 같은 장소에서 식사를 거르고 계신 어르신을 보았습니다. 오늘은 거동도 불편해 보였습니다." />
          </div>

          <div className="field-group">
            <label>사진 첨부 <span className="optional">선택</span></label>
            <p>위치나 상황을 확인할 수 있는 사진을 첨부하면 도움이 됩니다.</p>
            <input ref={fileRef} className="sr-only" type="file" accept="image/png,image/jpeg,image/webp" onChange={(event) => handleFile(event.target.files[0])} />
            <button className={`upload ${preview ? "has-preview" : ""}`} type="button" onClick={() => fileRef.current?.click()} onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); handleFile(event.dataTransfer.files[0]); }}>
              {preview ? <img src={preview} alt="첨부 사진 미리보기" /> : <span className="upload-icon"><CameraIcon /></span>}
              <span className="upload-copy">
                <strong>{fileName || "사진을 선택하거나 이곳에 끌어놓으세요"}</strong>
                <small>JPG, PNG, WEBP · 최대 10MB</small>
              </span>
              <span className="upload-button">파일 선택</span>
            </button>
          </div>

          <div className="field-group">
            <label htmlFor="report-contact">연락처 <span className="optional">선택</span></label>
            <p>추가 확인이 필요한 경우에만 연락드려요.</p>
            <input id="report-contact" name="contact" type="tel" inputMode="tel" placeholder="010-0000-0000" />
          </div>

          <label className="consent-card">
            <input type="checkbox" checked={agreed} onChange={(event) => setAgreed(event.target.checked)} />
            <span><strong>개인정보 수집 및 이용에 동의합니다. <em>*</em></strong><small>제보 처리 목적으로만 사용되며, 처리 완료 후 안전하게 폐기됩니다.</small></span>
          </label>

          <button className="submit" type="submit" disabled={!agreed}>
            안전하게 제보 접수하기 <span>→</span>
          </button>
          <p className="form-security">🔒 입력한 내용은 암호화되어 안전하게 전달됩니다.</p>
        </form>
      </div>
    </main>
  );
}

export default Report;
