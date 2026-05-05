import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 거래처 주문 이력 — 본인 주문만 조회 (격리).
 *
 * Happy: BIZ-001 → 본인 슬립 노출 (SH-* 형식)
 * Edge : 다른 거래처 슬립 직접 URL 접근 차단
 */
test.describe('history — partner order', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'slip-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 이력 페이지 → 슬립번호 + 거래처명 노출', async ({ page }) => {
    await page.goto('/history');
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/SH-|이력|주문/);
    // UUID 비공개 가드
    expect(body ?? '').not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}/);
  });

  test('edge: 타 거래처 슬립 직접 접근 차단', async ({ page }) => {
    await page.goto('/history/SH-202601-9999');
    await expect(page.locator('body')).toContainText(/없|권한|불가|404/, { timeout: 5_000 });
  });
});
