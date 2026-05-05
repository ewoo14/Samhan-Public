/**
 * legacy partner-order Code.js → lib/code.js 변환 스크립트.
 *
 * 입력: lib/_legacy-code-raw.js (legacy partner-order Code.js 3,303라인 그대로 복사본)
 * 출력: lib/code.js
 *
 * 변환 규칙: estimate-legacy 와 동일 (외부 호출 함수 async + UrlFetchApp.fetch await
 * + REDACTED 토큰 → process.env 치환 + 모든 top-level function export).
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const RAW = path.join(__dirname, '..', 'lib', '_legacy-code-raw.js');
const OUT = path.join(__dirname, '..', 'lib', 'code.js');

// 외부 호출 (UrlFetchApp.fetch) 사용 함수 + 간접 caller — async.
const ASYNC_FNS = [
  'doGet',
  'saveOrderSnapshot',
  'getOrderSnapshotHistory',
  'callZoneApi',
  'getEcountSession',
  'sendOrderFromUi',
  'fetchNotionDcConfig_',
  'initDcConfigFromNotion',
  'queryAuthDb_',
  'checkAuthStatus',
  'requestAuthApproval',
  'setAuthPassword',
  'tryLogin',
  'getAccessExpiration',
  'saveTutorialState',
  'createAuthRow_',
  'updateAuthPage_',
  'getOrderHistory',
  'saveOrderToNotion',
  'logActionToNotion',
  'logFrontEvent',
];

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

// 1) 토큰 redact 치환
for (const [needle, repl] of Object.entries(TOKEN_MAP)) {
  src = src.replace(new RegExp(`'Bearer ${needle}'`, 'g'), `'Bearer ' + ${repl}`);
  src = src.replace(new RegExp(`"Bearer ${needle}"`, 'g'), `"Bearer " + ${repl}`);
  src = src.replace(new RegExp(`'${needle}'`, 'g'), repl);
  src = src.replace(new RegExp(`"${needle}"`, 'g'), repl);
}

// 2) async 함수 호출 사이트에 await 추가
for (const fn of ASYNC_FNS) {
  const re = new RegExp(`(?<!function\\s)\\b(${fn})\\(`, 'g');
  src = src.replace(re, (_m, p1) => `await ${p1}(`);
}

// 3) UrlFetchApp.fetch(...) → await UrlFetchApp.fetch(...)
src = src.replace(/(^|\W)(UrlFetchApp\.fetch\b)/g, (m, p1, p2) => {
  if (/await\s*$/.test(p1.replace(/[\s;{(=]+$/, ''))) return m;
  return `${p1}await ${p2}`;
});

// 3b) inline arrow function 들 중 본문에 `await ` 를 포함하는 것만 async 로 강제.
//     본문 길이 가변 — pattern: `= (args) => { ... await ... }` 매치 시 async 추가.
//     일반 sync arrow (`.map(x => ...)`) 는 영향 X (return 값 의미 변경 회피).
src = (function convertAwaitArrows(s) {
  // 후보: `(args) => {` 시작 위치 찾고, 매칭 brace 닫힐 때까지 본문 검사.
  const out = [];
  let i = 0;
  const re = /(=\s*)(\(([^)]*)\)\s*=>\s*\{)/g;
  let m;
  while ((m = re.exec(s)) !== null) {
    out.push({ idx: m.index, eq: m[1], decl: m[2], args: m[3], full: m[0] });
  }
  // 뒤에서 앞으로 처리하여 인덱스 안정.
  for (let k = out.length - 1; k >= 0; k--) {
    const cand = out[k];
    if (/=\s*async/.test(cand.eq)) continue;
    // brace 매칭으로 본문 끝 찾기
    let depth = 1;
    let j = cand.idx + cand.full.length;
    while (j < s.length && depth > 0) {
      const c = s[j];
      if (c === '{') depth++;
      else if (c === '}') depth--;
      else if (c === '/' && s[j + 1] === '/') {
        // line comment
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
      // async 추가 — eq + 'async ' + decl
      const newDecl = cand.eq + 'async ' + cand.decl;
      s = s.slice(0, cand.idx) + newDecl + s.slice(cand.idx + cand.full.length);
    }
  }
  return s;
})(src);

// 4) 함수 선언 → async function
for (const fn of ASYNC_FNS) {
  const re = new RegExp(`(^|\\n)function ${fn}\\b`, 'g');
  src = src.replace(re, (_m, p1) => `${p1}async function ${fn}`);
}

const PREAMBLE = `/**
 * order-legacy lib/code.js — legacy partner-order Code.js (3,303 lines) Node.js 1:1 포팅.
 *
 * 본 파일은 scripts/build-code.mjs 가 lib/_legacy-code-raw.js 로부터 자동 생성.
 * 수동 편집 금지.
 *
 * 변환 규칙 (build-code.mjs 참조):
 *  - logic 0% 변경 (함수 시그니처/식별자/문자열 보존)
 *  - 외부 호출 함수 ${ASYNC_FNS.length}개 → async (UrlFetchApp.fetch await)
 *  - REDACTED_NOTION_* 토큰 → process.env.NOTION_TOKEN_* 치환
 *  - module.exports 에 모든 top-level function export
 *
 * 원본 출처: migration/source/scripts/partner-order/Code.js (Apps Script)
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

// MailApp / GmailApp — _triggerAuth / forceAuthCheck 가 사용하는 admin trigger.
// 운영 시 호출 X — stub 만 제공.
const MailApp = { getRemainingDailyQuota: () => 0 };
const GmailApp = {
  createDraft: () => ({ deleteDraft: () => {} }),
};

// ─── legacy Code.js 본문 시작 ───────────────────────────────────────────────
`;

const FOOTER = `
// ─── legacy Code.js 본문 끝 ────────────────────────────────────────────────

/**
 * Express GET / 진입 시 호출 — legacy doGet() 1:1 호환 bootstrap.
 *
 * partner-order doGet 은 estimate 와 거의 동일하되, 가격 인상 데이터 (homeInc/commInc/...)
 * 와 인증 흐름이 추가됨. preloadSheets 후 동기 함수 호출.
 */
async function bootstrap(userEmail) {
  const sheetsToPreload = [
    HOME_NAME,
    SINGLE_NAME,
    SINGLE_PARTS_NAME,
    COMM_NAME,
    COMM_PARTS_NAME,
    CUSTOMERS_NAME,
    MANAGERS_NAME,
    '단가인상',
    '구형',
    '추천',
    '스펙',
  ];
  try {
    await preloadSheets(SRC_SHEET_ID, sheetsToPreload);
  } catch (e) {
    Logger.log('[bootstrap] preloadSheets 실패: ' + e.message);
  }

  const t = {};
  t.userEmail = userEmail || '';
  try { t.homemulti = JSON.stringify(getHomeMulti()); } catch (e) { t.homemulti = '[]'; }
  try { t.singleSets = JSON.stringify(getSingleSets()); } catch (e) { t.singleSets = '[]'; }
  try { t.singleParts = JSON.stringify(getSingleParts()); } catch (e) { t.singleParts = '[]'; }
  try { t.homeDefaults = JSON.stringify(getHomeDefaults()); } catch (e) { t.homeDefaults = '{}'; }
  try { t.singleDefaults = JSON.stringify(getSingleDefaults()); } catch (e) { t.singleDefaults = '{}'; }
  try { t.singleMatPrices = JSON.stringify(getSingleMatPrices()); } catch (e) { t.singleMatPrices = '{}'; }
  try { t.commercialMulti = JSON.stringify(getCommercialMulti()); } catch (e) { t.commercialMulti = '[]'; }
  try { t.commercialParts = JSON.stringify(getCommercialParts()); } catch (e) { t.commercialParts = '[]'; }
  try { t.oldProducts = JSON.stringify(getOldProducts_()); } catch (e) { t.oldProducts = '[]'; }
  try { t.homeInc = JSON.stringify(getHomeIncreasePrices_()); } catch (e) { t.homeInc = '{}'; }
  try { t.commInc = JSON.stringify(getCommIncreasePrices_()); } catch (e) { t.commInc = '{}'; }
  try { t.singleInc = JSON.stringify(getSingleIncreasePrices_()); } catch (e) { t.singleInc = '{}'; }
  try { t.singlePartsInc = JSON.stringify(getSinglePartsIncreasePrices_()); } catch (e) { t.singlePartsInc = '{}'; }
  try { t.specDetailMap = JSON.stringify(getSpecDetailMap_()); } catch (e) { t.specDetailMap = '{}'; }
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

function clearSheetCache() {
  return shim.clearSheetCache();
}

module.exports = {
  bootstrap,
  clearSheetCache,
  doGet,
  saveOrderSnapshot,
  getOrderSnapshotHistory,
  cachePutJSON_,
  cacheGetJSON_,
  getHomeIncreasePrices_,
  getCommIncreasePrices_,
  extractSingleIncreasePrices_,
  getSingleIncreasePrices_,
  getSinglePartsIncreasePrices_,
  extractIncreasePrices_,
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
  getCustomers_,
  searchCustomerByBizOrCode,
  getManagers_,
  searchManagersByName_,
  findManagerByNameExact_,
  getScriptCreds_,
  callZoneApi,
  getEcountSession,
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
  checkAuthStatus,
  requestAuthApproval,
  setAuthPassword,
  hashPassword_,
  tryLogin,
  queryAuthDb_,
  getAccessExpiration,
  saveTutorialState,
  createAuthRow_,
  updateAuthPage_,
  forceAuthCheck,
  getOrderHistory,
  saveOrderToNotion,
  logActionToNotion,
  logFrontEvent,
};
`;

const out = PREAMBLE + src + FOOTER;
fs.writeFileSync(OUT, out, 'utf8');
console.log(`[build-code] ${path.basename(OUT)} written (${out.length} bytes, ${out.split('\\n').length} lines)`);
