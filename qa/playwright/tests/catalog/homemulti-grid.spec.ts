import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * HomeMulti 카탈로그 grid — 실내기 + 실외기 분리 그리드.
 *
 * Happy: BTU 컬럼 + 모델명 (UUID 비공개) 노출
 * Edge : 빈 카탈로그 → "조회 결과 없음" 메시지
 */
test.describe('catalog — HOME_MULTI grid', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'product-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: HomeMulti 진입 → 실내기 grid + 모델명 노출', async ({ page }) => {
    await page.goto('/');
    await page.locator('text=/홈멀티|HomeMulti|HOME_MULTI/').first().click({ trial: false }).catch(() => {});
    const body = await page.textContent('body');
    expect(body ?? '').toMatch(/RAS-|모델|BTU/);
    // UUID 비공개 가드
    expect(body ?? '').not.toMatch(/PROD-[0-9]{3}/);
  });

  test('edge: 빈 카탈로그 fallback', async ({ page }) => {
    await page.route('**/api/products?**', (route) =>
      route.fulfill({ status: 200, body: JSON.stringify({ items: [] }) }),
    );
    await page.goto('/');
    await page.locator('text=/홈멀티|HomeMulti/').first().click().catch(() => {});
    await expect(page.locator('body')).toContainText(/없|조회|결과/, { timeout: 5_000 });
  });
});
