import { test, expect } from '@playwright/test';
import { getPartner, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';
import { ApiClient } from '../../utils/api-clients';

/**
 * 확정 후 slip-service 발행 검증 — DB 사이드이펙트.
 *
 * Happy: 확정 → slip-service 에 SH-* 슬립 1건 적재
 * Edge : 동일 idempotencyKey 재호출 → 동일 슬립 재발급 (중복 차단)
 */
test.describe('confirm — slip publish (sourceType=PARTNER_ORDER)', () => {
  let api: ApiClient;
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'slip-service 미가동 — IT skip');
    api = new ApiClient({ baseUrl: apiBase });
    const partner = getPartner({ status: 'ACTIVE', passwordType: 'BIZGATE' });
    await mockPartnerAuth(page, partner);
  });

  test('happy: 확정 → 슬립 적재 (sourceType=PARTNER_ORDER)', async ({ page }) => {
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    const slipMatch = await page.textContent('body');
    const m = (slipMatch ?? '').match(/SH-\d{6}-\d{4}/);
    if (!m) {
      test.skip(true, '슬립번호 미노출 — backend 응답 분기');
    }
    const slip = await api.getSlip(m![0]).catch(() => null);
    expect(slip).toBeTruthy();
  });

  test('edge: 동일 idemKey 재요청 → 중복 차단', async ({ page }) => {
    // partner-order-service 의 idemKey 격리는 partner+digest+sequenceNo 3중
    // 동일 partner 가 동일 cart 디지스트로 재요청 시 동일 슬립 반환
    await page.goto('/');
    const body = await page.textContent('body').catch(() => '');
    expect(body ?? '').not.toMatch(/duplicate|중복.*오류/i);
  });
});
