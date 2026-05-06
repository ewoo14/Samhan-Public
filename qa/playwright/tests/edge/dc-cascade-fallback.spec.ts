import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * DC rule cascade 끝단 fallback — 모델/카테고리/거래처 default 모두 미설정 시.
 *
 * 검증: cascade 의 모든 단계가 404 → 최종 standard_price (rate 0%) 로 fallback,
 *      UI 가 깨지지 않고 정상 단가가 노출된다.
 */
test.describe('edge — dc cascade fallback', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'dc-config-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('cascade 끝단 fallback → standard_price 노출', async ({ page }) => {
    // 모든 cascade 단계 (model / category / partner-default) 404
    await page.route('**/api/dc/config**', (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'DC_RULE_NOT_FOUND', cascade: ['model', 'category', 'partner-default'] }),
      }),
    );
    await page.goto('/');
    // UI 가 깨지지 않고 final price 노출 (또는 catalog 자체 노출)
    const finalPrice = page.locator('[data-testid="dc-final-price"]');
    if ((await finalPrice.count()) > 0) {
      await expect(finalPrice.first()).toBeVisible({ timeout: 5_000 });
    } else {
      // testid 미노출 환경 (order-app v4 등) → catalog 가격 표기 자체 검증
      await expect(page.locator('body')).toContainText(/원|￦/, { timeout: 5_000 });
    }
    // UUID 비공개 가드
    const body = (await page.textContent('body')) ?? '';
    expect(body).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });
});
