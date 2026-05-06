import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * Visual regression — 페이지 메뉴 drawer 전개.
 *
 * 검증: drawer 가 펼쳐졌을 때 시각적 일관성 (배경/색상/간격).
 */
test.describe('visual — page menu drawer', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — IT skip');
    await mockPartnerAuth(page, Partners.activeBizgate());
  });

  test('drawer 펼침 snapshot', async ({ page }) => {
    await page.goto('/');
    const trigger = page.locator('button:has-text("메뉴"), [aria-label*="menu" i], [data-testid="menu-toggle"]').first();
    if ((await trigger.count()) === 0) {
      test.skip(true, '메뉴 trigger 미노출 — skip');
    }
    await trigger.click();
    await page.waitForTimeout(300); // drawer transition
    await expect(page).toHaveScreenshot('page-menu-drawer-open.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
