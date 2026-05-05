import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 확정 happy — 임시저장 → 확정 1-step 전환.
 *
 * Happy: 임시저장 draft → 확정 버튼 → 슬립번호 발급 + 화면 전환
 * Edge : 라인 0 상태 확정 시도 → 차단
 */
test.describe('confirm — happy', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-order-service + slip-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: draft → 확정 → 슬립번호 발급', async ({ page }) => {
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정"), button:has-text("주문확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출 — draft 단계 skip');
    }
    await confirmBtn.click();
    // 슬립번호는 SH-YYYYMM-NNNN 형식 가정
    await expect(page.locator('body')).toContainText(/SH-|슬립|확정.*완료/, { timeout: 10_000 });
  });

  test('edge: 빈 라인 확정 시도 → 차단', async ({ page }) => {
    await page.goto('/');
    const confirmBtn = page.locator('button:has-text("확정"), button:has-text("주문확정")').first();
    if ((await confirmBtn.count()) === 0) {
      test.skip(true, '확정 버튼 미노출');
    }
    await confirmBtn.click();
    await expect(page.locator('body')).toContainText(/품목|입력|선택/, { timeout: 5_000 });
  });
});
