/**
 * E2 기둥1 라이브 컬렉션 동기화 — 2세션 실서버 GUI QA 전용 Playwright 설정 (#699 owed backfill).
 * VITE_MOCK_MODE OFF — 실 게이트웨이 :8080 연결. 렌더러 :5175 선기동 필요
 * (`VITE_API_BASE_URL=http://localhost:8080 node_modules/.bin/vite dev --config vite.renderer.dev.config.ts`).
 */
import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  timeout: 180_000,
  retries: 0,
  workers: 1,
  reporter: [['line']],
  use: {
    baseURL: process.env['AUDIT_BASE_URL'] ?? 'http://127.0.0.1:5175',
    viewport: { width: 1440, height: 900 },
    screenshot: 'on',
    video: 'off',
    headless: true,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
