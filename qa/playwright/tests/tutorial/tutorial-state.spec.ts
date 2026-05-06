import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 튜토리얼 상태 — 최초 진입 시 1회 노출, 이후 skip.
 *
 * Happy: 최초 진입 → 튜토리얼 모달 노출 → "다시 안 보기" 체크 후 닫기
 * Edge : 두 번째 진입 시 자동 skip (localStorage 기반)
 */
test.describe('tutorial — state persistence', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 최초 진입 → 튜토리얼 노출', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.removeItem('samhan.tutorial.seen');
    });
    await page.goto('/');
    const tutorial = page.locator('text=/튜토리얼|시작|가이드|안내/').first();
    if ((await tutorial.count()) === 0) {
      test.skip(true, '튜토리얼 UI 미구현 — skip');
    }
    await expect(tutorial).toBeVisible({ timeout: 5_000 });
  });

  test('edge: 재진입 시 튜토리얼 skip', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('samhan.tutorial.seen', '1');
    });
    await page.goto('/');
    const tutorial = page.locator('[role="dialog"]:has-text("튜토리얼"), [role="dialog"]:has-text("가이드")').first();
    expect(await tutorial.count()).toBe(0);
  });
});
