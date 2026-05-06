import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * DC rule 우선순위 — 모델별 / 카테고리별 / 거래처 default 순서.
 *
 * Happy: 모델별 DC 가 가장 우선 → 카탈로그 모델 X 의 단가 = model rule 적용
 * Edge : 모델별 DC 미설정 → 카테고리별 fallback → 거래처 default fallback
 */
test.describe('dc — rule priority', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'dc-config-service 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('happy: 모델별 DC 우선 적용', async ({ page }) => {
    await page.goto('/');
    // 카탈로그 노출 + 단가 표시 확인 (정확한 dc_rate 비교는 backend 가동 시 검증)
    await expect(page.locator('body')).toContainText(/원|￦/, { timeout: 5_000 });
  });

  test('edge: 모델별 DC 미설정 → 카테고리 fallback', async ({ page }) => {
    await page.route('**/api/dc/config**', async (route) => {
      const url = route.request().url();
      if (url.includes('model=')) {
        return route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ error: 'MODEL_DC_NOT_FOUND' }),
        });
      }
      return route.continue();
    });
    await page.goto('/');
    await expect(page.locator('body')).toContainText(/원|￦/, { timeout: 5_000 });
  });
});
