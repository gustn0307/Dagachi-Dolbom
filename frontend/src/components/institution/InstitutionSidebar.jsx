import {
  useCallback,
  useEffect,
  useState,
} from "react";
import { NavLink } from "react-router-dom";

import { institutionApi } from "../../api/institutionApi";

const REPORT_COUNT_CHANGED_EVENT =
  "institution-report-count-changed";

const ACTIVITY_COUNT_CHANGED_EVENT =
  "institution-activity-count-changed";

const SIDEBAR_REFRESH_INTERVAL =
  15000;

const Icon = ({ children }) => (
  <span
    className="side-icon"
    aria-hidden="true"
  >
    {children}
  </span>
);

function InstitutionSidebar() {
  const [
    unassignedReportCount,
    setUnassignedReportCount,
  ] = useState(0);

  const [
    pendingApplicationCount,
    setPendingApplicationCount,
  ] = useState(0);

  /*
   * 미배정 제보 개수 조회
   */
  const loadUnassignedReportCount =
    useCallback(async () => {
      try {
        const data =
          await institutionApi
            .getUnassignedReports({
              page: 0,
              size: 1,
            });

        setUnassignedReportCount(
          Number(
            data?.totalElements ?? 0,
          ),
        );
      } catch (error) {
        console.error(
          "미배정 제보 개수를 불러오지 못했습니다.",
          error,
        );

        setUnassignedReportCount(0);
      }
    }, []);

  /*
   * 승인 대기 봉사 신청 개수 조회
   */
  const loadPendingApplicationCount =
    useCallback(async () => {
      try {
        const data =
          await institutionApi
            .getPendingApplicationSummary();

        setPendingApplicationCount(
          Number(
            data?.totalPendingCount ??
              0,
          ),
        );
      } catch (error) {
        console.error(
          "승인 대기 봉사 신청 개수를 불러오지 못했습니다.",
          error,
        );

        setPendingApplicationCount(0);
      }
    }, []);

  /*
   * 사이드바에 표시되는 모든 숫자를 갱신한다.
   */
  const loadSidebarCounts =
    useCallback(async () => {
      await Promise.allSettled([
        loadUnassignedReportCount(),
        loadPendingApplicationCount(),
      ]);
    }, [
      loadUnassignedReportCount,
      loadPendingApplicationCount,
    ]);

  useEffect(() => {
    loadSidebarCounts();

    /*
     * 같은 브라우저 안에서 제보 또는
     * 활동 신청 상태가 변경됐을 때 갱신한다.
     */
    const handleCountChanged = () => {
      loadSidebarCounts();
    };

    /*
     * 다른 화면을 보고 돌아왔을 때
     * 즉시 최신 숫자를 불러온다.
     */
    const handleWindowFocus = () => {
      loadSidebarCounts();
    };

    window.addEventListener(
      REPORT_COUNT_CHANGED_EVENT,
      handleCountChanged,
    );

    window.addEventListener(
      ACTIVITY_COUNT_CHANGED_EVENT,
      handleCountChanged,
    );

    window.addEventListener(
      "focus",
      handleWindowFocus,
    );

    /*
     * 봉사자가 신청했을 때 F5를 누르지 않아도
     * 일정 시간 안에 숫자가 변경되도록 한다.
     */
    const intervalId =
      window.setInterval(
        loadSidebarCounts,
        SIDEBAR_REFRESH_INTERVAL,
      );

    return () => {
      window.removeEventListener(
        REPORT_COUNT_CHANGED_EVENT,
        handleCountChanged,
      );

      window.removeEventListener(
        ACTIVITY_COUNT_CHANGED_EVENT,
        handleCountChanged,
      );

      window.removeEventListener(
        "focus",
        handleWindowFocus,
      );

      window.clearInterval(
        intervalId,
      );
    };
  }, [loadSidebarCounts]);

  return (
    <aside className="institution-sidebar">
      <div className="institution-logo">
        <span className="institution-logo-mark">
          ♥
        </span>

        <div>
          <h2>다같이 돌봄</h2>
          <p>기관 파트너</p>
        </div>
      </div>

      <nav>
        <NavLink
          to="/institution"
          end
        >
          <Icon>▦</Icon>
          <span>대시보드</span>
        </NavLink>

        <NavLink to="/institution/reports">
          <Icon>⌕</Icon>
          <span>제보 관리</span>

          {unassignedReportCount >
            0 && (
            <em
              aria-label={
                `미배정 제보 ${unassignedReportCount}건`
              }
            >
              {unassignedReportCount >
              99
                ? "99+"
                : unassignedReportCount}
            </em>
          )}
        </NavLink>

        <NavLink to="/institution/care-targets">
          <Icon>♡</Icon>
          <span>돌봄 대상자</span>
        </NavLink>

        <NavLink to="/institution/volunteers">
          <Icon>♧</Icon>
          <span>봉사자 관리</span>
        </NavLink>

        <NavLink to="/institution/activities">
          <Icon>✓</Icon>
          <span>활동 관리</span>

          {pendingApplicationCount >
            0 && (
            <em
              aria-label={
                `승인 대기 봉사 신청 ${pendingApplicationCount}건`
              }
            >
              {pendingApplicationCount >
              99
                ? "99+"
                : pendingApplicationCount}
            </em>
          )}
        </NavLink>

        <NavLink to="/institution/statistics">
          <Icon>⌁</Icon>
          <span>통계</span>
        </NavLink>
      </nav>

      <div className="institution-help">
        <span>?</span>

        <p>
          <strong>
            도움이 필요하신가요?
          </strong>
          기관 전용 고객센터
          <br />
          02-1234-5678
        </p>
      </div>
    </aside>
  );
}

export default InstitutionSidebar;