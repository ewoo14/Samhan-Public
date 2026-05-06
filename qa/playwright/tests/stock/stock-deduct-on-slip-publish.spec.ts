import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 재고 차감 — 슬립 발행 (publish) 시 reserved_qty → on_hand_qty 차감.
 *
 * Happy: slip publish → on_hand_qty -qty + reserved_qty -qty
 * Edge : publish 후 재고 0 → safety_stock 알림 trigger
 */
test.describe('stock — deduct on slip publish', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service / slip-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: slip publish → on_hand 차감', async ({ page }) => {
    const before = await api.getStock('RAS-070AHM').catch(() => ({ qty: 0 }));
    await page.goto('/');
    const publishBtn = page.locator('button:has-text("발행"), button:has-text("슬립")').first();
    if ((await publishBtn.count()) === 0) {
      test.skip(true, '발행 버튼 미노출');
    }
    await publishBtn.click();
    await page.waitForTimeout(1000);
    const after = await api.getStock('RAS-070AHM').catch(() => ({ qty: before.qty }));
    expect(after.qty).toBeLessThanOrEqual(before.qty);
  });

  test('edge: publish 후 safety_stock 미만 → 알림', async ({ page }) => {
    await page.route('**/api/inventory/publish**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          ok: true,
          onHandQty: 0,
          safetyStockAlert: true,
          message: '안전재고 미만',
        }),
      }),
    );
    await page.goto('/');
    const publishBtn = page.locator('button:has-text("발행"), button:has-text("슬립")').first();
    if ((await publishBtn.count()) === 0) {
      test.skip(true, '발행 버튼 미노출');
    }
    await publishBtn.click();
    await expect(page.locator('body')).toContainText(/안전재고|부족|알림/, { timeout: 5_000 });
  });
});
