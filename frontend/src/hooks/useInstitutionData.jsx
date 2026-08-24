import { useCallback, useEffect, useState } from "react";

export function useInstitutionData(loader, dependencies = []) {
  // BACKEND: 모든 조회 화면이 공유하는 비동기 상태 훅입니다.
  // loader에는 services 폴더의 API 함수를 전달하며 로딩/오류/재시도를 자동 처리합니다.
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const load = useCallback(async () => {
    setLoading(true); setError(null);
    // BACKEND: API 응답을 화면 데이터로 변환해야 한다면 loader 함수 또는 service에서
    // 변환한 후 반환하세요. 컴포넌트에서는 서버 응답 형식을 직접 다루지 않습니다.
    try { setData(await loader()); }
    catch (reason) { setError(reason instanceof Error ? reason.message : "데이터를 불러오지 못했습니다."); }
    finally { setLoading(false); }
  }, dependencies);
  useEffect(() => { load(); }, [load]);
  return { data, loading, error, reload: load, setData };
}

export function DataState({ loading, error, onRetry }) {
  if (loading) return <div className="data-state"><span className="loading-spinner" />데이터를 불러오고 있습니다.</div>;
  if (error) return <div className="data-state error"><b>!</b><span>{error}</span><button onClick={onRetry}>다시 시도</button></div>;
  return null;
}
