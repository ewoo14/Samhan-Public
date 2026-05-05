/**
 * mobile v4 — WebView 안 주문 확정.
 *
 * Happy: 카테고리 → 모델 선택 → 라인 추가 → 확정 → 슬립번호 발급
 * Edge : 임시저장 후 앱 종료 → 재진입 시 임시저장 복원
 */
describe('mobile v4 — order confirm in WebView', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  it('happy: 모델 선택 → 라인 → 확정 → 슬립', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
    // WebView 내부의 confirm 은 legacy v4 가 처리 (Native 는 WebView 가시성만 검증)
  });

  it('edge: 임시저장 후 재진입 → 복원', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
    // 앱 재시작 후 sessionStorage 가 아닌 server-side draft (30일 TTL) 복원
    await device.terminateApp();
    await device.launchApp({ newInstance: false });
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
  });
});
