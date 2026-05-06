import { defineConfig, devices } from '@playwright/test';

/**
 * SamhanLogis Phase 7 QA — Playwright config
 *
 * 5 project:
 *  - web-order-app      : Vite dev server, port 5184, 거래처 주문서 v4
 *  - web-estimate-app   : Express EJS, port 5183, 종합견적서 v2
 *  - electron-desktop   : electron-vite build, packaged binary 또는 dev
 *  - mobile-chrome      : Pixel 7 viewport, mobile-staff WebView 시나리오
 *  - mobile-safari      : iPhone 14 viewport, mobile-staff WebView 시나리오
 *
 * 환경 변수:
 *  - QA_ORDER_APP_URL    (기본 http://localhost:5184)
 *  - QA_ESTIMATE_APP_URL (기본 http://localhost:5183)
 *  - QA_API_BASE_URL     (기본 http://localhost:8080)
 *  - QA_ELECTRON_PATH    (electron 실행 파일 경로, 미설정 시 electron project skip)
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI
    ? [['html', { open: 'never' }], ['list'], ['junit', { outputFile: 'test-results/junit.xml' }]]
    : [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: {
    timeout: 5_000,
  },
  use: {
    actionTimeout: 10_000,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ignoreHTTPSErrors: true,
  },

  projects: [
    {
      name: 'web-order-app',
      testMatch: /.*\/(auth|catalog|draft|confirm|history|tutorial)\/.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.QA_ORDER_APP_URL ?? 'http://localhost:5184',
      },
    },
    {
      name: 'web-estimate-app',
      testMatch: /.*\/(auth|catalog|draft|confirm|history)\/.*\.spec\.ts/,
      use: {
        ...devices['Desktop Chrome'],
        baseURL: process.env.QA_ESTIMATE_APP_URL ?? 'http://localhost:5183',
      },
    },
    {
      name: 'electron-desktop',
      testMatch: /.*\/(auth|catalog|confirm)\/.*\.spec\.ts/,
      use: {
        baseURL: process.env.QA_ORDER_APP_URL ?? 'http://localhost:5184',
      },
    },
    {
      name: 'mobile-chrome',
      testMatch: /.*\/(auth|catalog|draft|confirm)\/.*\.spec\.ts/,
      use: {
        ...devices['Pixel 7'],
        baseURL: process.env.QA_ORDER_APP_URL ?? 'http://localhost:5184',
      },
    },
    {
      name: 'mobile-safari',
      testMatch: /.*\/(auth|catalog|draft|confirm)\/.*\.spec\.ts/,
      use: {
        ...devices['iPhone 14'],
        baseURL: process.env.QA_ORDER_APP_URL ?? 'http://localhost:5184',
      },
    },
  ],

  outputDir: 'test-results/',
});
