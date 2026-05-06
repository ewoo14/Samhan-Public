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
    // Phase 7 5/6차 정정 — 폰트 로드 race 방지 가드 (self-host 적용 후에도 woff2 fetch 비동기).
    await page.evaluate(() => document.fonts.ready);
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-after-bizgate.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
