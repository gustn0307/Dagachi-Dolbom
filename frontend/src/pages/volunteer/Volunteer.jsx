import { useEffect, useState } from "react";
import PageHeader from "../../components/common/PageHeader";
import { fetchActivities, applyForActivity } from "../../api/userApi";

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

function Volunteer() {
  const [activeTab, setActiveTab] = useState("direct");
  const [activities, setActivities] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  const [selectedActivityId, setSelectedActivityId] = useState(null);
  const [isApplying, setIsApplying] = useState(false);
  const [applyError, setApplyError] = useState(null);
  const [toastMessage, setToastMessage] = useState(null);

  useEffect(() => {
    if (activeTab !== "direct") return;

    let ignore = false;
    setIsLoading(true);
    setError(null);

    fetchActivities({ page: 0, size: 20 })
      .then((data) => {
        if (!ignore) setActivities(data.content);
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

  const selectedActivity =
    activities.find((a) => a.activityId === selectedActivityId) ?? null;

  const handleSelect = (activityId) => {
    setApplyError(null);
    setSelectedActivityId((prev) => (prev === activityId ? null : activityId));
  };

  const handleApply = async () => {
    if (!selectedActivityId) return;

    setIsApplying(true);
    setApplyError(null);

    try {
      await applyForActivity(selectedActivityId);
      setToastMessage("신청이 완료되었습니다. 기관 승인을 기다려주세요.");
      setSelectedActivityId(null);

      // 목록 새로고침 (신청 상태 반영)
      const data = await fetchActivities({ page: 0, size: 20 });
      setActivities(data.content);
    } catch (err) {
      const message =
        err?.response?.data?.message ?? "신청 중 오류가 발생했습니다.";
      setApplyError(message);
    } finally {
      setIsApplying(false);
    }
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
                    {String(index + 1).padStart(2, "0")}
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

                  <span className="visit-select-indicator">
                  </span>
                </div>  
              );
            })}
        </section>
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
