import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * 거래처 status 경계 시나리오 — BLOCKED / EXPIRED / TEMP_CREDENTIAL.
 *
 * partner-bizgate.spec.ts 가 happy + BLOCKED 만 다루므로 본 spec 은
 * EXPIRED + TEMP_CREDENTIAL + BLOCKED 진입 차단/안내 동작을 명시 검증한다.
 *
 * 가드:
 *  - UUID 비공개 (partner.id 노출 금지)
 *  - 비즈니스 식별자 (거래처명/사업자번호) 노출 허용
 */
test.describe('partner status — boundary cases', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'partner-auth-service 미가동 — IT skip');
  });

  test('boundary: BLOCKED → 차단 안내 + 메인 진입 거부', async ({ page }) => {
    const partner = Partners.blocked();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/차단|불가|문의|거래정지/, { timeout: 5_000 });
    const body = (await page.textContent('body')) ?? '';
    expect(body).not.toContain(partner.id); // UUID 비공개
  });

  test('boundary: EXPIRED → 만료 안내 + 갱신 유도', async ({ page }) => {
    const partner = Partners.expired();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/만료|기간|갱신|재계약/, { timeout: 5_000 });
    const body = (await page.textContent('body')) ?? '';
    expect(body).not.toContain(partner.id);
  });

  test('boundary: TEMP_CREDENTIAL → 임시 인증 안내 + 정식 전환 유도', async ({ page }) => {
    const partner = Partners.tempCredential();
    await mockPartnerAuth(page, partner);
    await page.goto('/');
    // 임시 인증 거래처는 legacy 상 정식 BizGate 전환 안내 노출
    await expect(page.locator('body')).toContainText(/임시|전환|등록|인증/, { timeout: 5_000 });
    const body = (await page.textContent('body')) ?? '';
    expect(body).not.toContain(partner.id);
  });
});
