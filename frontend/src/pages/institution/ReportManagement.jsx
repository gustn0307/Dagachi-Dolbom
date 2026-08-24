import { useState } from "react";
import { institutionApi } from "../../api/institutionApi";
import { DataState, useInstitutionData } from "../../hooks/useInstitutionData";

function ReportManagement() {
  // BACKEND: 현재 검색/필터는 내려받은 더미 배열에서 처리합니다.
  // 운영에서는 getReports({ query, status, page })로 전달해 서버 페이지네이션을 사용하세요.
  const [filter, setFilter] = useState("전체");
  const [query, setQuery] = useState("");
  const { data = [], loading, error, reload } = useInstitutionData(institutionApi.getReports, []);
  if (loading || error) return <div className="institution-page"><DataState loading={loading} error={error} onRetry={reload} /></div>;
  const visible = data.filter((item) => (filter === "전체" || item.status === filter) && `${item.id}${item.title}${item.place}`.includes(query));
  return <div className="institution-page"><div className="page-title-row compact"><div><p>제보 관리</p><h1>접수된 제보를 확인하세요</h1><span>내용을 검토하고 처리 상태를 관리할 수 있습니다.</span></div><button className="orange-action">＋ 제보 직접 등록</button></div>
    <section className="panel table-panel"><div className="filter-bar"><div className="filter-tabs">{["전체","신규","확인 중","기관 연계","연계 완료"].map((item)=><button key={item} className={filter===item?"active":""} onClick={()=>setFilter(item)}>{item}{item==="신규"&&<em>12</em>}</button>)}</div><div className="table-search"><span>⌕</span><input value={query} onChange={(e)=>setQuery(e.target.value)} placeholder="제보 검색" /></div><button className="filter-button">☷ 필터</button></div>
      <div className="data-table"><div className="table-head"><span>제보 번호</span><span>제보 내용</span><span>접수일</span><span>우선순위</span><span>처리 상태</span><span></span></div>{visible.map(item=><article key={item.id}><span className="id-cell">{item.id}</span><span className="main-cell"><strong>{item.title}</strong><small>⌖ {item.place}</small></span><span>{item.receivedAt}</span><span><i className={`priority ${item.priority}`}>{item.priority}</i></span><span><i className={`table-status ${item.status.replace(' ','-')}`}>{item.status}</i></span><button aria-label="상세보기">›</button></article>)}</div>
      <div className="table-footer"><span>총 {visible.length}건의 제보</span><div><button>‹</button><button className="active">1</button><button>2</button><button>3</button><button>›</button></div></div>
    </section></div>;
}
export default ReportManagement;
