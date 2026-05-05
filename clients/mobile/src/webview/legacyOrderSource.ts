/**
 * Mobile v4 — order-app v4 (Vite + PWA, legacy partner-order/index.html 9427 라인 임베드) 의 source URL 결정.
 *
 * 회고 #2 (2026-05-05) — 사용자 명시:
 *   "주문서는 여전히 구글 스크립트 모바일 버전의 UI와 처음 모바일 게이트를 제외한 나머지는
 *    모두 다름을 확인. ... 종합견적서 모바일용 앱은 구글 스크립트를 거의 그대로 계승한 것으로 보이나..."
 *
 * 정정 결정 (mobile-staff v3 와 동일 패턴 1:1):
 *   - 이전 v4 = `legacySource.ts` 가 web/order-app v4 (Vite) `/legacy/index.html` 임베드
 *     → RN HomeScreen + extra-menu 가 별도 noise 추가 (legacy 미존재).
 *   - 신규 v4 = `legacyOrderSource.ts` 가 web/order-app v4 의 root `/` 임베드
 *     → order-app v4 의 `index.html` 자체가 legacy partner-order/index.html (9427 라인) 그대로 +
 *       `<script type="module" src="/src/main.ts">` 한 줄 (shim) 만 추가.
 *     → estimate-app v2 와 동일하게 mobile-mode CSS 분기를 100% legacy 가 처리.
 *
 * 정정 #2 (2026-05-05 PR #70 revert 후속):
 *   - PR #70 으로 legacy-v2 (clients/web/order-legacy 의 Express + EJS 포팅, port 5185) 가 main 에서 제거됨.
 *   - 본 source 는 운영 시 작동하지 않는 :5185 가 아니라, main 에 존재하는 order-app v4 (Vite) 를 가리킴.
 *
 * 운영:
 *   - dev (`__DEV__ === true`): `http://localhost:4173`
 *     (clients/web/order-app v4 — vite preview --port 4173 --strictPort,
 *      또는 vite dev `npm run dev` = config 의 server.port 5180,
 *      또는 vite preview = config 의 preview.port 5181 — 환경마다 상이.
 *      default 는 PM 환경에서 가동 중인 :4173 (preview 포트 명시 override).
 *      Expo Go LAN 테스트 시 EXPO_PUBLIC_ORDER_APP_URL 로 덮어쓰기.)
 *   - production: `https://order.samhan-air.com`
 *     (DECISIONS Phase 6 Section 4 sub-domain)
 *
 * 모바일 자동 분기 (코드 변경 0):
 *   - order-app v4 의 index.html (legacy partner-order/index.html 1:1):
 *     - line 128  : `.mobile-gate { display:none; flex-direction:column; gap:16px; margin:20px 0 12px }`
 *     - line 129  : `body.no-active .mobile-gate { display:flex }` (인증 통과 후 default 4 카테고리 노출)
 *     - line 132  : `body.mobile-mode .grid { grid-template-columns: minmax(0,1fr) !important }`
 *     - line 4491 : `document.body.classList.toggle('mobile-mode', isMobile)`
 *     - line 8435 : `document.body.classList.toggle('mobile-mode', isMobile)`
 *   → react-native-webview 의 device width (iPhone 14 Pro = 390, Galaxy S22 = 360 < 1280) → mobile-mode 자동.
 *
 * 환경변수 override:
 *   - `EXPO_PUBLIC_ORDER_APP_URL` 가 정의되면 dev/prod 분기 무시하고 사용.
 *     예) `EXPO_PUBLIC_ORDER_APP_URL=http://localhost:5180` (vite dev 5180),
 *         `EXPO_PUBLIC_ORDER_APP_URL=http://192.168.0.5:4173` (Expo Go LAN preview).
 *
 * UUID 미노출:
 *   - order-app v4 의 임베드된 legacy 자체가 사업자번호/거래처코드/모델명 만 노출 (UUID X).
 */

const DEFAULT_DEV_URL = 'http://localhost:4173';
const DEFAULT_PROD_URL = 'https://order.samhan-air.com';

export interface LegacyOrderUriOptions {
  /** dev override URL — Expo Go LAN 테스트용 (e.g. `http://192.168.0.5:4173`). */
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
 * order-app v4 의 진입 URL.
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
 * order-app v4 URL 환경변수 검증 — MobileOrderWebViewScreen mount 단계에서 호출 가능.
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
