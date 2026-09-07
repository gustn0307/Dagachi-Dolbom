import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { userApi } from "../../api/userApi";
import PageHeader from "../../components/common/PageHeader";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

const NOTICE_PAGE_SIZE = 20;

// URL의 page 값을 화면 기준 페이지 번호로 변환
function getNoticePage(searchParams) {
  const page = Number(searchParams.get("page"));

  if (!Number.isInteger(page) || page < 1) {
    return 1;
  }

  return page;
}

// 공지 등록일을 화면 표시용 형식으로 변환
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

  let startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));

  let endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

  startPage = Math.max(1, endPage - maxVisiblePages + 1);

  return Array.from(
    { length: endPage - startPage + 1 },
    (_, index) => startPage + index,
  );
}

const faqItems = [
  {
    q: "안부 확인 활동은 어떻게 진행되나요?",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "제보 후 처리 과정이 궁금해요.",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "마일리지는 언제 지급되나요?",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "비회원도 제보할 수 있나요?",
    a: "네, 가능합니다. 다만 원활한 확인을 위해 최소한의 연락처 정보를 요청할 수 있습니다.",
  },
];

function Notice() {
  const navigate = useNavigate();
  const { noticeId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();

  const currentPage = getNoticePage(searchParams);
  const isDetail = Boolean(noticeId);

  const { data, loading, error, reload } = useInstitutionData(async () => {
    if (isDetail) {
      try {
        const notice = await userApi.getNotice(noticeId);

        return {
          notice,
          notFound: false,
        };
      } catch (reason) {
        if (reason?.response?.status === 404) {
          return {
            notice: null,
            notFound: true,
          };
        }

        throw reason;
      }
    }

    const response = await userApi.getNotices({
      page: currentPage - 1,
      size: NOTICE_PAGE_SIZE,
    });

    return {
      notices: response?.content ?? [],
      totalPages: response?.totalPages ?? 0,
    };
  }, [isDetail, noticeId, currentPage]);

  const notices = data?.notices ?? [];
  const notice = data?.notice ?? null;
  const totalPages = data?.totalPages ?? 0;

  const pageNumbers = createPageNumbers(totalPages, currentPage);

  // 공지 상세 조회 결과가 404(미존재·비공개) 상태인지 확인
  const isNotFound = isDetail && data?.notFound === true;

  // 공지 목록 페이지 변경
  const handlePageChange = (nextPage) => {
    setSearchParams({
      page: String(nextPage),
    });
  };

  // 공지 제목 클릭 시 상세 페이지 이동
  const handleNoticeClick = (noticeId) => {
    navigate(`/notice/${noticeId}?page=${currentPage}`);
  };

  // 공지 상세에서 기존 목록 페이지로 이동
  const handleBackToList = () => {
    navigate(`/notice?page=${currentPage}`);
  };

  if (isDetail) {
    return (
      <>
        <PageHeader
          eyebrow="알림마당"
          title="공지사항"
          text="다같이 돌봄의 소식을 안내합니다."
        />

        <section className="notice-detail">
          {loading ? (
            <DataState loading={true} error={null} onRetry={reload} />
          ) : isNotFound ? (
            <>
              <div className="notice-detail-error">
                <strong>해당 공지사항을 찾을 수 없습니다.</strong>
                <p>삭제되었거나 더 이상 공개되지 않은 공지입니다.</p>
              </div>

              <button type="button" onClick={handleBackToList}>
                목록으로
              </button>
            </>
          ) : error ? (
            <>
              <DataState loading={false} error={error} onRetry={reload} />

              <button type="button" onClick={handleBackToList}>
                목록으로
              </button>
            </>
          ) : (
            <>
              <div className="notice-detail-head">
                <h2>{notice?.title}</h2>
                <time>{formatNoticeDate(notice?.createdAt)}</time>
              </div>

              <div className="notice-detail-content">{notice?.content}</div>

              <div className="notice-detail-actions">
                <button type="button" onClick={handleBackToList}>
                  목록으로
                </button>
              </div>
            </>
          )}
        </section>
      </>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow="알림마당"
        title="공지사항 · 자주 묻는 질문"
        text="다같이 돌봄의 소식과 이용 방법을 안내합니다."
      />

      <section className="notice-board notice-list-section">
        <h2>공지사항</h2>

        {loading || error ? (
          <DataState loading={loading} error={error} onRetry={reload} />
        ) : notices.length === 0 ? (
          <div className="notice-list-empty">등록된 공지사항이 없습니다.</div>
        ) : (
          <div className="notice-list">
            <div className="notice-list-head">
              <span>제목</span>
              <span>등록일</span>
            </div>

            {notices.map((item) => (
              <div className="notice-list-row" key={item.id}>
                <button
                  type="button"
                  className="notice-list-title"
                  onClick={() => handleNoticeClick(item.id)}
                >
                  {item.title}
                </button>

                <time>{formatNoticeDate(item.createdAt)}</time>
              </div>
            ))}
          </div>
        )}

        {!loading && !error && totalPages > 1 && (
          <div className="notice-pagination">
            <button
              type="button"
              disabled={currentPage === 1}
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
                {pageNumber}
              </button>
            ))}

            <button
              type="button"
              disabled={currentPage >= totalPages}
              onClick={() => handlePageChange(currentPage + 1)}
            >
              ›
            </button>
          </div>
        )}
      </section>

      <section className="notice-board">
        <h2>자주 묻는 질문</h2>

        {faqItems.map((item) => (
          <details className="faq" key={item.q}>
            <summary>
              <b>Q.</b>

              <span>{item.q}</span>

              <i>⌄</i>
            </summary>

            <p>{item.a}</p>
          </details>
        ))}
      </section>
    </>
  );
}

export default Notice;
