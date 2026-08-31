import { useEffect, useState } from "react";
import PageHeader from "../../components/common/PageHeader";
import { fetchActivities, applyForActivity } from "../../api/userApi";

const PAGE_SIZE = 10;
const NARROW_BREAKPOINT = 380;

const STATUS_LABEL = {
  RECRUITING: "모집중",
  READY: "모집완료",
};

function formatSchedule(isoString) {
  const date = new Date(isoString);
  return date.toLocaleString("ko-KR", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
}

// 현재 페이지 기준 앞뒤 siblingCount개만 보여주고 나머지는 "..."으로 축약
function getPageNumbers(currentPage, totalPages, siblingCount) {
  const pages = [];
  const start = Math.max(0, currentPage - siblingCount);
  const end = Math.min(totalPages - 1, currentPage + siblingCount);

  if (start > 0) pages.push(0, start > 1 ? "..." : 1);
  for (let i = start; i <= end; i++) pages.push(i);
  if (end < totalPages - 1) {
    pages.push(end < totalPages - 2 ? "..." : totalPages - 2, totalPages - 1);
  }

  return [...new Set(pages)];
}

function Volunteer() {
  const [activeTab, setActiveTab] = useState("direct");
  const [activities, setActivities] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isNarrow, setIsNarrow] = useState(
    typeof window !== "undefined"
      ? window.innerWidth <= NARROW_BREAKPOINT
      : false
  );

  const [selectedActivityId, setSelectedActivityId] = useState(null);
  const [isApplying, setIsApplying] = useState(false);
  const [applyError, setApplyError] = useState(null);
  const [toastMessage, setToastMessage] = useState(null);
  const [hoveredPageBtn, setHoveredPageBtn] = useState(null);

  useEffect(() => {
    if (activeTab !== "direct") return;

    let ignore = false;
    setIsLoading(true);
    setError(null);

    fetchActivities({ page: currentPage, size: PAGE_SIZE })
      .then((data) => {
        if (ignore) return;
        setActivities(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);

        // 서버가 반환한 page가 요청한 page와 다르면(마지막 페이지 초과 등) 보정
        if (data.page !== currentPage) {
          setCurrentPage(data.page);
        }
      })
      .catch(() => {
        if (!ignore) setError("활동 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setIsLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [activeTab, currentPage]);

  // 탭 전환 시 페이지/선택 초기화
  useEffect(() => {
    setCurrentPage(0);
    setSelectedActivityId(null);
  }, [activeTab]);

  // 목록이 바뀌면(새로고침 등) 더 이상 없는 항목이 선택돼있지 않도록 정리
  useEffect(() => {
    if (
      selectedActivityId &&
      !activities.some((a) => a.activityId === selectedActivityId)
    ) {
      setSelectedActivityId(null);
    }
  }, [activities, selectedActivityId]);

  useEffect(() => {
    if (!toastMessage) return;
    const timer = setTimeout(() => setToastMessage(null), 2500);
    return () => clearTimeout(timer);
  }, [toastMessage]);

  // 화면 폭 변화(회전 포함) 감지 → 좁은 화면에서는 페이지네이션을 축약 표시
  useEffect(() => {
    const mql = window.matchMedia(`(max-width: ${NARROW_BREAKPOINT}px)`);
    const handler = (e) => setIsNarrow(e.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, []);

  const selectedActivity =
    activities.find((a) => a.activityId === selectedActivityId) ?? null;

  const handleSelect = (activityId) => {
    setApplyError(null);
    setSelectedActivityId((prev) => (prev === activityId ? null : activityId));
  };

  const handlePageChange = (nextPage) => {
    if (nextPage === currentPage || isLoading) return;
    setSelectedActivityId(null);
    setApplyError(null);
    setCurrentPage(nextPage);
    document
      .querySelector(".visit-list")
      ?.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const handleApply = async () => {
    if (!selectedActivityId) return;

    setIsApplying(true);
    setApplyError(null);

    try {
      await applyForActivity(selectedActivityId);
      setToastMessage("신청이 완료되었습니다. 기관 승인을 기다려주세요.");
      setSelectedActivityId(null);

      // 신청 상태 반영: 현재 보고 있던 페이지 그대로 새로고침
      const data = await fetchActivities({ page: currentPage, size: PAGE_SIZE });
      setActivities(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (err) {
      const message =
        err?.response?.data?.message ?? "신청 중 오류가 발생했습니다.";
      setApplyError(message);
    } finally {
      setIsApplying(false);
    }
  };

  // ---- 페이지네이션 버튼 스타일 헬퍼 (인라인, 별도 파일/CSS 없음) ----
  const pageBtnStyle = (key, { active = false, disabled = false, wide = false } = {}) => ({
    minWidth: isNarrow ? 36 : 40,
    minHeight: isNarrow ? 36 : 40,
    padding: wide ? (isNarrow ? "0 10px" : "0 16px") : "0 10px",
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 2,
    border: `1px solid ${active ? "#f4771c" : "#ece5dd"}`,
    borderRadius: 10,
    background: active ? "#f4771c" : "#fff",
    color: active
      ? "#fff"
      : hoveredPageBtn === key && !disabled
      ? "#f4771c"
      : "#685d52",
    fontSize: isNarrow ? 13 : 14,
    fontWeight: 700,
    fontFamily: "inherit",
    cursor: disabled ? "not-allowed" : "pointer",
    opacity: disabled ? 0.4 : 1,
    transition: "0.15s",
  });

  const renderPagination = () => {
    // totalPages가 0(데이터 없음 등 예외)이어도 최소 1로 취급해 버튼이 항상 렌더링되게 함
    const effectiveTotalPages = Math.max(totalPages, 1);
    const siblingCount = isNarrow ? 1 : 2;
    const startIdx = totalElements === 0 ? 0 : currentPage * PAGE_SIZE + 1;
    const endIdx = Math.min(totalElements, (currentPage + 1) * PAGE_SIZE);

    return (
      <nav
        aria-label="목록 페이지 이동"
        style={{
          maxWidth: 860,
          margin: isNarrow ? "0 auto 20px" : "4px auto 28px",
          paddingLeft: isNarrow ? 15 : 22,
          paddingRight: isNarrow ? 15 : 22,
          display: "grid",
          gap: isNarrow ? 10 : 12,
          textAlign: "center",
        }}
      >
        <p style={{ color: "#897e75", fontSize: isNarrow ? 12 : 13, margin: 0 }}>
          전체 {totalElements}건 중 {startIdx}~{endIdx}번째
        </p>

        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            flexWrap: "wrap",
            gap: isNarrow ? 5 : 6,
          }}
        >
          <button
            type="button"
            style={pageBtnStyle("prev", {
              wide: true,
              disabled: currentPage === 0 || isLoading,
            })}
            disabled={currentPage === 0 || isLoading}
            onClick={() => handlePageChange(currentPage - 1)}
            onMouseEnter={() => setHoveredPageBtn("prev")}
            onMouseLeave={() => setHoveredPageBtn(null)}
            aria-label="이전 페이지"
          >
            <span aria-hidden="true">←</span>
            {!isNarrow && <span> 이전</span>}
          </button>

          {getPageNumbers(currentPage, effectiveTotalPages, siblingCount).map((p, idx) =>
            p === "..." ? (
              <span
                key={`ellipsis-${idx}`}
                aria-hidden="true"
                style={{
                  minWidth: 24,
                  minHeight: isNarrow ? 36 : 40,
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "#b3aba4",
                  fontSize: 14,
                }}
              >
                …
              </span>
            ) : (
              <button
                type="button"
                key={p}
                style={pageBtnStyle(p, { active: p === currentPage, disabled: isLoading })}
                disabled={isLoading}
                aria-current={p === currentPage ? "page" : undefined}
                aria-label={`${p + 1}페이지`}
                onClick={() => handlePageChange(p)}
                onMouseEnter={() => setHoveredPageBtn(p)}
                onMouseLeave={() => setHoveredPageBtn(null)}
              >
                {p + 1}
              </button>
            )
          )}

          <button
            type="button"
            style={pageBtnStyle("next", {
              wide: true,
              disabled: currentPage >= effectiveTotalPages - 1 || isLoading,
            })}
            disabled={currentPage >= effectiveTotalPages - 1 || isLoading}
            onClick={() => handlePageChange(currentPage + 1)}
            onMouseEnter={() => setHoveredPageBtn("next")}
            onMouseLeave={() => setHoveredPageBtn(null)}
            aria-label="다음 페이지"
          >
            {!isNarrow && <span>다음 </span>}
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </nav>
    );
  };

  return (
    <>
      <PageHeader
        eyebrow="자원봉사"
        title="오늘의 안부 확인 활동"
        text="대상자와 봉사자 모두가 안심할 수 있도록 2인 1조로 진행합니다."
      />

      <section className="tabs">
        <button
          type="button"
          className={activeTab === "direct" ? "active" : ""}
          onClick={() => setActiveTab("direct")}
        >
          내가 직접 선택
        </button>
        <button
          type="button"
          className={activeTab === "auto" ? "active" : ""}
          onClick={() => setActiveTab("auto")}
        >
          배정 받기
        </button>
      </section>

      {activeTab === "direct" && (
        <>
          <section className="visit-list">
            {isLoading && (
              <p
                style={{
                  textAlign: "center",
                  color: "#897e75",
                  padding: "24px 0",
                }}
              >
                불러오는 중입니다...
              </p>
            )}

            {!isLoading && error && (
              <p
                style={{
                  textAlign: "center",
                  color: "#897e75",
                  padding: "24px 0",
                }}
              >
                {error}
              </p>
            )}

            {!isLoading && !error && activities.length === 0 && (
              <p
                style={{
                  textAlign: "center",
                  color: "#897e75",
                  padding: "24px 0",
                }}
              >
                현재 등록된 방문 활동이 없습니다.
              </p>
            )}

            {!isLoading &&
              !error &&
              activities.length > 0 &&
              activities.map((activity, index) => {
                const isSelected = activity.activityId === selectedActivityId;
                const displayIndex = currentPage * PAGE_SIZE + index + 1;

                return (
                  <div
                    className={`visit${isSelected ? " selected" : ""}`}
                    key={activity.activityId}
                    role="button"
                    tabIndex={0}
                    onClick={() => handleSelect(activity.activityId)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        handleSelect(activity.activityId);
                      }
                    }}
                    style={{ cursor: "pointer" }}
                  >
                    <span className="visit-num">
                      {String(displayIndex).padStart(2, "0")}
                    </span>

                    <div>
                      <h2 style={{ fontSize: "15px" }}>
                        안부확인
                        <small>
                          {STATUS_LABEL[activity.status] ?? activity.status} ·{" "}
                          {formatSchedule(activity.scheduledAt)}
                        </small>
                      </h2>
                      <p>
                        {activity.region} · {activity.ageGroup} ·{" "}
                        {activity.gender === "FEMALE" ? "여성" : "남성"} 어르신
                        {" · "}모집 {activity.approvedCount}/
                        {activity.requiredPeople}명
                        {activity.distanceKm != null &&
                          ` · 약 ${activity.distanceKm}km`}
                      </p>
                    </div>

                    <span className="visit-select-indicator" />
                  </div>
                );
              })}
          </section>

          {!isLoading && !error && activities.length > 0 && renderPagination()}
        </>
      )}

      {activeTab === "auto" && (
        <section className="visit-list">
          <p
            style={{ textAlign: "center", color: "#897e75", padding: "24px 0" }}
          >
            자동배정 기능은 준비 중입니다.
          </p>
        </section>
      )}

      {/* 선택/신청 안내 섹션 - 탭/로딩/에러 상태와 무관하게 항상 하단에 표시 */}
      <section className="visit-detail">
        <h2>안부 확인 전 확인하세요</h2>

        {selectedActivity ? (
          <p style={{ margin: "0 0 14px", fontWeight: 700 }}>
            선택한 활동: {selectedActivity.region} ·{" "}
            {formatSchedule(selectedActivity.scheduledAt)}
          </p>
        ) : (
          <p style={{ margin: "0 0 14px", color: "#897e75" }}>
            위 목록에서 참여할 활동을 먼저 선택해주세요.
          </p>
        )}

        <ol>
          <li>대상자를 직접 만나셨나요?</li>
          <li>식사 및 건강 상태에 큰 변화가 없나요?</li>
          <li>특이사항이 있다면 기록해 주세요.</li>
        </ol>

        {applyError && (
          <p style={{ color: "#c0392b", margin: "0 0 12px" }}>{applyError}</p>
        )}

        <button
          className="submit"
          type="button"
          disabled={!selectedActivityId || isApplying}
          onClick={handleApply}
        >
          {isApplying ? "신청 중..." : "이 활동 신청하기"}
        </button>
      </section>

      {toastMessage && <div className="toast">{toastMessage}</div>}
    </>
  );
}

export default Volunteer;