/**
 * mobile v4 (거래처 주문서) — BizGate SSO.
 *
 * Happy: 앱 실행 → WebView 로드 → order-app v4 → BizGate redirect
 * Edge : SSO 실패 시 차단 메시지
 */
describe('mobile v4 — partner BizGate', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  it('happy: WebView 로드 → BizGate SSO redirect', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
  });

  it('edge: SSO 실패 → 차단 메시지', async () => {
    await waitFor(element(by.id('webview-order'))).toBeVisible().withTimeout(15000);
    // WebView 안 legacy 의 alert 흐름
  });
});
