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
    // Phase 7 4차 — body[data-theme] 정식 도입. localStorage 우선이므로 명시 설정.
    await page.addInitScript(() => {
      try { localStorage.setItem('samhan.theme', 'light'); } catch (e) {}
    });
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('/');
    // Phase 7 5/6차 정정 — 폰트 로드 race 방지 가드.
    // self-host Pretendard 적용 후에도 woff2 fetch + decode 비동기 완료 대기 필수.
    // 미적용 시 system-ui fallback 으로 1차 렌더 → baseline 폭/높이 미스매치 발생.
    await page.evaluate(() => document.fonts.ready);
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);
    // light baseline — body 의 data-theme 가 light/dark 중 하나
    await expect(page.locator('body')).toHaveAttribute('data-theme', /^(light|dark)$/);
    await page.evaluate(() => document.body.setAttribute('data-theme', 'light'));
    await expect(page).toHaveScreenshot('home-light.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });

    // dark baseline — attribute 만 변경하여 동일 페이지에서 토큰 전환 검증
    await page.evaluate(() => document.body.setAttribute('data-theme', 'dark'));
    await expect(page).toHaveScreenshot('home-dark.png', {
      maxDiffPixelRatio: 0.02,
      animations: 'disabled',
    });
  });

  test('dark: body[data-theme="dark"] 토큰 적용 검증', async ({ page }) => {
    // Phase 7 4차 정정 — body[data-theme] 정식 도입 (skip 가드 제거).
    // design-system tokens.css 의 [data-theme="dark"] 셀렉터가 body 에 직접 적용되며,
    // body { background-color: var(--color-bg-primary); } 바인딩으로 즉시 dark 색상 반영.
    await page.goto('/');
    // Phase 7 5/6차 정정 — 폰트 로드 race 방지 가드 (self-host 적용 후에도 비동기 fetch).
    await page.evaluate(() => document.fonts.ready);
    await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => null);

    // 정식 도입 — data-theme 가 light/dark 중 하나로 반드시 존재
    await expect(page.locator('body')).toHaveAttribute('data-theme', /^(light|dark)$/);

    // dark 강제 적용 후 토큰 검증
    await page.evaluate(() => document.body.setAttribute('data-theme', 'dark'));
    const theme = await page.locator('body').getAttribute('data-theme');
    expect(theme).toBe('dark');
    const bgColor = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    // light 기본(흰색) 이 아님 — dark 토큰 (#1a1a1a) 이 실제로 적용
    expect(bgColor).not.toBe('rgb(255, 255, 255)');
    expect(bgColor).not.toBe('rgba(0, 0, 0, 0)'); // 미적용 fallback 도 차단
  });
});
