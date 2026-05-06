import { test, expect } from '@playwright/test';
import { isBackendAvailable } from '../../fixtures/auth';

/**
 * Visual regression — 영업직원 견적서 form (estimate-app v2 첫 화면).
 *
 * baseURL = QA_ESTIMATE_APP_URL (기본 http://localhost:5183).
 */
test.describe('visual — estimate form (영업직원)', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — IT skip');
  });

  test('견적 form 첫 화면 snapshot', async ({ page }) => {
    await page.goto('/');
    // estimate-app v2 의 메인 form 컨테이너 (page-level snapshot)
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('estimate-form.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
      fullPage: false,
    });
  });
});
