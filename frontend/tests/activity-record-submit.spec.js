import { expect, test } from "@playwright/test";

const RECORD_ID = 24;

const successResponse = (data) => ({
  success: true,
  code: "SUCCESS",
  message: null,
  data,
});

const draftRecord = {
  recordId: RECORD_ID,
  activityId: 10,
  checklistVersion: 1,

  visitResult: "MET",
  startedAt: "2026-09-22T14:00:00",
  completedAt: "2026-09-22T15:30:00",
  specialNote: "특이사항 없음",

  updatedAt: "2026-09-22T15:30:00",

  responses: [
    {
      checklistItemId: 1,
      question: "식사는 잘 하셨나요?",
      selectedValue: "YES",
      textValue: null,
    },
    {
      checklistItemId: 2,
      question: "건강 상태는 괜찮으신가요?",
      selectedValue: "YES",
      textValue: null,
    },
    {
      checklistItemId: 3,
      question: "추가 지원이 필요한가요?",
      selectedValue: "NO",
      textValue: null,
    },
  ],

  signatureUploaded: true,

  reviewStatus: "DRAFT",
  reviewNote: null,
};

const submittedRecord = {
  ...draftRecord,

  reviewStatus: "SUBMITTED",
  updatedAt: "2026-09-22T15:31:00",
};

const checklist = {
  checklistVersion: 1,

  items: [
    {
      id: 1,
      code: "MEAL_STATUS",
      question: "식사는 잘 하셨나요?",
      itemType: "SINGLE_CHOICE",
      options: ["YES", "NO", "UNKNOWN"],
      required: true,
      sortOrder: 1,
      selectedValue: "YES",
      textValue: null,
    },
    {
      id: 2,
      code: "HEALTH_CONDITION",
      question: "건강 상태는 괜찮으신가요?",
      itemType: "SINGLE_CHOICE",
      options: ["YES", "NO", "UNKNOWN"],
      required: true,
      sortOrder: 2,
      selectedValue: "YES",
      textValue: null,
    },
    {
      id: 3,
      code: "SUPPORT_NEEDED",
      question: "추가 지원이 필요한가요?",
      itemType: "SINGLE_CHOICE",
      options: ["YES", "NO"],
      required: true,
      sortOrder: 3,
      selectedValue: "NO",
      textValue: null,
    },
  ],
};

async function prepareActivityRecordPage(page) {
  await page.addInitScript(() => {
    sessionStorage.setItem(
      "accessToken",
      "playwright-test-token",
    );
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

  await page.route(
    `**/api/activity-records/${RECORD_ID}/checklist`,
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          successResponse(checklist),
        ),
      });
    },
  );

  await page.route(
    `**/api/activity-records/${RECORD_ID}`,
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          successResponse(draftRecord),
        ),
      });
    },
  );
}

test.describe(
  "[RECORD-05] 활동기록 최종 제출",
  () => {
    test(
      "[REQ-REC-03][REQ-REC-09][REQ-REC-12][REQ-REC-15] Draft 저장 후 SUBMITTED로 전환한다",
      async ({ page }) => {
        await prepareActivityRecordPage(page);

        const requestOrder = [];

        let draftBody = null;
        let submitBody = null;

        await page.route(
          `**/api/activity-records/${RECORD_ID}/draft`,
          async (route) => {
            requestOrder.push("draft");

            const request = route.request();

            const rawBody = request.postData();

            draftBody = rawBody
              ? JSON.parse(rawBody)
              : null;

            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify(
                successResponse(draftRecord),
              ),
            });
          },
        );

        await page.route(
          `**/api/activity-records/${RECORD_ID}/submit`,
          async (route) => {
            requestOrder.push("submit");

            submitBody =
              route.request().postData();

            await route.fulfill({
              status: 200,
              contentType: "application/json",
              body: JSON.stringify(
                successResponse(submittedRecord),
              ),
            });
          },
        );

        await page.goto(
          `/activity-records/${RECORD_ID}`,
        );

        await expect(
          page.getByRole("heading", {
            name: "활동기록 작성",
          }),
        ).toBeVisible();

        await expect(
          page.getByText(
            "✓ 서명이 등록되어 있습니다.",
          ),
        ).toBeVisible();

        const submitButton =
          page.getByRole("button", {
            name: "최종 제출",
          });

        await expect(
          submitButton,
        ).toBeEnabled();

        page.once(
          "dialog",
          async (dialog) => {
            expect(dialog.message()).toBe(
              "활동기록을 최종 제출하시겠습니까?",
            );

            await dialog.accept();
          },
        );

        await submitButton.click();

        await expect
          .poll(() => requestOrder)
          .toEqual([
            "draft",
            "submit",
          ]);

        expect(draftBody).toEqual({
          visitResult: "MET",

          completedAt:
            "2026-09-22T15:30:00",

          specialNote:
            "특이사항 없음",

          responses: [
            {
              itemId: 1,
              selectedValue: "YES",
              textValue: null,
            },
            {
              itemId: 2,
              selectedValue: "YES",
              textValue: null,
            },
            {
              itemId: 3,
              selectedValue: "NO",
              textValue: null,
            },
          ],
        });

        // RECORD-05 Submit은 Request Body를 사용하지 않습니다.
        expect(submitBody).toBeNull();

        await expect(
          page.getByText(
            "제출 완료",
          ).first(),
        ).toBeVisible();

        await expect(
          page.getByText(
            "제출된 활동기록은 조회만 할 수 있습니다.",
          ),
        ).toBeVisible();

        await expect(
          page.getByRole("button", {
            name: "최종 제출",
          }),
        ).toHaveCount(0);
      },
    );
  },
);