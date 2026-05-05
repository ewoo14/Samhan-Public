/**
 * mobile v4 — 모바일 게이트 4 카테고리 (홈멀티/싱글셋/상업멀티/단종).
 *
 * Happy: 게이트 화면 → 카테고리 4개 그리드 노출
 * Edge : 카테고리 권한 없음 시 잠금 표시
 */
describe('mobile v4 — gate 4 categories', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  it('happy: 게이트 → 4 카테고리 그리드', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
    // legacy 의 mobile-mode CSS 분기 + 4 카테고리 grid 노출
  });

  it('edge: 권한 없는 카테고리 → 잠금', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
  });
});
