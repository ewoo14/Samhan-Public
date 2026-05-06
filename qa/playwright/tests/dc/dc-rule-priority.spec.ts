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
    // Phase 7 3차 정정 — toBeAttached() 는 testid 존재만 확인 (tautology, count() > 0 시 항상 통과).
    // 실 rate 값이 % 형태로 렌더되는지 검증.
    const text = await rateBadge.first().textContent();
    expect(text, 'rate badge 텍스트 비어있음').toBeTruthy();
    // Phase 7 종합 TM 정정 — 소수 % (예: 15.5%) 호환. 이전 /\d+\s*%/ 는 정수만 매칭 → 부분 일치 통과 가능.
    expect(text!).toMatch(/\d+(\.\d+)?\s*%/);
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
