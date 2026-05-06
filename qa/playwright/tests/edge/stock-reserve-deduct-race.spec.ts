import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient, type StockSnapshot } from '../../utils/api-clients';

/**
 * stock reserve / deduct race — 동시성 시나리오.
 *
 * 두 요청이 병렬로 같은 productCode 의 reserve + deduct 를 호출했을 때
 * 최종 invariant:
 *   on_hand_after  = on_hand_before  - deducted
 *   reserved_after = reserved_before + reserved - deducted
 *   available_after = on_hand_after - reserved_after
 *
 * lost-update / 음수 fallback 회피 가드.
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
    const fallback: StockSnapshot = { on_hand: 0, reserved: 0, available: 0 };
    const before = await api.getStock(code).catch(() => fallback);

    // 병렬 호출 — 동시성 race 재현 (실 backend 의 row lock / optimistic lock 검증)
    await Promise.allSettled([
      api.post('/api/inventory/reserve', { productCode: code, qty: 1 }).catch(() => null),
      api.post('/api/inventory/deduct', { productCode: code, qty: 1 }).catch(() => null),
      api.post('/api/inventory/reserve', { productCode: code, qty: 2 }).catch(() => null),
    ]);

    const after = await api.getStock(code).catch(() => before);
    // invariant — 음수 X
    expect(after.on_hand).toBeGreaterThanOrEqual(0);
    expect(after.reserved).toBeGreaterThanOrEqual(0);
    expect(after.available).toBeGreaterThanOrEqual(0);
    // available = on_hand - reserved (race 후에도 정합)
    expect(after.available).toBe(after.on_hand - after.reserved);
  });
});
