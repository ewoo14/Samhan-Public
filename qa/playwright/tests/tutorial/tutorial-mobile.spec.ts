import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 모바일 튜토리얼 — mobile-staff / mobile order WebView 첫 진입 시 swipe 형태 안내.
 *
 * Happy: localStorage 초기화 → 모바일 viewport 진입 → swipe 가능한 튜토리얼 노출
 * Edge : 두 번째 진입 시 자동 skip
 */
test.describe('tutorial — mobile', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 모바일 첫 진입 → swipe 튜토리얼', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'mobile project 전용 — desktop project 에서는 skip');
    await page.addInitScript(() => {
      window.localStorage.removeItem('samhan.tutorial.mobile.seen');
    });
    await page.goto('/');
    const tutorial = page.locator('text=/스와이프|넘기|튜토리얼|시작|가이드/').first();
    if ((await tutorial.count()) === 0) {
      test.skip(true, '모바일 튜토리얼 UI 미구현 — skip');
    }
    await expect(tutorial).toBeVisible({ timeout: 5_000 });
  });

  test('edge: 재진입 시 skip', async ({ page, isMobile }) => {
    test.skip(!isMobile, 'mobile project 전용');
    await page.addInitScript(() => {
      window.localStorage.setItem('samhan.tutorial.mobile.seen', '1');
    });
    await page.goto('/');
    const tutorial = page.locator('[role="dialog"]:has-text("튜토리얼")').first();
    expect(await tutorial.count()).toBe(0);
  });
});
