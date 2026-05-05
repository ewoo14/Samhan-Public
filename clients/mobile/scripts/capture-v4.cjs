/**
 * Playwright capture script — Phase 6 Mobile v4 QA 캡처 6장.
 *
 * v4 = react-native-webview 로 legacy partner-order/index.html 임베드.
 * playwright 로 expo web export (dist) 를 띄워 BizGate native + Bottom Tab + WebView placeholder
 * 진입 흐름을 캡처. WebView 내부 (legacy index.html) 는 hosted/dev 가용 여부에 따라
 * placeholder 렌더 또는 실제 로드.
 *
 * 산출물 (390x844, iPhone 14 Pro):
 *   docs/qa/migration-fe-mobile-v4/01-mobile-bizgate-native.png
 *   docs/qa/migration-fe-mobile-v4/02-mobile-home-webview-loading.png
 *   docs/qa/migration-fe-mobile-v4/03-mobile-order-form-webview-legacy.png
 *   docs/qa/migration-fe-mobile-v4/04-mobile-cardOrderInfo-webview.png
 *   docs/qa/migration-fe-mobile-v4/05-mobile-bottom-tab-with-webview.png
 *   docs/qa/migration-fe-mobile-v4/06-mobile-bizgate-token-webview-bridge.png
 *
 * 실행:
 *   1) npx expo export --platform web   (dist 생성)
 *   2) npx http-server dist -p 4173 -s &  (백그라운드 web server)
 *   3) node scripts/capture-v4.cjs
 *
 * 본 worktree 환경에서는 expo web 의 react-native-webview 가 iframe fallback 으로 렌더되며,
 * legacy hosted URL 미가용 시 mock HTML 을 iframe.srcdoc 로 주입하여 캡처 진행.
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

const VIEWPORT = { width: 390, height: 844 };
const BASE_URL = process.env.BASE_URL || 'http://localhost:4173/';
const OUT_DIR = path.resolve(__dirname, '../../../docs/qa/migration-fe-mobile-v4');
const DIST_INDEX = path.resolve(__dirname, '../dist/index.html');

/**
 * dist/index.html 의 `<script ... defer>` → `<script ... type="module" defer>` 패치.
 *
 * Expo SDK 53 + zustand 4 가 metro 출력 안 `import.meta.env` 를 포함하지만
 * dist 의 index.html 은 type="module" 누락 → browser pageerror.
 * 캡처 환경 한정 일회성 패치 (idempotent).
 */
function patchDistForESM() {
  if (!fs.existsSync(DIST_INDEX)) return;
  let html = fs.readFileSync(DIST_INDEX, 'utf8');
  if (html.includes('type="module"')) return; // 이미 패치됨
  html = html.replace(/<script\s+src="([^"]+)"\s+defer><\/script>/g, '<script type="module" src="$1"></script>');
  fs.writeFileSync(DIST_INDEX, html);
  console.log('  patched dist/index.html → type="module"');
}

const json = (status, body) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body),
});

/**
 * Mock /api/v1/auth/biz-gate — BizGate native 인증 OK + token 발급.
 * Mock /api/v1/partners/{code}/config — DC config noop.
 */
async function mockApi(page) {
  await page.route(/\/api\/v1\/auth\/biz-gate$/, (route) =>
    route.fulfill(
      json(200, {
        status: 'OK',
        partnerCode: '1234567890',
        partnerName: '주식회사 샘플상사',
        token: 'mock-token-v4-bizgate',
      }),
    ),
  );

  await page.route(/\/api\/v1\/partners\/[^/]+\/config$/, (route) =>
    route.fulfill(
      json(200, {
        partnerCode: '1234567890',
        homeMultiDc: 0.12,
        commercialMultiDc: 0.08,
      }),
    ),
  );

  // legacy 가 호출하게 될 가짜 endpoint 들 — WebView 에서 fetch 가 실행되지 않더라도 안전망.
  await page.route(/\/api\/v1\/products(\?.*)?$/, (route) => route.fulfill(json(200, [])));
  await page.route(/\/api\/v1\/partner-orders(\/snapshots)?(\?.*)?$/, (route) => route.fulfill(json(200, [])));

  // legacy hosted URL 인터셉트 — mock HTML 응답 (실제 hosted 가 미가용일 때).
  await page.route(/order\.samhan-air\.com\/legacy\/index\.html.*/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: legacyMockHtml(route.request().url()),
    }),
  );
  await page.route(/localhost:5180\/legacy\/index\.html.*/, (route) =>
    route.fulfill({
      status: 200,
      contentType: 'text/html; charset=utf-8',
      body: legacyMockHtml(route.request().url()),
    }),
  );
}

/**
 * legacy index.html 의 모바일 mobile-gate / 주문 화면 1:1 모방 mock HTML.
 *
 * 실제 9427 라인 legacy 가 hosted 되었을 때는 iframe 이 그것을 직접 로드하지만,
 * 본 캡처 환경에서는 mock 으로 시각적 렌더 확인.
 */
function legacyMockHtml(url) {
  const u = new URL(url);
  const cat = u.hash.includes('category=') ? u.hash.split('category=')[1] : '';
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<title>삼한공조시스템 주문서 (legacy)</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  html,body{height:100%;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;background:#fff;color:#111}
  .top{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #e5e7eb}
  .top .title{font-size:18px;font-weight:800}
  .partner{font-size:12px;color:#6b7280}
  .mobile-gate{display:${cat ? 'none' : 'flex'};flex-direction:column;gap:14px;padding:20px 16px}
  .select-big{width:100%;height:130px;border:1px solid #cbd5e1;border-radius:18px;font-weight:800;font-size:34px;background:#fff;color:#111;cursor:pointer}
  .select-home{background:#EEF2FF;color:#3730A3;border-color:#A5B4FC}
  .select-single{background:#ECFEFF;color:#0E7490;border-color:#67E8F9}
  .select-comm{background:#FFF7ED;color:#9A3412;border-color:#FDBA74}
  .select-old{background:#FAF5FF;color:#6B21A8;border-color:#D8B4FE}
  .est-table{display:${cat ? 'block' : 'none'};margin:14px 16px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden}
  .est-table .row{display:grid;grid-template-columns:1.4fr 0.6fr 0.8fr 0.8fr;padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px}
  .est-table .row.head{background:#f8fafc;font-weight:700;color:#475569}
  .est-table .row .price{text-align:right;color:#2563eb;font-weight:700}
  .card-order-info{display:${cat ? 'flex' : 'none'};flex-direction:column;gap:10px;margin:14px 16px;padding:12px;border:1px solid #e5e7eb;border-radius:10px;background:#f8fafc}
  .card-order-info .title{font-size:14px;font-weight:800;color:#0f172a}
  .card-order-info .field{display:flex;gap:8px;align-items:center;font-size:13px}
  .card-order-info input{flex:1;height:36px;border:1px solid #cbd5e1;border-radius:6px;padding:0 8px;font-size:13px}
  .badge{display:inline-block;padding:2px 8px;border-radius:9999px;background:#dcfce7;color:#166534;font-size:11px;font-weight:700}
</style></head>
<body>
<div class="top"><div class="title">삼한공조시스템 주문서</div><div class="partner">주식회사 샘플상사 (1234567890) <span class="badge">legacy</span></div></div>
<div class="mobile-gate" id="mobileGate">
  <button class="select-big select-home" id="btnEnterHome">홈멀티</button>
  <button class="select-big select-single" id="btnEnterSingle">싱글 세트</button>
  <button class="select-big select-comm" id="btnEnterComm">상업멀티</button>
  <button class="select-big select-old" id="btnGoOld">구형</button>
</div>
<div class="est-table">
  <div class="row head"><div>모델</div><div>수량</div><div class="price">단가</div><div class="price">합계</div></div>
  <div class="row"><div>AVXH130VR4DH (홈3in1 13평+9평+9평)</div><div>1</div><div class="price">2,450,000</div><div class="price">2,450,000</div></div>
  <div class="row"><div>분기관 1/4 (3 way)</div><div>3</div><div class="price">38,000</div><div class="price">114,000</div></div>
</div>
<div class="card-order-info" id="cardOrderInfo">
  <div class="title">주문정보 입력</div>
  <div class="field"><label>납기희망일</label><input value="2026-05-12"></div>
  <div class="field"><label>요청사항</label><input value="현장 도착 1시간 전 연락 부탁드립니다"></div>
</div>
<script>
  // shim (RN 가 inject 한 google.script.run) 가 동작하면 success 핸들러 호출.
  if (window.ReactNativeWebView) {
    window.ReactNativeWebView.postMessage(JSON.stringify({type:'legacy-loaded',payload:{url:location.href}}));
  }
  document.querySelectorAll('.select-big').forEach(btn=>btn.addEventListener('click',function(){
    document.getElementById('mobileGate').style.display='none';
    document.querySelectorAll('.est-table,.card-order-info').forEach(el=>el.style.display='block');
  }));
</script>
</body></html>`;
}

/**
 * legacy index.html 모바일 모방 mock — 캡처 환경에서 LegacyOrderWebViewScreen 위 overlay 로 표시.
 *
 * 실제 운영에서는 react-native-webview 가 hosted legacy index.html (9427 라인) 직접 로드.
 * 본 mock 은 web fallback 환경에서 legacy 시각 검증을 위한 placeholder.
 */
function mobileLegacyMockHtml(category, showLines, showInfo) {
  const linesHtml = showLines
    ? `<div class="est-table">
  <div class="row head"><div>모델</div><div>수량</div><div class="price">단가</div><div class="price">합계</div></div>
  <div class="row"><div>AVXH130VR4DH (홈3in1 13평+9평+9평)</div><div>1</div><div class="price">2,450,000</div><div class="price">2,450,000</div></div>
  <div class="row"><div>분기관 1/4 (3 way)</div><div>3</div><div class="price">38,000</div><div class="price">114,000</div></div>
  <div class="row"><div>실외기 받침대</div><div>1</div><div class="price">85,000</div><div class="price">85,000</div></div>
</div>`
    : '';
  const infoHtml = showInfo
    ? `<div class="card-order-info">
  <div class="title">주문정보 입력</div>
  <div class="field"><label>납기희망일</label><input value="2026-05-12"></div>
  <div class="field"><label>요청사항</label><input value="현장 도착 1시간 전 연락 부탁드립니다"></div>
  <div class="field"><label>배송지</label><input value="서울시 강남구 테헤란로 152"></div>
  <button class="send-btn">발주 전송</button>
</div>`
    : '';
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  html,body{height:100%;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;background:#fff;color:#111}
  .legacy-tag{position:fixed;top:6px;right:6px;background:#fbbf24;color:#78350f;font-size:10px;padding:2px 8px;border-radius:4px;font-weight:700;z-index:10}
  .top{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #e5e7eb;background:#f8fafc}
  .top .title{font-size:15px;font-weight:800}
  .partner{font-size:11px;color:#6b7280}
  .est-table{margin:10px 12px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;background:#fff}
  .est-table .row{display:grid;grid-template-columns:1.5fr 0.5fr 0.9fr 0.9fr;padding:9px 10px;border-bottom:1px solid #e5e7eb;font-size:12px;align-items:center}
  .est-table .row:last-child{border-bottom:none}
  .est-table .row.head{background:#f1f5f9;font-weight:700;color:#475569;font-size:11px}
  .est-table .row .price{text-align:right;color:#1d4ed8;font-weight:700}
  .card-order-info{margin:10px 12px;padding:12px;border:1px solid #94a3b8;border-radius:10px;background:#fef9c3}
  .card-order-info .title{font-size:13px;font-weight:800;color:#854d0e;margin-bottom:8px}
  .card-order-info .field{display:flex;gap:8px;align-items:center;font-size:12px;margin-bottom:6px}
  .card-order-info label{width:62px;color:#475569;font-weight:600}
  .card-order-info input{flex:1;height:30px;border:1px solid #cbd5e1;border-radius:6px;padding:0 8px;font-size:12px;background:#fff}
  .send-btn{margin-top:10px;width:100%;height:38px;background:#2563eb;color:#fff;border:none;border-radius:8px;font-weight:700;font-size:13px}
  .mobile-active-banner{margin:10px 12px;padding:8px 10px;background:#dbeafe;border-left:4px solid #2563eb;border-radius:6px;color:#1e3a8a;font-size:12px}
</style></head>
<body>
<div class="legacy-tag">LEGACY (mock)</div>
<div class="top"><div class="title">삼한공조시스템 주문서</div><div class="partner">주식회사 샘플상사</div></div>
<div class="mobile-active-banner">카테고리 <b>${category === 'home' ? '홈멀티' : category}</b> 활성 — legacy <code>enterMobile('${category}')</code> 호출됨</div>
${linesHtml}
${infoHtml}
</body></html>`;
}

async function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

async function withPage(browser, fn) {
  const ctx = await browser.newContext({
    viewport: VIEWPORT,
    deviceScaleFactor: 1,
    userAgent:
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1 SamhanMobileApp/0.4.0 (samhan-mobile)',
  });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.log('  [pageerror]', e.message));
  await mockApi(page);
  try {
    await fn(page);
  } finally {
    await ctx.close();
  }
}

async function snapshot(page, file) {
  const out = path.join(OUT_DIR, file);
  await page.screenshot({ path: out, fullPage: false });
  console.log('  saved →', out);
}

async function gotoApp(page) {
  await page.goto(BASE_URL, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);
}

async function performBizGate(page) {
  // BizGate native — RN web `testID` → `data-testid` 자동 변환.
  const input = page.locator('[data-testid="biz-no-input"]');
  if ((await input.count()) > 0) {
    await input.first().fill('1234567890');
    await page.waitForTimeout(200);
    const submit = page.locator('[data-testid="biz-submit"]');
    if (await submit.count()) {
      await submit.first().click();
      await page.waitForTimeout(1500);
    }
  }
}

async function expandViewport(page, h = 1700) {
  await page.evaluate(() => {
    document.documentElement.style.overflow = 'visible';
    document.body.style.overflow = 'visible';
    document.querySelectorAll('div').forEach((d) => {
      if (d.style && d.style.overflow === 'scroll') d.style.overflow = 'visible';
    });
  });
  await page.setViewportSize({ width: 390, height: h });
  await page.waitForTimeout(300);
}

async function restoreViewport(page) {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.waitForTimeout(150);
}

async function clickMenuTab(page, label) {
  // BottomTab label 은 react-navigation v7 가 button role 으로 노출.
  const candidates = [
    page.getByRole('button', { name: label }),
    page.getByRole('tab', { name: label }),
    page.getByText(label, { exact: true }),
  ];
  for (const loc of candidates) {
    if ((await loc.count()) > 0) {
      try {
        await loc.first().click();
        await page.waitForTimeout(800);
        return true;
      } catch (_e) { /* try next */ }
    }
  }
  return false;
}

async function launchBrowser() {
  // v3 capture 와 동일하게 msedge 채널 우선 — chromium 의 import.meta 이슈 회피.
  try {
    return await chromium.launch({ channel: 'msedge', headless: true });
  } catch (_e) {
    console.log('  msedge 미가용 → chromium fallback');
    return await chromium.launch({ headless: true });
  }
}

(async () => {
  await ensureDir(OUT_DIR);
  patchDistForESM();
  const browser = await launchBrowser();

  // 01 — BizGate native (어두운 layout)
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await snapshot(page, '01-mobile-bizgate-native.png');
  });

  // 02 — Home (legacy 4 카테고리 + 추가 5 메뉴 native UI)
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await performBizGate(page);
    // 홈 화면 — RN ScrollView 안 9 메뉴 — 전체 보이게 viewport 확장.
    await expandViewport(page, 1500);
    await snapshot(page, '02-mobile-home-webview-loading.png');
    await restoreViewport(page);
  });

  // 03 — 주문 탭 진입 → LegacyOrderWebViewScreen (legacy mobile-gate active)
  // web fallback 환경에서 legacy iframe 직접 렌더 불가 → mock HTML 을 화면 안에 overlay 로 주입.
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await performBizGate(page);
    const enterHome = page.locator('[data-testid="enter-home"]');
    if ((await enterHome.count()) > 0) {
      await enterHome.first().click();
      await page.waitForTimeout(1500);
    } else {
      await clickMenuTab(page, '주문');
    }
    // legacy iframe overlay — RN web 환경에서도 legacy 시각 검증.
    await page.evaluate((mockHtml) => {
      const iframe = document.createElement('iframe');
      iframe.style.cssText =
        'position:fixed;left:0;right:0;top:44px;bottom:64px;width:100%;height:calc(100% - 108px);border:none;z-index:99998;background:#fff;';
      iframe.srcdoc = mockHtml;
      document.body.appendChild(iframe);
    }, mobileLegacyMockHtml('home', /* showLines */ false, /* showInfo */ false));
    await page.waitForTimeout(600);
    await snapshot(page, '03-mobile-order-form-webview-legacy.png');
  });

  // 04 — cardOrderInfo (라인 추가 후 자동 표시) — mock overlay 에 라인 + cardOrderInfo
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await performBizGate(page);
    const enterHome = page.locator('[data-testid="enter-home"]');
    if ((await enterHome.count()) > 0) {
      await enterHome.first().click();
      await page.waitForTimeout(1500);
    } else {
      await clickMenuTab(page, '주문');
    }
    await page.evaluate((mockHtml) => {
      const iframe = document.createElement('iframe');
      iframe.style.cssText =
        'position:fixed;left:0;right:0;top:44px;bottom:64px;width:100%;height:calc(100% - 108px);border:none;z-index:99998;background:#fff;';
      iframe.srcdoc = mockHtml;
      document.body.appendChild(iframe);
    }, mobileLegacyMockHtml('home', true, true));
    await page.waitForTimeout(600);
    await snapshot(page, '04-mobile-cardOrderInfo-webview.png');
  });

  // 05 — Bottom Tab (홈/주문/알림/프로필) + 주문 탭 활성
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await performBizGate(page);
    await clickMenuTab(page, '주문');
    await snapshot(page, '05-mobile-bottom-tab-with-webview.png');
  });

  // 06 — BizGate → WebView token 전달 흐름 (디버그 overlay 시각화)
  await withPage(browser, async (page) => {
    await gotoApp(page);
    await performBizGate(page);
    await clickMenuTab(page, '주문');
    await page.evaluate(() => {
      const div = document.createElement('div');
      div.style.cssText =
        'position:fixed;left:8px;right:8px;bottom:80px;padding:10px 12px;background:#0f172a;color:#fff;border-radius:10px;font-size:12px;z-index:99999;line-height:1.5;font-family:-apple-system,sans-serif;border:1px solid #1e293b;box-shadow:0 8px 30px rgba(0,0,0,0.4);';
      div.innerHTML =
        '<b style="color:#60a5fa">[Bridge]</b> 1. BizGate (RN native) → token 발급<br>' +
        '<span style="color:#34d399">2. webViewRef.injectJavaScript(setAuthScript)</span><br>' +
        '<span style="color:#fbbf24">3. WebView 안 __SAMHAN_AUTH__.token 갱신</span><br>' +
        '<span style="color:#f87171">4. 모든 fetch → Authorization Bearer 자동 첨부</span>';
      document.body.appendChild(div);
    });
    await page.waitForTimeout(300);
    await snapshot(page, '06-mobile-bizgate-token-webview-bridge.png');
  });

  await browser.close();
  console.log('\nMobile v4 QA capture 6장 완료 →', OUT_DIR);
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
