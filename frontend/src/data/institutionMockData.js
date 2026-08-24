// BACKEND: 개발 전용 응답 예시입니다. 실제 서버 DTO도 이 필드 구조를 맞추면
// 화면 컴포넌트를 변경하지 않고 VITE_USE_MOCK_API=false만 설정해 연결할 수 있습니다.
export const institutionMockData = {
  dashboard: {
    metrics: [
      { key: "reports", label: "오늘 접수된 제보", value: 12, unit: "건", note: "+3 어제보다 증가", tone: "orange", icon: "⌕" },
      { key: "completed", label: "처리 완료", value: 8, unit: "건", note: "오늘 처리율 67%", tone: "green", icon: "✓" },
      { key: "targets", label: "돌봄 대상자", value: 48, unit: "명", note: "+2 이번 달 신규", tone: "blue", icon: "♡" },
      { key: "volunteers", label: "활동 봉사자", value: 26, unit: "명", note: "이번 주 활동 예정", tone: "purple", icon: "♧" },
    ],
    recentReports: [
      { id: "R-2026-0812", title: "식사가 필요해 보이는 어르신", category: "돌봄", place: "행복구 한마음로", receivedAt: "10분 전", status: "신규" },
      { id: "R-2026-0811", title: "거동이 불편한 독거 어르신", category: "돌봄", place: "행복구 중앙동", receivedAt: "1시간 전", status: "확인 중" },
      { id: "R-2026-0809", title: "장기간 방치된 주거 환경", category: "주거", place: "행복구 새봄길", receivedAt: "어제", status: "연계 완료" },
    ],
    schedules: [
      { id: 1, time: "10:00", title: "초기 상담 방문", detail: "박정희 어르신 · 중앙동" },
      { id: 2, time: "14:30", title: "반찬 배달 봉사", detail: "봉사자 4명 참여" },
      { id: 3, time: "16:00", title: "사례 회의", detail: "2층 회의실" },
    ],
  },
  reports: [
    { id:"R-2026-0812", title:"식사가 필요해 보이는 어르신", place:"행복구 한마음로 123", receivedAt:"08.12 14:32", status:"신규", priority:"긴급" },
    { id:"R-2026-0811", title:"거동이 불편한 독거 어르신", place:"행복구 중앙동 주민센터 인근", receivedAt:"08.12 11:05", status:"확인 중", priority:"일반" },
    { id:"R-2026-0810", title:"도움이 필요해 보이는 노숙인", place:"행복역 3번 출구", receivedAt:"08.11 18:20", status:"기관 연계", priority:"관심" },
    { id:"R-2026-0809", title:"장기간 방치된 주거 환경", place:"행복구 새봄길 22", receivedAt:"08.11 09:14", status:"연계 완료", priority:"일반" },
    { id:"R-2026-0808", title:"폭염 중 야외에 계신 어르신", place:"한마음 공원 정자", receivedAt:"08.10 15:46", status:"종결", priority:"긴급" },
  ],
  careTargets: [
    { id:1, name:"박정희", meta:"여 · 78세", area:"중앙동", careLevel:"집중 돌봄", nextSchedule:"오늘 방문" },
    { id:2, name:"이영수", meta:"남 · 81세", area:"새봄동", careLevel:"정기 돌봄", nextSchedule:"내일 전화" },
    { id:3, name:"김순자", meta:"여 · 74세", area:"한마음동", careLevel:"정기 돌봄", nextSchedule:"8월 14일 방문" },
    { id:4, name:"최동호", meta:"남 · 69세", area:"중앙동", careLevel:"모니터링", nextSchedule:"8월 16일 전화" },
  ],
  volunteers: [
    { id:1, name:"정민수", phone:"010-****-1234", fields:"반찬 배달 · 말벗", activityCount:24, nextSchedule:"오늘 활동", status:"활동 가능" },
    { id:2, name:"한소희", phone:"010-****-1235", fields:"생활 지원 · 병원 동행", activityCount:18, nextSchedule:"내일 활동", status:"활동 가능" },
    { id:3, name:"오세진", phone:"010-****-1236", fields:"주거 환경 개선", activityCount:12, nextSchedule:"8월 15일", status:"활동 가능" },
    { id:4, name:"윤하늘", phone:"010-****-1237", fields:"반찬 배달 · 전화 안부", activityCount:9, nextSchedule:"일정 없음", status:"활동 가능" },
  ],
  activities: [
    { id:1, title:"반찬 배달 및 안부 확인", target:"박정희 어르신", assignee:"정민수 외 3명", date:"오늘", time:"14:30", status:"진행 예정" },
    { id:2, title:"초기 상담 방문", target:"이영수 어르신", assignee:"김담당 사회복지사", date:"내일", time:"10:00", status:"확정" },
    { id:3, title:"주거 환경 정리", target:"최동호 어르신", assignee:"오세진 외 5명", date:"8월 15일", time:"09:30", status:"모집 중" },
    { id:4, title:"전화 안부 확인", target:"김순자 어르신", assignee:"윤하늘 봉사자", date:"8월 16일", time:"16:00", status:"확정" },
  ],
  statistics: {
    summary: { totalReports: 248, completedReports: 214, completionRate: 86.3, averageResponseHours: 3.2 },
    monthlyReports: [
      { month:"3월", received:28, completed:23 }, { month:"4월", received:34, completed:29 },
      { month:"5월", received:31, completed:28 }, { month:"6월", received:42, completed:35 },
      { month:"7월", received:51, completed:46 }, { month:"8월", received:62, completed:53 },
    ],
    categories: [
      { label:"식생활 지원", value:82, color:"#f36f2b" }, { label:"안전 확인", value:61, color:"#5b9e69" },
      { label:"주거 지원", value:48, color:"#568db7" }, { label:"의료 지원", value:35, color:"#8a70b4" },
      { label:"기타", value:22, color:"#d6a43f" },
    ],
    statusCounts: [{ label:"신규", value:12 }, { label:"확인 중", value:9 }, { label:"기관 연계", value:13 }, { label:"완료", value:214 }],
  },
};
