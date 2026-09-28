import { expect, test } from "@playwright/test";

test("비로그인 사용자가 제보 화면으로 이동한 뒤 홈으로 돌아올 수 있다", async ({
  page,
}) => {
  // given
  // playwright.config.js의 baseURL을 사용하므로
  // 실제 주소를 반복해서 작성하지 않고 "/"로 접속합니다.
  await page.goto("/");

  // 랜딩 페이지가 정상적으로 표시됐는지 먼저 확인합니다.
  await expect(
    page.getByRole("link", { name: "로그인 없이 제보하기 →" }),
  ).toBeVisible();

  // when
  // Codegen이 생성한 role 기반 locator를 사용해
  // 비로그인 제보 화면으로 이동합니다.
  await page
    .getByRole("link", { name: "로그인 없이 제보하기 →" })
    .click();

  // then
  // 단순히 클릭만 성공한 것이 아니라
  // 실제로 랜딩 페이지("/")를 벗어났는지 검증합니다.
  await expect(page).not.toHaveURL("http://127.0.0.1:3000/");

  // when
  await page.getByRole("link", { name: "홈", exact: true }).click();

  // then
  // 홈 링크를 누른 뒤 다시 랜딩 페이지로 돌아왔는지 검증합니다.
  await expect(page).toHaveURL("http://127.0.0.1:3000/");
});