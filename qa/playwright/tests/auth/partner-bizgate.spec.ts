import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 거래처 BizGate SSO 로그인 — 8 status enum 시나리오.
 * status: ACTIVE / TEMP_PASSWORD / BLOCKED / EXPIRED / PENDING / DUPLICATE / UNKNOWN / WITHDRAW
 *
 * Happy: ACTIVE 거래처 → 메인 진입 + JWT 발급
 * Edge : BLOCKED 거래처 → 로그인 차단 메시지
 */
test.describe('partner BizGate SSO — auth', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
  });

  test('happy: ACTIVE BizGate SSO 로그인 → 메인 진입', async ({ page }) => {
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    await expect(page).toHaveTitle(/주문|samhan/i);
    // 헤더에 거래처명 노출 (UUID 노출 금지)
    const body = await page.textContent('body');
    expect(body ?? '').toContain(partner.name);
    expect(body ?? '').not.toContain(partner.id);
  });

  test('edge: BLOCKED 거래처 → 차단 메시지', async ({ page }) => {
    const partner = Partners.blocked();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    // legacy 는 status 검증 → alert 또는 차단 화면
    await expect(page.locator('body')).toContainText(/차단|불가|문의/, { timeout: 5_000 });
  });
});
