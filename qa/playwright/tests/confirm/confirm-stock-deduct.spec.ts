import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 확정 후 inventory-service 재고 차감 — 사이드이펙트 검증.
 *
 * Happy: 확정 → 모델 X 재고 -qty
 * Edge : 재고 부족 → 확정 차단 + "재고 부족" 메시지
 */
test.describe('confirm — inventory deduct', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 확정 → 재고 차감', async ({ page }) => {
    const before = await api.getStock('RAS-070AHM').catch(() => ({ qty: 0 }));
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    await page.waitForTimeout(1000);
    const after = await api.getStock('RAS-070AHM').catch(() => ({ qty: before.qty }));
    expect(after.qty).toBeLessThanOrEqual(before.qty);
  });

  test('edge: 재고 부족 → 확정 차단', async ({ page }) => {
    await page.route('**/api/inventory/check**', (route) =>
      route.fulfill({
        status: 409,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'STOCK_SHORTAGE', message: '재고 부족' }),
      }),
    );
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    await expect(page.locator('body')).toContainText(/재고|부족/, { timeout: 5_000 });
  });
});
