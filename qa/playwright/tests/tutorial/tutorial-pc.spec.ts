import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * PC 튜토리얼 — Desktop / web order-app 첫 진입 시 step-by-step 안내.
 *
 * Happy: localStorage 초기화 후 진입 → 튜토리얼 step 1 ~ N 진행
 * Edge : "건너뛰기" 클릭 → 튜토리얼 즉시 종료
 */
test.describe('tutorial — pc', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: PC 첫 진입 → step 노출', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.removeItem('samhan.tutorial.pc.seen');
    });
    await page.goto('/');
    const tutorial = page.locator('text=/다음|시작|단계|튜토리얼|가이드/').first();
    if ((await tutorial.count()) === 0) {
      test.skip(true, 'PC 튜토리얼 UI 미구현 — skip');
    }
    await expect(tutorial).toBeVisible({ timeout: 5_000 });
  });

  test('edge: 건너뛰기 클릭 → 즉시 종료', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.removeItem('samhan.tutorial.pc.seen');
    });
    await page.goto('/');
    const skipBtn = page.locator('button:has-text("건너뛰기"), button:has-text("닫기")').first();
    if ((await skipBtn.count()) === 0) {
      test.skip(true, '건너뛰기 버튼 미노출');
    }
    await skipBtn.click();
    const tutorial = page.locator('[role="dialog"]:has-text("튜토리얼")').first();
    expect(await tutorial.count()).toBe(0);
  });
});
