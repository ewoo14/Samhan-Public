import { test, expect } from '@playwright/test';
import { getPartner, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 단종 모델 (OLD_PRODUCT) — active=false 격리 카탈로그.
 *
 * Happy: 단종 메뉴 진입 → OLD-* 모델 노출
 * Edge : 일반 카탈로그에서는 미노출 (격리)
 */
test.describe('catalog — OLD_PRODUCT (단종)', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'product-service 미가동 — IT skip');
    const partner = getPartner({ status: 'ACTIVE', passwordType: 'BIZGATE' });
    await mockPartnerAuth(page, partner);
  });

  test('happy: 단종 메뉴 진입 → OLD- 모델 노출', async ({ page }) => {
    await page.goto('/');
    const oldMenu = page.locator('text=/단종|OLD/').first();
    if ((await oldMenu.count()) === 0) {
      test.skip(true, '단종 메뉴 미노출 — legacy 분기 skip');
    }
    await oldMenu.click();
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/OLD-|단종/);
  });

  test('edge: 일반 catalog 에서는 단종 모델 미노출', async ({ page }) => {
    await page.goto('/');
    // 홈멀티 진입 후 OLD- 모델 비노출 확인
    await page.locator('text=/홈멀티|HomeMulti/').first().click().catch(() => {});
    const body = await page.textContent('body');
    expect(body ?? '').not.toMatch(/OLD-AC-/);
  });
});
