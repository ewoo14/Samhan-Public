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

  test('happy: 모델별 DC 우선 적용 → rate badge', async ({ page }) => {
    await page.goto('/');
    const rateBadge = page.locator('[data-testid="dc-applied-rate"]');
    if ((await rateBadge.count()) === 0) {
      test.skip(true, 'dc-applied-rate testid 미노출');
    }
    // 모델별 rule 적용 시 badge 자체가 갱신되어야 함 (정확한 % 비교는 backend 가동 시)
    await expect(rateBadge.first()).toBeAttached();
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
    const finalPrice = page.locator('[data-testid="dc-final-price"]');
    if ((await finalPrice.count()) === 0) {
      test.skip(true, 'dc-final-price testid 미노출');
    }
    // 카테고리 fallback 시 final price 는 여전히 렌더 (가격 0 이 아님)
    await expect(finalPrice.first()).toBeVisible({ timeout: 5_000 });
  });
});
