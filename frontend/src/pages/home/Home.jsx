import { Link } from "react-router-dom";
import heroImage from "../../assets/public-hero-care.png";

const services = [
  {
    id: "report",
    path: "/report",
    icon: "⚐",
    tone: "orange",
    title: "제보하기",
    text: "도움이 필요해 보이는 이웃의 상황을 알려주세요.",
  },
  {
    id: "volunteer",
    path: "/volunteer",
    icon: "♡",
    tone: "green",
    title: "자원봉사 참여하기",
    text: "간단한 안부 확인으로 따뜻한 변화를 만들어요.",
  },
  {
    id: "mypage",
    path: "/mypage",
    icon: "◯",
    tone: "yellow",
    title: "마이페이지",
    text: "활동 내역과 마일리지를 확인하고 관리해요.",
  },
  {
    id: "notice",
    path: "/notice",
    icon: "▤",
    tone: "blue",
    title: "공지사항 · FAQ",
    text: "새로운 소식과 자주 묻는 질문을 확인하세요.",
  },
];

function Home() {
  return (
    <>
      <section
        className="hero home-photo-hero"
        style={{ "--home-hero-image": `url(${heroImage})` }}
      >
        <div className="hero-copy">
          <p>당신의 관심이</p>

          <h1>
            누군가의 오늘을
            <br />
            <em>지킵니다</em>
          </h1>

          <span>
            작은 제보와 안부 확인이 모여
            <br />
            우리 이웃의 내일을 더 안전하고 따뜻하게 만듭니다.
          </span>

          <Link to="/report" className="primary-cta">
            ✎ 돌봄 사각지대 제보하기 <b>›</b>
          </Link>
        </div>

      </section>

      <section className="service-grid">
        {services.map((service) => (
          <Link
            key={service.id}
            to={service.path}
            className={`service-card ${service.tone}`}
          >
            <span className="service-icon">
              {service.icon}
            </span>

            <h2>{service.title}</h2>

            <p>{service.text}</p>

            <b>›</b>
          </Link>
        ))}
      </section>

      <Impact />
    </>
  );
}

function Impact() {
  return (
    <section className="impact">
      <p>
        <span>❧</span>
        {" "}
        “우리의 작은 관심이 모여, 더 안전하고 따뜻한 지역사회를
        만듭니다.”
      </p>

      <div>
        <strong>-</strong>
        <small>함께하는 시민</small>
      </div>

      <div>
        <strong>-</strong>
        <small>누적 활동</small>
      </div>

      <div>
        <strong>-</strong>
        <small>도움이 필요한 이웃</small>
      </div>
    </section>
  );
}

export default Home;
