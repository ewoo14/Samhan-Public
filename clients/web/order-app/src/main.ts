/**
 * Vite entry — clients/web/order-app v4 (legacy partner-order/index.html 임베드).
 *
 * <p>역할:
 * 1. shim 설치 (synchronous) — `window.google.script.run` Proxy + UrlFetchApp noop
 * 2. 빈 부트스트랩 객체 즉시 주입 — legacy inline script (line 1230~) 가 안전하게 동작
 * 3. 비동기 부트스트랩 prefetch — `/api/v1/partner-orders/bootstrap` 응답을
 *    `window.__SAMHAN_BOOTSTRAP__` 에 병합 + `samhan:bootstrap-ready` CustomEvent 발행
 * 4. PWA service worker 등록 (vite-plugin-pwa virtual import)
 *
 * <p>실행 순서 보증:
 * - 본 모듈 (`<script type="module">` in head) 은 기본적으로 defer 동작 (HTML parse 완료 후 실행)
 * - legacy inline `<script>` (body, line 1226~9437) 는 parser-blocking 이라 본 모듈보다 먼저 실행됨
 * - 그러나 legacy 의 실제 render/init (initGate / renderHome / initEvents 등 9029~9047 라인) 은
 *   `DOMContentLoaded` 시점에 실행 → 본 모듈의 sync 부분이 그 전에 완료됨
 * - shim 의 sync 부분 (window.google + window.__SAMHAN_BOOTSTRAP__={}) 만 보장하면 OK
 *
 * <p>제한 (TODO M4 backend):
 * - `/api/v1/partner-orders/bootstrap` 미구현 → 부트스트랩은 빈 객체. legacy 카탈로그 (홈멀티/싱글/상업) 는
 *   비어있는 상태로 진입. BizGate / 로그인 / mobile-gate 는 정상 동작 (RPC 12 site shim 만 의존).
 */
import { installLegacyShim } from './legacyShim'
import { samhanApi } from './samhanApi'

// ─── 1) shim 동기 설치 + 빈 부트스트랩 — legacy inline script 가 즉시 사용 가능 ───
installLegacyShim({})

// ─── 2) 비동기 부트스트랩 prefetch + window 객체 갱신 + CustomEvent ───
samhanApi
  .fetchBootstrap()
  .then((bootstrap) => {
    Object.assign(window.__SAMHAN_BOOTSTRAP__ || {}, bootstrap)
    // legacy 가 listen 하면 재렌더, 미구현이어도 무영향
    document.dispatchEvent(
      new CustomEvent('samhan:bootstrap-ready', { detail: bootstrap }),
    )
  })
  .catch((err: unknown) => {
    console.warn('[v4 main] bootstrap prefetch error', err)
  })

// ─── 3) PWA service worker 등록 (vite-plugin-pwa virtual module) ───
if ('serviceWorker' in navigator) {
  // virtual:pwa-register 는 vite-plugin-pwa 가 빌드 시 주입. 타입은 plugin 의 client.d.ts.
  import('virtual:pwa-register')
    .then(({ registerSW }) => {
      registerSW({
        immediate: true,
        onOfflineReady() {
          console.info('[v4 PWA] offline ready')
        },
        onNeedRefresh() {
          console.info('[v4 PWA] new version available — reload to apply')
        },
      })
    })
    .catch((err: unknown) => {
      // dev 환경 또는 plugin 미주입 시 swallow
      console.info('[v4 PWA] registerSW skipped', err)
    })
}
