import { useEffect, useRef, useState } from "react";
import PageHeader from "../../components/common/PageHeader";
import { useAuth } from "../../auth/AuthContext";
import { createReport } from "../../api/userApi";

const MAX_IMAGES = 3;
const MAX_IMAGE_SIZE = 10 * 1024 * 1024;

const ALLOWED_IMAGE_TYPES = [
  "image/jpeg",
  "image/png",
];

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

  const { isAuthenticated } = useAuth();

  const [location, setLocation] = useState("");
  const [description, setDescription] = useState("");
  const [contact, setContact] = useState("");

  const [files, setFiles] = useState([]);
  const [previews, setPreviews] = useState([]);

  const [agreed, setAgreed] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const [result, setResult] = useState(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    return () => {
      previews.forEach((preview) => {
        URL.revokeObjectURL(preview.url);
      });
    };
  }, [previews]);

  const validateFiles = (selectedFiles) => {
    if (selectedFiles.length > MAX_IMAGES) {
      return `사진은 최대 ${MAX_IMAGES}장까지 첨부할 수 있습니다.`;
    }

    for (const file of selectedFiles) {
      if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
        return "JPG 또는 PNG 이미지만 첨부할 수 있습니다.";
      }

      if (file.size > MAX_IMAGE_SIZE) {
        return "사진 한 장의 크기는 최대 10MB까지 가능합니다.";
      }
    }

    return null;
  };

  const handleFiles = (selectedFiles) => {
    const nextFiles = Array.from(selectedFiles);

    if (nextFiles.length === 0) {
      return;
    }

    const validationError = validateFiles(nextFiles);

    if (validationError) {
      setError(validationError);

      if (fileRef.current) {
        fileRef.current.value = "";
      }

      return;
    }

    previews.forEach((preview) => {
      URL.revokeObjectURL(preview.url);
    });

    const nextPreviews = nextFiles.map((file) => ({
      name: file.name,
      url: URL.createObjectURL(file),
    }));

    setFiles(nextFiles);
    setPreviews(nextPreviews);
    setError("");
  };

  const removeImage = (index) => {
    const removedPreview = previews[index];

    if (removedPreview) {
      URL.revokeObjectURL(removedPreview.url);
    }

    setFiles((current) =>
      current.filter((_, fileIndex) => fileIndex !== index),
    );

    setPreviews((current) =>
      current.filter((_, previewIndex) => previewIndex !== index),
    );

    if (fileRef.current) {
      fileRef.current.value = "";
    }
  };

  const resetForm = () => {
    previews.forEach((preview) => {
      URL.revokeObjectURL(preview.url);
    });

    setLocation("");
    setDescription("");
    setContact("");
    setFiles([]);
    setPreviews([]);
    setAgreed(false);
    setSubmitted(false);
    setResult(null);
    setError("");

    if (fileRef.current) {
      fileRef.current.value = "";
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (submitting) {
      return;
    }

    setError("");

    if (!location.trim()) {
      setError("발견 위치를 입력해 주세요.");
      return;
    }

    if (!description.trim()) {
      setError("상황 설명을 입력해 주세요.");
      return;
    }

    if (!isAuthenticated && !contact.trim()) {
      setError("비회원 제보는 연락처 입력이 필요합니다.");
      return;
    }

    if (!agreed) {
      setError("개인정보 수집 및 이용에 동의해 주세요.");
      return;
    }

    const validationError = validateFiles(files);

    if (validationError) {
      setError(validationError);
      return;
    }

    const requestData = {
      content: description.trim(),
      address: location.trim(),
      latitude: null,
      longitude: null,
      guestPhone: isAuthenticated ? null : contact.trim(),
    };

    const formData = new FormData();

    formData.append(
      "request",
      new Blob(
        [JSON.stringify(requestData)],
        {
          type: "application/json",
        },
      ),
    );

    files.forEach((file) => {
      formData.append("images", file);
    });

    try {
      setSubmitting(true);

      const response = await createReport(formData);

      setResult(response);
      setSubmitted(true);

      window.scrollTo({
        top: 0,
        behavior: "smooth",
      });
    } catch (requestError) {
      const message =
        requestError?.response?.data?.message
        ?? "제보 접수 중 오류가 발생했습니다.";

      setError(message);
    } finally {
      setSubmitting(false);
    }
  };

  if (submitted && result) {
    return (
      <main className="report-page report-complete">
        <section className="success-panel" aria-live="polite">
          <span className="success-check">✓</span>

          <p className="success-kicker">
            접수 완료
          </p>

          <h1>
            소중한 제보가 접수되었어요
          </h1>

          <p className="success-copy">
            담당 기관에서 내용을 확인한 뒤 도움이 필요한 이웃에게 신속하게
            연락하겠습니다.
          </p>

          <div className="receipt-card">
            <div>
              <span>접수 번호</span>
              <strong>
                {result.reportId}
              </strong>
            </div>

            <div>
              <span>처리 상태</span>
              <strong className="status-pill">
                {result.status === "SUBMITTED"
                  ? "확인 대기"
                  : result.status}
              </strong>
            </div>
          </div>

          <button
            className="secondary-action"
            type="button"
            onClick={resetForm}
          >
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
        text={
          isAuthenticated
            ? "접수한 제보는 회원 계정에 저장되며 마이페이지에서 처리 상태를 확인할 수 있습니다."
            : "로그인 없이도 제보할 수 있습니다. 비회원 제보는 처리 확인을 위해 연락처가 필요합니다."
        }
      />

      <div className="report-layout">
        <aside
          className="report-guide"
          aria-label="제보 안내"
        >
          <span className="guide-badge">
            안심 제보
          </span>

          <h2>
            제보 전 확인해 주세요
          </h2>

          <ol>
            <li>
              <b>01</b>
              <span>
                <strong>위치를 알려주세요</strong>
                정확하지 않아도 괜찮아요.
              </span>
            </li>

            <li>
              <b>02</b>
              <span>
                <strong>상황을 설명해 주세요</strong>
                직접 본 내용을 중심으로 적어주세요.
              </span>
            </li>

            <li>
              <b>03</b>
              <span>
                <strong>안전하게 전달해요</strong>
                담당 기관만 내용을 확인합니다.
              </span>
            </li>
          </ol>

          <div className="emergency-note">
            <span>긴급한 상황인가요?</span>
            생명이나 안전이 위급한 경우에는{" "}
            <strong>112 또는 119</strong>로 먼저 연락해 주세요.
          </div>
        </aside>

        <form
          className="form-card report-form"
          onSubmit={handleSubmit}
        >
          <div className="form-intro">
            <div>
              <span>
                {isAuthenticated
                  ? "회원 제보서"
                  : "비회원 제보서"}
              </span>

              <h2>
                이웃의 상황을 알려주세요
              </h2>
            </div>

            <small>
              <i>*</i> 필수 입력
            </small>
          </div>

          <div className="field-group">
            <label htmlFor="report-location">
              발견 위치 <em>*</em>
            </label>

            <p>
              건물명이나 주변의 눈에 띄는 장소를 함께 적어주세요.
            </p>

            <div className="input-with-icon">
              <LocationIcon />

              <input
                id="report-location"
                name="location"
                type="text"
                required
                maxLength="255"
                value={location}
                onChange={(event) =>
                  setLocation(event.target.value)
                }
                placeholder="예: 행복구 한마음로 123, 온누리 약국 앞"
              />
            </div>
          </div>

          <div className="field-group">
            <div className="label-row">
              <label htmlFor="report-description">
                상황 설명 <em>*</em>
              </label>

              <span>
                {description.length}/500
              </span>
            </div>

            <p>
              도움이 필요해 보인 이유와 현재 상황을 구체적으로 적어주세요.
            </p>

            <textarea
              id="report-description"
              name="description"
              rows="6"
              maxLength="500"
              required
              value={description}
              onChange={(event) =>
                setDescription(event.target.value)
              }
              placeholder="예: 며칠째 같은 장소에서 식사를 거르고 계신 어르신을 보았습니다. 오늘은 거동도 불편해 보였습니다."
            />
          </div>

          <div className="field-group">
            <label>
              사진 첨부{" "}
              <span className="optional">
                선택
              </span>
            </label>

            <p>
              JPG 또는 PNG 이미지를 최대 3장까지 첨부할 수 있습니다.
            </p>

            <input
              ref={fileRef}
              className="sr-only"
              type="file"
              accept="image/png,image/jpeg"
              multiple
              onChange={(event) =>
                handleFiles(event.target.files)
              }
            />

            <button
              className={`upload ${previews.length > 0 ? "has-preview" : ""}`}
              type="button"
              onClick={() =>
                fileRef.current?.click()
              }
              onDragOver={(event) =>
                event.preventDefault()
              }
              onDrop={(event) => {
                event.preventDefault();
                handleFiles(
                  event.dataTransfer.files,
                );
              }}
            >
              {previews.length > 0 ? (
                <span className="upload-copy">
                  <strong>
                    사진 {previews.length}장 선택됨
                  </strong>

                  <small>
                    JPG, PNG · 최대 3장 · 파일당 최대 10MB
                  </small>
                </span>
              ) : (
                <>
                  <span className="upload-icon">
                    <CameraIcon />
                  </span>

                  <span className="upload-copy">
                    <strong>
                      사진을 선택하거나 이곳에 끌어놓으세요
                    </strong>

                    <small>
                      JPG, PNG · 최대 3장 · 파일당 최대 10MB
                    </small>
                  </span>
                </>
              )}

              <span className="upload-button">
                파일 선택
              </span>
            </button>

            {previews.length > 0 && (
              <div className="report-image-preview-list">
                {previews.map((preview, index) => (
                  <div
                    key={`${preview.name}-${index}`}
                    className="report-image-preview"
                  >
                    <img
                      src={preview.url}
                      alt={`첨부 사진 ${index + 1}`}
                    />

                    <button
                      type="button"
                      onClick={() =>
                        removeImage(index)
                      }
                    >
                      삭제
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {!isAuthenticated && (
            <div className="field-group">
              <label htmlFor="report-contact">
                연락처 <em>*</em>
              </label>

              <p>
                비회원 제보는 추가 확인을 위해 연락 가능한 번호가 필요합니다.
              </p>

              <input
                id="report-contact"
                name="contact"
                type="tel"
                inputMode="tel"
                maxLength="30"
                required
                value={contact}
                onChange={(event) =>
                  setContact(event.target.value)
                }
                placeholder="010-0000-0000"
              />
            </div>
          )}

          {isAuthenticated && (
            <div className="field-group">
              <label>
                연락처
              </label>

              <p>
                로그인 회원은 회원 정보로 제보자가 식별되므로 별도의 연락처 입력이 필요하지 않습니다.
              </p>
            </div>
          )}

          <label className="consent-card">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(event) =>
                setAgreed(event.target.checked)
              }
            />

            <span>
              <strong>
                개인정보 수집 및 이용에 동의합니다.{" "}
                <em>*</em>
              </strong>

              <small>
                제보 처리 목적으로만 사용되며, 처리 완료 후 안전하게 관리됩니다.
              </small>
            </span>
          </label>

          {error && (
            <p
              className="auth-error"
              role="alert"
            >
              {error}
            </p>
          )}

          <button
            className="submit"
            type="submit"
            disabled={!agreed || submitting}
          >
            {submitting
              ? "제보를 접수하고 있습니다..."
              : "안전하게 제보 접수하기"}

            {!submitting && <span>→</span>}
          </button>

          <p className="form-security">
            🔒 입력한 내용은 암호화된 연결을 통해 안전하게 전달됩니다.
          </p>
        </form>
      </div>
    </main>
  );
}

export default Report;