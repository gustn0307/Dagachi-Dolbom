import {
  useCallback,
  useState,
} from "react";
import { useNavigate } from "react-router-dom";
import { institutionApi } from "../../api/institutionApi";
import CareRecipientForm from "../../components/institution/CareRecipientForm";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

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

// 한 페이지에 표시할 대상자 수
const PAGE_SIZE = 20;

/**
 * 최근 확인일을 한국 날짜 형식으로 변환한다.
 */
const formatLastCheckedAt = (value) => {
  if (!value) return "확인 기록 없음";

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "확인 기록 없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
};

function CareTargetManagement() {
  const navigate = useNavigate();

  /*
   * 검색창에 입력 중인 값이다.
   * 입력만으로 요청하지 않고 검색 버튼이나 Enter를 누르면 적용한다.
   */
  const [
    keywordInput,
    setKeywordInput,
  ] = useState("");

  // 실제 API 요청에 사용하는 검색어
  const [keyword, setKeyword] =
    useState("");

  // 관리 상태: 빈 문자열이면 전체
  const [status, setStatus] =
    useState("");

  // 동의 상태: 빈 문자열이면 전체
  const [
    consentStatus,
    setConsentStatus,
  ] = useState("");

  /*
   * 목록 정렬 조건
   *
   * createdAt,desc:
   * 최근 등록된 대상자부터 표시
   *
   * lastCheckedAt,asc:
   * 최근 확인일이 오래된 대상자부터 표시
   *
   * lastCheckedAt,desc:
   * 최근 확인일이 최신인 대상자부터 표시
   */
  const [sortOption, setSortOption] =
    useState("createdAt,desc");

  // 백엔드 페이지 번호는 0부터 시작한다.
  const [page, setPage] =
    useState(0);

  // CARE-03 등록 모달 표시 여부
  const [
    showCreateForm,
    setShowCreateForm,
  ] = useState(false);

  // 등록 요청 진행 상태
  const [submitting, setSubmitting] =
    useState(false);

  // 등록 요청 오류 메시지
  const [
    submitError,
    setSubmitError,
  ] = useState("");

  /**
   * 현재 검색·필터·정렬·페이지 조건으로
   * CARE-01 목록 API를 호출한다.
   */
  const listLoader = useCallback(
    () =>
      institutionApi.getCareRecipients({
        // 빈 검색 조건은 요청에서 제외한다.
        keyword: keyword || undefined,

        // 전체 선택 시 요청에서 제외한다.
        status: status || undefined,

        // 전체 선택 시 요청에서 제외한다.
        consentStatus:
          consentStatus || undefined,

        // 0부터 시작하는 페이지 번호
        page,

        // 한 페이지에 최대 20명
        size: PAGE_SIZE,

        // createdAt 또는 lastCheckedAt 정렬
        sort: sortOption,
      }),
    [
      keyword,
      status,
      consentStatus,
      page,
      sortOption,
    ],
  );

  const {
    data,
    loading,
    error,
    reload,
  } = useInstitutionData(
    listLoader,
    [listLoader],
  );

  // PageResponse의 현재 페이지 대상자 목록
  const recipients =
    data?.content ?? [];

  // 현재 검색 조건에 해당하는 전체 대상자 수
  const totalElements =
    data?.totalElements ?? 0;

  // 전체 페이지 수
  const totalPages =
    data?.totalPages ?? 0;

  // 첫 페이지 여부
  const isFirst =
    data?.first ?? page === 0;

  // 마지막 페이지 여부
  const isLast =
    data?.last ?? totalPages === 0;

  /**
   * 검색 버튼 또는 Enter를 눌렀을 때
   * 검색어를 실제 요청 조건에 적용한다.
   */
  const handleSearch = (event) => {
    event.preventDefault();

    // 새로운 검색은 첫 페이지부터 시작한다.
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  /**
   * 검색·필터·정렬 조건을 모두 초기화한다.
   */
  const handleResetFilters = () => {
    setKeywordInput("");
    setKeyword("");
    setStatus("");
    setConsentStatus("");
    setSortOption("createdAt,desc");
    setPage(0);
  };

  /**
   * CARE-03 신규 돌봄 대상자를 등록한다.
   */
  const handleCreate = async (request) => {
    setSubmitting(true);
    setSubmitError("");

    try {
      await institutionApi
        .createCareRecipient(request);

      // 등록 성공 시 모달을 닫는다.
      setShowCreateForm(false);

      /*
       * 등록된 대상자를 바로 확인할 수 있도록
       * 최근 등록순의 첫 페이지로 이동한다.
       */
      if (
        page === 0 &&
        sortOption === "createdAt,desc"
      ) {
        await reload();
      } else {
        setSortOption("createdAt,desc");
        setPage(0);
      }
    } catch (reason) {
      setSubmitError(
        reason?.response?.data?.message ??
          "대상자 등록에 실패했습니다.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  /*
   * 첫 조회 또는 조회 조건 변경 중에는
   * 공통 로딩·오류 화면을 표시한다.
   */
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

  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>돌봄 대상자</p>

          <h1>
            돌봄 대상자를 관리하세요
          </h1>

          <span>
            대상자의 상태와 최근 확인일을
            한눈에 확인합니다.
          </span>
        </div>

        <button
          type="button"
          className="orange-action"
          onClick={() => {
            setSubmitError("");
            setShowCreateForm(true);
          }}
        >
          ＋ 대상자 등록
        </button>
      </div>

      <section className="mini-metrics">
        <article>
          <span>조회 대상자</span>

          <strong>
            {totalElements}명
          </strong>
        </article>

        <article>
          <span>
            현재 페이지 관리 중
          </span>

          <strong className="green-text">
            {
              recipients.filter(
                (recipient) =>
                  recipient.status ===
                  "ACTIVE",
              ).length
            }
            명
          </strong>
        </article>

        <article>
          <span>
            현재 페이지 동의 대기
          </span>

          <strong className="orange-text">
            {
              recipients.filter(
                (recipient) =>
                  recipient.consentStatus ===
                  "PENDING",
              ).length
            }
            명
          </strong>
        </article>
      </section>

      <section className="panel table-panel">
        {/* 검색·필터·정렬 영역 */}
        <div className="filter-bar">
          <form
            className="care-list-search"
            onSubmit={handleSearch}
          >
            <div className="table-search wide">
              <span>⌕</span>

              <input
                value={keywordInput}
                placeholder="이름, 주소 또는 전화번호 검색"
                onChange={(event) =>
                  setKeywordInput(
                    event.target.value,
                  )
                }
              />
            </div>

            <button
              type="submit"
              className="filter-button"
            >
              검색
            </button>
          </form>

          {/* 관리 상태 필터 */}
          <select
            className="filter-button care-filter-select"
            value={status}
            aria-label="관리 상태"
            onChange={(event) => {
              setStatus(
                event.target.value,
              );

              // 필터 변경 시 첫 페이지로 이동한다.
              setPage(0);
            }}
          >
            <option value="">
              관리 상태 전체
            </option>

            <option value="ACTIVE">
              관리 중
            </option>

            <option value="INACTIVE">
              관리 종료
            </option>
          </select>

          {/* 동의 상태 필터 */}
          <select
            className="filter-button care-filter-select"
            value={consentStatus}
            aria-label="동의 상태"
            onChange={(event) => {
              setConsentStatus(
                event.target.value,
              );

              // 필터 변경 시 첫 페이지로 이동한다.
              setPage(0);
            }}
          >
            <option value="">
              동의 상태 전체
            </option>

            <option value="PENDING">
              동의 대기
            </option>

            <option value="AGREED">
              동의 완료
            </option>

            <option value="WITHDRAWN">
              동의 철회
            </option>
          </select>

          {/* 등록일·최근 확인일 정렬 */}
          <select
            className="filter-button care-filter-select care-sort-select"
            value={sortOption}
            aria-label="목록 정렬"
            onChange={(event) => {
              setSortOption(
                event.target.value,
              );

              // 정렬 변경 시 첫 페이지로 이동한다.
              setPage(0);
            }}
          >
            <option value="createdAt,desc">
              최근 등록순
            </option>

            <option value="lastCheckedAt,asc">
              최근 확인이 오래된 순
            </option>

            <option value="lastCheckedAt,desc">
              최근 확인이 최신인 순
            </option>
          </select>

          {/* 검색·필터·정렬 초기화 */}
          <button
            type="button"
            className="filter-button"
            onClick={handleResetFilters}
          >
            초기화
          </button>
        </div>

        {/* 조회 결과가 없는 경우 */}
        {recipients.length === 0 ? (
          <div className="data-state">
            {keyword ||
            status ||
            consentStatus
              ? "검색 조건에 맞는 돌봄 대상자가 없습니다."
              : "등록된 돌봄 대상자가 없습니다."}
          </div>
        ) : (
          /* 현재 페이지 대상자 목록 */
          <div className="people-grid">
            {recipients.map(
              (recipient, index) => (
                <article
                  key={
                    recipient.recipientId
                  }
                >
                  <div
                    className={
                      `person-avatar avatar-${index % 4}`
                    }
                  >
                    {recipient.name?.[0] ??
                      "?"}
                  </div>

                  <div className="person-info">
                    <h3>
                      {recipient.name}
                    </h3>

                    <p>
                      {GENDER_LABEL[
                        recipient.gender
                      ] ??
                        recipient.gender}

                      {" · "}

                      {recipient.birthYear
                        ? `${recipient.birthYear}년생`
                        : "출생연도 미등록"}
                    </p>

                    <p>
                      {recipient.address ||
                        "주소 미등록"}
                    </p>

                    <span
                      className={
                        `care-tag ${recipient.status}`
                      }
                    >
                      {STATUS_LABEL[
                        recipient.status
                      ] ??
                        recipient.status}
                    </span>

                    {" "}

                    <span
                      className={
                        `care-tag ${recipient.consentStatus}`
                      }
                    >
                      {CONSENT_STATUS_LABEL[
                        recipient.consentStatus
                      ] ??
                        recipient.consentStatus}
                    </span>
                  </div>

                  <div className="next-care">
                    <span>최근 확인일</span>

                    <strong>
                      {formatLastCheckedAt(
                        recipient.lastCheckedAt,
                      )}
                    </strong>
                  </div>

                  <button
                    type="button"
                    onClick={() =>
                      navigate(
                        `/institution/care-targets/${recipient.recipientId}`,
                      )
                    }
                  >
                    상세 보기
                  </button>
                </article>
              ),
            )}
          </div>
        )}

        {/* 페이지네이션 */}
        {totalPages > 0 && (
          <div className="table-footer care-pagination">
            <span>
              전체 {totalElements}명 ·{" "}
              {page + 1}/{totalPages} 페이지
            </span>

            <div>
              <button
                type="button"
                disabled={isFirst}
                onClick={() =>
                  setPage((current) =>
                    Math.max(
                      current - 1,
                      0,
                    ),
                  )
                }
              >
                이전
              </button>

              {Array.from(
                {
                  length: totalPages,
                },
                (_, index) => index,
              ).map((pageNumber) => (
                <button
                  type="button"
                  key={pageNumber}
                  className={
                    pageNumber === page
                      ? "active"
                      : ""
                  }
                  aria-current={
                    pageNumber === page
                      ? "page"
                      : undefined
                  }
                  onClick={() =>
                    setPage(pageNumber)
                  }
                >
                  {pageNumber + 1}
                </button>
              ))}

              <button
                type="button"
                disabled={isLast}
                onClick={() =>
                  setPage((current) =>
                    Math.min(
                      current + 1,
                      totalPages - 1,
                    ),
                  )
                }
              >
                다음
              </button>
            </div>
          </div>
        )}
      </section>

      {/* CARE-03 신규 대상자 등록 모달 */}
      {showCreateForm && (
        <div
          className="care-modal-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            // 모달 바깥을 누른 경우에만 닫는다.
            if (
              event.target ===
              event.currentTarget
            ) {
              setShowCreateForm(false);
            }
          }}
        >
          <section
            className="care-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="care-create-title"
          >
            <div className="care-modal-header">
              <div>
                <p>돌봄 대상자</p>

                <h2 id="care-create-title">
                  신규 대상자 등록
                </h2>
              </div>

              <button
                type="button"
                aria-label="닫기"
                disabled={submitting}
                onClick={() =>
                  setShowCreateForm(false)
                }
              >
                ×
              </button>
            </div>

            {submitError && (
              <div className="care-form-error">
                {submitError}
              </div>
            )}

            <CareRecipientForm
              mode="create"
              submitting={submitting}
              onSubmit={handleCreate}
              onCancel={() =>
                setShowCreateForm(false)
              }
            />
          </section>
        </div>
      )}
    </div>
  );
}

export default CareTargetManagement;