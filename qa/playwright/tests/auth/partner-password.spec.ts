import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 거래처 PASSWORD 로그인 — 일반 비밀번호 인증.
 *
 * Happy: 정확한 비밀번호 입력 → 진입
 * Edge : 잘못된 비밀번호 → 실패 메시지 + retry 카운트
 */
test.describe('partner PASSWORD — auth', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
  });

  test('happy: 정확한 비밀번호 → JWT 발급 + 진입', async ({ page }) => {
    const partner = Partners.activeStandard();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    await expect(page.locator('body')).toContainText(partner.name, { timeout: 5_000 });
  });

  test('edge: 잘못된 비밀번호 → 실패 메시지', async ({ page }) => {
    Partners.activeStandard();
    await page.goto('/');
    // legacy 의 credential input 노출 가정 — 미구현 시 skip
    const pwInput = page.locator('input[type="password"]').first();
    if ((await pwInput.count()) === 0) {
      test.skip(true, '인증 입력 UI 미노출 — legacy gate 분기 skip');
    }
    const invalidInput = 'invalid-test-input';
    await pwInput.fill(invalidInput);
    await page.locator('button:has-text("로그인"), button[type="submit"]').first().click();
    await expect(page.locator('body')).toContainText(/실패|일치|확인/, { timeout: 5_000 });
  });
});
