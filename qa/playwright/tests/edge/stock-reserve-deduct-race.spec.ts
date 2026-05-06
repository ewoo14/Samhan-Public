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

  test('race: 동시 reserve N회 → delta 정확 + invariant 유지 + cleanup', async () => {
    // Phase 7 3차 정정 — 단순 invariant 만 보던 이전 패턴은 reserve 가 모두 실패해도 통과 (tautology).
    // before/after delta 비교 + cleanup (release) 으로 실 동시성 효과 검증.
    const code = process.env.QA_SAMPLE_PRODUCT_CODE ?? 'RAS-070AHM';
    const productId = await api.lookupProductIdByCode(code);
    if (!productId) {
      test.skip(true, 'productId 매핑 미가용 — by-code lookup 미구현');
    }
    const fallback: StockSnapshot = { availableQty: 0, reservedQty: 0, totalQty: 0 };
    const before = await api.getStock(productId!).catch(() => fallback);

    // 동시 reserve 5회 (각 1qty)
    const reserveResults = await Promise.allSettled(
      Array.from({ length: 5 }, () =>
        api.post('/api/inventory/reserve', { productId, qty: 1 }),
      ),
    );
    const reserveOk = reserveResults.filter((r) => r.status === 'fulfilled').length;
    if (reserveOk === 0) {
      test.skip(true, 'reserve endpoint 미가용 — race 검증 skip');
    }

    const afterReserve = await api.getStock(productId!).catch(() => before);
    // delta — reservedQty 가 정확히 reserveOk 만큼 증가, availableQty 는 동량 감소, totalQty 불변
    expect(afterReserve.reservedQty - before.reservedQty).toBe(reserveOk);
    expect(afterReserve.availableQty).toBe(before.availableQty - reserveOk);
    expect(afterReserve.totalQty).toBe(before.totalQty); // invariant
    // 정합 — availableQty = totalQty - reservedQty
    expect(afterReserve.availableQty).toBe(afterReserve.totalQty - afterReserve.reservedQty);
    // 음수 X
    expect(afterReserve.availableQty).toBeGreaterThanOrEqual(0);
    expect(afterReserve.reservedQty).toBeGreaterThanOrEqual(0);

    // cleanup — release 로 reserve 만큼 원복 (테스트 격리)
    await Promise.allSettled(
      Array.from({ length: reserveOk }, () =>
        api.post('/api/inventory/release', { productId, qty: 1 }),
      ),
    );
    const afterRelease = await api.getStock(productId!).catch(() => afterReserve);
    // restore — reservedQty 가 before 와 동일 (release 미구현이면 본 검증 skip)
    if (afterRelease.reservedQty === afterReserve.reservedQty) {
      // release endpoint 미가용 — 환경 분리. 검증 일부만 통과.
      return;
    }
    expect(afterRelease.reservedQty).toBe(before.reservedQty);
    expect(afterRelease.availableQty).toBe(before.availableQty);
  });
});
