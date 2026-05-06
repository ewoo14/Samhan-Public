import { test, expect } from '@playwright/test';
import { Partners, isBackendAvailable, mockPartnerAuth } from '../../fixtures/auth';

/**
 * backend 5xx fallback — gateway / 백엔드 장애 시 client UI 동작.
 *
 * 검증:
 *  - 5xx 응답 시 사용자 친화적 안내 (alert / inline error / retry)
 *  - white-screen 방지 (body 자체가 빈 문자열 X)
 *  - UUID/스택트레이스 노출 X
 */
test.describe('edge — api 5xx fallback', () => {
  test.beforeEach(async ({ page }) => {
    const apiBase = process.env.QA_API_BASE_URL ?? 'http://localhost:8080';
    const ok = await isBackendAvailable(apiBase);
    test.skip(!ok, 'gateway 미가동 — IT skip');
    const partner = Partners.activeBizgate();
    await mockPartnerAuth(page, partner);
  });

  test('5xx: backend 503 → 안내 메시지 + UI 보존', async ({ page }) => {
    // 모든 /api/** 호출 503 fallback
    await page.route('**/api/**', (route) =>
      route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'SERVICE_UNAVAILABLE', message: '일시적 장애' }),
      }),
    );
    await page.goto('/');
    // white-screen 방지 — body 가 비어있지 않음
    const body = (await page.textContent('body')) ?? '';
    expect(body.length).toBeGreaterThan(10);
    // 스택트레이스 / UUID 풀형식 노출 X (Phase 7 4차 — 502 와 가드 강도 통일)
    expect(body).not.toMatch(/at .+\.(?:js|ts):\d+/);
    expect(body).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
  });

  test('5xx: 502 → 재시도 안내 또는 정상 종료', async ({ page }) => {
    await page.route('**/api/**', (route) =>
      route.fulfill({
        status: 502,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'BAD_GATEWAY' }),
      }),
    );
    await page.goto('/');
    // Phase 7 4차 정정 — 503 케이스와 동일 가드 강도로 통일.
    //   1) length > 10 (이전 > 0): 의미 있는 안내 문구가 렌더 (white-screen + 단문 모두 차단)
    //   2) UUID 풀형식 차단 (이전 {8}-{4} 부분만 → {8}-{4}-{4}-{4}-{12})
    //   3) 스택트레이스 비노출 가드 동일
    const bodyText502 = await page.locator('body').innerText();
    expect(bodyText502.length).toBeGreaterThan(10);
    expect(bodyText502).not.toMatch(/at\s+\w+\.\w+:\d+/);
    expect(bodyText502).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i);
  });
});
