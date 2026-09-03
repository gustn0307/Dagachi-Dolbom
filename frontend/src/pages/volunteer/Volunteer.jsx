import { useEffect, useState } from "react";
import PageHeader from "../../components/common/PageHeader";
import { fetchActivities, applyForActivity } from "../../api/userApi";

const PAGE_SIZE = 10;
const NARROW_BREAKPOINT = 380;
const AGE_GROUPS = ["50대 이하", "60대", "70대", "80대", "90대 이상"];
const GENDER_OPTIONS = [
  { value: "", label: "전체" },
  { value: "MALE", label: "남성" },
  { value: "FEMALE", label: "여성" },
];

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
  const [showConfirmModal, setShowConfirmModal] = useState(false);

  const [currentPage, setCurrentPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isNarrow, setIsNarrow] = useState(
    typeof window !== "undefined"
      ? window.innerWidth <= NARROW_BREAKPOINT
      : false,
  );

  const [selectedActivityId, setSelectedActivityId] = useState(null);
  const [isApplying, setIsApplying] = useState(false);
  const [applyError, setApplyError] = useState(null);
  const [toastMessage, setToastMessage] = useState(null);
  const [hoveredPageBtn, setHoveredPageBtn] = useState(null);

  // ---- 필터/정렬 state ----
  const [regionInput, setRegionInput] = useState("");
  const [appliedRegion, setAppliedRegion] = useState("");
  const [selectedAgeGroups, setSelectedAgeGroups] = useState([]);
  const [selectedGender, setSelectedGender] = useState(""); // "" | "MALE" | "FEMALE"
  const [sortMode, setSortMode] = useState("latest"); // "latest" | "distance"
  const [coords, setCoords] = useState(null); // { latitude, longitude }
  const [geoLoading, setGeoLoading] = useState(false);

  useEffect(() => {
    if (activeTab !== "direct") return;

    let ignore = false;
    setIsLoading(true);
    setError(null);

    fetchActivities({
      page: currentPage,
      size: PAGE_SIZE,
      region: appliedRegion || undefined,
      ageGroups: selectedAgeGroups,
      gender: selectedGender || undefined,
      latitude: coords?.latitude,
      longitude: coords?.longitude,
    })
      .then((data) => {
        if (ignore) return;
        setActivities(data.content);
        setTotalPages(data.totalPages);
        setTotalElements(data.totalElements);

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
  }, [
    activeTab,
    currentPage,
    appliedRegion,
    selectedAgeGroups,
    selectedGender,
    coords,
  ]);

  useEffect(() => {
    setCurrentPage(0);
    setSelectedActivityId(null);
    setApplyError(null);
  }, [activeTab]);

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

      const data = await fetchActivities({
        page: currentPage,
        size: PAGE_SIZE,
        region: appliedRegion || undefined,
        ageGroups: selectedAgeGroups,
        gender: selectedGender || undefined,
        latitude: coords?.latitude,
        longitude: coords?.longitude,
      });
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

  // ---- 필터 핸들러 ----
  const resetPageAndSelection = () => {
    setCurrentPage(0);
    setSelectedActivityId(null);
  };

  const handleSubmitRegion = (e) => {
    e.preventDefault();
    resetPageAndSelection();
    setAppliedRegion(regionInput.trim());
  };

  const handleClearRegion = () => {
    resetPageAndSelection();
    setRegionInput("");
    setAppliedRegion("");
  };

  const handleToggleAgeGroup = (ageGroup) => {
    resetPageAndSelection();
    setSelectedAgeGroups((prev) =>
      prev.includes(ageGroup)
        ? prev.filter((a) => a !== ageGroup)
        : [...prev, ageGroup],
    );
  };

  const handleChangeGender = (gender) => {
    resetPageAndSelection();
    if (gender === "") {
      setSelectedGender("");
      return;
    }
    setSelectedGender((prev) => (prev === gender ? "" : gender));
  };

  const handleChangeSort = (mode) => {
    if (mode === "latest") {
      resetPageAndSelection();
      setSortMode("latest");
      setCoords(null);
      return;
    }

    if (!navigator.geolocation) {
      setToastMessage("이 브라우저에서는 위치 정보를 사용할 수 없습니다.");
      return;
    }

    setGeoLoading(true);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        resetPageAndSelection();
        setSortMode("distance");
        setCoords({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        });
        setGeoLoading(false);
      },
      () => {
        setToastMessage(
          "위치 정보를 가져올 수 없습니다. 위치 권한을 확인해주세요.",
        );
        setGeoLoading(false);
      },
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60000 },
    );
  };

  const handleResetAllFilters = () => {
    resetPageAndSelection();
    setRegionInput("");
    setAppliedRegion("");
    setSelectedAgeGroups([]);
    setSelectedGender("");
    setSortMode("latest");
    setCoords(null);
  };

  const hasActiveFilters =
    Boolean(appliedRegion) ||
    selectedAgeGroups.length > 0 ||
    Boolean(selectedGender) ||
    sortMode === "distance";

  // ---- 페이지네이션 버튼 스타일 헬퍼 ----
  const pageBtnStyle = (
    key,
    { active = false, disabled = false, wide = false } = {},
  ) => ({
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
        <p
          style={{ color: "#897e75", fontSize: isNarrow ? 12 : 13, margin: 0 }}
        >
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

          {getPageNumbers(currentPage, effectiveTotalPages, siblingCount).map(
            (p, idx) =>
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
                  style={pageBtnStyle(p, {
                    active: p === currentPage,
                    disabled: isLoading,
                  })}
                  disabled={isLoading}
                  aria-current={p === currentPage ? "page" : undefined}
                  aria-label={`${p + 1}페이지`}
                  onClick={() => handlePageChange(p)}
                  onMouseEnter={() => setHoveredPageBtn(p)}
                  onMouseLeave={() => setHoveredPageBtn(null)}
                >
                  {p + 1}
                </button>
              ),
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

  const renderFilterBar = () => (
    <section
      className="activity-filter-bar"
      aria-label="활동 목록 필터 및 정렬"
      style={{
        maxWidth: 860,
        margin: "0 auto 18px",
        padding: "14px 16px",
        border: "1px solid #ece5dd",
        borderRadius: 14,
        background: "#fff",
        display: "grid",
        gap: 14,
      }}
    >
      {/* 지역 검색 */}
      <form
        onSubmit={handleSubmitRegion}
        role="search"
        aria-label="지역으로 검색"
        style={{ display: "flex", gap: 8 }}
      >
        <label htmlFor="region-search" className="sr-only">
          지역 검색
        </label>
        <input
          id="region-search"
          type="text"
          inputMode="search"
          placeholder="지역으로 검색 (예: 잠실동)"
          value={regionInput}
          onChange={(e) => setRegionInput(e.target.value)}
          style={{
            flex: 1,
            minHeight: 44,
            padding: "0 14px",
            border: "1px solid #ece5dd",
            borderRadius: 10,
            fontSize: 15,
          }}
        />
        <button
          type="submit"
          style={{
            minHeight: 44,
            minWidth: 64,
            border: "1px solid #f4771c",
            borderRadius: 10,
            background: "#f4771c",
            color: "#fff",
            fontWeight: 700,
            cursor: "pointer",
          }}
        >
          검색
        </button>
        {appliedRegion && (
          <button
            type="button"
            onClick={handleClearRegion}
            aria-label="지역 검색 초기화"
            style={{
              minHeight: 44,
              minWidth: 44,
              border: "1px solid #ece5dd",
              borderRadius: 10,
              background: "#fff",
              color: "#897e75",
              cursor: "pointer",
            }}
          >
            ✕
          </button>
        )}
      </form>

      {/* 연령대 필터 */}
      <div>
        <p
          style={{
            margin: "0 0 8px",
            fontSize: 13,
            color: "#897e75",
            fontWeight: 700,
          }}
        >
          연령대
        </p>
        <div
          role="group"
          aria-label="연령대 선택"
          style={{ display: "flex", flexWrap: "wrap", gap: 8 }}
        >
          {AGE_GROUPS.map((ag) => {
            const active = selectedAgeGroups.includes(ag);
            return (
              <button
                key={ag}
                type="button"
                aria-pressed={active}
                onClick={() => handleToggleAgeGroup(ag)}
                style={{
                  minHeight: 40,
                  padding: "0 16px",
                  borderRadius: 20,
                  border: `1px solid ${active ? "#f4771c" : "#ece5dd"}`,
                  background: active ? "#fff3ea" : "#fff",
                  color: active ? "#f4771c" : "#685d52",
                  fontWeight: 700,
                  fontSize: 14,
                  cursor: "pointer",
                }}
              >
                {ag}
              </button>
            );
          })}
        </div>
      </div>

      {/* 성별 필터 */}
      <div>
        <p
          style={{
            margin: "0 0 8px",
            fontSize: 13,
            color: "#897e75",
            fontWeight: 700,
          }}
        >
          성별
        </p>
        <div
          role="group"
          aria-label="성별 선택"
          style={{ display: "flex", gap: 8 }}
        >
          {GENDER_OPTIONS.map(({ value, label }) => {
            const active = selectedGender === value;
            return (
              <button
                key={label}
                type="button"
                aria-pressed={active}
                onClick={() => handleChangeGender(value)}
                style={{
                  minHeight: 40,
                  padding: "0 16px",
                  borderRadius: 10,
                  border: `1px solid ${active ? "#f4771c" : "#ece5dd"}`,
                  background: active ? "#f4771c" : "#fff",
                  color: active ? "#fff" : "#685d52",
                  fontWeight: 700,
                  fontSize: 14,
                  cursor: "pointer",
                }}
              >
                {label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 정렬 */}
      <div>
        <p
          style={{
            margin: "0 0 8px",
            fontSize: 13,
            color: "#897e75",
            fontWeight: 700,
          }}
        >
          정렬
        </p>
        <div
          role="group"
          aria-label="정렬 방식 선택"
          style={{ display: "flex", gap: 8 }}
        >
          <button
            type="button"
            aria-pressed={sortMode === "latest"}
            onClick={() => handleChangeSort("latest")}
            style={{
              minHeight: 40,
              padding: "0 16px",
              borderRadius: 10,
              border: `1px solid ${sortMode === "latest" ? "#f4771c" : "#ece5dd"}`,
              background: sortMode === "latest" ? "#f4771c" : "#fff",
              color: sortMode === "latest" ? "#fff" : "#685d52",
              fontWeight: 700,
              cursor: "pointer",
            }}
          >
            최신순
          </button>
          <button
            type="button"
            aria-pressed={sortMode === "distance"}
            disabled={geoLoading}
            onClick={() => handleChangeSort("distance")}
            style={{
              minHeight: 40,
              padding: "0 16px",
              borderRadius: 10,
              border: `1px solid ${sortMode === "distance" ? "#f4771c" : "#ece5dd"}`,
              background: sortMode === "distance" ? "#f4771c" : "#fff",
              color: sortMode === "distance" ? "#fff" : "#685d52",
              fontWeight: 700,
              cursor: geoLoading ? "wait" : "pointer",
              opacity: geoLoading ? 0.6 : 1,
            }}
          >
            {geoLoading ? "위치 확인 중..." : "거리순"}
          </button>
        </div>
      </div>

      {hasActiveFilters && (
        <button
          type="button"
          onClick={handleResetAllFilters}
          style={{
            justifySelf: "start",
            padding: "6px 10px",
            border: "none",
            background: "transparent",
            color: "#897e75",
            fontSize: 13,
            textDecoration: "underline",
            cursor: "pointer",
          }}
        >
          필터 초기화
        </button>
      )}
    </section>
  );

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
          {renderFilterBar()}

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
                조건에 맞는 방문 활동이 없습니다.
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
          onClick={() => setShowConfirmModal(true)}
        >
          {isApplying ? "신청 중..." : "이 활동 신청하기"}
        </button>
      </section>

      {showConfirmModal && selectedActivity && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="confirm-modal-title"
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
            padding: 16,
          }}
          onClick={() => !isApplying && setShowConfirmModal(false)}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: 16,
              padding: 28,
              maxWidth: 360,
              width: "100%",
              textAlign: "center",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h2
              id="confirm-modal-title"
              style={{ fontSize: 18, margin: "0 0 12px", color: "#3d332a" }}
            >
              이 활동을 신청하시겠어요?
            </h2>
            <p
              style={{
                margin: "0 0 24px",
                color: "#897e75",
                fontSize: 14,
                lineHeight: 1.5,
              }}
            >
              {selectedActivity.region} ·{" "}
              {formatSchedule(selectedActivity.scheduledAt)}
              <br />
              신청 후 기관 담당자의 승인을 기다리게 됩니다.
            </p>

            <div style={{ display: "flex", gap: 8 }}>
              <button
                type="button"
                disabled={isApplying}
                onClick={() => setShowConfirmModal(false)}
                style={{
                  flex: 1,
                  minHeight: 48,
                  border: "1px solid #ece5dd",
                  borderRadius: 10,
                  background: "#fff",
                  color: "#685d52",
                  fontWeight: 700,
                  fontSize: 15,
                  cursor: isApplying ? "not-allowed" : "pointer",
                }}
              >
                취소
              </button>
              <button
                type="button"
                disabled={isApplying}
                onClick={async () => {
                  await handleApply();
                  setShowConfirmModal(false);
                }}
                style={{
                  flex: 1,
                  minHeight: 48,
                  border: "1px solid #f4771c",
                  borderRadius: 10,
                  background: "#f4771c",
                  color: "#fff",
                  fontWeight: 700,
                  fontSize: 15,
                  cursor: isApplying ? "not-allowed" : "pointer",
                  opacity: isApplying ? 0.7 : 1,
                }}
              >
                {isApplying ? "신청 중..." : "신청 확정"}
              </button>
            </div>
          </div>
        </div>
      )}

      {toastMessage && <div className="toast">{toastMessage}</div>}
    </>
  );
}

export default Volunteer;
