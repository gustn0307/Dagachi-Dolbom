import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { institutionApi } from "../../api/institutionApi";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

const GENDER_LABEL = {
  MALE: "남",
  FEMALE: "여",
};

const formatParticipatedAt = (value) => {
  if (!value) {
    return "활동 기록 없음";
  }

  const date = new Date(value);

  if (Number.isNaN(date.getTime())) {
    return "활동 기록 없음";
  }

  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
};

function VolunteerManagement() {
  const navigate = useNavigate();

  // 검색창에 입력 중인 값
  const [keywordInput, setKeywordInput] =
    useState("");

  // 실제 API 요청에 사용하는 검색어
  const [keyword, setKeyword] =
    useState("");

  // recent: 최근 활동순
  // participation: 참여 횟수 많은 순
  const [sortType, setSortType] =
    useState("recent");

  // 백엔드 페이지 번호는 0부터 시작한다.
  const [page, setPage] =
    useState(0);

  const size = 20;

  /**
   * VOL-01~03 봉사자 목록, 검색 및 정렬 조회.
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
   * VOL-05 화면 상단 봉사자 현황 조회.
   */
  const {
    data: overview,
    loading: overviewLoading,
    error: overviewError,
    reload: reloadOverview,
  } = useInstitutionData(
    institutionApi.getVolunteerOverview,
    [],
  );

  /**
   * 검색 버튼 처리.
   */
  const handleSearch = (event) => {
    event.preventDefault();

    setPage(0);
    setKeyword(keywordInput.trim());
  };

  /**
   * 목록과 현황을 모두 다시 조회한다.
   */
  const handleRetry = () => {
    reload();
    reloadOverview();
  };

  if (
    loading ||
    overviewLoading ||
    error ||
    overviewError
  ) {
    return (
      <div className="institution-page">
        <DataState
          loading={
            loading ||
            overviewLoading
          }
          error={
            error ||
            overviewError
          }
          onRetry={handleRetry}
        />
      </div>
    );
  }

  // PageResponse의 실제 목록
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

  return (
    <div className="institution-page">
      <div className="page-title-row compact">
        <div>
          <p>봉사자 관리</p>

          <h1>
            함께한 봉사자를 확인하세요
          </h1>

          <span>
            기관 활동에 참여한 봉사자와
            현재 활동 현황을 확인합니다.
          </span>
        </div>
      </div>

      {/* VOL-05 기관 봉사자 현황 */}
      <section className="mini-metrics">
        <article>
          <span>전체 봉사자</span>

          <strong>
            {overview?.totalVolunteerCount ?? 0}명
          </strong>
        </article>

        <article>
          <span>현재 활동 중</span>

          <strong className="green-text">
            {overview?.activeVolunteerCount ?? 0}명
          </strong>
        </article>

        <article>
          <span>참여 예정</span>

          <strong className="orange-text">
            {overview?.scheduledVolunteerCount ?? 0}명
          </strong>
        </article>
      </section>

      <section className="panel table-panel">
        {/* 검색 및 정렬 영역 */}
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
              setSortType(
                event.target.value,
              );

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

        {/* 봉사자 목록 */}
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

                  {/* VOL-07 봉사자 상세 이동 */}
                  <button
                    type="button"
                    aria-label={
                      `${volunteer.name} 상세 보기`
                    }
                    onClick={() =>
                      navigate(
                        `/institution/volunteers/${volunteer.userId}`,
                      )
                    }
                  >
                    ›
                  </button>
                </article>
              ),
            )}
          </div>
        )}

        {/* 페이지 이동 */}
        {totalPages > 0 && (
          <div
            className={
              "table-footer care-pagination"
            }
          >
            <span>
              검색 결과 {totalElements}명 ·{" "}
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
