import { expect, test } from "@playwright/test";

test.use({
  permissions: ["geolocation"],
  geolocation: {
    latitude: 37.5665,
    longitude: 126.978,
  },
});

const AUTO_MATCH_CANDIDATES_KEY = "autoMatchCandidates";
const AUTO_MATCH_INDEX_KEY = "autoMatchCandidateIndex";
const AUTO_MATCH_SEEN_IDS_KEY = "autoMatchSeenActivityIds";

const successResponse = (data) => ({
  success: true,
  code: "SUCCESS",
  message: null,
  data,
});

const emptyPage = {
  content: [],
  page: 0,
  size: 10,
  totalElements: 0,
  totalPages: 0,
};

function buildCandidate({
  activityId,
  region,
  ageGroup,
  gender,
  reason,
  distanceKm,
}) {
  return {
    activityId,
    region,
    ageGroup,
    gender,
    scheduledAt: "2026-10-01T10:00:00",
    requiredPeople: 2,
    approvedCount: 0,
    applicantCount: 0,
    distanceKm,
    reason,
    model: reason ? "gpt-4o-mini" : null,
  };
}

const candidate1 = buildCandidate({
  activityId: 1,
  region: "강남구",
  ageGroup: "80대",
  gender: "FEMALE",
  reason: "첫 번째 활동 추천 이유입니다.",
  distanceKm: 1.2,
});

const candidate2 = buildCandidate({
  activityId: 2,
  region: "송파구",
  ageGroup: "70대",
  gender: "MALE",
  reason: "두 번째 활동 추천 이유입니다.",
  distanceKm: 2.4,
});

/**
 * /volunteer 화면에 USER 권한으로 들어가기 위한 공통 Mock입니다.
 *
 * 실제 Backend를 호출하지 않고:
 * - sessionStorage에 테스트용 Access Token 저장
 * - /api/auth/me에서 USER 반환
 * - 최초 "내가 직접 선택" 탭의 활동 목록은 빈 목록 반환
 */
async function prepareVolunteerPage(page) {
  await page.addInitScript(() => {
    sessionStorage.setItem("accessToken", "playwright-test-token");
  });

  await page.route("**/api/auth/me", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        successResponse({
          userId: 100,
          email: "volunteer@test.com",
          name: "테스트 사용자",
          nickname: "테스터",
          role: "USER",
        }),
      ),
    });
  });

  await page.route("**/api/activities?*", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successResponse(emptyPage)),
    });
  });
}

/**
 * 배정 받기 탭으로 이동합니다.
 */
async function openAutoMatchTab(page) {
  await page.goto("/volunteer");

  await page
    .getByRole("button", {
      name: "배정 받기",
    })
    .click();
}

// ------------------------------------------------------------------
// Batch A - 같은 batch 안에서는 서버 재호출 없이 다음 후보 표시
// ------------------------------------------------------------------

test("AI 자동배정 - 같은 batch의 다른 활동 보기는 서버를 다시 호출하지 않는다", async ({
  page,
}) => {
  await prepareVolunteerPage(page);

  let batchRequestCount = 0;

  await page.route(
    "**/api/activity-applications/auto-match/candidates**",
    async (route) => {
      batchRequestCount += 1;

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(successResponse([candidate1, candidate2])),
      });
    },
  );

  await openAutoMatchTab(page);

  await expect(
    page.getByText("추천 이유: 첫 번째 활동 추천 이유입니다."),
  ).toBeVisible();

  expect(batchRequestCount).toBe(1);

  await page
    .getByRole("button", {
      name: "다른 활동 보기",
    })
    .click();

  await expect(
    page.getByText("추천 이유: 두 번째 활동 추천 이유입니다."),
  ).toBeVisible();

  // 같은 batch의 두 번째 후보이므로
  // 새로운 후보 API 요청이 발생하면 안 됩니다.
  expect(batchRequestCount).toBe(1);
});

// ------------------------------------------------------------------
// sessionStorage - 새로고침 후 현재 후보 복원
// ------------------------------------------------------------------

test("AI 자동배정 - 새로고침 후에도 현재 보던 후보를 이어서 보여준다", async ({
  page,
}) => {
  await prepareVolunteerPage(page);

  let batchRequestCount = 0;

  await page.route(
    "**/api/activity-applications/auto-match/candidates**",
    async (route) => {
      batchRequestCount += 1;

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(successResponse([candidate1, candidate2])),
      });
    },
  );

  await openAutoMatchTab(page);

  await page
    .getByRole("button", {
      name: "다른 활동 보기",
    })
    .click();

  await expect(
    page.getByText("추천 이유: 두 번째 활동 추천 이유입니다."),
  ).toBeVisible();

  expect(batchRequestCount).toBe(1);

  // 새로고침하면 activeTab은 direct로 초기화되지만,
  // AI batch/index/seen IDs는 sessionStorage에 남습니다.
  await page.reload();

  await page
    .getByRole("button", {
      name: "배정 받기",
    })
    .click();

  // 새 batch를 받지 않고 기존 두 번째 후보가 복원되어야 합니다.
  await expect(
    page.getByText("추천 이유: 두 번째 활동 추천 이유입니다."),
  ).toBeVisible();

  expect(batchRequestCount).toBe(1);

  const storedIndex = await page.evaluate(
    (key) => sessionStorage.getItem(key),
    AUTO_MATCH_INDEX_KEY,
  );

  expect(storedIndex).toBe("1");
});

// ------------------------------------------------------------------
// Batch 소진 - 소진된 후보가 새로고침 후 다시 살아나면 안 됨
// ------------------------------------------------------------------

test("AI 자동배정 - batch를 모두 본 뒤에는 이전 후보가 새로고침으로 복원되지 않는다", async ({
  page,
}) => {
  await prepareVolunteerPage(page);

  let batchRequestCount = 0;

  await page.route(
    "**/api/activity-applications/auto-match/candidates**",
    async (route) => {
      batchRequestCount += 1;

      const candidates =
        batchRequestCount === 1 ? [candidate1, candidate2] : [];

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(successResponse(candidates)),
      });
    },
  );

  await openAutoMatchTab(page);

  // 1번 → 2번 후보
  await page
    .getByRole("button", {
      name: "다른 활동 보기",
    })
    .click();

  await expect(
    page.getByText("추천 이유: 두 번째 활동 추천 이유입니다."),
  ).toBeVisible();

  // 2번 후보 이후에는 현재 batch가 소진됩니다.
  await page
    .getByRole("button", {
      name: "다른 활동 보기",
    })
    .click();

  await expect(
    page.getByText("더 이상 추천할 활동이 없습니다. 처음부터 다시 볼까요?"),
  ).toBeVisible();

  expect(batchRequestCount).toBe(2);

  // 우리가 수동 테스트에서 잡았던 버그를 회귀 테스트로 고정합니다.
  // 소진된 후보 배열이 sessionStorage에 남아 있으면 안 됩니다.
  await expect
    .poll(() =>
      page.evaluate(
        (key) => sessionStorage.getItem(key),
        AUTO_MATCH_CANDIDATES_KEY,
      ),
    )
    .toBe("[]");

  await page.reload();

  await page
    .getByRole("button", {
      name: "배정 받기",
    })
    .click();

  await expect(
    page.getByText("더 이상 추천할 활동이 없습니다. 처음부터 다시 볼까요?"),
  ).toBeVisible();

  // 예전에 보던 두 번째 후보가 다시 나타나면 안 됩니다.
  await expect(
    page.getByText("추천 이유: 두 번째 활동 추천 이유입니다."),
  ).toHaveCount(0);
});

// ------------------------------------------------------------------
// AI fallback - reason=null이어도 후보 화면은 정상 표시
// ------------------------------------------------------------------

test.describe("REQ-AI-17 AI 매칭 실패 fallback", () => {
  test("fallback 후보는 추천 이유 없이 정상 표시된다", async ({ page }) => {
    await prepareVolunteerPage(page);

    const fallbackCandidate = {
      ...candidate1,
      reason: null,
      model: null,
    };

    await page.route(
      "**/api/activity-applications/auto-match/candidates**",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(successResponse([fallbackCandidate])),
        });
      },
    );

    await openAutoMatchTab(page);

    await expect(page.getByText(/강남구 · 80대/)).toBeVisible();

    // fallback에서는 추천 이유 영역 자체가 없어야 합니다.
    await expect(page.getByText(/추천 이유:/)).toHaveCount(0);

    await expect(
      page.getByRole("button", {
        name: "신청하기",
      }),
    ).toBeVisible();
  });
});

// ------------------------------------------------------------------
// 신청 성공 - 추천 세션 초기화 + 내 신청 현황 이동
// ------------------------------------------------------------------

test.describe("REQ-ACT-18 자동배정 후보 신청 확정", () => {
  test("신청 성공 후 추천 세션을 초기화하고 내 신청 현황으로 이동한다", async ({
    page,
  }) => {
    await prepareVolunteerPage(page);

    let applyRequestBody = null;

    await page.route(
      "**/api/activity-applications/auto-match/candidates**",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(successResponse([candidate1])),
        });
      },
    );

    await page.route(
      "**/api/activity-applications/auto-match",
      async (route) => {
        if (route.request().method() !== "POST") {
          await route.continue();
          return;
        }

        applyRequestBody = route.request().postDataJSON();

        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(
            successResponse({
              applicationId: 900,
              activityId: 1,
              applicationType: "AUTO",
              status: "PENDING",
            }),
          ),
        });
      },
    );

    await page.route(
      "**/api/users/me/activity-applications**",
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(
            successResponse({
              content: [
                {
                  applicationId: 900,
                  activityId: 1,
                  status: "PENDING",
                  applicationType: "AUTO",
                  activityStatus: "RECRUITING",
                  scheduledAt: "2026-10-01T10:00:00",
                  region: "강남구",
                  ageGroup: "80대",
                  gender: "FEMALE",
                },
              ],
              page: 0,
              size: 10,
              totalElements: 1,
              totalPages: 1,
            }),
          ),
        });
      },
    );

    await openAutoMatchTab(page);

    await expect(
      page.getByText("추천 이유: 첫 번째 활동 추천 이유입니다."),
    ).toBeVisible();

    await page
      .getByRole("button", {
        name: "신청하기",
      })
      .click();

    await expect(
      page.getByText("신청이 완료되었습니다. 기관 승인을 기다려주세요."),
    ).toBeVisible();

    const myApplicationsTab = page.getByRole("button", {
      name: "내 신청 현황",
    });

    await expect(myApplicationsTab).toHaveClass(/active/);

    // 내 신청 현황의 실제 신청 카드에 PENDING 상태가 표시되어야 합니다.
    await expect(
      page.locator(".visit").getByText("대기중", {
        exact: true,
      }),
    ).toBeVisible();

    expect(applyRequestBody).toEqual({
      activityId: 1,
    });

    // 신청 성공 시 이번 추천 session을 모두 초기화해야 합니다.
    await expect
      .poll(() =>
        page.evaluate(
          (key) => sessionStorage.getItem(key),
          AUTO_MATCH_CANDIDATES_KEY,
        ),
      )
      .toBe("[]");

    await expect
      .poll(() =>
        page.evaluate(
          (key) => sessionStorage.getItem(key),
          AUTO_MATCH_INDEX_KEY,
        ),
      )
      .toBe("0");

    await expect
      .poll(() =>
        page.evaluate(
          (key) => sessionStorage.getItem(key),
          AUTO_MATCH_SEEN_IDS_KEY,
        ),
      )
      .toBe("[]");
  });
});

// ------------------------------------------------------------------
// 로그아웃 - 이전 사용자의 자동배정 sessionStorage 정리
// ------------------------------------------------------------------

test.describe("REQ-AUTH-04 로그아웃", () => {
  test("로그아웃하면 이전 사용자의 추천 sessionStorage를 모두 제거한다", async ({
    page,
  }) => {
    await prepareVolunteerPage(page);

    await page.addInitScript(
      ({ candidatesKey, indexKey, seenIdsKey }) => {
        sessionStorage.setItem(
          candidatesKey,
          JSON.stringify([
            {
              activityId: 1,
              region: "강남구",
            },
          ]),
        );

        sessionStorage.setItem(indexKey, "0");

        sessionStorage.setItem(seenIdsKey, JSON.stringify([1]));
      },
      {
        candidatesKey: AUTO_MATCH_CANDIDATES_KEY,
        indexKey: AUTO_MATCH_INDEX_KEY,
        seenIdsKey: AUTO_MATCH_SEEN_IDS_KEY,
      },
    );

    await page.goto("/volunteer");

    await page
      .getByRole("button", {
        name: "로그아웃",
      })
      .click();

    await expect(page).toHaveURL(/\/login/);

    const sessionValues = await page.evaluate(
      ({ candidatesKey, indexKey, seenIdsKey }) => ({
        accessToken: sessionStorage.getItem("accessToken"),
        candidates: sessionStorage.getItem(candidatesKey),
        index: sessionStorage.getItem(indexKey),
        seenIds: sessionStorage.getItem(seenIdsKey),
      }),
      {
        candidatesKey: AUTO_MATCH_CANDIDATES_KEY,
        indexKey: AUTO_MATCH_INDEX_KEY,
        seenIdsKey: AUTO_MATCH_SEEN_IDS_KEY,
      },
    );

    expect(sessionValues.accessToken).toBeNull();

    expect(sessionValues.candidates).toBeNull();

    expect(sessionValues.index).toBeNull();

    expect(sessionValues.seenIds).toBeNull();
  });
});
