import { test, expect } from '@playwright/test';
import { isBackendAvailable } from '../../fixtures/auth';

/**
 * Visual regression — 모바일 게이트 4 카테고리 button.
 *
 * 검증: 색상 / 폰트 / 간격이 baseline 과 일치.
 * baseline 갱신: `npx playwright test --update-snapshots`
 *
 * 3 project (mobile-chrome / mobile-safari / electron-desktop) 별 baseline 별도 보존.
 */
test.describe('visual — mobile gate 4 카테고리', () => {
  test.beforeEach(async () => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — visual baseline IT skip');
  });

  test('mobile gate snapshot — 4 button 노출', async ({ page }) => {
    await page.goto('/');
    // Phase 7 3차 정정 (Designer P1) — testid 단일화로 selector 의존 일관화.
    // legacy DOM 보존 + #mobileGate 에 data-testid 만 추가 (clients/web/order-app/index.html).
    const gate = page.locator('[data-testid="mobile-gate"]');
    if ((await gate.count()) === 0) {
      test.skip(true, '게이트 UI 미노출 — skip');
    }
    await expect(gate).toBeVisible({ timeout: 10_000 });
    await expect(page).toHaveScreenshot('mobile-gate.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });
});
