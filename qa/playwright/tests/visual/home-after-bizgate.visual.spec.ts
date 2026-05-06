import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * Visual regression — BizGate SSO 통과 후 Home 첫 진입.
 *
 * mockPartnerAuth 로 sessionStorage 주입 → 게이트 skip → Home 노출.
 */
test.describe('visual — home after BizGate', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — IT skip');
    await mockPartnerAuth(page, Partners.activeBizgate());
  });

  test('Home 진입 snapshot', async ({ page }) => {
    await page.goto('/');
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-after-bizgate.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
