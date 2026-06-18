// formula-f1-capture.mjs — 종합견적서(estimate-app) 실화면 캡처 (실 DB)
// 데스크톱 node_modules 의 chromium 사용. 실데이터, 가짜 금지.
import pw from 'file:///C:/dev/Samhan-Public/clients/desktop/node_modules/@playwright/test/index.js';
const { chromium } = pw;

const BASE = 'http://localhost:5183';
const EMAIL = 'dev_master@samhan-air.com';
const OUT = 'C:/dev/Samhan-Public/docs/qa/formula-f1-estimate-app';

const consoleErrors = [];
const pageErrors = [];
const failedReqs = [];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function rowInfo(page, bodySel, headSel) {
  return await page.evaluate(({ bodySel }) => {
    const body = document.querySelector(bodySel);
    if (!body) return { exists: false, rows: 0, sample: [] };
    const trs = Array.from(body.querySelectorAll('tr'));
    const sample = trs.slice(0, 3).map((tr) =>
      Array.from(tr.querySelectorAll('td')).slice(0, 6).map((td) => (td.innerText || '').trim()).filter(Boolean).join(' | ')
    );
    return { exists: true, rows: trs.length, sample };
  }, { bodySel });
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });

  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(msg.text());
  });
  page.on('pageerror', (err) => pageErrors.push(String(err && err.stack || err)));
  page.on('requestfailed', (req) => failedReqs.push(`${req.failure()?.errorText || '?'} ${req.url()}`));
  page.on('dialog', async (d) => { console.log('[DIALOG]', d.message().replace(/\n/g, ' ')); await d.dismiss().catch(() => {}); });

  console.log('>> goto', `${BASE}/?email=${EMAIL}`);
  await page.goto(`${BASE}/?email=${encodeURIComponent(EMAIL)}`, { waitUntil: 'domcontentloaded', timeout: 60000 });

  // 인증 RPC + 카드 렌더 대기
  await sleep(2500);

  // USER_AUTH 상태 확인
  const auth = await page.evaluate(() => (typeof USER_AUTH !== 'undefined' ? USER_AUTH : null));
  console.log('>> USER_AUTH =', JSON.stringify(auth));

  // gate 상태
  const gateState = await page.evaluate(() => {
    const biz = document.getElementById('pageBizGate');
    const mob = document.getElementById('mobileGate');
    return {
      bizGateHidden: biz ? biz.classList.contains('hidden') : 'no-el',
      mobileGateHidden: mob ? mob.classList.contains('hidden') : 'no-el',
      bodyClass: document.body.className,
      bodyChildCount: document.body.children.length,
    };
  });
  console.log('>> gate/body state =', JSON.stringify(gateState));

  // welcome 게이트(mobileGate) 강제 dismiss + initGate 의 3s 후 goOrderInfo() 자동전환 무력화
  // (실데이터 화면 변경 없음 — 단지 캡처 시점에 홈/싱글/상업 탭이 order-info 로 덮이는 것 방지)
  await page.evaluate(() => {
    const mob = document.getElementById('mobileGate');
    if (mob) mob.classList.add('hidden');
    const biz = document.getElementById('pageBizGate');
    if (biz) biz.classList.add('hidden');
    // 자동 전표작성 전환 차단
    try { window.goOrderInfo = function () {}; } catch (_) {}
  });
  // initGate observer 의 3s 타임아웃이 지나가도록 충분히 대기 후 캡처 시작
  await sleep(3500);

  // 카탈로그 데이터 길이
  const counts = await page.evaluate(() => ({
    HOMEMULTI: (typeof HOMEMULTI !== 'undefined' && HOMEMULTI) ? HOMEMULTI.length : 'undef',
    SINGLE_SETS: (typeof SINGLE_SETS !== 'undefined' && SINGLE_SETS) ? SINGLE_SETS.length : 'undef',
    COMMULTI: (typeof COMMULTI !== 'undefined' && COMMULTI) ? COMMULTI.length : 'undef',
  }));
  console.log('>> catalog lengths =', JSON.stringify(counts));

  // --- 홈멀티 ---
  await page.evaluate(() => { if (typeof goHome === 'function') goHome(); });
  await sleep(800);
  const home = await rowInfo(page, '#homeBody');
  console.log('>> HOME rows =', JSON.stringify(home), 'bodyClass=', await page.evaluate(() => document.body.className));
  await page.screenshot({ path: `${OUT}/01-home.png`, fullPage: false });

  // --- 싱글중대형 ---
  await page.evaluate(() => { if (typeof goSingle === 'function') goSingle(); });
  await sleep(800);
  const single = await rowInfo(page, '#singleBody');
  console.log('>> SINGLE rows =', JSON.stringify(single), 'bodyClass=', await page.evaluate(() => document.body.className));
  await page.screenshot({ path: `${OUT}/02-single.png`, fullPage: false });

  // --- 상업멀티 ---
  await page.evaluate(() => { if (typeof goComm === 'function') goComm(); });
  await sleep(1000);
  const comm = await rowInfo(page, '#commBody');
  console.log('>> COMM rows =', JSON.stringify(comm), 'bodyClass=', await page.evaluate(() => document.body.className));
  await page.screenshot({ path: `${OUT}/03-comm.png`, fullPage: false });

  console.log('\n===== DIAGNOSTICS =====');
  console.log('consoleErrors:', JSON.stringify(consoleErrors.slice(0, 15), null, 1));
  console.log('pageErrors:', JSON.stringify(pageErrors.slice(0, 10), null, 1));
  console.log('failedReqs:', JSON.stringify(failedReqs.slice(0, 25), null, 1));
  console.log('=======================');

  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
