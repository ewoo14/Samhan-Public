import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 거래처 임시 비밀번호 로그인 → 강제 변경 흐름.
 *
 * Happy: 임시 PW 정확 입력 → 비밀번호 변경 화면 강제 노출
 * Edge : 변경 미완료 후 다른 페이지 진입 시 차단
 */
test.describe('partner TEMP_PASSWORD — auth', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
  });

  test('happy: 임시 PW 입력 → 비밀번호 변경 화면 노출', async ({ page }) => {
    const partner = Partners.tempCredential();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/비밀번호.*변경|임시/, { timeout: 5_000 });
  });

  test('edge: 변경 미완료 → 다른 화면 차단', async ({ page }) => {
    const partner = Partners.tempCredential();
    await mockPartnerAuth(page, partner);
    await page.goto('/order');
    // 강제 redirect 또는 alert
    await expect(page.locator('body')).toContainText(/비밀번호|변경/, { timeout: 5_000 });
  });
});
