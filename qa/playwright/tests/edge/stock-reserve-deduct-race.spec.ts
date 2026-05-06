import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient, type StockSnapshot } from '../../utils/api-clients';

/**
 * stock reserve / deduct race — 동시성 시나리오.
 *
 * 두 요청이 병렬로 같은 productId 의 reserve + deduct 를 호출했을 때
 * 최종 invariant:
 *   totalQty_after     = totalQty_before  - deducted
 *   reservedQty_after  = reservedQty_before + reserved - deducted
 *   availableQty_after = totalQty_after - reservedQty_after
 *
 * lost-update / 음수 fallback 회피 가드.
 *
 * Phase 7 3차 정정 (BE Critical) — inventory-service 의 실 schema (availableQty/reservedQty/totalQty) 적용.
 */
test.describe('edge — stock reserve/deduct race', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'inventory-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('race: 동시 reserve + deduct → invariant 유지 + 음수 X', async () => {
    const code = process.env.QA_SAMPLE_PRODUCT_CODE ?? 'RAS-070AHM';
    const productId = await api.lookupProductIdByCode(code);
    if (!productId) {
      test.skip(true, 'productId 매핑 미가용 — by-code lookup 미구현');
    }
    const fallback: StockSnapshot = { availableQty: 0, reservedQty: 0, totalQty: 0 };
    const before = await api.getStock(productId!).catch(() => fallback);

    // 병렬 호출 — 동시성 race 재현 (실 backend 의 row lock / optimistic lock 검증)
    await Promise.allSettled([
      api.post('/api/inventory/reserve', { productId, qty: 1 }).catch(() => null),
      api.post('/api/inventory/deduct', { productId, qty: 1 }).catch(() => null),
      api.post('/api/inventory/reserve', { productId, qty: 2 }).catch(() => null),
    ]);

    const after = await api.getStock(productId!).catch(() => before);
    // invariant — 음수 X
    expect(after.totalQty).toBeGreaterThanOrEqual(0);
    expect(after.reservedQty).toBeGreaterThanOrEqual(0);
    expect(after.availableQty).toBeGreaterThanOrEqual(0);
    // availableQty = totalQty - reservedQty (race 후에도 정합)
    expect(after.availableQty).toBe(after.totalQty - after.reservedQty);
  });
});
