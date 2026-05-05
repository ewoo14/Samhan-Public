/**
 * Playwright capture script — Mobile v4 QA 캡처 5장 (회고 #2 정정).
 *
 * 회고 #2 (2026-05-05) — 사용자 명시:
 *   "주문서는 ... 처음 모바일 게이트를 제외한 나머지는 모두 다름을 확인."
 *
 * 정정 결정 (mobile-staff v3 의 `capture-v3.cjs` 패턴 1:1 적용):
 *   - 이전 v4 = `capture-home.cjs` (mock HTML overlay 4장 + expo export bundle 1장 — homeMockHtml /
 *     webviewMockHtml 함수 가 별도 mock 카드 grid 를 내장).
 *   - 신규 v4 = mock HTML overlay 일체 폐기. PM 의 order-legacy v4 dev server (port 5185) 에 직접
 *     진입, react-native-webview 환경 (iPhone UA + 390x844 viewport) 으로 가장.
 *
 * 사용자 첨부 캡처 1:1 매핑 (docs/qa/legacy-original/partner-order/):
 *   01-mobile-gate.png       → Screenshot 20.17.37 (모바일 게이트 4 카테고리 큰 진입 버튼)
 *   02-page-menu.png         → Screenshot 20.17.55 (▼ 페이지 메뉴 drawer — 4 카테고리 보기 + 견적/주문하기 +
 *                                                   과거 발송내역 + 자동 로그아웃 timer)
 *   03-home-active.png       → 홈멀티 진입 직후 (4 카테고리 중 home 진입 — 라인 grid + 옵션·필터 sidebar)
 *   04-page-history.png      → 과거 발송내역 페이지 (#pageHistory 활성)
 *   05-bizgate.png           → 인증 게이트 (#pageBizGate — biz-box 로그인 form, 미인증 default 화면)
 *
 * 산출물 (390x844, iPhone 14 Pro viewport):
 *   docs/qa/migration-fe-mobile-v4-design-audit/01-mobile-gate.png
 *   docs/qa/migration-fe-mobile-v4-design-audit/02-page-menu.png
 *   docs/qa/migration-fe-mobile-v4-design-audit/03-home-active.png
 *   docs/qa/migration-fe-mobile-v4-design-audit/04-page-history.png
 *   docs/qa/migration-fe-mobile-v4-design-audit/05-bizgate.png
 *
 * 실행 (PM 의 order-legacy v4 dev server 가 port 5185 에서 가동 중이어야 함):
 *   curl http://localhost:5185/healthz   # 200 = 가동 중
 *   node scripts/capture-v4.cjs          # 캡처 진행
 *
 * 미가동 시:
 *   abort + 사용자 안내 (cd c:/dev/SamhanLogis/clients/web/order-legacy && node server.js).
 *   본 스크립트는 dev server 자동 시작 시도하지 않음 — port lock / DB seed 충돌 방지.
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const http = require('http');

const VIEWPORT = { width: 390, height: 844 };
const ORDER_BASE = process.env.QA_ORDER_BASE_URL || 'http://localhost:5185';
const OUT_DIR = path.resolve(__dirname, '../../../docs/qa/migration-fe-mobile-v4-design-audit');

// react-native-webview 가 order-legacy 진입 시 보내는 user agent — App.tsx 의
// `applicationNameForUserAgent = ' SamhanMobileApp/0.5.0 (samhan-mobile-v4-webview)'` 와 동일.
const MOBILE_USER_AGENT =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) ' +
  'AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 ' +
  'Safari/604.1 SamhanMobileApp/0.5.0 (samhan-mobile-v4-webview)';

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

/**
 * dev server healthz 확인. 미가동 시 false 리턴.
 */
function checkDevServer(baseUrl) {
  return new Promise((resolve) => {
    const req = http.get(baseUrl + '/healthz', { timeout: 2000 }, (res) => {
      let body = '';
      res.on('data', (chunk) => (body += chunk));
      res.on('end', () => resolve(res.statusCode === 200 || res.statusCode === 503));
      // 503 = 의존성 (Sheets/Notion/eCount) 일부 down — Express 서버 자체는 살아있음.
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

/**
 * order-legacy v4 의 인증 게이트 (#pageBizGate) 강제 닫음 — 캡처 02~04 용.
 * 실 운영에서는 tryLogin (Apps Script 1:1) 가 cookie 로 자동 통과시키지만,
 * dev 환경에서는 게이트가 잠시 노출될 수 있으므로 캡처 직전 정리.
 */
async function dismissBizGate(page) {
  await page
    .evaluate(() => {
      const ids = ['pageBizGate'];
      for (const id of ids) {
        const el = document.getElementById(id);
        if (el) {
          el.classList.add('hidden');
          el.style.display = 'none';
          el.setAttribute('aria-hidden', 'true');
        }
      }
      // legacy 의 모바일 분기 안전망 — body.no-active 강제 (모바일 게이트 노출).
      document.body.classList.remove(
        'orderInfo-active',
        'home-active',
        'single-active',
        'comm-active',
        'old-active',
        'history-active',
      );
      document.body.classList.add('mobile-mode', 'no-active');
    })
    .catch(() => {});
}

/**
 * order-legacy v4 진입 — 모바일 viewport + iPhone UA 가 자동으로 mobile-mode 활성.
 * (index.ejs line 4480 / 8424: `document.body.classList.toggle('mobile-mode', isMobile)`).
 */
async function gotoOrderApp(page) {
  console.log(`  [info] navigating ${ORDER_BASE}/`);
  await page.goto(`${ORDER_BASE}/`, { waitUntil: 'load', timeout: 60000 });
  // legacy order 의 init / 인증 / mobile-mode toggle 안정화 대기.
  await page.waitForTimeout(2500);
}

(async () => {
  ensureDir(OUT_DIR);

  console.log('Mobile v4 — 실 dev server 캡처 시작 (회고 #2)');
  console.log(`  order-legacy v4 base = ${ORDER_BASE}`);

  const alive = await checkDevServer(ORDER_BASE);
  if (!alive) {
    console.error(
      `\n[abort] order-legacy v4 dev server 미가동: ${ORDER_BASE}/healthz 응답 없음.\n` +
        `        먼저 다음 명령으로 dev server 를 시작하세요:\n` +
        `        cd c:/dev/SamhanLogis/clients/web/order-legacy && node server.js\n` +
        `        (또는 npm run dev)`,
    );
    process.exit(2);
  }
  console.log('  [ok] dev server alive (healthz 200/503)');

  const browser = await launchBrowser();

  try {
    const ctx = await browser.newContext({
      viewport: VIEWPORT,
      deviceScaleFactor: 2,
      userAgent: MOBILE_USER_AGENT,
      locale: 'ko-KR',
      timezoneId: 'Asia/Seoul',
    });
    const page = await ctx.newPage();
    page.on('pageerror', (e) => console.log('  [pageerror]', e.message));

    // -------- 05 — 인증 게이트 (#pageBizGate — 미인증 default) --------
    // 사용자 첨부에는 없지만 v4 진입 흐름 첫 화면 — biz-box 어두운 카드 layout.
    await gotoOrderApp(page);
    // 게이트 우선 캡처 (dismiss 전).
    await snapshot(page, '05-bizgate.png');

    // -------- 01 — 모바일 게이트 4 카테고리 (홈멀티/싱글세트/상업멀티/구형) --------
    // 사용자 첨부: Screenshot 20.17.37.JPG (홈멀티/싱글 세트/상업멀티/구형 4 큰 진입 버튼).
    await dismissBizGate(page);
    await page.waitForTimeout(600);
    await snapshot(page, '01-mobile-gate.png');

    // -------- 03 — 홈멀티 진입 직후 (라인 grid + 옵션·필터 sidebar) --------
    // (캡처 순서 03 → 02 — drawer 가 home-active 상태에서만 사용자 첨부와 일치하므로 03 먼저 진입.)
    // legacy `el('#btnEnterHome').click()` → enterMobile('home') → home-active body class.
    await page.evaluate(() => {
      const btn = document.getElementById('btnEnterHome') || document.getElementById('btnGoHome');
      if (btn) btn.click();
    });
    await page.waitForTimeout(1200);
    // 안전망 — home-active body class 강제.
    await page.evaluate(() => {
      document.body.classList.remove(
        'no-active',
        'orderInfo-active',
        'single-active',
        'comm-active',
        'old-active',
        'history-active',
      );
      document.body.classList.add('mobile-mode', 'home-active');
    });
    await page.waitForTimeout(400);
    await snapshot(page, '03-home-active.png');

    // -------- 02 — ▼ 페이지 메뉴 drawer (싱글세트 보기 + 상업멀티 보기 + 구형 보기 + 견적/주문하기 +
    //                                       과거 발송내역 + 자동 로그아웃 timer + 닫기 ▲) --------
    // 사용자 첨부: Screenshot 20.17.55.JPG (홈멀티 활성 상태에서 페이지 메뉴 drawer 활성).
    // legacy 의 #handleTop click → toggleDrawer('top') → #drawerTop.active 가 위에서 슬라이드.
    await page.evaluate(() => {
      // toggleDrawer('top') 직접 호출 — handleTop click 보다 결정적.
      if (typeof toggleDrawer === 'function') {
        toggleDrawer('top');
      }
      const drawer = document.getElementById('drawerTop');
      if (drawer) {
        drawer.classList.add('active');
        // 안전망 — relocateUI 가 #mobileTopContent 에 .top-actions 를 동적 inject 하므로 visibility 보장.
        drawer.style.zIndex = '99999';
      }
    });
    await page.waitForTimeout(900);
    await snapshot(page, '02-page-menu.png');

    // -------- 04 — 과거 발송내역 (#pageHistory 활성) --------
    // 02 의 drawer 닫고 #btnHistory click → history-active body class.
    await page.evaluate(() => {
      const drawer = document.getElementById('drawerTop');
      if (drawer) drawer.classList.remove('active');
      const btn = document.getElementById('btnHistory');
      if (btn) btn.click();
    });
    await page.waitForTimeout(1000);
    // 안전망 — history-active body class 강제.
    await page.evaluate(() => {
      document.body.classList.remove(
        'no-active',
        'orderInfo-active',
        'home-active',
        'single-active',
        'comm-active',
        'old-active',
      );
      document.body.classList.add('mobile-mode', 'history-active');
      const ph = document.getElementById('pageHistory');
      if (ph) {
        ph.classList.remove('hidden');
        ph.style.display = 'flex';
      }
    });
    await page.waitForTimeout(400);
    await snapshot(page, '04-page-history.png');

    await ctx.close();
    console.log('\nMobile v4 QA capture 5장 완료 (회고 #2) →', OUT_DIR);
  } finally {
    await browser.close();
  }
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
