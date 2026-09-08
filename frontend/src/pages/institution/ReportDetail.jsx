import { useEffect, useState } from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

const STATUS_LABELS = {
  SUBMITTED: "접수",
  REVIEWING: "검토 중",
  NEED_MORE_INFO: "추가 정보 필요",
  ACCEPTED: "접수 승인",
  REJECTED: "반려",
  CLOSED: "종결",
};

const GENDER_LABELS = {
  MALE: "남성",
  FEMALE: "여성",
  NONE: "미지정",
};

const RECIPIENT_STATUS_LABELS = {
  ACTIVE: "관리 중",
  INACTIVE: "관리 종료",
};

const CONSENT_STATUS_LABELS = {
  PENDING: "동의 대기",
  AGREED: "동의 완료",
  WITHDRAWN: "동의 철회",
};

const NEXT_STATUS_OPTIONS = {
  SUBMITTED: [
    {
      value: "REVIEWING",
      label: "검토 시작",
    },
  ],
  REVIEWING: [
    {
      value: "NEED_MORE_INFO",
      label: "추가 정보 요청",
    },
    {
      value: "ACCEPTED",
      label: "접수 승인",
    },
    {
      value: "REJECTED",
      label: "반려",
    },
  ],
  NEED_MORE_INFO: [
    {
      value: "REVIEWING",
      label: "다시 검토",
    },
    {
      value: "ACCEPTED",
      label: "접수 승인",
    },
    {
      value: "REJECTED",
      label: "반려",
    },
  ],
  ACCEPTED: [
    {
      value: "CLOSED",
      label: "종결",
    },
  ],
  REJECTED: [
    {
      value: "CLOSED",
      label: "종결",
    },
  ],
  CLOSED: [],
};

const INITIAL_RECIPIENT_FORM = {
  name: "",
  gender: "FEMALE",
  birthYear: "",
  phone: "",
  address: "",
  detailAddress: "",
  consentStatus: "PENDING",
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

function formatFileSize(value) {
  const size = Number(value);

  if (!Number.isFinite(size)) {
    return "";
  }

  if (size < 1024) {
    return `${size} B`;
  }

  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }

  return `${(
    size /
    (1024 * 1024)
  ).toFixed(1)} MB`;
}

function getErrorMessage(error) {
  return (
    error?.response?.data?.message ||
    error?.message ||
    "요청을 처리하지 못했습니다."
  );
}

function normalizeAddress(value) {
  return (value ?? "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function isSameAddress(
  firstAddress,
  secondAddress,
) {
  const first =
    normalizeAddress(firstAddress);

  const second =
    normalizeAddress(secondAddress);

  return (
    first !== "" &&
    second !== "" &&
    first === second
  );
}

function ReportDetail() {
  const navigate = useNavigate();
  const { reportId } = useParams();

  const [changingStatus, setChangingStatus] =
    useState("");

  const [modalType, setModalType] =
    useState("");

  const [recipients, setRecipients] =
    useState([]);

  const [
    selectedRecipientId,
    setSelectedRecipientId,
  ] = useState("");

  const [
    recipientKeyword,
    setRecipientKeyword,
  ] = useState("");

  const [
    recipientLoading,
    setRecipientLoading,
  ] = useState(false);

  const [
    recipientSaving,
    setRecipientSaving,
  ] = useState(false);

  const [aiLoading, setAiLoading] =
    useState(false);

  const [
    recipientForm,
    setRecipientForm,
  ] = useState(
    INITIAL_RECIPIENT_FORM,
  );

  const {
    data: report,
    loading,
    error,
    reload,
  } = useInstitutionData(
    () =>
      institutionApi.getReport(reportId),
    [reportId],
  );

  useEffect(() => {
    setModalType("");
    setRecipients([]);
    setSelectedRecipientId("");
    setRecipientKeyword("");
    setRecipientForm(
      INITIAL_RECIPIENT_FORM,
    );
  }, [reportId]);

  const sortRecipientsByAddress = (
    recipientList,
  ) => {
    return [...recipientList].sort(
      (first, second) => {
        const firstSameAddress =
          isSameAddress(
            first.address,
            report?.address,
          );

        const secondSameAddress =
          isSameAddress(
            second.address,
            report?.address,
          );

        if (
          firstSameAddress &&
          !secondSameAddress
        ) {
          return -1;
        }

        if (
          !firstSameAddress &&
          secondSameAddress
        ) {
          return 1;
        }

        return 0;
      },
    );
  };

  const handleStatusChange = async (
    nextStatus,
  ) => {
    const nextStatusLabel =
      STATUS_LABELS[nextStatus] ??
      nextStatus;

    const confirmed = window.confirm(
      `제보 상태를 '${nextStatusLabel}' 상태로 변경하시겠습니까?`,
    );

    if (!confirmed) {
      return;
    }

    try {
      setChangingStatus(nextStatus);

      await institutionApi
        .updateReportStatus(
          reportId,
          nextStatus,
        );

      window.alert(
        `제보 상태를 '${nextStatusLabel}' 상태로 변경했습니다.`,
      );

      await reload();
    } catch (statusError) {
      if (
        statusError?.response?.status ===
        409
      ) {
        window.alert(
          "현재 상태에서는 선택한 상태로 변경할 수 없습니다.",
        );

        await reload();
        return;
      }

      window.alert(
        getErrorMessage(statusError),
      );
    } finally {
      setChangingStatus("");
    }
  };

  const loadRecipients = async (
    keyword = "",
  ) => {
    try {
      setRecipientLoading(true);
      setSelectedRecipientId("");

      const response =
        await institutionApi
          .getCareRecipients({
            page: 0,
            size: 100,
            status: "ACTIVE",
            keyword:
              keyword.trim() ||
              undefined,
          });

      const recipientList =
        Array.isArray(response?.content)
          ? response.content
          : [];

      setRecipients(
        sortRecipientsByAddress(
          recipientList,
        ),
      );
    } catch (recipientError) {
      window.alert(
        getErrorMessage(recipientError),
      );
    } finally {
      setRecipientLoading(false);
    }
  };

  const openExistingRecipientModal =
    async () => {
      setRecipientKeyword("");
      setSelectedRecipientId("");
      setModalType("existing");

      await loadRecipients("");
    };

  const handleRecipientSearch =
    async (event) => {
      event.preventDefault();

      await loadRecipients(
        recipientKeyword,
      );
    };

  const resetRecipientSearch =
    async () => {
      setRecipientKeyword("");

      await loadRecipients("");
    };

  const handleLinkExistingRecipient =
    async () => {
      if (!selectedRecipientId) {
        window.alert(
          "연결할 돌봄 대상자를 선택해 주세요.",
        );
        return;
      }

      const selectedRecipient =
        recipients.find(
          (item) =>
            String(item.recipientId) ===
            String(selectedRecipientId),
        );

      const confirmed = window.confirm(
        `${selectedRecipient?.name ?? "선택한 대상자"}님을 이 제보에 연결하시겠습니까?`,
      );

      if (!confirmed) {
        return;
      }

      try {
        setRecipientSaving(true);

        await institutionApi
          .linkReportCareRecipient(
            reportId,
            Number(selectedRecipientId),
          );

        window.alert(
          "기존 돌봄 대상자를 연결했습니다.",
        );

        setModalType("");
        setSelectedRecipientId("");
        setRecipientKeyword("");

        await reload();
      } catch (linkError) {
        if (
          linkError?.response?.status ===
          409
        ) {
          window.alert(
            "이미 돌봄 대상자가 연결된 제보입니다.",
          );

          setModalType("");
          await reload();
          return;
        }

        window.alert(
          getErrorMessage(linkError),
        );
      } finally {
        setRecipientSaving(false);
      }
    };

  const openNewRecipientModal = () => {
    setRecipientForm({
      ...INITIAL_RECIPIENT_FORM,
      address: report?.address ?? "",
    });

    setModalType("new");
  };

  const handleRecipientFormChange = (
    event,
  ) => {
    const { name, value } =
      event.target;

    setRecipientForm((current) => ({
      ...current,
      [name]: value,
    }));
  };

  const handleCreateRecipient =
    async (event) => {
      event.preventDefault();

      if (!recipientForm.name.trim()) {
        window.alert(
          "대상자 이름을 입력해 주세요.",
        );
        return;
      }

      if (!recipientForm.address.trim()) {
        window.alert(
          "대상자 주소를 입력해 주세요.",
        );
        return;
      }

      if (
        recipientForm.birthYear &&
        (Number(
          recipientForm.birthYear,
        ) < 1900 ||
          Number(
            recipientForm.birthYear,
          ) > 2100)
      ) {
        window.alert(
          "출생연도를 확인해 주세요.",
        );
        return;
      }

      const request = {
        name:
          recipientForm.name.trim(),

        gender:
          recipientForm.gender,

        birthYear:
          recipientForm.birthYear
            ? Number(
                recipientForm.birthYear,
              )
            : null,

        phone:
          recipientForm.phone.trim() ||
          null,

        address:
          recipientForm.address.trim(),

        detailAddress:
          recipientForm.detailAddress.trim() ||
          null,

        /*
         * 화면에서 좌표를 입력받지 않습니다.
         * 제보에 좌표가 있으면 재사용하고,
         * 없으면 null로 전송합니다.
         */
        latitude:
          report?.latitude !== null &&
          report?.latitude !== undefined
            ? Number(report.latitude)
            : null,

        longitude:
          report?.longitude !== null &&
          report?.longitude !== undefined
            ? Number(report.longitude)
            : null,

        consentStatus:
          recipientForm.consentStatus,
      };

      const confirmed = window.confirm(
        `${request.name}님을 신규 돌봄 대상자로 등록하고 제보에 연결하시겠습니까?`,
      );

      if (!confirmed) {
        return;
      }

      try {
        setRecipientSaving(true);

        await institutionApi
          .createAndLinkReportCareRecipient(
            reportId,
            request,
          );

        window.alert(
          "신규 돌봄 대상자를 등록하고 제보에 연결했습니다.",
        );

        setModalType("");
        setRecipientForm(
          INITIAL_RECIPIENT_FORM,
        );

        await reload();
      } catch (createError) {
        if (
          createError?.response?.status ===
          409
        ) {
          window.alert(
            "이미 돌봄 대상자가 연결된 제보입니다.",
          );

          setModalType("");
          await reload();
          return;
        }

        window.alert(
          getErrorMessage(createError),
        );
      } finally {
        setRecipientSaving(false);
      }
    };

  const handleCreateAiSummary =
    async () => {
      const confirmed = window.confirm(
        report?.aiSummary
          ? "AI 요약을 다시 생성하시겠습니까?"
          : "AI 요약을 생성하시겠습니까?",
      );

      if (!confirmed) {
        return;
      }

      try {
        setAiLoading(true);

        await institutionApi
          .createReportAiSummary(
            reportId,
          );

        window.alert(
          "AI 요약을 생성했습니다.",
        );

        await reload();
      } catch (aiError) {
        window.alert(
          getErrorMessage(aiError),
        );
      } finally {
        setAiLoading(false);
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

  if (!report) {
    return (
      <div className="institution-page">
        <div className="data-state error">
          <b>!</b>

          <span>
            제보 정보를 찾을 수 없습니다.
          </span>

          <button
            type="button"
            onClick={() =>
              navigate(
                "/institution/reports",
              )
            }
          >
            목록으로 돌아가기
          </button>
        </div>
      </div>
    );
  }

  const statusLabel =
    STATUS_LABELS[report.status] ??
    report.status;

  const nextStatusOptions =
    NEXT_STATUS_OPTIONS[
      report.status
    ] ?? [];

  const images =
    Array.isArray(report.images)
      ? report.images
      : [];

  const recipient =
    report.recipient;

  const aiSummary =
    report.aiSummary;

  return (
    <div className="institution-page">
      <button
        type="button"
        className="detail-back"
        onClick={() =>
          navigate(
            "/institution/reports",
          )
        }
      >
        ← 제보 목록으로
      </button>

      <div className="page-title-row report-detail-title">
        <div>
          <p>제보 관리</p>

          <h1>
            제보 #{report.reportId}
          </h1>

          <span>
            제보 내용을 확인하고 처리
            상태를 관리하세요.
          </span>
        </div>

        <div className="detail-badges">
          <i className="table-status report-current-status">
            {statusLabel}
          </i>
        </div>
      </div>

      <section className="panel report-detail-section">
        <div className="panel-title">
          <div>
            <h2>제보 기본정보</h2>
            <p>
              기관에 배정된 제보의 상세
              내용입니다.
            </p>
          </div>
        </div>

        <dl className="report-detail-info">
          <div>
            <dt>제보 번호</dt>
            <dd>#{report.reportId}</dd>
          </div>

          <div>
            <dt>현재 상태</dt>
            <dd>{statusLabel}</dd>
          </div>

          <div>
            <dt>접수일</dt>
            <dd>
              {formatDateTime(
                report.createdAt,
              )}
            </dd>
          </div>

          <div>
            <dt>수정일</dt>
            <dd>
              {formatDateTime(
                report.updatedAt,
              )}
            </dd>
          </div>

          <div className="report-detail-wide">
            <dt>주소</dt>
            <dd>
              {report.address || "-"}
            </dd>
          </div>

          <div className="report-detail-wide report-content-row">
            <dt>제보 내용</dt>
            <dd>
              {report.content || "-"}
            </dd>
          </div>
        </dl>
      </section>

      <section className="panel report-detail-section">
        <div className="panel-title">
          <div>
            <h2>처리 상태 변경</h2>
            <p>
              현재 상태에서 변경 가능한
              항목만 표시됩니다.
            </p>
          </div>
        </div>

        <div className="report-status-actions">
          {nextStatusOptions.length > 0 ? (
            nextStatusOptions.map(
              (option) => (
                <button
                  type="button"
                  key={option.value}
                  disabled={
                    changingStatus !== ""
                  }
                  className={
                    option.value ===
                      "REJECTED" ||
                    option.value ===
                      "CLOSED"
                      ? "secondary"
                      : ""
                  }
                  onClick={() =>
                    handleStatusChange(
                      option.value,
                    )
                  }
                >
                  {changingStatus ===
                  option.value
                    ? "처리 중..."
                    : option.label}
                </button>
              ),
            )
          ) : (
            <p>
              종결된 제보는 상태를 더 이상
              변경할 수 없습니다.
            </p>
          )}
        </div>
      </section>

      <section className="panel report-detail-section">
        <div className="panel-title">
          <div>
            <h2>제보 이미지</h2>
            <p>
              제보자가 첨부한 이미지를
              확인하세요.
            </p>
          </div>
        </div>

        {images.length > 0 ? (
          <div className="report-image-grid">
            {images.map((image) => (
              <a
                key={image.imageId}
                href={image.imageUrl}
                target="_blank"
                rel="noreferrer"
                className="report-image-card"
              >
                <img
                  src={image.imageUrl}
                  alt={
                    image.originalFilename ||
                    "제보 이미지"
                  }
                />

                <div>
                  <strong>
                    {image.originalFilename ||
                      "첨부 이미지"}
                  </strong>

                  <span>
                    {formatFileSize(
                      image.fileSize,
                    )}
                  </span>
                </div>
              </a>
            ))}
          </div>
        ) : (
          <div className="report-detail-empty">
            첨부된 이미지가 없습니다.
          </div>
        )}
      </section>

      <section className="panel report-detail-section">
        <div className="panel-title">
          <div>
            <h2>연결된 돌봄 대상자</h2>
            <p>
              제보와 연결된 대상자를
              확인하세요.
            </p>
          </div>
        </div>

        {recipient ? (
          <div className="report-recipient-card">
            <div>
              <span>이름</span>
              <strong>
                {recipient.name}
              </strong>
            </div>

            <div>
              <span>성별</span>
              <strong>
                {GENDER_LABELS[
                  recipient.gender
                ] ??
                  recipient.gender ??
                  "-"}
              </strong>
            </div>

            <div>
              <span>출생연도</span>
              <strong>
                {recipient.birthYear
                  ? `${recipient.birthYear}년`
                  : "-"}
              </strong>
            </div>

            <div>
              <span>관리 상태</span>
              <strong>
                {RECIPIENT_STATUS_LABELS[
                  recipient.status
                ] ??
                  recipient.status ??
                  "-"}
              </strong>
            </div>

            <div>
              <span>동의 상태</span>
              <strong>
                {CONSENT_STATUS_LABELS[
                  recipient.consentStatus
                ] ??
                  recipient.consentStatus ??
                  "-"}
              </strong>
            </div>
          </div>
        ) : (
          <div className="report-recipient-empty">
            <p>
              아직 연결된 돌봄 대상자가
              없습니다.
            </p>

            <div>
              <button
                type="button"
                onClick={
                  openExistingRecipientModal
                }
              >
                기존 대상자 연결
              </button>

              <button
                type="button"
                className="primary"
                onClick={
                  openNewRecipientModal
                }
              >
                신규 대상자로 등록
              </button>
            </div>
          </div>
        )}
      </section>

      <section className="panel report-detail-section">
        <div className="panel-title">
          <div>
            <h2>AI 요약</h2>
            <p>
              AI 분석 결과는 참고 정보로만
              사용하세요.
            </p>
          </div>

          <button
            type="button"
            className="report-ai-button"
            disabled={aiLoading}
            onClick={
              handleCreateAiSummary
            }
          >
            {aiLoading
              ? "생성 중..."
              : aiSummary
                ? "AI 요약 다시 생성"
                : "AI 요약 생성"}
          </button>
        </div>

        {aiSummary ? (
          <div className="report-ai-summary">
            <p>{aiSummary.summary}</p>

            <div>
              <span>
                모델:{" "}
                {aiSummary.model || "-"}
              </span>

              <span>
                생성일:{" "}
                {formatDateTime(
                  aiSummary.createdAt,
                )}
              </span>
            </div>
          </div>
        ) : (
          <div className="report-detail-empty">
            생성된 AI 요약이 없습니다.
          </div>
        )}
      </section>

      {modalType === "existing" && (
        <div
          className="report-modal-backdrop"
          onMouseDown={() => {
            if (!recipientSaving) {
              setModalType("");
            }
          }}
        >
          <section
            className="report-modal"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <div className="report-modal-header">
              <div>
                <h2>기존 대상자 연결</h2>

                <p>
                  이름, 전화번호 또는 주소로
                  검색하고 대상자를 선택하세요.
                </p>
              </div>

              <button
                type="button"
                disabled={recipientSaving}
                onClick={() =>
                  setModalType("")
                }
              >
                ×
              </button>
            </div>

            <div className="report-modal-body">
              <form
                className="report-recipient-search"
                onSubmit={
                  handleRecipientSearch
                }
              >
                <input
                  type="search"
                  value={
                    recipientKeyword
                  }
                  placeholder="이름, 전화번호 또는 주소 검색"
                  aria-label="돌봄 대상자 검색"
                  onChange={(event) =>
                    setRecipientKeyword(
                      event.target.value,
                    )
                  }
                />

                <button
                  type="submit"
                  disabled={recipientLoading}
                >
                  검색
                </button>

                <button
                  type="button"
                  className="secondary"
                  disabled={recipientLoading}
                  onClick={
                    resetRecipientSearch
                  }
                >
                  초기화
                </button>
              </form>

              {recipientLoading ? (
                <p className="report-modal-empty">
                  대상자 목록을 불러오는
                  중입니다.
                </p>
              ) : recipients.length ===
                0 ? (
                <p className="report-modal-empty">
                  검색된 관리 중 대상자가
                  없습니다.
                </p>
              ) : (
                <div className="report-recipient-list">
                  {recipients.map(
                    (item) => {
                      const sameAddress =
                        isSameAddress(
                          item.address,
                          report.address,
                        );

                      return (
                        <label
                          key={
                            item.recipientId
                          }
                        >
                          <input
                            type="radio"
                            name="recipient"
                            value={
                              item.recipientId
                            }
                            checked={
                              String(
                                selectedRecipientId,
                              ) ===
                              String(
                                item.recipientId,
                              )
                            }
                            onChange={(
                              event,
                            ) =>
                              setSelectedRecipientId(
                                event
                                  .target
                                  .value,
                              )
                            }
                          />

                          <span>
                            <strong>
                              {item.name}
                            </strong>

                            <small>
                              {GENDER_LABELS[
                                item.gender
                              ] ??
                                item.gender ??
                                "-"}
                              {" · "}

                              {item.birthYear
                                ? `${item.birthYear}년생`
                                : "출생연도 없음"}
                              {" · "}

                              {item.address ||
                                "주소 없음"}
                            </small>

                            {sameAddress && (
                              <em className="same-address-label">
                                제보 주소와 동일
                              </em>
                            )}
                          </span>
                        </label>
                      );
                    },
                  )}
                </div>
              )}
            </div>

            <div className="report-modal-actions">
              <button
                type="button"
                className="secondary"
                disabled={recipientSaving}
                onClick={() =>
                  setModalType("")
                }
              >
                취소
              </button>

              <button
                type="button"
                disabled={
                  !selectedRecipientId ||
                  recipientSaving
                }
                onClick={
                  handleLinkExistingRecipient
                }
              >
                {recipientSaving
                  ? "연결 중..."
                  : "선택 대상자 연결"}
              </button>
            </div>
          </section>
        </div>
      )}

      {modalType === "new" && (
        <div
          className="report-modal-backdrop"
          onMouseDown={() => {
            if (!recipientSaving) {
              setModalType("");
            }
          }}
        >
          <section
            className="report-modal large"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <div className="report-modal-header">
              <div>
                <h2>
                  신규 돌봄 대상자 등록
                </h2>

                <p>
                  등록과 동시에 이 제보에
                  연결됩니다.
                </p>
              </div>

              <button
                type="button"
                disabled={recipientSaving}
                onClick={() =>
                  setModalType("")
                }
              >
                ×
              </button>
            </div>

            <form
              onSubmit={
                handleCreateRecipient
              }
            >
              <div className="report-recipient-form">
                <label>
                  <span>이름 *</span>

                  <input
                    name="name"
                    value={
                      recipientForm.name
                    }
                    maxLength={100}
                    required
                    onChange={
                      handleRecipientFormChange
                    }
                  />
                </label>

                <label>
                  <span>성별 *</span>

                  <select
                    name="gender"
                    value={
                      recipientForm.gender
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  >
                    <option value="FEMALE">
                      여성
                    </option>

                    <option value="MALE">
                      남성
                    </option>

                    <option value="NONE">
                      미지정
                    </option>
                  </select>
                </label>

                <label>
                  <span>출생연도</span>

                  <input
                    type="number"
                    name="birthYear"
                    min="1900"
                    max="2100"
                    value={
                      recipientForm.birthYear
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  />
                </label>

                <label>
                  <span>전화번호</span>

                  <input
                    name="phone"
                    maxLength={30}
                    placeholder="010-0000-0000"
                    value={
                      recipientForm.phone
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  />
                </label>

                <label className="wide">
                  <span>기본 주소 *</span>

                  <input
                    name="address"
                    maxLength={255}
                    required
                    value={
                      recipientForm.address
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  />
                </label>

                <label className="wide">
                  <span>
                    상세 주소 (선택)
                  </span>

                  <input
                    name="detailAddress"
                    maxLength={255}
                    placeholder="동, 호수 등"
                    value={
                      recipientForm
                        .detailAddress
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  />
                </label>

                <label className="wide">
                  <span>
                    초기 동의 상태 *
                  </span>

                  <select
                    name="consentStatus"
                    value={
                      recipientForm
                        .consentStatus
                    }
                    onChange={
                      handleRecipientFormChange
                    }
                  >
                    <option value="PENDING">
                      동의 대기
                    </option>

                    <option value="AGREED">
                      동의 완료
                    </option>
                  </select>
                </label>
              </div>

              <div className="report-modal-actions">
                <button
                  type="button"
                  className="secondary"
                  disabled={recipientSaving}
                  onClick={() =>
                    setModalType("")
                  }
                >
                  취소
                </button>

                <button
                  type="submit"
                  disabled={recipientSaving}
                >
                  {recipientSaving
                    ? "등록 중..."
                    : "등록하고 연결"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </div>
  );
}

export default ReportDetail;