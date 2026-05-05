/**
 * Mobile v4 — legacy index.html 의 source URL 결정.
 *
 * 운영:
 *   - dev (`__DEV__ === true`): `http://localhost:5180/legacy/index.html`
 *     (web/order-app v4 의 Vite dev server — legacy entry).
 *   - production: `https://order.samhan-air.com/legacy/index.html`
 *     (정정: 도메인 전략 — order.samhan-air.com hosts legacy bundle).
 *
 * 미해결:
 *   - bundled (asset) source 옵션은 react-native-webview `originWhitelist`
 *     설정이 까다로워 일단 hosted URL 만 채택.
 *   - 향후 offline 지원 시 `expo-file-system` 으로 다운로드 후 file:// 로드 검토.
 *
 * 카테고리 hash:
 *   - legacy index.html 가 `enterMobile(which)` 함수로 카테고리 진입.
 *   - WebView 안 navigation: `#category=home|single|comm|old` 쿼리 추가 시
 *     legacy 가 자동 enterMobile 호출 (또는 RN 측에서 injectJavaScript).
 */

const DEV_URL = 'http://localhost:5180/legacy/index.html';
const PROD_URL = 'https://order.samhan-air.com/legacy/index.html';

export interface LegacyUriOptions {
  /** 진입 카테고리 — undefined = mobile-gate (4 카테고리 큰 진입 버튼). */
  category?: 'home' | 'single' | 'comm' | 'old';
  /** dev override URL (Expo Go LAN 테스트용 e.g. http://192.168.0.5:5180/legacy/index.html) */
  devOverride?: string;
}

export function getLegacyUri(opts: LegacyUriOptions = {}): string {
  // `__DEV__` 는 Metro / RN 빌드 시 정의됨. web 환경 (expo export web) 도 dev/prod 분기 동일.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const isDev = typeof (globalThis as any).__DEV__ !== 'undefined' ? (globalThis as any).__DEV__ : false;
  const base = opts.devOverride ?? (isDev ? DEV_URL : PROD_URL);
  if (opts.category) {
    return base + '#category=' + encodeURIComponent(opts.category);
  }
  return base;
}

/** dev / prod base URL pair (헬퍼 export — 디버깅용). */
export const LEGACY_URLS = {
  dev: DEV_URL,
  prod: PROD_URL,
};
