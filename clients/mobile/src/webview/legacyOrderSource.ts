/**
 * Mobile v4 — order-legacy v4 (Node + Express + EJS) 의 source URL 결정.
 *
 * 회고 #2 (2026-05-05) — 사용자 명시:
 *   "주문서는 여전히 구글 스크립트 모바일 버전의 UI와 처음 모바일 게이트를 제외한 나머지는
 *    모두 다름을 확인. ... 종합견적서 모바일용 앱은 구글 스크립트를 거의 그대로 계승한 것으로 보이나..."
 *
 * 정정 결정 (mobile-staff v3 와 동일 패턴 1:1):
 *   - 이전 v4 = `legacySource.ts` 가 web/order-app v4 (Vite, port 5180) `/legacy/index.html` 임베드
 *     → RN HomeScreen + extra-menu 가 별도 noise 추가 (legacy 미존재).
 *   - 신규 v4 = `legacyOrderSource.ts` 가 web/order-legacy v4 (Express + EJS, port 5185) `/` 임베드
 *     → estimate-app v2 와 동일하게 mobile-mode CSS 분기를 100% legacy 가 처리.
 *
 * 운영:
 *   - dev (`__DEV__ === true`): `http://localhost:5185/`
 *     (clients/web/order-legacy v4 dev server — `node server.js` PORT 5185)
 *   - production: `https://order.samhan-air.com/`
 *     (DECISIONS Phase 6 Section 4 sub-domain)
 *
 * 모바일 자동 분기 (코드 변경 0):
 *   - order-legacy v4 의 views/index.ejs:
 *     - line 119  : `.mobile-gate { display:none; flex-direction:column; gap:16px; margin:20px 0 12px }`
 *     - line 120  : `body.no-active .mobile-gate { display:flex }` (인증 통과 후 default 4 카테고리 노출)
 *     - line 121  : `.select-big { width:100%; height:150px; border:1px solid var(--c-line); border-radius:18px; font-weight:800; font-size:36px }`
 *     - line 123  : `body.mobile-mode .grid { grid-template-columns: minmax(0,1fr) !important }`
 *     - line 4480 : `document.body.classList.toggle('mobile-mode', isMobile)`
 *     - line 8424 : `document.body.classList.toggle('mobile-mode', isMobile)`
 *   → react-native-webview 의 device width (iPhone 14 Pro = 390, Galaxy S22 = 360 < 1280) → mobile-mode 자동.
 *
 * 환경변수 override:
 *   - `EXPO_PUBLIC_ORDER_APP_URL` 가 정의되면 dev/prod 분기 무시하고 사용.
 *
 * UUID 미노출:
 *   - order-legacy v4 의 EJS 자체가 사업자번호/거래처코드/모델명 만 노출 (UUID X).
 */

const DEFAULT_DEV_URL = 'http://localhost:5185/';
const DEFAULT_PROD_URL = 'https://order.samhan-air.com/';

export interface LegacyOrderUriOptions {
  /** dev override URL — Expo Go LAN 테스트용 (e.g. `http://192.168.0.5:5185/`). */
  devOverride?: string;
}

/**
 * Expo SDK 53 의 `EXPO_PUBLIC_ORDER_APP_URL` 환경변수 우선 채택.
 */
function resolveBaseUrl(devOverride?: string): string {
  if (devOverride) return devOverride;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const proc = (globalThis as any).process as { env?: Record<string, string | undefined> } | undefined;
  const envUrl = proc?.env?.EXPO_PUBLIC_ORDER_APP_URL;
  if (envUrl) return envUrl;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const isDev = typeof (globalThis as any).__DEV__ !== 'undefined' ? (globalThis as any).__DEV__ : false;
  return isDev ? DEFAULT_DEV_URL : DEFAULT_PROD_URL;
}

/**
 * order-legacy v4 의 진입 URL.
 *
 * @param opts.devOverride — dev URL 강제 override.
 */
export function getLegacyOrderUri(opts: LegacyOrderUriOptions = {}): string {
  return resolveBaseUrl(opts.devOverride);
}

/**
 * v4 임무 명세 시그니처 — 무인자 helper. `getLegacyOrderUri()` 의 default 흐름과 동일.
 *
 * 사용 위치: `MobileOrderWebViewScreen.tsx` 의 `WebView source.uri`.
 */
export function getOrderAppUrl(): string {
  return getLegacyOrderUri();
}

/** dev / prod base URL pair (헬퍼 export — 디버깅 / .env.example 검증용). */
export const LEGACY_ORDER_URLS = {
  dev: DEFAULT_DEV_URL,
  prod: DEFAULT_PROD_URL,
};

/**
 * order-legacy v4 URL 환경변수 검증 — MobileOrderWebViewScreen mount 단계에서 호출 가능.
 *
 * @returns 환경변수 또는 default 가 정상 URL 형태인지 boolean.
 */
export function validateOrderAppUrl(): { ok: boolean; url: string; source: 'env' | 'default-dev' | 'default-prod' } {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const proc = (globalThis as any).process as { env?: Record<string, string | undefined> } | undefined;
  const envUrl = proc?.env?.EXPO_PUBLIC_ORDER_APP_URL;
  if (envUrl) {
    try {
      // eslint-disable-next-line no-new
      new URL(envUrl);
      return { ok: true, url: envUrl, source: 'env' };
    } catch {
      return { ok: false, url: envUrl, source: 'env' };
    }
  }
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const isDev = typeof (globalThis as any).__DEV__ !== 'undefined' ? (globalThis as any).__DEV__ : false;
  const url = isDev ? DEFAULT_DEV_URL : DEFAULT_PROD_URL;
  return { ok: true, url, source: isDev ? 'default-dev' : 'default-prod' };
}
