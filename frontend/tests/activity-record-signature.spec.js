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
  activityRecordId: RECORD_ID,
  activityId: 10,
  reviewStatus: "DRAFT",
  checklistVersion: 1,
  startedAt: "2026-09-22T14:00:00",
  visitResult: "MET",
  completedAt: "2026-09-22T15:30:00",
  specialNote: "특이사항 없음",
  signatureUploaded: false,
  signedAt: null,
  reviewNote: null,
  updatedAt: "2026-09-22T15:30:00",
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
  ],
};

async function prepareActivityRecordPage(page) {
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

  await page.route(
    `**/api/activity-records/${RECORD_ID}/checklist`,
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(successResponse(checklist)),
      });
    },
  );

  await page.route(`**/api/activity-records/${RECORD_ID}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(successResponse(draftRecord)),
    });
  });
}

test.describe("[REQ-REC-12][REQ-REC-13] 활동기록 서명", () => {
  test("Canvas 서명 등록 시 Draft 객체 저장 후 PNG multipart 서명을 업로드한다", async ({
    page,
  }) => {
    await prepareActivityRecordPage(page);

    const requestOrder = [];
    let draftBody = null;
    let signatureContentType = "";
    let signatureMultipartBody = "";

    await page.route(
      `**/api/activity-records/${RECORD_ID}/draft`,
      async (route) => {
        requestOrder.push("draft");

        const request = route.request();
        const rawBody = request.postData();

        draftBody = rawBody ? JSON.parse(rawBody) : null;

        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(successResponse(draftRecord)),
        });
      },
    );

    await page.route(
      `**/api/activity-records/${RECORD_ID}/signature`,
      async (route) => {
        requestOrder.push("signature");

        const request = route.request();

        signatureContentType = request.headers()["content-type"] ?? "";

        const buffer = request.postDataBuffer();

        signatureMultipartBody = buffer ? buffer.toString("latin1") : "";

        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(
            successResponse({
              signatureUploaded: true,
              signedAt: "2026-09-22T15:31:00",
            }),
          ),
        });
      },
    );

    await page.goto(`/activity-records/${RECORD_ID}`);

    await expect(
      page.getByRole("heading", {
        name: "활동기록 작성",
      }),
    ).toBeVisible();

    await expect(
      page.getByText("아직 서명이 등록되지 않았습니다."),
    ).toBeVisible();

    const canvas = page.locator(".activity-record-signature-canvas");

    await expect(canvas).toBeVisible();

    await canvas.evaluate((element) => {
      const rect = element.getBoundingClientRect();

      // 테스트에서 직접 발생시키는 PointerEvent는 브라우저의
      // 실제 pointer capture 상태가 없으므로 capture API만 대체합니다.
      element.setPointerCapture = () => {};
      element.hasPointerCapture = () => false;
      element.releasePointerCapture = () => {};

      const dispatchPointer = (type, x, y, button = 0, buttons = 1) => {
        element.dispatchEvent(
          new PointerEvent(type, {
            bubbles: true,
            cancelable: true,
            pointerId: 1,
            pointerType: "mouse",
            button,
            buttons,
            clientX: rect.left + x,
            clientY: rect.top + y,
          }),
        );
      };

      dispatchPointer("pointerdown", rect.width * 0.2, rect.height * 0.5);

      dispatchPointer("pointermove", rect.width * 0.45, rect.height * 0.3);

      dispatchPointer("pointermove", rect.width * 0.7, rect.height * 0.6);

      dispatchPointer("pointerup", rect.width * 0.7, rect.height * 0.6, 0, 0);
    });

    const registerButton = page.getByRole("button", {
      name: "서명 등록",
    });

    await expect(registerButton).toBeEnabled();

    await registerButton.click();

    await expect(page.getByText("서명이 등록되었습니다.")).toBeVisible();

    expect(requestOrder).toEqual(["draft", "signature"]);

    expect(draftBody).toEqual({
      visitResult: "MET",
      completedAt: "2026-09-22T15:30:00",
      specialNote: "특이사항 없음",
      responses: [
        {
          itemId: 1,
          selectedValue: "YES",
          textValue: null,
        },
      ],
    });

    expect(signatureContentType).toContain("multipart/form-data");

    expect(signatureMultipartBody).toContain('name="signature"');

    expect(signatureMultipartBody).toContain(
      `filename="signature-${RECORD_ID}.png"`,
    );

    expect(signatureMultipartBody).toContain("Content-Type: image/png");
  });
});
