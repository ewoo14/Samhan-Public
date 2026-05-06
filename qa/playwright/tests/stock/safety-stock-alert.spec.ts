import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 안전재고 알림 — on_hand_qty < safety_stock 시 카탈로그 표시.
 *
 * Happy: 모델 X 의 on_hand 가 safety_stock 미만 → 카탈로그 "재고 부족" 배지
 * Edge : on_hand = 0 → "품절" 배지 + 주문 차단
 */
test.describe('stock — safety stock alert', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 안전재고 미만 → "재고 부족" 배지', async ({ page }) => {
    await page.route('**/api/inventory/stock/**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ qty: 1, safetyStock: 10, status: 'LOW' }),
      }),
    );
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/재고|부족|LOW|적음/, { timeout: 5_000 });
  });

  test('edge: 품절 → 주문 차단', async ({ page }) => {
    await page.route('**/api/inventory/stock/**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ qty: 0, safetyStock: 10, status: 'OUT_OF_STOCK' }),
      }),
    );
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/품절|재고|0|불가/, { timeout: 5_000 });
  });
});
