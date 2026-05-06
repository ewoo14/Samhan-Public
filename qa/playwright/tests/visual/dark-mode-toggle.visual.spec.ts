import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * Visual regression — 다크모드 toggle 전후.
 *
 * 검증: prefers-color-scheme 또는 toggle button 으로 dark theme 전환 시
 *      배경/텍스트 색상이 baseline 과 일치.
 */
test.describe('visual — dark mode toggle', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — IT skip');
    await mockPartnerAuth(page, Partners.activeBizgate());
  });

  test('light → dark 전환 snapshot', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-light.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });

    await page.emulateMedia({ colorScheme: 'dark' });
    await page.reload();
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-dark.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
