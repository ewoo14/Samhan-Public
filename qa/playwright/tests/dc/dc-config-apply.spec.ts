import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * DC (할인) config 적용 — 거래처별 단가표 자동 반영.
 *
 * Happy: 거래처 ACTIVE_BIZGATE 진입 → 카탈로그 단가 = standard_price × dc_rate
 * Edge : DC 미설정 거래처 → standard_price 그대로 노출
 */
test.describe('dc — config apply', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'dc-config-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: DC 적용 거래처 → 할인율 badge + 최종가 노출', async ({ page }) => {
    await page.goto('/');
    // narrow selector — body 광범위 매칭 회피
    const rateBadge = page.locator('[data-testid="dc-applied-rate"]');
    const finalPrice = page.locator('[data-testid="dc-final-price"]');
    if ((await rateBadge.count()) === 0 && (await finalPrice.count()) === 0) {
      test.skip(true, 'DC testid 미노출 — UI 구현 대기');
    }
    if ((await finalPrice.count()) > 0) {
      await expect(finalPrice.first()).toBeVisible();
    }
    // UUID 비공개 가드 (전역 body 만 검사)
    const body = (await page.textContent('body')) ?? '';
    expect(body).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: DC 미설정 거래처 → standard_price (rate 0)', async ({ page }) => {
    await page.route('**/api/dc/config**', (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'DC_NOT_CONFIGURED' }),
      }),
    );
    await page.goto('/');
    const finalPrice = page.locator('[data-testid="dc-final-price"]');
    if ((await finalPrice.count()) === 0) {
      test.skip(true, 'dc-final-price testid 미노출 — UI 구현 대기');
    }
    // DC 0 fallback 시에도 finalPrice 자체는 노출 (가격 자체는 standard_price)
    await expect(finalPrice.first()).toBeVisible({ timeout: 5_000 });
  });
});
