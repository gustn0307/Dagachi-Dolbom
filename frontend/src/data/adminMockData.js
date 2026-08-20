// BACKEND: 관리자 API 응답 계약의 예시입니다. 운영 빌드에서는
// VITE_USE_MOCK_API=false로 설정하여 이 데이터가 사용되지 않게 합니다.
export const adminMockData={
  users:[
    {id:"U-10428",name:"김민지",email:"minji.kim@example.com",phone:"010-****-2814",joinedAt:"2026.08.10",reports:3,volunteerHours:12,status:"활성"},
    {id:"U-10427",name:"박준호",email:"junho.park@example.com",phone:"010-****-9931",joinedAt:"2026.08.09",reports:0,volunteerHours:24,status:"활성"},
    {id:"U-10426",name:"이서연",email:"seoyeon.lee@example.com",phone:"010-****-1148",joinedAt:"2026.08.08",reports:5,volunteerHours:0,status:"활성"},
    {id:"U-10425",name:"최현우",email:"hyunwoo.choi@example.com",phone:"010-****-7762",joinedAt:"2026.08.06",reports:1,volunteerHours:8,status:"정지"},
    {id:"U-10424",name:"정하늘",email:"haneul.jeong@example.com",phone:"010-****-4203",joinedAt:"2026.08.02",reports:2,volunteerHours:16,status:"활성"},
  ],
  institutions:[
    {id:"I-00318",name:"행복복지관",type:"종합사회복지관",manager:"김담당",phone:"02-1234-5678",area:"서울 행복구",joinedAt:"2026.07.18",status:"승인"},
    {id:"I-00317",name:"새봄노인복지센터",type:"노인복지시설",manager:"이은정",phone:"02-3456-7788",area:"서울 새봄구",joinedAt:"2026.08.10",status:"승인 대기"},
    {id:"I-00316",name:"한마음지역센터",type:"지역복지센터",manager:"박성호",phone:"031-774-2100",area:"경기 한마음시",joinedAt:"2026.08.09",status:"서류 검토"},
    {id:"I-00315",name:"온누리돌봄센터",type:"재가복지시설",manager:"최수진",phone:"032-901-8821",area:"인천 온누리구",joinedAt:"2026.06.22",status:"승인"},
    {id:"I-00314",name:"우리동네복지관",type:"종합사회복지관",manager:"정민호",phone:"051-338-1042",area:"부산 우리구",joinedAt:"2026.05.14",status:"운영 정지"},
  ]
};
