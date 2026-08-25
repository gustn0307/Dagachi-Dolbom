import { useState } from "react";
import { institutionApi } from "../../api/institutionApi";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

const GENDER_LABEL = {
  MALE: "남",
  FEMALE: "여",
};

/**
 * 최근 활동 완료 시각을 한국 형식으로 표시한다.
 */
const formatParticipatedAt = (value) => {
  if (!value) {
    return "활동 기록 없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
};

function VolunteerManagement() {
  // 검색창에 현재 입력 중인 값
  const [keywordInput, setKeywordInput] =
    useState("");

  // 실제 API 요청에 사용하는 검색어
  const [keyword, setKeyword] =
    useState("");

  // recent: 최근 활동순
  // participation: 참여 횟수 많은 순
  const [sortType, setSortType] =
    useState("recent");

  // 현재 페이지 번호
  // 백엔드 페이지 번호는 0부터 시작한다.
  const [page, setPage] =
    useState(0);

  // 한 페이지에 표시할 봉사자 수
  const size = 20;

  /**
   * 검색어, 정렬값 또는 페이지 번호가 바뀌면
   * 봉사자 목록 API를 다시 호출한다.
   */
  const {
    data,
    loading,
    error,
    reload,
  } = useInstitutionData(
    () =>
      institutionApi.getVolunteers({
        keyword,
        sortType,
        page,
        size,
      }),
    [
      keyword,
      sortType,
      page,
    ],
  );

  /**
   * 검색 버튼을 눌렀을 때 실행된다.
   */
  const handleSearch = (event) => {
    // form의 기본 새로고침을 막는다.
    event.preventDefault();

    // 새로운 검색은 첫 페이지부터 조회한다.
    setPage(0);

    // 검색어 앞뒤 공백을 제거한다.
    setKeyword(keywordInput.trim());
  };

  /**
   * 데이터를 불러오는 중이거나 오류가 발생한 경우
   * 공통 상태 화면을 표시한다.
   */
  if (loading || error) {
    return (
      <div className="institution-page">
        <DataState
          loading={loading}
          error={error}
          onRetry={reload}
        />
      </div>
    );
  }

  /**
   * 봉사자 API 응답은 배열이 아니라 PageResponse 객체다.
   * 실제 봉사자 배열은 data.content에 들어 있다.
   */
  const volunteers =
    data?.content ?? [];

  const totalElements =
    data?.totalElements ?? 0;

  const totalPages =
    data?.totalPages ?? 0;

  const isFirst =
    data?.first ?? true;

  const isLast =
    data?.last ?? true;

  /**
   * 현재 페이지에 표시된 봉사자들의
   * 참여 활동 횟수를 모두 더한다.
   */
  const currentPageParticipationCount =
    volunteers.reduce(
      (sum, volunteer) =>
        sum +
        (volunteer.participationCount ?? 0),
      0,
    );

  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>봉사자 관리</p>

          <h1>
            함께한 봉사자를 확인하세요
          </h1>

          <span>
            기관 활동에 참여 완료한 봉사자와
            활동 횟수를 확인합니다.
          </span>
        </div>
      </div>

      <section className="mini-metrics">
        <article>
          <span>조회 봉사자</span>

          <strong>
            {totalElements}명
          </strong>
        </article>

        <article>
          <span>
            현재 페이지 참여 활동
          </span>

          <strong className="green-text">
            {currentPageParticipationCount}회
          </strong>
        </article>

        <article>
          <span>현재 정렬</span>

          <strong className="orange-text">
            {sortType === "participation"
              ? "참여 횟수순"
              : "최근 활동순"}
          </strong>
        </article>
      </section>

      <section className="panel table-panel">
        <div className="filter-bar">
          <form
            className="care-list-search"
            onSubmit={handleSearch}
          >
            <div className="table-search wide">
              <span>⌕</span>

              <input
                value={keywordInput}
                placeholder={
                  "이름, 닉네임 또는 전화번호 검색"
                }
                onChange={(event) =>
                  setKeywordInput(
                    event.target.value,
                  )
                }
              />
            </div>

            <button
              type="submit"
              className="filter-button"
            >
              검색
            </button>
          </form>

          <select
            className={
              "filter-button care-filter-select"
            }
            value={sortType}
            aria-label="봉사자 정렬"
            onChange={(event) => {
              // 정렬값 변경
              setSortType(
                event.target.value,
              );

              // 정렬 변경 후 첫 페이지로 이동
              setPage(0);
            }}
          >
            <option value="recent">
              최근 활동순
            </option>

            <option value="participation">
              참여 횟수 많은 순
            </option>
          </select>
        </div>

        {volunteers.length === 0 ? (
          <div className="data-state">
            조건에 맞는 봉사자가 없습니다.
          </div>
        ) : (
          <div className="volunteer-table">
            <div className="table-head">
              <span />
              <span>봉사자</span>
              <span>닉네임·성별</span>
              <span>참여 활동</span>
              <span>최근 활동일</span>
              <span>구분</span>
              <span />
            </div>

            {volunteers.map(
              (volunteer, index) => (
                <article
                  key={volunteer.userId}
                >
                  <span
                    className={
                      `person-avatar avatar-${index % 4}`
                    }
                  >
                    {volunteer.name?.[0] ??
                      "?"}
                  </span>

                  <span>
                    <strong>
                      {volunteer.name}
                    </strong>

                    <small>
                      {volunteer.phone ||
                        "전화번호 없음"}
                    </small>
                  </span>

                  <span>
                    {volunteer.nickname ||
                      "닉네임 없음"}

                    {" · "}

                    {GENDER_LABEL[
                      volunteer.gender
                    ] ??
                      volunteer.gender ??
                      "성별 없음"}
                  </span>

                  <b>
                    {volunteer
                      .participationCount ??
                      0}
                    회
                  </b>

                  <span>
                    {formatParticipatedAt(
                      volunteer
                        .lastParticipatedAt,
                    )}
                  </span>

                  <i
                    className={
                      "table-status 연계-완료"
                    }
                  >
                    참여 완료
                  </i>

                  {/*
                    봉사자 상세 버튼은
                    VOL-05 이후 연결한다.
                  */}
                  <span aria-hidden="true" />
                </article>
              ),
            )}
          </div>
        )}

        {totalPages > 0 && (
          <div
            className={
              "table-footer care-pagination"
            }
          >
            <span>
              전체 {totalElements}명 ·{" "}
              {page + 1}/{totalPages} 페이지
            </span>

            <div>
              <button
                type="button"
                disabled={isFirst}
                onClick={() =>
                  setPage((current) =>
                    Math.max(
                      current - 1,
                      0,
                    ),
                  )
                }
              >
                이전
              </button>

              <button
                type="button"
                className="active"
                aria-current="page"
              >
                {page + 1}
              </button>

              <button
                type="button"
                disabled={isLast}
                onClick={() =>
                  setPage(
                    (current) =>
                      current + 1,
                  )
                }
              >
                다음
              </button>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

export default VolunteerManagement;