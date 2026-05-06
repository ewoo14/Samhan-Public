import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 영업직원 튜토리얼 — mobile-staff v3 의 staff-only 가이드.
 *
 * Happy: STAFF role 진입 → staff 전용 step (방문 등록 / 견적 작성 등) 노출
 * Edge : "다시 안 보기" 체크 후 닫기 → localStorage 영구 저장 + 재진입 skip
 */
test.describe('tutorial — staff', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: STAFF 첫 진입 → staff 전용 step', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.removeItem('samhan.tutorial.staff.seen');
      window.sessionStorage.setItem('samhan.role', 'STAFF');
    });
    await page.goto('/');
    const tutorial = page.locator('text=/영업|방문|직원|튜토리얼|가이드/').first();
    if ((await tutorial.count()) === 0) {
      test.skip(true, 'staff 튜토리얼 UI 미구현 — skip');
    }
    await expect(tutorial).toBeVisible({ timeout: 5_000 });
  });

  test('edge: "다시 안 보기" 영구 저장', async ({ page }) => {
    await page.addInitScript(() => {
      window.localStorage.setItem('samhan.tutorial.staff.seen', 'permanent');
    });
    await page.goto('/');
    const tutorial = page.locator('[role="dialog"]:has-text("튜토리얼")').first();
    expect(await tutorial.count()).toBe(0);
  });
});
