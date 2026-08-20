import PageHeader from "../../components/common/PageHeader";

const faqItems = [
  {
    q: "안부 확인 활동은 어떻게 진행되나요?",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "제보 후 처리 과정이 궁금해요.",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "마일리지는 언제 지급되나요?",
    a: "대상자 확인부터 활동 기록까지, 필요한 절차를 안전하게 안내해 드립니다.",
  },
  {
    q: "비회원도 제보할 수 있나요?",
    a: "네, 가능합니다. 다만 원활한 확인을 위해 최소한의 연락처 정보를 요청할 수 있습니다.",
  },
];

function Notice() {
  return (
    <>
      <PageHeader
        eyebrow="알림마당"
        title="공지사항 · 자주 묻는 질문"
        text="다같이 돌봄의 소식과 이용 방법을 안내합니다."
      />

      <section className="notice-board">
        <h2>자주 묻는 질문</h2>

        {faqItems.map((item) => (
          <details
            className="faq"
            key={item.q}
          >
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