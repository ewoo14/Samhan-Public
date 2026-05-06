import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 재고 이동 이력 — inventory movement (입고/출고/조정) timeline.
 *
 * Happy: 모델 X 의 movement 이력 → in/out/adjust 항목 노출
 * Edge : 본인 거래처 movement 만 (다른 거래처 movement 차단)
 */
test.describe('history — stock movement', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 본인 거래처 movement 노출', async ({ page }) => {
    await page.goto('/history/stock');
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/입고|출고|조정|이동|재고/);
    // UUID 비공개 가드
    expect(body ?? '').not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: 타 거래처 movement 직접 접근 차단', async ({ page }) => {
    await page.route('**/api/inventory/movement?partner=*', (route) => {
      const url = route.request().url();
      if (url.includes('partner=BIZ-OTHER')) {
        return route.fulfill({
          status: 403,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'FORBIDDEN' }),
        });
      }
      return route.continue();
    });
    await page.goto('/history/stock?partner=BIZ-OTHER');
    await expect(page.locator('body')).toContainText(/권한|불가|403|차단|없/, { timeout: 5_000 });
  });
});
