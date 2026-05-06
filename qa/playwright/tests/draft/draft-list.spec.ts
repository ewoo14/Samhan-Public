import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 임시저장 목록 — 거래처별 격리.
 *
 * Happy: BIZ-001 거래처 → BIZ-001 draft 만 노출
 * Edge : 빈 목록 → "임시저장 없음" 메시지
 */
test.describe('draft — list', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: BIZ-001 → 본인 draft 만 노출', async ({ page }) => {
    await page.goto('/drafts');
    const body = await page.textContent('body');
    // 다른 거래처 코드 비노출
    expect(body ?? '').not.toContain('BIZ-002');
    expect(body ?? '').not.toContain('BIZ-003');
  });

  test('edge: 빈 목록 → 안내 메시지', async ({ page }) => {
    await page.route('**/api/partner-orders/drafts**', (route) =>
      route.fulfill({ status: 200, body: JSON.stringify({ items: [] }) }),
    );
    await page.goto('/drafts');
    await expect(page.locator('body')).toContainText(/없|비었|0/, { timeout: 5_000 });
  });
});
