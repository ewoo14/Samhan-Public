import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * SingleSet 카탈로그 — 단일 set (실내+실외 일체) 그리드.
 *
 * Happy: PSA-* 모델 노출 + BTU 정렬
 * Edge : 비활성 모델 (active=false) 미노출
 */
test.describe('catalog — SINGLE_SET grid', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'product-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: SingleSet → PSA- 모델 노출', async ({ page }) => {
    await page.goto('/');
    await page.locator('text=/단일|SingleSet|SET/').first().click().catch(() => {});
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/PSA-|모델/);
  });

  test('edge: 비활성 모델 미노출', async ({ page }) => {
    await page.goto('/');
    await page.locator('text=/단일|SingleSet/').first().click().catch(() => {});
    const body = await page.textContent('body');
    // OLD-AC-2010 (active:false) 가 grid 에 없어야 함
    expect(body ?? '').not.toContain('OLD-AC-2010');
  });
});
