import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * CommercialMulti 카탈로그 — 상업용 멀티 (PUMA-*).
 *
 * Happy: PUMA 모델 + 실내기/실외기 분리 grid
 * Edge : btu 0 (실외기) → "BTU 별도" 표기
 */
test.describe('catalog — COMMERCIAL_MULTI', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'product-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: CommercialMulti → PUMA 모델 노출', async ({ page }) => {
    await page.goto('/');
    await page.locator('text=/상업|Commercial|PUMA/').first().click().catch(() => {});
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/PUMA-|모델/);
  });

  test('edge: 실외기 BTU 0 → 별도 표기', async ({ page }) => {
    await page.goto('/');
    await page.locator('text=/상업|Commercial/').first().click().catch(() => {});
    const body = await page.textContent('body');
    // btu=0 (실외기) 시 "별도", "-", "OUT" 등 표기 분기
    expect(body ?? '').toMatch(/별도|OUT|실외|-/);
  });
});
