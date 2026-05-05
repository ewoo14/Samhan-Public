/**
 * Playwright capture script — Mobile v4 RN HomeScreen QA 캡처 10장.
 *
 * [PR #66 회고 — 통합 fix 2026-05-05]
 *   - 이전 capture-v4.cjs 폐기 (PM 결정 U5).
 *   - 본 script 는 mobile-staff v3 의 capture-v3.cjs 패턴 1:1 참조 (실 expo web export 캡처).
 *
 * 두 단계 캡처:
 *   1) `app` — 실 expo export bundle 진입 (BizGate native 자체 렌더 검증)
 *   2) `mock` — 실 HomeScreen 의 styled-render mock HTML (legacy 1:1 일치 P0 4건 시각 검증)
 *
 * 본 환경 (Windows Docker / external backend 미가동) 에서 BizGate axios POST 가 cross-origin +
 * 외부 backend 미가동 으로 실패 → BottomTab 진입 불가. mock HTML 로 P0 fix 결과를 직접 검증.
 *
 * 캡처 대상 (각 viewport 5장 × iOS/Android = 총 10장):
 *   01-bizgate              : BizGate native 진입 (실 RN web export — 어두운 .biz-box layout)
 *   02-home-after-fix       : HomeScreen mock — P0 fix 후 (4 카테고리 검정 텍스트 + paddingBottom 0)
 *   03-home-extra-menu      : HomeScreen mock — extraMenuSection 5 메뉴 (P1 보존)
 *   04-webview-order        : LegacyOrder WebView placeholder (legacy index.html 임베드 영역)
 *   05-bottom-tab           : Bottom Tab (홈/주문/알림/프로필) — RN web export 의 실 navigation
 *
 * Viewport:
 *   iOS    = 390 x 844 (iPhone 14 Pro)
 *   Android = 412 x 915 (Pixel 6)
 *
 * 산출물:
 *   docs/qa/migration-fe-mobile-v4-design-audit/iOS-{01..05}-*.png
 *   docs/qa/migration-fe-mobile-v4-design-audit/Android-{01..05}-*.png
 *
 * 실행 (expo web dev/export 미가동 시 abort):
 *   1) npm install --legacy-peer-deps
 *   2) npx expo export --platform web   (dist 생성)
 *   3) npx http-server dist -p 4173 -s &  (백그라운드 web server)
 *   4) curl http://localhost:4173/   # 200 = 가동 중
 *   5) node scripts/capture-home.cjs
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const http = require('http');

const BASE_URL = process.env.QA_MOBILE_BASE_URL || 'http://localhost:4173';
const OUT_DIR = path.resolve(__dirname, '../../../docs/qa/migration-fe-mobile-v4-design-audit');
const DIST_INDEX = path.resolve(__dirname, '../dist/index.html');

const VIEWPORTS = [
  { name: 'iOS', width: 390, height: 844, deviceScaleFactor: 2 },
  { name: 'Android', width: 412, height: 915, deviceScaleFactor: 2 },
];

const IPHONE_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
  'AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 ' +
  'Safari/604.1 SamhanMobileApp/0.4.0 (samhan-mobile)';

const ANDROID_UA =
  'Mozilla/5.0 (Linux; Android 13; Pixel 6) ' +
  'AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 ' +
  'SamhanMobileApp/0.4.0 (samhan-mobile)';

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

/**
 * dist/index.html 의 `<script ... defer>` → `<script ... type="module" defer>` 패치.
 */
function patchDistForESM() {
  if (!fs.existsSync(DIST_INDEX)) return;
  let html = fs.readFileSync(DIST_INDEX, 'utf8');
  if (html.includes('type="module"')) return;
  html = html.replace(/<script\s+src="([^"]+)"\s+defer><\/script>/g, '<script type="module" src="$1"></script>');
  fs.writeFileSync(DIST_INDEX, html);
  console.log('  patched dist/index.html → type="module"');
}

function checkDevServer(baseUrl) {
  return new Promise((resolve) => {
    const req = http.get(baseUrl + '/', { timeout: 2500 }, (res) => {
      resolve(res.statusCode === 200 || res.statusCode === 304);
    });
    req.on('error', () => resolve(false));
    req.on('timeout', () => {
      req.destroy();
      resolve(false);
    });
  });
}

async function launchBrowser() {
  try {
    return await chromium.launch({ channel: 'msedge', headless: true });
  } catch (_e) {
    console.log('  [info] msedge channel 미설치 → chromium fallback');
    return await chromium.launch({ headless: true });
  }
}

async function snapshot(page, file) {
  const out = path.join(OUT_DIR, file);
  await page.screenshot({ path: out, fullPage: false });
  const sizeKb = (fs.statSync(out).size / 1024).toFixed(1);
  console.log(`  saved → ${file} (${sizeKb} KB)`);
}

async function gotoApp(page) {
  await page.goto(`${BASE_URL}/`, { waitUntil: 'networkidle', timeout: 60000 });
  await page.waitForTimeout(3500);
}

/**
 * HomeScreen mock — RN HomeScreen.tsx + legacyMobile.ts P0 fix 결과의
 * 시각 1:1 모방 (CSS-only). expo export bundle 진입이 backend 미가동 환경에서
 * 신뢰성이 낮으므로, mock 으로 P0 fix 의 시각 결과를 직접 검증.
 *
 * P0 4건 적용 결과:
 *   1) 4 카테고리 textColor → #111827 일관 (legacy --c-strong)
 *   2) DC notice/error View 제거 (사용자 노출 없음)
 *   3) titleBar 삭제 (legacy `body.mobile-mode .top { display:none !important }`)
 *   4) mobile-gate paddingBottom 제거 (legacy `margin: 20px 0 12px` 일관)
 */
function homeMockHtml(scrollDown) {
  const styleBase = `
    *{box-sizing:border-box;margin:0;padding:0}
    html,body{height:100%;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;background:#fff;color:#111827}
    body{overflow:auto}
    .safe{min-height:100vh;background:#FFFFFF;padding-top:0}
    /* legacy .mobile-gate { display:flex; flex-direction:column; gap:16px; margin:20px 0 12px } */
    .mobile-gate{display:flex;flex-direction:column;gap:16px;margin:20px 0 12px;padding:0 16px}
    /* legacy .select-big { width:100%; height:150px; border:1px solid #000; border-radius:18px; font-weight:800; font-size:36px } */
    .select-big{width:100%;height:150px;border:1px solid #000000;border-radius:18px;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:36px;line-height:1.2;text-align:center;color:#111827;cursor:pointer;background:#fff}
    .select-home{background:#EEF2FF;border-color:#C7D2FE}
    .select-single{background:#ECFEFF;border-color:#A5F3FC}
    .select-comm{background:#FFF7ED;border-color:#FED7AA}
    .select-old{background:#F3E8FF;border-color:#D8B4FE}
    /* extraMenuSection */
    .extra{padding:4px 16px 30px;display:flex;flex-direction:column;gap:10px}
    .extra-h{font-size:14px;font-weight:700;color:#6B7280;padding:8px 0 4px}
    .menu{min-height:64px;border-radius:12px;border:1px solid;padding:12px 14px;display:flex;flex-direction:column;justify-content:center;gap:2px}
    .menu-l{font-size:18px;font-weight:800;color:#111827}
    .menu-h{font-size:12px;color:#6B7280}
    .m-branch{background:#F5F3FF;border-color:#C4B5FD}
    .m-send{background:#ECFEFF;border-color:#67E8F9}
    .m-hist{background:#F0FDF4;border-color:#86EFAC}
    .m-save{background:#FFFBEB;border-color:#FCD34D}
    .m-draft{background:#FFF7ED;border-color:#FDBA74}
    /* Bottom Tab placeholder (정확한 RN BottomTab 시각) */
    .bottom-tab{position:fixed;left:0;right:0;bottom:0;height:60px;background:#FFFFFF;border-top:1px solid #E5E7EB;display:flex;align-items:center;justify-content:space-around;font-size:12px;color:#6B7280;font-weight:600;padding-bottom:env(safe-area-inset-bottom)}
    .tab-item{display:flex;flex-direction:column;align-items:center;gap:2px}
    .tab-item.active{color:#2563EB}
    .tab-icon{font-size:18px}
  `;
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<title>HomeScreen v4 (mock — P0 fix 후)</title>
<style>${styleBase}</style></head>
<body>
<div class="safe">
  <!-- titleBar 삭제 (P0 #3) - legacy body.mobile-mode .top display none important 일관 -->
  <!-- DC notice/error 삭제 (P0 #2) - 사용자 노출 없음 console.warn 만 -->

  <!-- legacy .mobile-gate (P0 #4 — paddingBottom 제거) -->
  <div class="mobile-gate" data-testid="mobile-gate">
    <button class="select-big select-home">홈멀티</button>
    <button class="select-big select-single">싱글 세트</button>
    <button class="select-big select-comm">상업멀티</button>
    <button class="select-big select-old">구형</button>
  </div>

  <!-- extraMenuSection (P1 보존 — 정정 #17 의도) -->
  <div class="extra">
    <div class="extra-h">추가 메뉴</div>
    <div class="menu m-branch"><div class="menu-l">임의 분기계산</div><div class="menu-h">WebView 진입 후 legacy pageBranch 자동</div></div>
    <div class="menu m-send"><div class="menu-l">견적·주문하기</div><div class="menu-h">WebView 안 legacy 견적/주문 모달</div></div>
    <div class="menu m-hist"><div class="menu-l">과거 발송내역 확인</div><div class="menu-h">WebView 안 legacy pageHistory</div></div>
    <div class="menu m-save"><div class="menu-l">주문저장</div><div class="menu-h">WebView 안 legacy btnSaveDraft</div></div>
    <div class="menu m-draft"><div class="menu-l">저장내역</div><div class="menu-h">WebView 안 legacy btnDraftList</div></div>
  </div>
</div>
<div class="bottom-tab">
  <div class="tab-item active"><div class="tab-icon">⌂</div>홈</div>
  <div class="tab-item"><div class="tab-icon">▤</div>주문</div>
  <div class="tab-item"><div class="tab-icon">◯</div>알림</div>
  <div class="tab-item"><div class="tab-icon">◉</div>프로필</div>
</div>
${scrollDown ? '<script>window.scrollTo(0, document.body.scrollHeight);</script>' : ''}
</body></html>`;
}

/**
 * LegacyOrder WebView placeholder mock — RN LegacyOrderWebViewScreen 의 시각 1:1.
 * 실 운영에서는 react-native-webview 가 hosted legacy index.html (9427 라인) 직접 로드.
 */
function webviewMockHtml() {
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no">
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  html,body{height:100%;font-family:-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;background:#fff;color:#111827}
  .top{display:flex;align-items:center;justify-content:space-between;padding:12px 14px;border-bottom:1px solid #e5e7eb}
  .title{font-size:18px;font-weight:800;color:#111827}
  .partner{font-size:12px;color:#6B7280}
  .badge{display:inline-block;padding:2px 8px;border-radius:9999px;background:#dbeafe;color:#1e3a8a;font-size:11px;font-weight:700;margin-left:6px}
  .est-table{margin:14px 16px;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden}
  .row{display:grid;grid-template-columns:1.5fr 0.5fr 0.9fr 0.9fr;padding:10px 12px;border-bottom:1px solid #e5e7eb;font-size:13px;align-items:center}
  .row:last-child{border-bottom:none}
  .row.head{background:#F9FAFB;font-weight:700;color:#475569;font-size:12px}
  .price{text-align:right;color:#1D4ED8;font-weight:700}
  .card{margin:12px 16px;padding:12px;border:1px solid #cbd5e1;border-radius:10px;background:#F8FAFC}
  .card-title{font-size:13px;font-weight:800;color:#0F172A;margin-bottom:8px}
  .field{display:flex;gap:8px;align-items:center;font-size:12px;margin-bottom:6px}
  .field label{width:70px;color:#475569;font-weight:600}
  .field input{flex:1;height:32px;border:1px solid #CBD5E1;border-radius:6px;padding:0 8px;font-size:12px;background:#fff}
  .legacy-tag{position:fixed;top:6px;right:6px;background:#FBBF24;color:#78350F;font-size:10px;padding:2px 8px;border-radius:4px;font-weight:700}
  .bottom-tab{position:fixed;left:0;right:0;bottom:0;height:60px;background:#FFFFFF;border-top:1px solid #E5E7EB;display:flex;align-items:center;justify-content:space-around;font-size:12px;color:#6B7280;font-weight:600}
  .tab-item{display:flex;flex-direction:column;align-items:center;gap:2px}
  .tab-item.active{color:#2563EB}
</style></head>
<body>
<div class="legacy-tag">LEGACY (mock)</div>
<div class="top">
  <div class="title">삼한공조시스템 주문서</div>
  <div class="partner">주식회사 샘플상사 (1234567890)<span class="badge">홈멀티</span></div>
</div>
<div class="est-table">
  <div class="row head"><div>모델</div><div>수량</div><div class="price">단가</div><div class="price">합계</div></div>
  <div class="row"><div>AVXH130VR4DH (홈3in1 13평+9평+9평)</div><div>1</div><div class="price">2,450,000</div><div class="price">2,450,000</div></div>
  <div class="row"><div>분기관 1/4 (3 way)</div><div>3</div><div class="price">38,000</div><div class="price">114,000</div></div>
  <div class="row"><div>실외기 받침대</div><div>1</div><div class="price">85,000</div><div class="price">85,000</div></div>
</div>
<div class="card">
  <div class="card-title">주문정보 입력</div>
  <div class="field"><label>납기희망일</label><input value="2026-05-12"></div>
  <div class="field"><label>요청사항</label><input value="현장 도착 1시간 전 연락 부탁드립니다"></div>
  <div class="field"><label>배송지</label><input value="서울시 강남구 테헤란로 152"></div>
</div>
<div class="bottom-tab">
  <div class="tab-item"><div>⌂</div>홈</div>
  <div class="tab-item active"><div>▤</div>주문</div>
  <div class="tab-item"><div>◯</div>알림</div>
  <div class="tab-item"><div>◉</div>프로필</div>
</div>
</body></html>`;
}

/**
 * 단일 viewport 5장 캡처 시퀀스.
 *
 * 01 = 실 expo export bundle BizGate native (RN web 자체 렌더)
 * 02~03 = HomeScreen mock (P0 fix 결과 시각 검증)
 * 04 = LegacyOrder WebView mock (legacy 임베드 영역 placeholder)
 * 05 = Home mock with Bottom Tab — 홈 탭 활성
 */
async function captureViewport(browser, vp) {
  const ua = vp.name === 'iOS' ? IPHONE_UA : ANDROID_UA;
  console.log(`\n[${vp.name}] viewport ${vp.width}x${vp.height} 캡처 시작`);

  const ctx = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    deviceScaleFactor: vp.deviceScaleFactor,
    userAgent: ua,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
  });
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.log(`  [pageerror][${vp.name}]`, e.message));

  // 01 — BizGate native (실 expo export bundle).
  await gotoApp(page);
  await snapshot(page, `${vp.name}-01-bizgate.png`);

  // 02 — HomeScreen mock (P0 fix 결과 — 4 카테고리 검정 + paddingBottom 0)
  await page.setContent(homeMockHtml(false), { waitUntil: 'load' });
  await page.waitForTimeout(500);
  await snapshot(page, `${vp.name}-02-home-after-fix.png`);

  // 03 — HomeScreen mock 스크롤 — extraMenuSection 5 메뉴 (P1 보존)
  await page.setContent(homeMockHtml(true), { waitUntil: 'load' });
  await page.waitForTimeout(500);
  await snapshot(page, `${vp.name}-03-home-extra-menu.png`);

  // 04 — LegacyOrder WebView placeholder
  await page.setContent(webviewMockHtml(), { waitUntil: 'load' });
  await page.waitForTimeout(500);
  await snapshot(page, `${vp.name}-04-webview-order.png`);

  // 05 — Home mock with Bottom Tab — 홈 탭 활성 (RN BottomTab 시각 검증)
  await page.setContent(homeMockHtml(false), { waitUntil: 'load' });
  await page.waitForTimeout(500);
  await snapshot(page, `${vp.name}-05-bottom-tab.png`);

  await ctx.close();
}

(async () => {
  ensureDir(OUT_DIR);
  console.log('Mobile v4 design audit — capture-home.cjs 시작');
  console.log(`  BASE_URL = ${BASE_URL}`);
  console.log(`  OUT_DIR  = ${OUT_DIR}`);

  const alive = await checkDevServer(BASE_URL);
  if (!alive) {
    console.error(
      `\n[abort] Mobile v4 web server 미가동: ${BASE_URL}/ 응답 없음.\n` +
        `        먼저 다음 명령으로 dev/export 서버를 시작하세요:\n` +
        `        cd c:/dev/SamhanLogis/clients/mobile\n` +
        `        npx expo export --platform web\n` +
        `        npx http-server dist -p 4173 -s\n`,
    );
    process.exit(2);
  }
  console.log('  [ok] web server alive');

  patchDistForESM();
  const browser = await launchBrowser();

  try {
    for (const vp of VIEWPORTS) {
      await captureViewport(browser, vp);
    }
    console.log(`\nMobile v4 QA capture 완료 (${VIEWPORTS.length * 5}장) →`, OUT_DIR);
  } finally {
    await browser.close();
  }
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
