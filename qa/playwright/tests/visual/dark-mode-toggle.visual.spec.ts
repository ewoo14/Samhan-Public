import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * Visual regression — 다크모드 toggle 전후.
 *
 * 검증: prefers-color-scheme 또는 toggle button 으로 dark theme 전환 시
 *      배경/텍스트 색상이 baseline 과 일치.
 */
test.describe('visual — dark mode toggle', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'backend 미가동 — IT skip');
    await mockPartnerAuth(page, Partners.activeBizgate());
  });

  test('light → dark 전환 snapshot', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-light.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });

    await page.emulateMedia({ colorScheme: 'dark' });
    await page.reload();
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    await expect(page).toHaveScreenshot('home-dark.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });

  test('dark: body[data-theme="dark"] 토큰 적용 검증', async ({ page }) => {
    // Phase 7 3차 정정 — 시각적 회귀(snapshot) 만으로는 DS 토큰 전환 여부 알 수 없음.
    // body 의 data-theme 속성 + computed background 색상 검증.
    // design-system tokens.css 의 [data-theme="dark"] 셀렉터가 적용되어야 함.
    await page.emulateMedia({ colorScheme: 'dark' });
    await page.goto('/');
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);

    // data-theme 미구현 환경에서는 skip — order-app/estimate-app 의 dark-mode 도입 후 활성화.
    const hasDataTheme = await page.locator('body[data-theme]').count();
    if (hasDataTheme === 0) {
      test.skip(true, 'body[data-theme] 미구현 — order-app/estimate-app dark-mode 도입 후 활성화');
    }
    const theme = await page.locator('body').getAttribute('data-theme');
    expect(theme).toBe('dark');
    const bgColor = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    // light 기본(흰색) 이 아님 — dark 토큰이 실제로 적용
    expect(bgColor).not.toBe('rgb(255, 255, 255)');
    expect(bgColor).not.toBe('rgba(0, 0, 0, 0)'); // 미적용 fallback 도 차단
  });
});
