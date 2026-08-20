import PageHeader from "../../components/common/PageHeader";

function Volunteer() {
  return (
    <>
      <PageHeader
        eyebrow="자원봉사"
        title="오늘의 안부 확인 활동"
        text="대상자와 봉사자 모두가 안심할 수 있도록 2인 1조로 진행합니다."
      />

      <section className="tabs">
        <button type="button">
          내가 직접 선택
        </button>

        <button type="button">
          배정 받기
        </button>
      </section>

      <section className="visit-list">
        <p
          style={{
            textAlign: "center",
            color: "#897e75",
            padding: "24px 0",
          }}
        >
          현재 등록된 방문 활동이 없습니다.
        </p>
      </section>

      <section className="visit-detail">
        <h2>안부 확인 전 확인하세요</h2>

        <ol>
          <li>대상자를 직접 만나셨나요?</li>
          <li>식사 및 건강 상태에 큰 변화가 없나요?</li>
          <li>특이사항이 있다면 기록해 주세요.</li>
        </ol>

        <button
          className="submit"
          type="button"
        >
          이 활동 신청하기
        </button>
      </section>
    </>
  );
}

export default Volunteer;