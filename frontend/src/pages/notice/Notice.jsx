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
    q: "다같이 돌봄은 어떤 서비스인가요?",
    a: "돌봄이 필요한 이웃을 제보하고, 지역 주민이 안부 확인 활동에 참여할 수 있도록 연결하는 지역사회 돌봄 서비스입니다. 활동 결과는 기관이 확인하고 필요한 경우 후속 조치에 활용합니다.",
  },
  {
    q: "비회원도 돌봄이 필요한 이웃을 제보할 수 있나요?",
    a: "네, 비회원도 제보할 수 있습니다. 비회원 제보의 경우 제보 확인에 필요한 연락처와 함께 상황 및 위치 정보를 입력할 수 있습니다.",
  },
  {
    q: "제보하면 바로 돌봄 대상자로 등록되나요?",
    a: "아니요. 접수된 제보는 기관 담당자가 내용을 확인한 뒤 처리합니다. 대상자 확인과 필요한 절차가 완료된 경우 돌봄 대상자와 연결될 수 있습니다.",
  },
  {
    q: "안부 확인 봉사활동은 어떻게 신청하나요?",
    a: "자원봉사 메뉴에서 모집 중인 활동을 확인하고 원하는 활동을 신청할 수 있습니다. 신청 후에는 기관의 승인을 기다리며, 승인된 참여자는 내 활동에서 진행 상태를 확인할 수 있습니다.",
  },
  {
    q: "활동을 신청하면 바로 시작할 수 있나요?",
    a: "아니요. 활동 신청이 승인되고 필요한 참여 인원이 모두 확정되어 활동이 준비 상태가 된 뒤 시작할 수 있습니다.",
  },
  {
    q: "2명이 함께 참여하는 활동은 기록도 각각 작성하나요?",
    a: "아니요. 같은 활동에 참여한 봉사자는 하나의 공동 활동기록을 함께 사용합니다. 한 참여자가 활동을 시작하면 다른 참여자도 같은 기록에 이어서 작성할 수 있습니다.",
  },
  {
    q: "안부 확인 활동에서는 어떤 내용을 기록하나요?",
    a: "대상자를 만난 경우 식사 여부, 건강 상태, 추가 지원 필요 여부를 체크리스트로 확인하고 활동 완료 시각과 특이사항을 기록합니다. 활동 확인을 위한 대상자 서명도 함께 등록합니다.",
  },
  {
    q: "대상자를 만나지 못한 경우에는 어떻게 하나요?",
    a: "방문했지만 대상자를 만나지 못했다면 '미만남'으로 기록할 수 있습니다. 이 경우 체크리스트와 대상자 서명은 작성하지 않으며, 만나지 못한 상황이나 재확인이 필요한 내용을 특이사항에 기록합니다.",
  },
  {
    q: "제출한 활동기록을 다시 수정할 수 있나요?",
    a: "최종 제출된 활동기록은 바로 수정할 수 없으며 조회만 가능합니다. 기관에서 보완을 요청한 경우에는 내용을 다시 수정한 뒤 재제출할 수 있습니다.",
  },
  {
    q: "봉사활동 마일리지는 언제 지급되나요?",
    a: "대상자를 만난 활동기록이 제출되고 기관의 검토와 승인이 완료된 경우 마일리지 지급 대상이 될 수 있습니다. 실제 지급 내역은 마일리지 거래내역을 통해 확인할 수 있습니다.",
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
