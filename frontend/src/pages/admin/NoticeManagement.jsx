import { useState } from "react";

import { adminApi } from "../../api/adminApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

const NOTICE_PAGE_SIZE = 20;

const NOTICE_TABS = [
  { key: "ALL", label: "전체" },
  { key: "DRAFT", label: "초안" },
  { key: "PUBLISHED", label: "게시" },
  { key: "HIDDEN", label: "숨김" },
  { key: "DELETED", label: "삭제됨" },
];

const NOTICE_STATUS_LABELS = {
  DRAFT: "초안",
  PUBLISHED: "게시",
  HIDDEN: "숨김",
};

// 선택한 탭을 관리자 공지 목록 API의 조회 조건으로 변환
function createNoticeParams(activeTab, page) {
  const params = {
    page,
    size: NOTICE_PAGE_SIZE,
  };

  if (activeTab === "DELETED") {
    return {
      ...params,
      deleted: true,
    };
  }

  if (activeTab === "ALL") {
    return {
      ...params,
      deleted: false,
    };
  }

  return {
    ...params,
    status: activeTab,
    deleted: false,
  };
}

// 공지 날짜를 화면 표시용 형식으로 변환
function formatNoticeDate(value) {
  if (!value) {
    return "-";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(value));
}

// 현재 페이지를 기준으로 최대 5개의 페이지 번호 생성
function createPageNumbers(totalPages, currentPage) {
  if (totalPages <= 0) {
    return [];
  }

  const maxVisiblePages = 5;

  let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));

  let endPage = Math.min(totalPages, startPage + maxVisiblePages);

  startPage = Math.max(0, endPage - maxVisiblePages);

  return Array.from(
    { length: endPage - startPage },
    (_, index) => startPage + index,
  );
}

function NoticeManagement() {
  const [activeTab, setActiveTab] = useState("ALL");
  const [page, setPage] = useState(0);

  const [modalMode, setModalMode] = useState(null);
  const [selectedNotice, setSelectedNotice] = useState(null);

  const [form, setForm] = useState({
    title: "",
    content: "",
  });

  const [formErrors, setFormErrors] = useState({
    title: "",
    content: "",
  });

  const [serverError, setServerError] = useState("");
  const [listActionError, setListActionError] = useState("");
  const [modalActionError, setModalActionError] = useState("");
  const [saving, setSaving] = useState(false);

  const { data, loading, error, reload } = useInstitutionData(async () => {
    const response = await adminApi.getAdminNotices(
      createNoticeParams(activeTab, page),
    );

    return {
      notices: response?.content ?? [],
      page: response?.page ?? page,
      totalElements: response?.totalElements ?? 0,
      totalPages: response?.totalPages ?? 0,
    };
  }, [activeTab, page]);

  const notices = data?.notices ?? [];
  const currentPage = data?.page ?? page;
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  const pageNumbers = createPageNumbers(totalPages, currentPage);

  // 공지 상태 탭 변경
  const handleTabChange = (tab) => {
    setActiveTab(tab);
    setPage(0);
    setListActionError("");
  };

  // 공지 페이지 변경
  const handlePageChange = (nextPage) => {
    setPage(nextPage);
    setListActionError("");
  };

  // 공지 등록 모달 열기
  const openCreateModal = () => {
    setForm({
      title: "",
      content: "",
    });

    setFormErrors({
      title: "",
      content: "",
    });

    setServerError("");
    setModalMode("CREATE");
  };

  // 공지 수정 모달 열기
  const openEditModal = (notice) => {
    setSelectedNotice(notice);

    setForm({
      title: notice.title,
      content: notice.content,
    });

    setFormErrors({
      title: "",
      content: "",
    });

    setServerError("");
    setModalActionError("");
    setModalMode("EDIT");
  };

  // 공지 조회 모달 열기
  const openViewModal = (notice) => {
    setSelectedNotice(notice);

    setForm({
      title: notice.title,
      content: notice.content,
    });

    setFormErrors({
      title: "",
      content: "",
    });

    setServerError("");
    setModalActionError("");
    setModalMode("VIEW");
  };

  // 공지 모달 닫기
  const closeModal = () => {
    if (saving) {
      return;
    }

    setModalMode(null);
    setServerError("");
    setModalActionError("");
  };

  // 공지 등록·수정 입력값 변경
  const handleFormChange = (event) => {
    const { name, value } = event.target;

    setForm((current) => ({
      ...current,
      [name]: value,
    }));

    setFormErrors((current) => ({
      ...current,
      [name]: "",
    }));
  };

  // 공지 제목 및 내용 입력값 검증
  const validateForm = () => {
    const errors = {
      title: "",
      content: "",
    };

    if (!form.title.trim()) {
      errors.title = "제목을 입력해주세요.";
    } else if (form.title.length > 255) {
      errors.title = "제목은 255자 이하여야 합니다.";
    }

    if (!form.content.trim()) {
      errors.content = "내용을 입력해주세요.";
    }

    setFormErrors(errors);

    return !errors.title && !errors.content;
  };

  // 관리자 공지 수정
  const handleUpdateNotice = async () => {
    if (!selectedNotice || !validateForm()) {
      return;
    }

    setSaving(true);
    setServerError("");

    try {
      await adminApi.updateAdminNotice(selectedNotice.id, {
        title: form.title,
        content: form.content,
      });

      setListActionError("");

      window.alert("수정되었습니다.");

      setModalMode(null);
      setSelectedNotice(null);

      await reload();
    } catch (reason) {
      setServerError(
        reason?.response?.data?.message ?? "공지 수정 중 오류가 발생했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  // 관리자 공지 게시 및 숨김 상태 변경
  const handleStatusChange = async (
    notice,
    nextStatus,
    closeAfterSuccess = false,
  ) => {
    const message =
      nextStatus === "PUBLISHED"
        ? "이 공지를 게시하시겠습니까?"
        : "이 공지를 숨김 처리하시겠습니까?";

    if (!window.confirm(message)) {
      return;
    }

    if (closeAfterSuccess) {
      setModalActionError("");
    } else {
      setListActionError("");
    }

    try {
      await adminApi.updateAdminNotice(notice.id, {
        status: nextStatus,
      });

      setListActionError("");

      if (closeAfterSuccess) {
        setModalMode(null);
        setSelectedNotice(null);
      }

      const leavesCurrentTab = activeTab === notice.status;

      if (leavesCurrentTab && notices.length === 1 && page > 0) {
        setPage(page - 1);
      } else {
        await reload();
      }
    } catch (reason) {
      const message =
        reason?.response?.data?.message ?? "공지 상태를 변경하지 못했습니다.";

      if (closeAfterSuccess) {
        setModalActionError(message);
      } else {
        setListActionError(message);
      }
    }
  };

  // 관리자 공지 Soft Delete
  const handleDeleteNotice = async (notice, closeAfterSuccess = false) => {
    const confirmed = window.confirm(
      "삭제한 공지는 현재 복구할 수 없습니다. 삭제하시겠습니까?",
    );

    if (!confirmed) {
      return;
    }

    if (closeAfterSuccess) {
      setModalActionError("");
    } else {
      setListActionError("");
    }

    try {
      await adminApi.deleteAdminNotice(notice.id);

      setListActionError("");

      if (closeAfterSuccess) {
        setModalMode(null);
        setSelectedNotice(null);
      }

      if (notices.length === 1 && page > 0) {
        setPage(page - 1);
      } else {
        await reload();
      }
    } catch (reason) {
      const message =
        reason?.response?.data?.message ?? "공지를 삭제하지 못했습니다.";

      if (closeAfterSuccess) {
        setModalActionError(message);
      } else {
        setListActionError(message);
      }
    }
  };

  // 관리자 공지 등록
  const handleCreateNotice = async () => {
    if (!validateForm()) {
      return;
    }

    setSaving(true);
    setServerError("");

    try {
      await adminApi.createAdminNotice({
        title: form.title,
        content: form.content,
      });

      setListActionError("");

      window.alert("등록되었습니다.");

      setModalMode(null);
      setForm({
        title: "",
        content: "",
      });

      const alreadyDraftFirstPage = activeTab === "DRAFT" && page === 0;

      setActiveTab("DRAFT");
      setPage(0);

      if (alreadyDraftFirstPage) {
        await reload();
      }
    } catch (reason) {
      setServerError(
        reason?.response?.data?.message ?? "공지 등록 중 오류가 발생했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  if (loading || error) {
    return (
      <div className="admin-page">
        <DataState loading={loading} error={error} onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="admin-page">
      <div className="admin-page-title">
        <div>
          <p>NOTICE MANAGEMENT</p>
          <h1>공지 관리</h1>
          <span>서비스에 노출되는 공지사항을 관리합니다.</span>
        </div>

        <button
          className="admin-primary"
          type="button"
          onClick={openCreateModal}
        >
          + 공지 등록
        </button>
      </div>

      <section className="admin-panel">
        <div className="admin-toolbar">
          <div className="admin-tabs">
            {NOTICE_TABS.map((tab) => (
              <button
                key={tab.key}
                type="button"
                className={activeTab === tab.key ? "active" : ""}
                onClick={() => handleTabChange(tab.key)}
              >
                {tab.label}
              </button>
            ))}
          </div>
        </div>

        {listActionError && (
          <div className="admin-notice-action-error">{listActionError}</div>
        )}

        <div className="admin-notice-table">
          <div className="admin-table-head admin-notice-head">
            <span>제목</span>
            <span>상태</span>
            <span>등록일</span>
            <span>수정일</span>
            <span>관리</span>
          </div>

          {notices.length === 0 ? (
            <div className="admin-notice-empty">등록된 공지가 없습니다.</div>
          ) : (
            notices.map((notice) => (
              <article className="admin-notice-row" key={notice.id}>
                <button
                  type="button"
                  className="admin-notice-title-button"
                  onClick={() => openViewModal(notice)}
                >
                  {notice.title}
                </button>

                <i
                  className={`admin-status notice-${notice.status.toLowerCase()}`}
                >
                  {NOTICE_STATUS_LABELS[notice.status] ?? notice.status}
                </i>

                <time>{formatNoticeDate(notice.createdAt)}</time>

                <time>{formatNoticeDate(notice.updatedAt)}</time>

                <span className="admin-notice-actions">
                  {activeTab !== "DELETED" && (
                    <>
                      <button
                        type="button"
                        className="row-menu"
                        onClick={() => openEditModal(notice)}
                      >
                        수정
                      </button>

                      {notice.status === "DRAFT" && (
                        <button
                          type="button"
                          className="row-menu"
                          onClick={() =>
                            handleStatusChange(notice, "PUBLISHED")
                          }
                        >
                          게시
                        </button>
                      )}

                      {notice.status === "PUBLISHED" && (
                        <button
                          type="button"
                          className="row-menu"
                          onClick={() => handleStatusChange(notice, "HIDDEN")}
                        >
                          숨김
                        </button>
                      )}

                      {notice.status === "HIDDEN" && (
                        <button
                          type="button"
                          className="row-menu"
                          onClick={() =>
                            handleStatusChange(notice, "PUBLISHED")
                          }
                        >
                          게시
                        </button>
                      )}
                      <button
                        type="button"
                        className="row-menu"
                        onClick={() => handleDeleteNotice(notice)}
                      >
                        삭제
                      </button>
                    </>
                  )}
                </span>
              </article>
            ))
          )}
        </div>

        <div className="admin-table-footer">
          <span>총 {totalElements}개의 공지</span>

          <div>
            <button
              type="button"
              disabled={currentPage === 0}
              onClick={() => handlePageChange(currentPage - 1)}
            >
              ‹
            </button>

            {pageNumbers.map((pageNumber) => (
              <button
                key={pageNumber}
                type="button"
                className={currentPage === pageNumber ? "active" : ""}
                onClick={() => handlePageChange(pageNumber)}
              >
                {pageNumber + 1}
              </button>
            ))}

            <button
              type="button"
              disabled={totalPages === 0 || currentPage >= totalPages - 1}
              onClick={() => handlePageChange(currentPage + 1)}
            >
              ›
            </button>
          </div>
        </div>
      </section>
      {modalMode && (
        <div className="admin-notice-modal-backdrop">
          <div
            className="admin-notice-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="notice-modal-title"
          >
            <div className="admin-notice-modal-header">
              <div>
                <p>NOTICE</p>
                <h2 id="notice-modal-title">
                  {modalMode === "VIEW"
                    ? "공지 조회"
                    : modalMode === "EDIT"
                      ? "공지 수정"
                      : "공지 등록"}
                </h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                onClick={closeModal}
                disabled={saving}
              >
                ×
              </button>
            </div>

            <div className="admin-notice-form">
              <label>
                <span>제목</span>

                <input
                  name="title"
                  value={form.title}
                  onChange={handleFormChange}
                  placeholder="공지 제목을 입력하세요."
                  disabled={saving || modalMode === "VIEW"}
                />

                <small className="admin-notice-count">
                  {form.title.length} / 255
                </small>

                {formErrors.title && (
                  <small className="admin-notice-form-error">
                    {formErrors.title}
                  </small>
                )}
              </label>

              <label>
                <span>내용</span>

                <textarea
                  name="content"
                  value={form.content}
                  onChange={handleFormChange}
                  placeholder="공지 내용을 입력하세요."
                  disabled={saving || modalMode === "VIEW"}
                />

                {formErrors.content && (
                  <small className="admin-notice-form-error">
                    {formErrors.content}
                  </small>
                )}
              </label>

              {serverError && (
                <div className="admin-notice-server-error">{serverError}</div>
              )}
            </div>

            {modalActionError && (
              <div className="admin-notice-server-error">
                {modalActionError}
              </div>
            )}

            <div className="admin-notice-modal-actions">
              {modalMode === "VIEW" ? (
                <>
                  {!selectedNotice?.deleted && (
                    <>
                      <button
                        type="button"
                        className="admin-primary"
                        onClick={() => openEditModal(selectedNotice)}
                      >
                        수정
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          handleStatusChange(
                            selectedNotice,
                            selectedNotice.status === "PUBLISHED"
                              ? "HIDDEN"
                              : "PUBLISHED",
                            true,
                          )
                        }
                      >
                        {selectedNotice.status === "PUBLISHED"
                          ? "숨김"
                          : "게시"}
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteNotice(selectedNotice, true)}
                      >
                        삭제
                      </button>
                    </>
                  )}

                  <button type="button" onClick={closeModal}>
                    닫기
                  </button>
                </>
              ) : (
                <>
                  <button type="button" onClick={closeModal} disabled={saving}>
                    취소
                  </button>

                  <button
                    className="admin-primary"
                    type="button"
                    onClick={
                      modalMode === "EDIT"
                        ? handleUpdateNotice
                        : handleCreateNotice
                    }
                    disabled={saving}
                  >
                    {saving ? "저장 중..." : "저장"}
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default NoticeManagement;
