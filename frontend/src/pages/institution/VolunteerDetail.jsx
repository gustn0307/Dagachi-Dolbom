import {
  useCallback,
  useState,
} from "react";
import {
  useNavigate,
  useParams,
} from "react-router-dom";
import { institutionApi } from "../../api/institutionApi";
import {
  DataState,
  useInstitutionData,
} from "../../hooks/useInstitutionData";

const GENDER_LABEL = {
  MALE: "남",
  FEMALE: "여",
};

const VISIT_RESULT_LABEL = {
  MET: "만남 완료",
  NOT_MET: "만나지 못함",
};

const REVIEW_STATUS_LABEL = {
  DRAFT: "작성 중",
  SUBMITTED: "검토 대기",
  APPROVED: "승인 완료",
  NEEDS_REVISION: "수정 요청",
  REJECTED: "반려",
};

/**
 * 값이 없을 때 대신 표시할 문구를 반환한다.
 */
const display = (
  value,
  fallback = "미등록",
) =>
  value === null ||
  value === undefined ||
  value === ""
    ? fallback
    : value;

/**
 * 날짜와 시간을 한국 형식으로 표시한다.
 */
const formatDateTime = (
  value,
  fallback = "기록 없음",
) => {
  if (!value) {
    return fallback;
  }

  const date =
    new Date(value);

  if (
    Number.isNaN(
      date.getTime(),
    )
  ) {
    return fallback;
  }

  return new Intl.DateTimeFormat(
    "ko-KR",
    {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    },
  ).format(date);
};

function VolunteerDetail() {
  // 주소의 봉사자 사용자 ID
  const { volunteerId } =
    useParams();

  const navigate =
    useNavigate();

  // 활동 이력의 현재 페이지
  const [page, setPage] =
    useState(0);

  // 한 페이지에 표시할 활동 수
  const size = 20;

  /**
   * VOL-06 봉사자 기본 상세 조회 함수.
   */
  const detailLoader =
    useCallback(
      () =>
        institutionApi.getVolunteer(
          volunteerId,
        ),
      [
        volunteerId,
      ],
    );

  /**
   * VOL-06 기관별 봉사자 활동 이력 조회 함수.
   */
  const activityLoader =
    useCallback(
      () =>
        institutionApi
          .getVolunteerActivities(
            volunteerId,
            {
              page,
              size,
            },
          ),
      [
        volunteerId,
        page,
      ],
    );

  /**
   * 봉사자 기본 상세정보 조회.
   */
  const {
    data: volunteer,
    loading: detailLoading,
    error: detailError,
    reload: reloadDetail,
  } = useInstitutionData(
    detailLoader,
    [
      detailLoader,
    ],
  );

  /**
   * 해당 기관에서의 활동 이력 조회.
   */
  const {
    data: activityData,
    loading: activityLoading,
    error: activityError,
    reload: reloadActivities,
  } = useInstitutionData(
    activityLoader,
    [
      activityLoader,
    ],
  );

  /**
   * 두 API를 모두 다시 호출한다.
   */
  const handleRetry = () => {
    reloadDetail();
    reloadActivities();
  };

  /**
   * 상세정보 또는 활동 이력을 불러오는 중이거나
   * 오류가 발생한 경우 공통 상태 화면을 표시한다.
   */
  if (
    detailLoading ||
    activityLoading ||
    detailError ||
    activityError
  ) {
    return (
      <div className="institution-page">
        <DataState
          loading={
            detailLoading ||
            activityLoading
          }
          error={
            detailError ||
            activityError
          }
          onRetry={handleRetry}
        />
      </div>
    );
  }

  // 실제 활동 이력 배열
  const activities =
    activityData?.content ?? [];

  // 전체 활동 이력 수
  const totalElements =
    activityData?.totalElements ?? 0;

  // 전체 페이지 수
  const totalPages =
    activityData?.totalPages ?? 0;

  const isFirst =
    activityData?.first ?? true;

  const isLast =
    activityData?.last ?? true;

  return (
    <div className="institution-page care-detail-page">
      <button
        type="button"
        className="detail-back"
        onClick={() =>
          navigate(
            "/institution/volunteers",
          )
        }
      >
        ← 봉사자 목록으로
      </button>

      <div className="page-title-row compact">
        <div>
          <p>봉사자 상세</p>

          <h1>
            {display(
              volunteer?.name,
              "이름 미등록",
            )}
          </h1>

          <span>
            봉사자의 기본정보와 이 기관에서의
            활동 이력을 확인합니다.
          </span>
        </div>

        <div className="detail-badges">
          <span className="care-tag AGREED">
            참여 봉사자
          </span>
        </div>
      </div>

      {/* 봉사자 활동 요약 */}
      <section className="detail-summary-grid">
        <article className="panel">
          <span>참여 완료 활동</span>

          <strong>
            {volunteer?.participationCount ?? 0}회
          </strong>
        </article>

        <article className="panel">
          <span>최근 활동일</span>

          <strong>
            {formatDateTime(
              volunteer?.lastParticipatedAt,
            )}
          </strong>
        </article>

        <article className="panel">
          <span>활동 이력</span>

          <strong>
            {totalElements}건
          </strong>
        </article>
      </section>

      {/* 봉사자 기본정보 */}
      <section className="panel detail-section">
        <div className="panel-title">
          <div>
            <h2>기본정보</h2>

            <p>
              기관 활동에 참여한 봉사자의
              연락처 정보입니다.
            </p>
          </div>
        </div>

        <dl className="detail-info-list">
          <div>
            <dt>사용자 번호</dt>

            <dd>
              {display(
                volunteer?.userId,
              )}
            </dd>
          </div>

          <div>
            <dt>이름</dt>

            <dd>
              {display(
                volunteer?.name,
              )}
            </dd>
          </div>

          <div>
            <dt>닉네임</dt>

            <dd>
              {display(
                volunteer?.nickname,
              )}
            </dd>
          </div>

          <div>
            <dt>성별</dt>

            <dd>
              {GENDER_LABEL[
                volunteer?.gender
              ] ??
                display(
                  volunteer?.gender,
                )}
            </dd>
          </div>

          <div>
            <dt>전화번호</dt>

            <dd>
              {display(
                volunteer?.phone,
              )}
            </dd>
          </div>
        </dl>
      </section>

      {/* 기관별 봉사 활동 이력 */}
      <section className="panel table-panel">
        <div className="panel-title">
          <div>
            <h2>기관 활동 이력</h2>

            <p>
              이 기관에서 참여 완료하고
              검토 승인된 활동만 표시합니다.
            </p>
          </div>
        </div>

        {activities.length === 0 ? (
          <div className="data-state">
            표시할 활동 이력이 없습니다.
          </div>
        ) : (
          <div className="data-table">
            <div className="table-head">
              <span>활동 번호</span>
              <span>돌봄 대상자</span>
              <span>시작일</span>
              <span>완료일</span>
              <span>방문 결과</span>
              <span />
            </div>

            {activities.map(
              (activity) => (
                <article
                  key={activity.activityId}
                >
                  <span className="id-cell">
                    {activity.activityId}
                  </span>

                  <span className="main-cell">
                    <strong>
                      {display(
                        activity.recipientName,
                        "대상자 미등록",
                      )}
                    </strong>

                    <small>
                      예정{" "}
                      {formatDateTime(
                        activity.scheduledAt,
                      )}
                    </small>
                  </span>

                  <span>
                    {formatDateTime(
                      activity.startedAt,
                    )}
                  </span>

                  <span>
                    {formatDateTime(
                      activity.completedAt,
                    )}
                  </span>

                  <span>
                    <i
                      className={
                        "table-status 연계-완료"
                      }
                    >
                      {VISIT_RESULT_LABEL[
                        activity.visitResult
                      ] ??
                        display(
                          activity.visitResult,
                        )}
                    </i>

                    <small>
                      {
                        REVIEW_STATUS_LABEL[
                          activity.reviewStatus
                        ]
                      }
                    </small>
                  </span>

                  <span aria-hidden="true" />
                </article>
              ),
            )}
          </div>
        )}

        {/* 활동 이력 페이지 이동 */}
        {totalPages > 0 && (
          <div
            className={
              "table-footer care-pagination"
            }
          >
            <span>
              전체 {totalElements}건 ·{" "}
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

export default VolunteerDetail;