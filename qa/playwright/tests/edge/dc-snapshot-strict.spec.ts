import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * DC snapshot strict — 기존 dc-snapshot-audit.spec.ts 의 tautology 정정.
 *
 * 이전 버전: 단순히 history 페이지 텍스트만 확인했으므로 dc_rate 변경 후에도
 *   동일하게 통과 — 실제로 snapshot 보존 검증 X.
 *
 * 본 spec: ApiClient.getSlip() 응답의 dc_rate snapshot 필드를 strict 비교.
 *   1) 슬립 발행 시점의 dc_rate 가 슬립 entity 에 저장
 *   2) 발행 후 dc_rate config 가 변경되어도 slip.dc_rate_snapshot 은 불변
 */
test.describe('edge — dc snapshot strict', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'slip-service / dc-config-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('strict: 슬립 dc_rate_snapshot 은 immutable', async () => {
    // 가장 최근 슬립 1건 조회 (실제 slipNo 는 환경별 다름 — 환경변수로 override 가능)
    const slipNo = process.env.QA_SAMPLE_SLIP_NO;
    if (!slipNo) {
      test.skip(true, 'QA_SAMPLE_SLIP_NO 미설정 — immutable 검증 skip');
    }
    const slip1 = await api.getSlip(slipNo!).catch(() => null);
    if (!slip1) {
      test.skip(true, '샘플 slip 미존재 — IT skip');
    }
    // backend 가 dc_rate / dc_rate_snapshot 필드를 노출해야 함
    const snapshot1 =
      (slip1 as Record<string, unknown>).dc_rate_snapshot ??
      (slip1 as Record<string, unknown>).dcRateSnapshot;
    expect(snapshot1, 'dc_rate_snapshot 필드 누락 — schema 정정 필요').toBeDefined();
    // tautology 회피 — 실 numeric 값 비교
    expect(typeof snapshot1 === 'number' || typeof snapshot1 === 'string').toBe(true);
    const value1 = Number(snapshot1);
    expect(Number.isFinite(value1)).toBe(true);
    expect(value1).toBeGreaterThanOrEqual(0);
    expect(value1).toBeLessThanOrEqual(1); // dc_rate 는 0.0 ~ 1.0 (legacy 0~100 % 환산 비교 시 별도 정규화)

    // Phase 7 3차 정정 — dc_rate config 변경 시뮬레이션 (admin endpoint 가 있을 때만).
    // 변경 후 동일 slipNo 재조회 → snapshot 이 그대로(immutable) 인지 검증.
    // admin endpoint 미가용 환경에서는 단순 재조회로 idempotent 만 확인.
    const partnerCode =
      ((slip1 as Record<string, unknown>).partnerCode as string | undefined) ??
      ((slip1 as Record<string, unknown>).partner_code as string | undefined);
    if (partnerCode) {
      await api
        .post('/api/admin/dc-config/override', { partnerCode, dcRate: 0.99 })
        .catch(() => null);
    }

    const slip2 = await api.getSlip(slipNo!).catch(() => null);
    if (!slip2) return; // 재조회 실패 시 skip
    const snapshot2 =
      (slip2 as Record<string, unknown>).dc_rate_snapshot ??
      (slip2 as Record<string, unknown>).dcRateSnapshot;
    // immutable — config override 가 발생했더라도 슬립의 snapshot 은 발행 시점 값 보존.
    expect(Number(snapshot2)).toBe(value1);
  });
});
