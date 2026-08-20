import PageHeader from "../../components/common/PageHeader";

function MyPage() {
  return (
    <>
      <PageHeader
        eyebrow="마이페이지"
        title="반가워요"
        text="당신의 관심을 통해 이웃의 오늘이 더 안전해지고 있어요."
      />

      <section className="profile-grid">
        <article className="mileage">
          <span>✦</span>

          <p>나의 마일리지</p>

          <strong>
            - <small>점</small>
          </strong>

          <button type="button">
            내역 보기 ›
          </button>
        </article>

        <article className="profile-stat">
          <strong>
            -
            <span>회</span>
          </strong>

          <p>완료한 안부 확인</p>
        </article>

        <article className="profile-stat">
          <strong>
            -
            <span>명</span>
          </strong>

          <p>함께한 이웃</p>
        </article>
      </section>

      <section className="recent">
        <h2>최근 활동</h2>

        <p style={{ color: "#897e75" }}>
          최근 활동 내역이 없습니다.
        </p>
      </section>
    </>
  );
}

export default MyPage;