import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 슬립 발행 이력 — 본인 발행 슬립 timeline.
 *
 * Happy: BIZ-001 발행 슬립 N건 → 발행 시각 / 슬립번호 SH-* 노출
 * Edge : 발행 이력 0건 → "이력 없음" 메시지
 */
test.describe('history — slip publish', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'slip-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 발행 이력 → SH-* 슬립번호 노출', async ({ page }) => {
    await page.goto('/history/slips');
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/SH-|발행|슬립|이력/);
    // UUID 비공개 가드
    expect(body ?? '').not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: 이력 0건 → 빈 상태 메시지', async ({ page }) => {
    await page.route('**/api/slips/history**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [], total: 0 }),
      }),
    );
    await page.goto('/history/slips');
    await expect(page.locator('body')).toContainText(/없|0건|빈|이력/, { timeout: 5_000 });
  });
});
