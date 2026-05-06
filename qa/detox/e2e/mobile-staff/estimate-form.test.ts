/**
 * mobile-staff (영업직원) — 견적서 진입.
 *
 * Happy: 앱 실행 → WebView 로드 → estimate-app v2 첫 화면 노출
 * Edge : 네트워크 단절 시 reload 안내
 */
describe('mobile-staff — estimate form 진입', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  beforeEach(async () => {
    await device.reloadReactNative();
  });

  it('happy: WebView 로드 → estimate-app 진입', async () => {
    // Expo wrapper 의 WebView 안 legacy estimate-app v2 (port 5183) 진입
    await waitFor(element(by.id('webview-estimate')))
      .toBeVisible()
      .withTimeout(15000);
  });

  it('edge: 네트워크 단절 시 reload 안내', async () => {
    await device.setURLBlacklist(['.*samhan-air.com.*', '.*localhost.*']);
    await device.reloadReactNative();
    await waitFor(element(by.text(/네트워크|연결|재시도/)))
      .toBeVisible()
      .withTimeout(10000);
    await device.setURLBlacklist([]);
  });
});
