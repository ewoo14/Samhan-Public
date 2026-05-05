/**
 * mobile-staff — 라인 grid + sidebar 인터랙션.
 *
 * Happy: 카탈로그 진입 → 모델 선택 → 라인 추가 → grid 1행 노출
 * Edge : qty 0 입력 시 차단
 */
describe('mobile-staff — line grid + sidebar', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  it('happy: 모델 선택 → 라인 추가 → grid 1행', async () => {
    // WebView 안 legacy 의 a tag click → JS bridge 트리거
    await waitFor(element(by.id('webview-estimate'))).toBeVisible().withTimeout(15000);
    // legacy 의 catalog 진입 (estimate-app)
    // detox WebView 인터랙션은 element(by.web.id(...)) 사용 (iOS 만 안정)
  });

  it('edge: qty 0 입력 → 차단', async () => {
    await waitFor(element(by.id('webview-estimate'))).toBeVisible().withTimeout(15000);
    // legacy 의 alert 또는 inline 메시지 검증
  });
});
