/**
 * Mobile v4 — WebView shim 스크립트.
 *
 * 목적:
 *   legacy `migration/source/scripts/partner-order/index.html` (9427 라인) 안에서
 *   호출되는 `google.script.run.<함수>(...).withSuccessHandler(...).withFailureHandler(...)`
 *   체인을 가로채서 SamhanLogis MS endpoint axios fetch 또는 noop 으로 라우팅한다.
 *
 * RN WebView 통합 핵심:
 *   - 본 스크립트는 `injectedJavaScriptBeforeContentLoaded` 로 주입된다.
 *   - WebView 안에서 `window.google.script.run` chain shim 을 정의한다.
 *   - 외부 호출 (e-Count + Notion) 은 모두 noop (정정: legacy 외부 호출 폐기, MS 단독).
 *   - SamhanLogis MS endpoint 는 `<WEBVIEW>` 안에서 직접 fetch 한다 (CORS allow + Authorization Bearer).
 *   - token 은 RN 측 BizGate 인증 후 `injectJavaScript('window.__SAMHAN_AUTH__ = ...')` 로 주입된다.
 *
 * Web v4 의 `legacy-rpc-mapping-partner-order.md` 와 매핑 1:1 동일.
 *
 * postMessage 사용:
 *   - WebView → RN: `window.ReactNativeWebView.postMessage(JSON.stringify({type, payload}))`
 *   - RN → WebView: `webViewRef.current.injectJavaScript('window.__SAMHAN_BRIDGE__.handle(...)')`
 *
 * 알려진 한계:
 *   - 본 shim 은 partner-order 의 12 RPC site (분석 §1.2) 매핑 만 다룬다.
 *   - 신규 RPC 추가 시 본 파일 + Web v4 의 매핑 표 동시 업데이트.
 */

/**
 * legacy index.html 안에서 실행될 IIFE 형태의 string.
 *
 * Note: TypeScript 안에서 string literal 로 보관 — 실행 컨텍스트는 WebView 내부.
 * 본 함수는 `webview/legacyShim.ts` 에서 `getInjectedShim()` 으로 export.
 */
export interface LegacyShimConfig {
  /** SamhanLogis API gateway base URL (예: `https://api.samhan-air.com` 또는 `http://localhost:8080`). */
  apiBaseUrl: string;
  /** BizGate 인증 token (Authorization Bearer). null 이면 인증 전. */
  token: string | null;
  /** 인증된 거래처 코드 — RPC 매핑에서 partnerCode query param 자동 추가. */
  partnerCode: string | null;
}

/**
 * legacy index.html 첫 로드 직전에 주입되는 shim.
 *
 * @param config — base URL / token / partnerCode 주입.
 * @returns IIFE JS string (`(()=>{ ... })();`)
 *
 * 핵심 책임:
 *  1. `window.google.script.run` chain shim 정의.
 *  2. `__SAMHAN_AUTH__` 전역 — token / partnerCode / apiBaseUrl 보관.
 *  3. `__SAMHAN_BRIDGE__` 전역 — RN 에서 token 갱신 / postMessage 핸들 등록.
 *  4. RPC 매핑 표 — Web v4 와 동일.
 *  5. 외부 호출 (e-Count + Notion) 은 모두 noop 처리.
 */
export function getInjectedShim(config: LegacyShimConfig): string {
  const { apiBaseUrl, token, partnerCode } = config;
  // 안전한 JSON 문자열 — 단일 인용부 escape.
  const safeBase = JSON.stringify(apiBaseUrl);
  const safeToken = JSON.stringify(token);
  const safePartner = JSON.stringify(partnerCode);

  return `
(function() {
  if (window.__SAMHAN_SHIM_INSTALLED__) return;
  window.__SAMHAN_SHIM_INSTALLED__ = true;

  // -------- Auth state (RN bridge 로 갱신 가능) --------
  window.__SAMHAN_AUTH__ = {
    apiBaseUrl: ${safeBase},
    token: ${safeToken},
    partnerCode: ${safePartner}
  };

  function postToRN(type, payload) {
    try {
      if (window.ReactNativeWebView && typeof window.ReactNativeWebView.postMessage === 'function') {
        window.ReactNativeWebView.postMessage(JSON.stringify({ type: type, payload: payload }));
      }
    } catch (_e) { /* swallow */ }
  }

  // -------- Bridge — RN → WebView 핸들 --------
  window.__SAMHAN_BRIDGE__ = {
    setAuth: function(next) {
      window.__SAMHAN_AUTH__ = Object.assign({}, window.__SAMHAN_AUTH__, next || {});
      try { window.dispatchEvent(new Event('samhan:auth')); } catch (_e) {}
    },
    handle: function(msg) {
      // RN → WebView 명령 라우팅 (확장 여지)
    },
    log: function(label, payload) { postToRN('log', { label: label, payload: payload }); }
  };

  // -------- HTTP helper (axios 대체 — fetch 직접) --------
  function buildUrl(path) {
    var base = (window.__SAMHAN_AUTH__.apiBaseUrl || '').replace(/\\/$/, '');
    if (!path) return base;
    if (/^https?:\\/\\//.test(path)) return path;
    return base + (path.charAt(0) === '/' ? path : '/' + path);
  }
  function authHeaders() {
    var h = { 'Accept': 'application/json', 'Content-Type': 'application/json' };
    if (window.__SAMHAN_AUTH__.token) h['Authorization'] = 'Bearer ' + window.__SAMHAN_AUTH__.token;
    return h;
  }
  async function http(method, path, body) {
    var url = buildUrl(path);
    var init = { method: method, headers: authHeaders(), credentials: 'omit' };
    if (body !== undefined) init.body = (typeof body === 'string') ? body : JSON.stringify(body);
    var res = await fetch(url, init);
    var text = await res.text();
    var data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_e) { data = text; }
    if (!res.ok) {
      var err = new Error('HTTP ' + res.status + ' ' + url);
      err.status = res.status;
      err.body = data;
      throw err;
    }
    return data;
  }

  // -------- RPC 매핑 (Web v4 의 legacy-rpc-mapping-partner-order.md 와 동일) --------
  // 분석 §1.2 partner-order index.html 의 google.script.run 12 RPC site:
  //   getProducts / getCustomers / getManagers / saveOrderSnapshot / getOrderSnapshotHistory
  //   sendOrderFromUi / requestAuthApproval / setAuthPassword / tryLogin / applyConfigFromServer
  //   logFrontEvent / getGateImages / getLogoImage 외 (총 12+ site)
  //
  // v4 정정: 외부 호출 (e-Count + Notion) 은 모두 폐기 (noop).
  var RPC = {
    // M1a product-service — 카테고리별 상품 + 분기 관 + 자재 가격
    getProducts: function() { return http('GET', '/api/v1/products?all=true'); },
    getHomeMulti: function() { return http('GET', '/api/v1/products?category=HOME_MULTI'); },
    getSingleSets: function() { return http('GET', '/api/v1/products?category=SINGLE_SET'); },
    getSingleParts: function() { return http('GET', '/api/v1/products?category=SINGLE_PART'); },
    getSingleMatPrices: function() { return http('GET', '/api/v1/material-prices'); },
    getCommercialMulti: function() { return http('GET', '/api/v1/products?category=COMMERCIAL_MULTI'); },
    getCommercialParts: function() { return http('GET', '/api/v1/products?category=COMMERCIAL_PART'); },
    getHomeDefaults: function() { return http('GET', '/api/v1/products/defaults?category=HOME_MULTI'); },
    getSingleDefaults: function() { return http('GET', '/api/v1/products/defaults?category=SINGLE_SET'); },

    // partner-service — 거래처 / 담당자
    getCustomers: function() { return http('GET', '/api/v1/partners?all=true'); },
    searchCustomerByBizOrCode: function(q) { return http('GET', '/api/v1/partners/search?q=' + encodeURIComponent(q)); },
    getManagers: function() { return http('GET', '/api/v1/employees?role=MANAGER'); },

    // partner-order-service — 주문 snapshot / 발송 / 분기계산
    saveOrderSnapshot: function(payload) { return http('POST', '/api/v1/partner-orders/snapshots', payload); },
    getOrderSnapshotHistory: function(filter) {
      var q = filter ? '?bizNo=' + encodeURIComponent(filter.bizNo || '') + '&date=' + encodeURIComponent(filter.date || '') : '';
      return http('GET', '/api/v1/partner-orders/snapshots' + q);
    },
    sendOrderFromUi: function(payload) { return http('POST', '/api/v1/partner-orders', payload); },
    saveBranchCalc: function(payload) { return http('POST', '/api/v1/branch-calcs', payload); },

    // partner-dc-service — DC config + 인증
    applyConfigFromServer: function() {
      var pc = window.__SAMHAN_AUTH__.partnerCode || '';
      return http('GET', '/api/v1/partners/' + encodeURIComponent(pc) + '/config');
    },
    requestAuthApproval: function(bizNo, isMobile) {
      return http('POST', '/api/v1/auth/biz-gate/request', { bizNo: bizNo, isMobile: !!isMobile });
    },
    setAuthPassword: function(bizNo, p1, isMobile) {
      return http('POST', '/api/v1/auth/biz-gate/set-password', { bizNo: bizNo, password: p1, isMobile: !!isMobile });
    },
    tryLogin: function(bizNo, pw, isMobile) {
      return http('POST', '/api/v1/auth/biz-gate/login', { bizNo: bizNo, password: pw, isMobile: !!isMobile });
    },

    // assets — gate 이미지 + 로고 (M1a Drive 마이그레이션 후 file-service)
    getGateImages: function() { return http('GET', '/api/v1/assets/gate-images'); },
    getLogoImage: function() { return http('GET', '/api/v1/assets/logo'); },

    // event log — 프런트 이벤트 (DataDog/log-service)
    logFrontEvent: function(bizNo, action, detail, isMobile) {
      return http('POST', '/api/v1/logs/front-events', { bizNo: bizNo, action: action, detail: detail, isMobile: !!isMobile });
    },

    // ---- 외부 호출 폐기 (e-Count + Notion noop) ----
    sendToECount: function() { postToRN('noop', { fn: 'sendToECount' }); return Promise.resolve({ noop: true }); },
    notionUpsert: function() { postToRN('noop', { fn: 'notionUpsert' }); return Promise.resolve({ noop: true }); },
    notionFetch: function() { postToRN('noop', { fn: 'notionFetch' }); return Promise.resolve({ ok: true, results: [] }); }
  };

  // -------- google.script.run chain shim --------
  function makeRunner() {
    var success = function() {};
    var failure = function(err) { console.warn('[SAMHAN shim] unhandled failure', err); };
    var chain = {
      withSuccessHandler: function(fn) { if (typeof fn === 'function') success = fn; return chain; },
      withFailureHandler: function(fn) { if (typeof fn === 'function') failure = fn; return chain; },
      withUserObject: function(_) { return chain; }
    };
    var proxy = new Proxy(chain, {
      get: function(target, prop) {
        if (prop in target) return target[prop];
        // RPC 함수 호출 → http call → success/failure
        return function() {
          var args = Array.prototype.slice.call(arguments);
          var fn = RPC[prop];
          if (!fn) {
            postToRN('rpc-missing', { name: prop, args: args });
            // 매핑 누락 — 안전한 빈 응답 (legacy 가 success 만 호출)
            try { success(null); } catch (e) { /* swallow */ }
            return;
          }
          Promise.resolve()
            .then(function() { return fn.apply(null, args); })
            .then(function(res) {
              postToRN('rpc-ok', { name: prop, status: 'ok' });
              try { success(res); } catch (e) { console.error('[SAMHAN shim] success handler error', e); }
            })
            .catch(function(err) {
              postToRN('rpc-error', { name: prop, message: String(err && err.message || err) });
              try { failure(err); } catch (e) { console.error('[SAMHAN shim] failure handler error', e); }
            });
        };
      }
    });
    return proxy;
  }

  window.google = window.google || {};
  window.google.script = window.google.script || {};
  window.google.script.run = makeRunner();
  // legacy 일부 코드는 google.script.host.close() 등 호출 → noop.
  window.google.script.host = window.google.script.host || {
    close: function() { postToRN('host-close', {}); },
    setHeight: function() {},
    setWidth: function() {}
  };

  // -------- 모바일 viewport 강제 + UA 보강 (legacy isMobileNow trigger) --------
  // legacy isMobileNow() = window.matchMedia('(max-width: 1280px)').matches → WebView width
  // 가 강제로 device width 이므로 자연스레 mobile 분기.
  // 추가로 viewport meta 가 없을 때 자동 삽입 (안전망).
  document.addEventListener('DOMContentLoaded', function() {
    if (!document.querySelector('meta[name="viewport"]')) {
      var m = document.createElement('meta');
      m.name = 'viewport';
      m.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
      document.head.appendChild(m);
    }
    postToRN('legacy-loaded', { url: location.href });
  });

  postToRN('shim-installed', { apiBaseUrl: window.__SAMHAN_AUTH__.apiBaseUrl, hasToken: !!window.__SAMHAN_AUTH__.token });
})();
true; // RN WebView injected JS 마지막 표현식 truthy 권장
`;
}

/**
 * RN → WebView 의 token 갱신 명령.
 *
 * BizGate 통과 후 또는 logout 시 RN 에서 호출.
 * `webViewRef.current?.injectJavaScript(setAuthScript({...}))`.
 */
export function setAuthScript(next: Partial<LegacyShimConfig>): string {
  const safeNext = JSON.stringify({
    apiBaseUrl: next.apiBaseUrl,
    token: next.token,
    partnerCode: next.partnerCode,
  });
  return `
    (function() {
      try {
        if (window.__SAMHAN_BRIDGE__ && typeof window.__SAMHAN_BRIDGE__.setAuth === 'function') {
          window.__SAMHAN_BRIDGE__.setAuth(${safeNext});
        }
      } catch (e) { console.error('[SAMHAN] setAuth failed', e); }
    })();
    true;
  `;
}
