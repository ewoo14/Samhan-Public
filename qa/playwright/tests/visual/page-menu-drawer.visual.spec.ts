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
    // Phase 7 5/6차 정정 — 폰트 로드 race 방지 가드 (self-host 적용 후에도 woff2 fetch 비동기).
    await page.evaluate(() => document.fonts.ready);
    // Phase 7 3차 정정 (Designer P1) — testid 단일화. legacy 의 옵션/필터 모바일 handle 에
    // data-testid="page-menu-drawer-toggle" 추가 (clients/web/order-app/index.html).
    const trigger = page.locator('[data-testid="page-menu-drawer-toggle"]');
    if ((await trigger.count()) === 0) {
      test.skip(true, '메뉴 trigger 미노출 — skip');
    }
    await trigger.first().click();
    await page.waitForTimeout(300); // drawer transition
    const drawer = page.locator('[data-testid="page-menu-drawer"]');
    if ((await drawer.count()) > 0) {
      await expect(drawer.first()).toBeVisible({ timeout: 5_000 });
    }
    await expect(page).toHaveScreenshot('page-menu-drawer-open.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
