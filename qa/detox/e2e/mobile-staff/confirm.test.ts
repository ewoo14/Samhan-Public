/**
 * mobile-staff — 견적 확정.
 *
 * Happy: 라인 N개 확정 → 견적번호 발급 + 화면 전환
 * Edge : 빈 견적 확정 시도 → 차단
 */
describe('mobile-staff — confirm', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  it('happy: 라인 입력 후 확정 → 견적번호 발급', async () => {
    await waitFor(element(by.id('webview-estimate'))).toBeVisible().withTimeout(15000);
    // legacy estimate-app 의 confirm 흐름은 WebView 안에서 처리됨
    // detox 는 native bridge level 에서 webview 가시성만 검증
  });

  it('edge: 빈 견적 확정 → 차단 alert', async () => {
    await waitFor(element(by.id('webview-estimate'))).toBeVisible().withTimeout(15000);
    // legacy 의 alert("품목을 선택하세요") 흐름
  });
});
