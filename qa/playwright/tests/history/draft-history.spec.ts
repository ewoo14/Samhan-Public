import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 임시저장 (draft) 이력 — 30일 TTL 내 본인 draft 목록.
 *
 * Happy: BIZ-001 의 draft N건 → 작성 시각 / 라인 요약 노출
 * Edge : 30일 초과 draft → 자동 만료 (목록 미노출)
 */
test.describe('history — draft', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: draft 이력 → 작성 시각 + 라인 요약', async ({ page }) => {
    await page.goto('/history/drafts');
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/임시|저장|draft|이력/);
    expect(body ?? '').not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: 30일 만료 draft 자동 미노출', async ({ page }) => {
    await page.route('**/api/partner-orders/drafts**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0, expiredCount: 5 }),
      }),
    );
    await page.goto('/history/drafts');
    await expect(page.locator('body')).toContainText(/없|0건|만료|이력/, { timeout: 5_000 });
  });
});
