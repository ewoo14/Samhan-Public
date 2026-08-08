import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  testMatch: '**/*-real-qa.spec.ts',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  workers: 1,
  retries: 0,
  reporter: 'line',
  use: {
    baseURL: process.env['AUDIT_BASE_URL'] ?? 'http://127.0.0.1:5825',
    viewport: { width: 1600, height: 1000 },
    headless: true,
    screenshot: 'off',
    video: 'off',
    trace: 'off',
    ...devices['Desktop Chrome'],
  },
})
