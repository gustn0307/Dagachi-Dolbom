import { useCallback, useEffect, useRef } from "react";

/**
 * 일정한 주기로 데이터를 다시 불러오는 공통 폴링 훅입니다.
 *
 * 주요 동작:
 * - 기본 5초마다 callback 실행
 * - 브라우저 탭이 숨겨지면 폴링 중지
 * - 탭으로 돌아오면 즉시 갱신
 * - 이전 요청이 진행 중이면 중복 요청 방지
 * - 컴포넌트가 사라지면 타이머와 이벤트 제거
 *
 * @param {Function} callback 폴링할 비동기 함수
 * @param {Object} options 폴링 설정
 * @param {number} options.interval 폴링 주기(ms)
 * @param {boolean} options.enabled 폴링 활성화 여부
 * @param {boolean} options.immediate 마운트 직후 실행 여부
 * @param {boolean} options.refreshOnFocus 탭 복귀 시 즉시 실행 여부
 */
export function usePolling(
  callback,
  {
    interval = 5000,
    enabled = true,
    immediate = false,
    refreshOnFocus = true,
  } = {},
) {
  const callbackRef = useRef(callback);
  const runningRef = useRef(false);

  /*
   * 컴포넌트가 다시 렌더링되더라도
   * 가장 최근 callback을 사용하도록 저장합니다.
   */
  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  /*
   * 외부에서 직접 새로고침할 때도 사용할 수 있는 함수입니다.
   * 요청이 진행 중이거나 숨겨진 탭이면 실행하지 않습니다.
   */
  const refresh = useCallback(async () => {
    if (!enabled || runningRef.current) {
      return;
    }

    if (
      typeof document !== "undefined" &&
      document.visibilityState === "hidden"
    ) {
      return;
    }

    runningRef.current = true;

    try {
      await callbackRef.current();
    } finally {
      runningRef.current = false;
    }
  }, [enabled]);

  useEffect(() => {
    if (!enabled) {
      return undefined;
    }

    let timerId = null;

    const stopTimer = () => {
      if (timerId !== null) {
        window.clearInterval(timerId);
        timerId = null;
      }
    };

    const startTimer = () => {
      stopTimer();

      if (document.visibilityState === "visible") {
        timerId = window.setInterval(() => {
          void refresh();
        }, interval);
      }
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === "hidden") {
        stopTimer();
        return;
      }

      if (refreshOnFocus) {
        void refresh();
      }

      startTimer();
    };

    const handleWindowFocus = () => {
      if (
        refreshOnFocus &&
        document.visibilityState === "visible"
      ) {
        void refresh();
      }
    };

    if (immediate && document.visibilityState === "visible") {
      void refresh();
    }

    startTimer();

    document.addEventListener(
      "visibilitychange",
      handleVisibilityChange,
    );

    window.addEventListener("focus", handleWindowFocus);

    return () => {
      stopTimer();

      document.removeEventListener(
        "visibilitychange",
        handleVisibilityChange,
      );

      window.removeEventListener(
        "focus",
        handleWindowFocus,
      );
    };
  }, [
    enabled,
    immediate,
    interval,
    refresh,
    refreshOnFocus,
  ]);

  return {
    refresh,
  };
}