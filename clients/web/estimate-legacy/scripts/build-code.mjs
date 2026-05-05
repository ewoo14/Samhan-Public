/**
 * legacy Code.js → lib/code.js 변환 스크립트.
 *
 * 입력: lib/_legacy-code-raw.js (legacy estimate Code.js 2,837라인 그대로 복사본)
 * 출력: lib/code.js
 *
 * 변환 규칙 (logic 0% 변경, 시그니처/식별자 보존):
 *  1. preamble 추가 — apps-script-shim 의 globals (Logger, Utilities, Session,
 *     CacheService, PropertiesService, UrlFetchApp, SpreadsheetApp, DriveApp, HtmlService)
 *     를 destructure 하여 legacy 함수가 그대로 참조 가능하게 함.
 *  2. 외부 호출 함수 13개를 `async` 로 변경 + `UrlFetchApp.fetch(...)` 앞에 `await` 추가.
 *     Apps Script 동기 시그니처 → Node.js Promise 환경 1:1 호환.
 *  3. async 함수 호출 사이트 (내부 호출) 에 `await` 추가.
 *  4. 하드코딩된 Notion 토큰 (REDACTED_NOTION_*) 을 process.env 로 치환.
 *     legacy 코드에는 이미 REDACTED placeholder 만 존재 (실 토큰 X).
 *  5. doGet() 함수는 보존하되, Express RPC dispatcher 가 사용할 bootstrap()
 *     async wrapper 를 추가로 export — preloadSheets 후 동기 read 호출.
 *  6. 모든 top-level function 을 module.exports 에 export.
 *
 * 본 변환은 idempotent — 재실행 시 동일 결과.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW = path.join(__dirname, '..', 'lib', '_legacy-code-raw.js');
const OUT = path.join(__dirname, '..', 'lib', 'code.js');

// 외부 호출 (UrlFetchApp.fetch) 사용 함수 — async 로 변경.
const ASYNC_FNS = [
  'doGet',
  'callZoneApi',
  'getEcountSession',
  'sendOrderFromUi',
  'fetchNotionDcConfig_',
  'initDcConfigFromNotion',
  'saveOrderToNotion',
  'getNotionHistory',
  'logFrontEvent',
  'checkUserAuth',
  'getInventoryTableHtml',
  'getInventoryTable',
  'forceAuth',
  'saveQuoteSnapshot',
  'getQuoteHistory',
];

// REDACTED 토큰 → process.env 매핑
const TOKEN_MAP = {
  REDACTED_NOTION_AUTH_TOKEN_001: "(process.env.NOTION_TOKEN_AUTH || '__MISSING_NOTION_TOKEN_AUTH__')",
  REDACTED_NOTION_TOKEN_002: "(process.env.NOTION_TOKEN_DC || '__MISSING_NOTION_TOKEN_DC__')",
  REDACTED_NOTION_TOKEN_ORDER_003: "(process.env.NOTION_TOKEN_ORDER || '__MISSING_NOTION_TOKEN_ORDER__')",
  REDACTED_NOTION_TOKEN_SHIPPING_004: "(process.env.NOTION_TOKEN_SHIPPING || process.env.NOTION_TOKEN_SEND || '__MISSING_NOTION_TOKEN_SHIPPING__')",
  REDACTED_NOTION_TOKEN_BEARER_005: "(process.env.NOTION_TOKEN_DC || '__MISSING_NOTION_TOKEN_BEARER__')",
  REDACTED_NOTION_TOKEN_QUOTE_006: "(process.env.NOTION_TOKEN_QUOTE || '__MISSING_NOTION_TOKEN_QUOTE__')",
  REDACTED_NOTION_TOKEN_LOG_007: "(process.env.NOTION_TOKEN_LOG || process.env.NOTION_TOKEN_SNAPSHOT || '__MISSING_NOTION_TOKEN_LOG__')",
  REDACTED_NOTION_TOKEN_AUTH_008: "(process.env.NOTION_TOKEN_AUTH || '__MISSING_NOTION_TOKEN_AUTH__')",
  REDACTED_NOTION_TOKEN_SNAPSHOT_009: "(process.env.NOTION_TOKEN_SNAPSHOT || '__MISSING_NOTION_TOKEN_SNAPSHOT__')",
};

let src = fs.readFileSync(RAW, 'utf8');

// 1) 토큰 redact 치환 — 'REDACTED_X' 문자열 (작은/큰 따옴표 모두) → ` + (process.env.X || '...') + `
//    legacy 패턴: 'Bearer REDACTED_X' / 'REDACTED_X' / "REDACTED_X"
for (const [needle, repl] of Object.entries(TOKEN_MAP)) {
  // Bearer 접두 패턴 — 'Bearer REDACTED_X' → 'Bearer ' + (env)
  src = src.replace(new RegExp(`'Bearer ${needle}'`, 'g'), `'Bearer ' + ${repl}`);
  src = src.replace(new RegExp(`"Bearer ${needle}"`, 'g'), `"Bearer " + ${repl}`);
  // 단순 문자열 — 'REDACTED_X' → (env)
  src = src.replace(new RegExp(`'${needle}'`, 'g'), repl);
  src = src.replace(new RegExp(`"${needle}"`, 'g'), repl);
}

// 2) async 함수 내부 호출에도 await 추가 (먼저 적용 — function 선언이 아직 `function X(` 형태).
//    pattern: `<callee>(...)` — 단, `function ` 직후의 함수 선언은 lookbehind 로 제외.
for (const fn of ASYNC_FNS) {
  const re = new RegExp(`(?<!function\\s)\\b(${fn})\\(`, 'g');
  src = src.replace(re, (m, p1) => `await ${p1}(`);
}

// 3) UrlFetchApp.fetch(...) 앞에 await — 동일 라인의 변수 대입 우측이라면 안전.
src = src.replace(/(^|\W)(UrlFetchApp\.fetch\b)/g, (m, p1, p2) => {
  if (/await\s*$/.test(p1.replace(/[\s;{(=]+$/, ''))) return m;
  return `${p1}await ${p2}`;
});

// 3b) inline arrow function 들 중 본문에 `await ` 를 포함하는 것만 async 로 강제.
//     일반 sync arrow (`.map(x => ...)`) 는 영향 X.
src = (function convertAwaitArrows(s) {
  const out = [];
  const re = /(=\s*)(\(([^)]*)\)\s*=>\s*\{)/g;
  let m;
  while ((m = re.exec(s)) !== null) {
    out.push({ idx: m.index, eq: m[1], decl: m[2], args: m[3], full: m[0] });
  }
  for (let k = out.length - 1; k >= 0; k--) {
    const cand = out[k];
    if (/=\s*async/.test(cand.eq)) continue;
    let depth = 1;
    let j = cand.idx + cand.full.length;
    while (j < s.length && depth > 0) {
      const c = s[j];
      if (c === '{') depth++;
      else if (c === '}') depth--;
      else if (c === '/' && s[j + 1] === '/') {
        while (j < s.length && s[j] !== '\n') j++;
        continue;
      } else if (c === '/' && s[j + 1] === '*') {
        j += 2;
        while (j < s.length - 1 && !(s[j] === '*' && s[j + 1] === '/')) j++;
        j += 2;
        continue;
      } else if (c === '"' || c === "'" || c === '`') {
        const quote = c;
        j++;
        while (j < s.length && s[j] !== quote) {
          if (s[j] === '\\') j++;
          j++;
        }
      }
      j++;
    }
    const body = s.slice(cand.idx + cand.full.length, j);
    if (/\bawait\s/.test(body)) {
      const newDecl = cand.eq + 'async ' + cand.decl;
      s = s.slice(0, cand.idx) + newDecl + s.slice(cand.idx + cand.full.length);
    }
  }
  return s;
})(src);

// 4) 지정된 함수를 async 로 변경 — `function X(` → `async function X(`
//    (step 2 가 끝난 뒤 적용해야 함수 선언이 await 으로 오염되지 않음.)
for (const fn of ASYNC_FNS) {
  const re = new RegExp(`(^|\\n)function ${fn}\\b`, 'g');
  src = src.replace(re, (_m, p1) => `${p1}async function ${fn}`);
}

// 5) doGet 자체도 SpreadsheetApp 호출이 sync (preload 가정) 이지만,
//    bootstrap() 은 별도 추가하므로 doGet 은 보존.

// 6) preamble + footer 추가
const PREAMBLE = `/**
 * estimate-legacy lib/code.js — legacy estimate Code.js (2,837 lines) Node.js 1:1 포팅.
 *
 * 본 파일은 scripts/build-code.mjs 가 lib/_legacy-code-raw.js 로부터 자동 생성.
 * 수동 편집 금지 — 변경은 build-code.mjs 의 변환 규칙에 추가하고 재생성.
 *
 * 변환 규칙 (build-code.mjs 참조):
 *  - logic 0% 변경 (함수 시그니처/식별자/문자열 보존)
 *  - 외부 호출 함수 ${ASYNC_FNS.length}개 → async (UrlFetchApp.fetch await)
 *  - REDACTED_NOTION_* 토큰 → process.env.NOTION_TOKEN_* 치환
 *  - module.exports 에 모든 top-level function export
 *
 * 원본 출처: migration/source/scripts/estimate/Code.js (Apps Script)
 */
'use strict';

const shim = require('./apps-script-shim');
const {
  Logger,
  Utilities,
  Session,
  CacheService,
  PropertiesService,
  UrlFetchApp,
  SpreadsheetApp,
  DriveApp,
  HtmlService,
  preloadSheets,
} = shim;

// legacy 가 의존하는 SRC_SHEET_ID 가 정의되기 전, bootstrap 단계에서 시트 prefetch.
// (실제 SRC_SHEET_ID 정의는 아래 legacy 본문 line 59 — preamble 가 이를 참조하지 않으므로 안전.)

// ─── legacy Code.js 본문 시작 ───────────────────────────────────────────────
`;

// 마지막 footer 에서 SHEETS_TO_PRELOAD 와 bootstrap, exports 추가.
const FOOTER = `
// ─── legacy Code.js 본문 끝 ────────────────────────────────────────────────

/**
 * Express GET / 진입 시 호출 — legacy doGet() 1:1 호환 bootstrap.
 *
 * Apps Script doGet 은 SpreadsheetApp.openById 가 동기인 환경을 가정하나,
 * Node.js 에서는 sheet read 가 비동기이므로 사전에 preloadSheets 로 모든 탭
 * (홈멀티 / 싱글 세트 / 싱글 구성품 / 상업멀티 / 상업멀티 구성 / 거래처 / 담당자 등)
 * 을 in-memory 로 채운 뒤, legacy 동기 함수들이 즉시 read 가능하도록 한다.
 *
 * @returns {Promise<object>} EJS render 데이터 (legacy doGet 가 t.* 로 채우는 항목 1:1)
 */
async function bootstrap(userEmail) {
  // legacy 가 read 하는 전 탭 prefetch (병렬). 누락 탭은 빈 sheet 반환.
  const sheetsToPreload = [
    HOME_NAME,
    SINGLE_NAME,
    SINGLE_PARTS_NAME,
    COMM_NAME,
    COMM_PARTS_NAME,
    CUSTOMERS_NAME,
    MANAGERS_NAME,
    '홈멀티',
    '싱글 세트',
    '싱글 구성품',
    '상업멀티',
    '상업멀티 구성',
    '구형',
    '추천',
    '단가인상',
    '스펙',
  ];
  try {
    await preloadSheets(SRC_SHEET_ID, sheetsToPreload);
  } catch (e) {
    Logger.log('[bootstrap] preloadSheets 실패: ' + e.message);
  }

  const t = {};
  t.userEmail = userEmail || ((Session.getActiveUser() || {}).getEmail ? Session.getActiveUser().getEmail() : '');
  try { t.authData = JSON.stringify(await checkUserAuth(t.userEmail)); } catch (e) { t.authData = '{}'; }
  try { t.homemulti = JSON.stringify(getHomeMulti()); } catch (e) { t.homemulti = '[]'; }
  try { t.singleSets = JSON.stringify(getSingleSets()); } catch (e) { t.singleSets = '[]'; }
  try { t.singleParts = JSON.stringify(getSingleParts()); } catch (e) { t.singleParts = '[]'; }
  try { t.homeDefaults = JSON.stringify(getHomeDefaults()); } catch (e) { t.homeDefaults = '{}'; }
  try { t.singleDefaults = JSON.stringify(getSingleDefaults()); } catch (e) { t.singleDefaults = '{}'; }
  try { t.singleMatPrices = JSON.stringify(getSingleMatPrices()); } catch (e) { t.singleMatPrices = '{}'; }
  try { t.commercialMulti = JSON.stringify(getCommercialMulti()); } catch (e) { t.commercialMulti = '[]'; }
  try { t.commercialParts = JSON.stringify(getCommercialParts()); } catch (e) { t.commercialParts = '[]'; }
  try { t.oldProducts = JSON.stringify(getOldProducts_()); } catch (e) { t.oldProducts = '[]'; }
  try { t.recommendData = JSON.stringify(getRecommendOduData()); } catch (e) { t.recommendData = '{}'; }
  try { t.specDetailMap = JSON.stringify(getSpecDetailMap_()); } catch (e) { t.specDetailMap = '{}'; }
  try { t.priceInc = JSON.stringify(getPriceIncData_()); } catch (e) { t.priceInc = '{}'; }
  try { t.logoData = getLogoImage(); } catch (e) { t.logoData = ''; }
  t.config = JSON.stringify({
    homeDiscount: DISCOUNT_RATE_HOME,
    commDiscount: DISCOUNT_RATE_COMM,
    showIHose: SHOW_I_HOSE,
    discount360: DISCOUNT_360_AMT,
    discount4way: DISCOUNT_4WAY_AMT,
    discountStand: DISCOUNT_STAND_AMT,
    oneWayDiscount: ONEWAY_DISCOUNT_AMT,
    deluxeDiscount: DELUXE_DISCOUNT_AMT,
    firstGradeDiscount: FIRSTGRADE_DISCOUNT_AMT,
    oldDiscount: 0.5,
    unitRoundTo: UNIT_ROUND_TO,
    unitRoundMode: UNIT_ROUND_MODE,
  });
  return t;
}

/**
 * sheet 캐시 강제 무효화 — POST /rpc/clearSheetCache.
 */
function clearSheetCache() {
  return shim.clearSheetCache();
}

// 모든 top-level function export — legacy RPC 호환.
module.exports = {
  bootstrap,
  clearSheetCache,
  // legacy 함수 — 이름 보존
  doGet,
  cachePutJSON_,
  cacheGetJSON_,
  cacheRemoveJSON_,
  getGateImages,
  getLogoImage,
  normalizeSize_,
  findIdx_,
  parseKRNumber_,
  parseKRFloat_,
  toYmd_,
  toMmDd_,
  normalizeTel_,
  todayYMD_,
  _normSpec_,
  sanitizeKoreanParen_,
  trimSymbols_,
  sanitizeDisp_,
  hpFromText_,
  isBlockedByNote_,
  isSoldOutByNote_,
  unifyCatL_,
  classifyHome_,
  getHomeMulti,
  classifySingleSetLM_,
  findHeaderIndex_,
  getSingleSets,
  extractRowsFromFormula_,
  getSingleParts,
  getSingleMatPrices,
  classifyCommercial_,
  getCommercialMulti,
  getCommercialParts,
  getSpecMap_,
  getSpecDetailMap_,
  getHomeDefaults,
  getSingleDefaults,
  getCustomerDataAsync,
  getCustomers_,
  searchCustomerByBizOrCode,
  getManagers_,
  searchManagersByName_,
  findManagerByNameExact_,
  getScriptCreds_,
  callZoneApi,
  getEcountSession,
  getRecommendOduData,
  decideWarehouseCode_,
  formatWonDiscountLabel_,
  formatPercentLabel_,
  combineRemarks_,
  getOldProducts_,
  sendOrderFromUi,
  detectHomeOrder,
  buildDefaultDcConfig_,
  fetchNotionDcConfig_,
  initDcConfigFromNotion,
  searchCustomerByBizno,
  getManagersForInput,
  forceAuth,
  saveOrderToNotion,
  getNotionHistory,
  logFrontEvent,
  checkUserAuth,
  getInventoryTableHtml,
  getInventoryTable,
  include,
  saveQuoteSnapshot,
  getQuoteHistory,
  getPriceIncData_,
};
`;

const out = PREAMBLE + src + FOOTER;
fs.writeFileSync(OUT, out, 'utf8');
console.log(`[build-code] ${path.basename(OUT)} written (${out.length} bytes, ${out.split('\\n').length} lines)`);
