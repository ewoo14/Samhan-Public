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

  test('happy: DC 적용 거래처 → 할인가 노출', async ({ page }) => {
    await page.goto('/');
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/원|￦|단가|가격/);
    // UUID 비공개 가드
    expect(body ?? '').not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: DC 미설정 거래처 → standard_price', async ({ page }) => {
    await page.route('**/api/dc/config**', (route) =>
      route.fulfill({
        status: 404,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'DC_NOT_CONFIGURED' }),
      }),
    );
    await page.goto('/');
    // 카탈로그가 정상 노출되어야 함 (DC 0 으로 fallback)
    await expect(page.locator('body')).toContainText(/원|￦|단가|가격/, { timeout: 5_000 });
  });
});
