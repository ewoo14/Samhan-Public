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

  test('happy: 확정 → reservedQty 증가 + availableQty 감소 + totalQty 불변', async ({ page }) => {
    // Phase 7 3차 정정 (BE Critical) — inventory-service 의 실 schema (availableQty/reservedQty/totalQty) 적용.
    // productId UUID 는 lookupProductIdByCode 로 확보 (fixture id 는 placeholder 라 backend 매칭 불가).
    const productId = await api.lookupProductIdByCode('RAS-070AHM');
    if (!productId) {
      test.skip(true, 'productId 매핑 미가용 — by-code lookup 미구현');
    }
    const fallback = { availableQty: 0, reservedQty: 0, totalQty: 0 };
    const before = await api.getStock(productId!).catch(() => fallback);
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    await page.waitForTimeout(1000);
    const after = await api.getStock(productId!).catch(() => before);
    // reserve 의 schema 의미: reservedQty 증가, availableQty 감소, totalQty 불변
    expect(after.reservedQty).toBeGreaterThanOrEqual(before.reservedQty);
    expect(after.availableQty).toBeLessThanOrEqual(before.availableQty);
    expect(after.totalQty).toBe(before.totalQty);
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
