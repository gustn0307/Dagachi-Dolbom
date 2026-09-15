import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  // Playwright가 테스트 파일을 찾을 폴더입니다.
  testDir: "./tests",

  // 하나의 테스트가 무한정 멈춰 있지 않도록 최대 실행 시간을 제한합니다.
  timeout: 30_000,

  // 테스트 실패 시 CI와 로컬에서 원인을 확인하기 쉽도록
  // HTML 형식의 테스트 결과 보고서를 생성합니다.
  reporter: "html",

  use: {
    // 각 테스트에서 page.goto("/")처럼 상대 경로를 사용할 수 있도록
    // Vite 개발 서버 주소를 공통 기준 URL로 지정합니다.
    baseURL: "http://127.0.0.1:3000",

    // 테스트가 실패했을 때만 trace를 남깁니다.
    // 이후 화면 이동, 클릭, 네트워크 흐름 등을 추적할 수 있습니다.
    trace: "on-first-retry",
  },

  // 우선 Chromium 하나만 공통 테스트 브라우저로 사용합니다.
  // 필요하면 나중에 Firefox / WebKit을 추가할 수 있습니다.
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
      },
    },
  ],

  // Playwright 테스트 실행 전에 Vite 개발 서버를 자동으로 실행합니다.
  webServer: {
    command: "npm run dev -- --host 127.0.0.1",
    url: "http://127.0.0.1:3000",

    // 로컬에서 이미 Vite 서버를 띄워둔 경우에는
    // 새 서버를 중복 실행하지 않고 기존 서버를 재사용합니다.
    reuseExistingServer: true,

    // Vite 서버가 올라올 때까지 기다리는 최대 시간입니다.
    timeout: 120_000,
  },
});