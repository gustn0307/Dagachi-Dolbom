import { useEffect, useState } from "react";
import PageHeader from "../../components/common/PageHeader";
import { getMyReports } from "../../api/userApi";

const STATUS_LABELS = {
  SUBMITTED: "접수 완료",
  REVIEWING: "검토 중",
  NEED_MORE_INFO: "추가 정보 필요",
  ACCEPTED: "접수 승인",
  REJECTED: "반려",
  CLOSED: "처리 완료",
};

function MyPage() {
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const loadReports = async () => {
      try {
        setLoading(true);
        setError("");

        // 로그인한 사용자의 제보를 최신순으로 조회합니다.
        const data = await getMyReports({
          page: 0,
          size: 5,
          sort: "createdAt,desc",
        });

        setReports(data.content ?? []);
      } catch (requestError) {
        setError(
          requestError?.response?.data?.message ??
            "제보 내역을 불러오지 못했습니다.",
        );
      } finally {
        setLoading(false);
      }
    };

    loadReports();
  }, []);

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

          <button type="button">내역 보기 ›</button>
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
        <h2>최근 제보</h2>

        {loading && (
          <p style={{ color: "#897e75" }}>제보 내역을 불러오는 중입니다.</p>
        )}

        {!loading && error && (
          <p style={{ color: "#d9534f" }}>{error}</p>
        )}

        {!loading && !error && reports.length === 0 && (
          <p style={{ color: "#897e75" }}>접수한 제보가 없습니다.</p>
        )}

        {!loading &&
          !error &&
          reports.map((report) => (
            <article
              key={report.reportId}
              style={{
                padding: "18px 0",
                borderBottom: "1px solid #eee5dc",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  gap: "16px",
                }}
              >
                <strong>제보 #{report.reportId}</strong>

                <span>
                  {STATUS_LABELS[report.status] ?? report.status}
                </span>
              </div>

              <p
                style={{
                  margin: "8px 0 4px",
                  color: "#4f463f",
                }}
              >
                {report.content}
              </p>

              {report.address && (
                <small style={{ color: "#897e75" }}>
                  {report.address}
                </small>
              )}
            </article>
          ))}
      </section>
    </>
  );
}

export default MyPage;