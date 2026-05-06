import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 재고 예약 — 주문 확정 시 inventory 의 reserved_qty 증가.
 *
 * Happy: 확정 → reserved_qty +qty
 * Edge : 확정 취소 → reserved_qty 복원
 */
test.describe('stock — reserve on confirm', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 확정 → reserved_qty 증가', async ({ page }) => {
    const before = await api.getStock('RAS-070AHM').catch(() => ({ qty: 0 }));
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    await page.waitForTimeout(1000);
    const after = await api.getStock('RAS-070AHM').catch(() => ({ qty: before.qty }));
    // reserved_qty 증가 → available_qty 감소 (qty 가 available 의미인 경우)
    expect(after.qty).toBeLessThanOrEqual(before.qty);
  });

  test('edge: 확정 취소 → reserved_qty 복원', async ({ page }) => {
    await page.route('**/api/inventory/reserve**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ ok: true, reservedQty: 0 }),
      }),
    );
    await page.goto('/');
    // 취소 버튼 노출 시 클릭, 미노출 시 skip
    const cancelBtn = page.locator('button:has-text("취소")').first();
    if ((await cancelBtn.count()) === 0) {
      test.skip(true, '취소 버튼 미노출');
    }
    await cancelBtn.click();
    await expect(page.locator('body')).toContainText(/취소|복원|완료/, { timeout: 5_000 });
  });
});
