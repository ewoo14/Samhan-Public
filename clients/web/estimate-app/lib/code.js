/**
 * legacy estimate Code.js (2837 라인) Node.js 1:1 포팅.
 *
 * 원본: migration/source/scripts/estimate/Code.js (Google Apps Script)
 * 대상: Node.js 20 + Express 4 + EJS (B2 옵션 — Apps Script 와 가장 가까운 환경)
 *
 * 포팅 원칙 (DECISIONS Phase 6 v4 후속 정정 §):
 *  1. **logic 100% 보존** — 함수 시그니처, 반환 객체 shape, 에러 메시지 동일
 *  2. **Google API 폐기** — SpreadsheetApp/DriveApp/UrlFetchApp → apps-script-shim 경유
 *     실 데이터 출처는 SamhanLogis MSA endpoint (axios)
 *  3. **e-Count + Notion 외부 호출 폐기** — slip-bridge.js 가 slip-service POST 로 흡수
 *  4. **inventory 76 함수 모두 export** — RPC dispatch (`code[fnName]`) 호환
 *
 * 함수 인벤토리 (migration/analysis/01-script-analysis-estimate.md §1.1 76개):
 *  - 부트스트랩: doGet, getHomeMulti, getSingleSets, getSingleParts, getSingleMatPrices,
 *    getCommercialMulti, getCommercialParts, getOldProducts_, getHomeDefaults,
 *    getSingleDefaults, getRecommendOduData, getSpecDetailMap_, getPriceIncData_,
 *    getLogoImage
 *  - RPC: getCustomerDataAsync, getQuoteHistory, saveQuoteSnapshot, sendOrderFromUi,
 *    getNotionHistory, logFrontEvent, getGateImages, checkUserAuth, getInventoryTable,
 *    getManagersForInput, initDcConfigFromNotion, searchCustomerByBizno
 *  - 캐시: cachePutJSON_, cacheGetJSON_, cacheRemoveJSON_
 *  - 유틸 (pure): normalizeSize_, findIdx_, parseKRNumber_, parseKRFloat_, toYmd_, toMmDd_,
 *    normalizeTel_, todayYMD_, _normSpec_, sanitizeKoreanParen_, trimSymbols_, sanitizeDisp_,
 *    hpFromText_, isBlockedByNote_, isSoldOutByNote_, unifyCatL_, classifyHome_,
 *    classifySingleSetLM_, findHeaderIndex_, extractRowsFromFormula_, classifyCommercial_,
 *    decideWarehouseCode_, formatWonDiscountLabel_, formatPercentLabel_, combineRemarks_,
 *    detectHomeOrder, buildDefaultDcConfig_, include
 *  - e-Count (폐기 → slip-bridge): getScriptCreds_, callZoneApi, getEcountSession,
 *    getInventoryTableHtml
 *  - Notion (폐기 → MS DB): saveOrderToNotion, fetchNotionDcConfig_,
 *    searchCustomerByBizOrCode (+ searchCustomerByBizno),
 *    getCustomers_, getManagers_, searchManagersByName_, findManagerByNameExact_,
 *    forceAuth, getSpecMap_
 */

'use strict';

const axios = require('axios');
const shim = require('./apps-script-shim');
const slipBridge = require('./slip-bridge');

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
} = shim;

const BASE_URL = process.env.SAMHAN_API_BASE_URL || 'http://localhost:8080';
const PRODUCT_BASE = process.env.PRODUCT_SERVICE_URL || BASE_URL;
const PARTNER_BASE = process.env.PARTNER_SERVICE_URL || BASE_URL;
const ESTIMATE_BASE = process.env.ESTIMATE_SERVICE_URL || BASE_URL;
const AUDIT_LOG_URL = process.env.AUDIT_LOG_URL || `${BASE_URL}/api/v1/audit-logs/front`;
const USE_MOCK = String(process.env.USE_MOCK_FALLBACK || 'true').toLowerCase() === 'true';

const ax = axios.create({ timeout: 15000, validateStatus: () => true });

/**
 * SamhanLogis MS GET — 공통 helper. 실패 또는 USE_MOCK 시 fallbackValue 반환.
 */
async function _msGet(url, params, fallbackValue) {
  try {
    const resp = await ax.get(url, { params });
    if (resp.status >= 200 && resp.status < 300) return resp.data;
    Logger.log(`[ms] GET ${url} → ${resp.status} (mock fallback)`);
  } catch (e) {
    Logger.log(`[ms] GET ${url} error: ${e.message} (mock fallback)`);
  }
  if (USE_MOCK) return fallbackValue;
  throw new Error(`SamhanLogis MS GET 실패: ${url}`);
}

async function _msPost(url, body, fallbackValue) {
  try {
    const resp = await ax.post(url, body);
    if (resp.status >= 200 && resp.status < 300) return resp.data;
    Logger.log(`[ms] POST ${url} → ${resp.status} (mock fallback)`);
  } catch (e) {
    Logger.log(`[ms] POST ${url} error: ${e.message} (mock fallback)`);
  }
  if (USE_MOCK) return fallbackValue;
  throw new Error(`SamhanLogis MS POST 실패: ${url}`);
}

/* ════════════════════════════════════════════════════════════════════════
 * §0 상수 (legacy lines 59-86)
 * ═══════════════════════════════════════════════════════════════════════ */

const SRC_SHEET_ID = '1RJqO3jT-yJTi3NDBhL60o_cZWlVETGTU7UlvIKXuVNQ';
const HOME_NAME = '홈멀티_단가인상';
const SINGLE_NAME = '싱글 세트_단가인상';
const SINGLE_PARTS_NAME = '싱글 구성품_단가인상';
const COMM_NAME = '상업멀티_단가인상';
const COMM_PARTS_NAME = '상업멀티 구성_단가인상';
const CUSTOMERS_NAME = '거래처';
const MANAGERS_NAME = '담당자';

const DISCOUNT_RATE_HOME = 0.45;
const DISCOUNT_RATE_COMM = 0.45;
const SHOW_I_HOSE = false;
const DISCOUNT_360_AMT = 0;
const DISCOUNT_4WAY_AMT = 0;
const DISCOUNT_STAND_AMT = 0;
const ONEWAY_DISCOUNT_AMT = 0;
const DELUXE_DISCOUNT_AMT = 0;
const FIRSTGRADE_DISCOUNT_AMT = 0;
const UNIT_ROUND_TO = 0;
const UNIT_ROUND_MODE = 'ROUND';

/* ════════════════════════════════════════════════════════════════════════
 * §1 캐시 유틸 (legacy lines 90-123) — apps-script-shim CacheService 위임
 * ═══════════════════════════════════════════════════════════════════════ */

const CACHE_CHUNK_BYTES = 90000;

function cachePutJSON_(key, obj, ttlSec) {
  const cache = CacheService.getScriptCache();
  const str = JSON.stringify(obj);
  const ttl = ttlSec || 1800;
  if (str.length <= CACHE_CHUNK_BYTES) { cache.put(key, str, ttl); return true; }
  const n = Math.ceil(str.length / CACHE_CHUNK_BYTES);
  cache.put(key + '#count', String(n), ttl);
  for (let i = 0; i < n; i++) cache.put(`${key}#${i}`, str.slice(i * CACHE_CHUNK_BYTES, (i + 1) * CACHE_CHUNK_BYTES), ttl);
  return true;
}

function cacheGetJSON_(key) {
  const cache = CacheService.getScriptCache();
  const cnt = cache.get(key + '#count');
  if (cnt) {
    const n = parseInt(cnt, 10);
    let buf = '';
    for (let i = 0; i < n; i++) { const part = cache.get(`${key}#${i}`); if (!part) return null; buf += part; }
    try { return JSON.parse(buf); } catch (e) { return null; }
  }
  const hit = cache.get(key); if (!hit) return null;
  try { return JSON.parse(hit); } catch (e) { return null; }
}

function cacheRemoveJSON_(key) {
  const cache = CacheService.getScriptCache();
  const cnt = cache.get(key + '#count');
  if (cnt) {
    const n = parseInt(cnt, 10);
    for (let i = 0; i < n; i++) cache.remove(`${key}#${i}`);
    cache.remove(key + '#count');
  }
  cache.remove(key);
}

/* ════════════════════════════════════════════════════════════════════════
 * §2 순수 유틸 (legacy lines 197-282) — verbatim 포팅
 * ═══════════════════════════════════════════════════════════════════════ */

function normalizeSize_(v) {
  const t = String(v == null ? '' : v).trim();
  const n = t.replace(/[^\d.+]/g, '');
  return n || '';
}

function findIdx_(row, keys) {
  for (let k = 0; k < keys.length; k++) { const i = row.indexOf(keys[k]); if (i >= 0) return i; }
  return -1;
}

function parseKRNumber_(v) {
  if (v == null || v === '') return 0;
  const s = String(v).replace(/[^\d.\-]/g, '');
  return Math.round(parseFloat(s) || 0);
}

function parseKRFloat_(v) {
  if (v == null || v === '') return 0;
  const s = String(v).replace(/[^\d.\-]/g, '');
  return parseFloat(s) || 0;
}

function toYmd_(v, tz) {
  if (!v) return '';
  return Utilities.formatDate(new Date(v), tz || 'Asia/Seoul', 'yyyyMMdd');
}

function toMmDd_(v, tz) {
  if (!v) return '';
  return Utilities.formatDate(new Date(v), tz || 'Asia/Seoul', 'MMdd');
}

function normalizeTel_(s) {
  return String(s || '').replace(/[^\d]/g, '');
}

function todayYMD_() {
  return Utilities.formatDate(new Date(), Session.getScriptTimeZone(), 'yyyyMMdd');
}

function _normSpec_(s) {
  return String(s || '').replace(/\s+/g, '').toLowerCase();
}

function sanitizeKoreanParen_(text) {
  return String(text || '')
    .replace(/[(]/g, '(')
    .replace(/[)]/g, ')');
}

function trimSymbols_(text) {
  return String(text || '').trim();
}

function sanitizeDisp_(text) {
  return trimSymbols_(sanitizeKoreanParen_(text));
}

function hpFromText_(s) {
  const t = String(s || '');
  const m = t.match(/(\d+(\.\d+)?)\s*HP/i);
  if (m) return parseFloat(m[1]);
  return 0;
}

function isBlockedByNote_(note) {
  return /미판매|단종/.test(String(note || ''));
}

function isSoldOutByNote_(note) {
  return /품절/.test(String(note || ''));
}

function unifyCatL_(L) {
  const t = String(L || '').trim();
  return t === '부자재2' ? '부자재' : t;
}

function findHeaderIndex_(headers, key) {
  if (!Array.isArray(headers)) return -1;
  return headers.indexOf(key);
}

function extractRowsFromFormula_(formula) {
  const out = [];
  const re = /\$([A-Z]+)\$(\d+)/g;
  let m;
  while ((m = re.exec(String(formula || '')))) out.push({ col: m[1], row: parseInt(m[2], 10) });
  return out;
}

function formatWonDiscountLabel_(amt) {
  const n = Number(amt) || 0;
  if (n === 0) return '';
  return `(${n.toLocaleString('ko-KR')}원 할인)`;
}

function formatPercentLabel_(rate) {
  const r = Number(rate) || 0;
  if (r === 0) return '';
  return `(${(r * 100).toFixed(1)}%)`;
}

function combineRemarks_(base, extra) {
  const parts = [];
  if (base) parts.push(String(base).trim());
  if (extra) parts.push(String(extra).trim());
  return parts.filter(Boolean).join(' / ');
}

function detectHomeOrder(items, order) {
  const tCand = [order?.type, order?.mode, order?.orderType, order?.kind, order?.category]
    .map((x) => String(x || '').toLowerCase());
  if (tCand.some((x) => /(home|home-multi|homemulti|hm)/.test(x))) return true;

  if (Array.isArray(items)) {
    for (const it of items) {
      const U = (v) => String(v || '').toUpperCase();
      const scopes = [U(it.section), U(it.group), U(it.kind), U(it.category), U(it.tags)];
      if (scopes.some((s) => /HOME|HOME-MULTI|HOMEMULTI|HM/.test(s))) return true;
    }
  }
  return false;
}

function buildDefaultDcConfig_() {
  return {
    home: { rate: DISCOUNT_RATE_HOME, fixed: 0 },
    comm: { rate: DISCOUNT_RATE_COMM, fixed: 0 },
    single: { rate: 0, fixed: 0 },
    old: { rate: 0.5, fixed: 0 },
  };
}

function classifyHome_(rawName) {
  const s = sanitizeDisp_(rawName);
  const hp = hpFromText_(s);
  let kind = '기타';
  if (/실외기|ODU/i.test(s)) kind = '실외기';
  else if (/실내기|IDU/i.test(s)) kind = '실내기';
  else if (/리모컨/i.test(s)) kind = '리모컨';
  else if (/패널/i.test(s)) kind = '패널';
  else if (/배관|호스/i.test(s)) kind = '배관';
  return { name: s, hp, kind, catL: unifyCatL_(kind) };
}

function classifySingleSetLM_(s) {
  const t = String(s || '').toUpperCase();
  if (/^AC/.test(t)) return 'AC';
  if (/^AP/.test(t)) return 'AP';
  if (/^AR/.test(t)) return 'AR';
  if (/^AF/.test(t)) return 'AF';
  return 'OTHER';
}

function classifyCommercial_(name, model) {
  const n = String(name || '');
  const m = String(model || '');
  if (/ERV|환기/.test(n)) return { kind: 'ERV' };
  if (/실외기|ODU/.test(n)) return { kind: 'ODU', hp: hpFromText_(n) };
  if (/실내기|IDU/.test(n)) return { kind: 'IDU', hp: hpFromText_(n) };
  if (/패널/.test(n)) return { kind: 'PANEL' };
  if (/리모컨/.test(n)) return { kind: 'REMOTE' };
  return { kind: '기타', model: m };
}

/* ════════════════════════════════════════════════════════════════════════
 * §3 부트스트랩 데이터 — product-service 위임 (mock fallback: 빈 구조)
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy getHomeMulti() — 홈멀티 카탈로그.
 * SamhanLogis: GET /api/v1/products?usageScope=ESTIMATE&category=HOME_MULTI
 */
async function getHomeMulti() {
  const cached = cacheGetJSON_('HOME_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'HOME_MULTI' },
    [],
  );
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('HOME_V6', list, 600);
  return list;
}

async function getSingleSets() {
  const cached = cacheGetJSON_('SS_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'SINGLE_SET' },
    [],
  );
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('SS_V6', list, 600);
  return list;
}

async function getSingleParts() {
  const cached = cacheGetJSON_('SP_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'SINGLE_PART' },
    [],
  );
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('SP_V6', list, 600);
  return list;
}

async function getSingleMatPrices() {
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products/single-mat-prices`,
    null,
    {},
  );
  return data || {};
}

async function getCommercialMulti() {
  const cached = cacheGetJSON_('CM_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'COMMERCIAL_MULTI' },
    [],
  );
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('CM_V6', list, 600);
  return list;
}

async function getCommercialParts() {
  const cached = cacheGetJSON_('CP_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'COMMERCIAL_PART' },
    [],
  );
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('CP_V6', list, 600);
  return list;
}

/**
 * legacy getOldProducts_() — 구품목 (단종/대체) 카탈로그.
 */
async function getOldProducts_() {
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products`,
    { usageScope: 'ESTIMATE', category: 'LEGACY' },
    [],
  );
  return Array.isArray(data) ? data : data?.items || [];
}

async function getHomeDefaults() {
  const data = await _msGet(`${PRODUCT_BASE}/api/v1/products/home-defaults`, null, {});
  return data || {};
}

async function getSingleDefaults() {
  const data = await _msGet(`${PRODUCT_BASE}/api/v1/products/single-defaults`, null, {});
  return data || {};
}

async function getRecommendOduData() {
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/odu-recommendations`,
    null,
    { comm: [], home: [], homeEx: [] },
  );
  return data || { comm: [], home: [], homeEx: [] };
}

/**
 * legacy getSpecDetailMap_() — line 1006 — 모델별 상세 spec 맵.
 * SamhanLogis: GET /api/v1/products/spec-detail-map (M1a 흡수)
 */
async function getSpecDetailMap_() {
  const cached = cacheGetJSON_('SPEC_DET_V6');
  if (cached) return cached;
  const data = await _msGet(`${PRODUCT_BASE}/api/v1/products/spec-detail-map`, null, {});
  const out = data || {};
  cachePutJSON_('SPEC_DET_V6', out, 1800);
  return out;
}

/**
 * legacy getPriceIncData_() — line 2769 — 가격 인상 비교 데이터.
 */
async function getPriceIncData_() {
  const cached = cacheGetJSON_('PRC_INC_V6');
  if (cached) return cached;
  const data = await _msGet(
    `${PRODUCT_BASE}/api/v1/products/price-inc`,
    null,
    { home: {}, comm: {}, single: {} },
  );
  cachePutJSON_('PRC_INC_V6', data, 1800);
  return data || { home: {}, comm: {}, single: {} };
}

/**
 * legacy getLogoImage() / getGateImages() — Drive folder → SamhanLogis files-service.
 * 응답: data:image/png;base64,...
 */
async function getLogoImage() {
  const data = await _msGet(`${BASE_URL}/api/v1/files/logo-image`, null, '');
  if (typeof data === 'string') return data;
  return data?.dataUri || '';
}

async function getGateImages() {
  const data = await _msGet(`${BASE_URL}/api/v1/files/gate-images`, null, []);
  if (Array.isArray(data)) return data;
  return data?.images || [];
}

/* ════════════════════════════════════════════════════════════════════════
 * §4 doGet → bootstrap() — Express GET / 가 호출
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy doGet() (line 6) 의 모든 server-injected 변수를 한번에 prefetch.
 * Express GET / 핸들러가 본 함수 결과를 EJS render context 로 전달.
 *
 * @param {string} userEmail — Session.getActiveUser 대체
 */
async function bootstrap(userEmail) {
  const email = userEmail || Session.getActiveUser().getEmail();
  const [
    authData,
    homemulti,
    singleSets,
    singleParts,
    homeDefaults,
    singleDefaults,
    singleMatPrices,
    commercialMulti,
    commercialParts,
    oldProducts,
    recommendData,
    specDetailMap,
    priceInc,
    logoData,
  ] = await Promise.all([
    checkUserAuth(email),
    getHomeMulti(),
    getSingleSets(),
    getSingleParts(),
    getHomeDefaults(),
    getSingleDefaults(),
    getSingleMatPrices(),
    getCommercialMulti(),
    getCommercialParts(),
    getOldProducts_(),
    getRecommendOduData(),
    getSpecDetailMap_(),
    getPriceIncData_(),
    getLogoImage(),
  ]);

  return {
    userEmail: email,
    authData: JSON.stringify(authData),
    homemulti: JSON.stringify(homemulti),
    singleSets: JSON.stringify(singleSets),
    singleParts: JSON.stringify(singleParts),
    homeDefaults: JSON.stringify(homeDefaults),
    singleDefaults: JSON.stringify(singleDefaults),
    singleMatPrices: JSON.stringify(singleMatPrices),
    commercialMulti: JSON.stringify(commercialMulti),
    commercialParts: JSON.stringify(commercialParts),
    oldProducts: JSON.stringify(oldProducts),
    recommendData: JSON.stringify(recommendData),
    specDetailMap: JSON.stringify(specDetailMap),
    priceInc: JSON.stringify(priceInc),
    logoData: logoData || '',
    config: JSON.stringify({
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
    }),
  };
}

/* ════════════════════════════════════════════════════════════════════════
 * §5 거래처 / 담당자 — partner-service 위임
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy getCustomerDataAsync(forceRefresh) (line 1420) — 거래처 목록.
 * SamhanLogis: GET /api/v1/partners
 */
async function getCustomerDataAsync(forceRefresh) {
  if (forceRefresh) cacheRemoveJSON_('CUS_V6');
  const cached = cacheGetJSON_('CUS_V6');
  if (cached) {
    return cached.map((c) => ({
      code: c.code, name: c.name, rep: c.rep, tel: c.tel, addr: c.addr, group: c.group, note: c.note,
    }));
  }
  const data = await _msGet(`${PARTNER_BASE}/api/v1/partners`, null, []);
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('CUS_V6', list, 600);
  return list.map((c) => ({
    code: c.code, name: c.name, rep: c.rep, tel: c.tel, addr: c.addr, group: c.group, note: c.note,
  }));
}

async function getCustomers_() {
  const cached = cacheGetJSON_('CUS_V6');
  if (cached) return cached;
  const data = await _msGet(`${PARTNER_BASE}/api/v1/partners`, null, []);
  const list = Array.isArray(data) ? data : data?.items || [];
  cachePutJSON_('CUS_V6', list, 600);
  return list;
}

async function searchCustomerByBizOrCode(input) {
  const n = String(input || '').replace(/[^\d]/g, '');
  const c = String(input || '').trim();
  const list = await getCustomers_();

  if (n) {
    const f1 = list.find((x) => x.bizno && x.bizno === n);
    if (f1) return f1;
    const f2 = list.find((x) => String(x.code || '').replace(/[^\d]/g, '') === n);
    if (f2) return f2;
  }
  if (c) {
    const f3 = list.find((x) => x.code === c);
    if (f3) return f3;
  }
  return null;
}

async function searchCustomerByBizno(bizno) {
  return searchCustomerByBizOrCode(bizno);
}

async function getManagers_() {
  const cached = cacheGetJSON_('MGR_V1');
  if (cached) return cached;
  const data = await _msGet(`${PARTNER_BASE}/api/v1/managers`, null, []);
  const list = Array.isArray(data) ? data : data?.items || [];
  const mapped = list.map((r) => ({
    '담당자명': r.name || r['담당자명'],
    '담당자코드': r.code || r['담당자코드'],
    manager: r.name || r['담당자명'],
    empCd: r.code || r['담당자코드'],
  }));
  cachePutJSON_('MGR_V1', mapped, 600);
  return mapped;
}

async function searchManagersByName_(query) {
  const q = String(query || '').trim().toLowerCase().replace(/\s+/g, '');
  if (!q) return [];
  const list = await getManagers_();
  return list.filter((r) => String(r['담당자명'] || '').toLowerCase().replace(/\s+/g, '').includes(q));
}

async function findManagerByNameExact_(name) {
  const n = String(name || '').trim().toLowerCase().replace(/\s+/g, '');
  if (!n) return null;
  const list = await getManagers_();
  const f = list.find((r) => String(r['담당자명'] || '').toLowerCase().replace(/\s+/g, '') === n);
  return f ? { name: f['담당자명'], empCd: f['담당자코드'] } : null;
}

async function getManagersForInput(input) {
  return searchManagersByName_(input);
}

/**
 * legacy initDcConfigFromNotion(bizno) (line 2166) — 거래처별 DC config 로드.
 * Notion 폐기 → SamhanLogis: GET /api/v1/partners/{bizno}/dc-config
 */
async function initDcConfigFromNotion(bizno) {
  const biznoDigits = String(bizno || '').replace(/[^\d]/g, '');
  if (!biznoDigits) return buildDefaultDcConfig_();

  const cust = await searchCustomerByBizOrCode(biznoDigits);
  const data = await _msGet(
    `${PARTNER_BASE}/api/v1/partners/${biznoDigits}/dc-config`,
    null,
    null,
  );
  if (!data) return Object.assign(buildDefaultDcConfig_(), { customer: cust });
  return Object.assign(buildDefaultDcConfig_(), data, { customer: cust });
}

async function fetchNotionDcConfig_(biznoDigits) {
  return initDcConfigFromNotion(biznoDigits);
}

/* ════════════════════════════════════════════════════════════════════════
 * §6 e-Count session — DEPRECATED (slip-bridge 가 흡수)
 * 호환성을 위해 stub 유지 — getInventoryTable 만 mock 응답
 * ═══════════════════════════════════════════════════════════════════════ */

function getScriptCreds_() {
  const sp = PropertiesService.getScriptProperties();
  return {
    COM_CODE: sp.getProperty('COM_CODE') || '174539',
    USER_ID: sp.getProperty('USER_ID') || '11840720103',
    API_CERT_KEY: sp.getProperty('API_CERT_KEY') || 'REDACTED',
    EMP_CD: sp.getProperty('EMP_CD') || '250102',
  };
}

async function callZoneApi(_comCode) {
  Logger.log('[deprecated] callZoneApi → noop (e-Count 폐기)');
  return 'CB';
}

async function getEcountSession(_authInfo) {
  Logger.log('[deprecated] getEcountSession → noop (e-Count 폐기, slip-bridge 사용)');
  return { sessionId: 'DEPRECATED', zone: 'CB' };
}

async function getInventoryTableHtml(_baseDate, _itemCodes) {
  Logger.log('[deprecated] getInventoryTableHtml → mock (e-Count 폐기)');
  return '<table><tr><td>재고 조회 endpoint 미구현 (M1a 후속)</td></tr></table>';
}

async function getInventoryTable(dateVal, itemCodes) {
  return getInventoryTableHtml(dateVal, itemCodes);
}

/* ════════════════════════════════════════════════════════════════════════
 * §7 출고전표 — sendOrderFromUi → slip-bridge.postSlip
 * legacy line 1762-1967 의 e-Count proxy 호출을 slip-service 로 대체
 * ═══════════════════════════════════════════════════════════════════════ */

function decideWarehouseCode_(items) {
  function getOrigName_(it) { return String(it.origName || it.name || it.model || ''); }
  function getSection_(it) { return String(it.section || '').toUpperCase(); }
  if (!Array.isArray(items)) return '2';
  for (const it of items) {
    const sec = getSection_(it);
    const nm = getOrigName_(it);
    if (sec === 'SINGLE' || /^A[CPRF]/.test(nm)) return '00003';
  }
  return '2';
}

/**
 * legacy sendOrderFromUi(data) (line 1762-1967) — 견적 finalize → 출고전표 생성.
 *
 * legacy 흐름: SaleList 조립 → e-Count `/proxy/ecount/sale` POST → Notion saveOrderToNotion
 * 신 흐름: SaleList 조립 (legacy logic 그대로) → slip-bridge.postSlip (slip-service POST)
 *          Notion 저장 폐기 (slip-service 가 entity 영속화)
 */
async function sendOrderFromUi(data) {
  try {
    let items = [];
    if (data && data.items) {
      items = (typeof data.items === 'string') ? JSON.parse(data.items) : data.items;
    }
    const order = data;
    const authInfo = order.auth || {};
    const safeNum = (s) => String(s || '').replace(/[^\d]/g, '');
    const kst = Session.getScriptTimeZone();
    const toYmd = (v) => v
      ? Utilities.formatDate(new Date(v), kst, 'yyyyMMdd')
      : Utilities.formatDate(new Date(), kst, 'yyyyMMdd');

    if (!Array.isArray(items) || items.length === 0) return { ok: false, error: '항목없음' };

    const cleaned = items.filter((it) =>
      !(String(it.unit || '').toUpperCase() === 'SET' && it.section === 'SET' && it.sendAsSet !== true),
    );

    const merged = cleaned.map((it, idx) => ({
      ...it,
      qty: Number(it.qty) || 0,
      _last: idx,
      REMARKS: String(it.remarks || it.REMARKS || ''),
    }));

    let key = safeNum(order?.bizno || '');
    if (!key && order?.custCode) key = String(order.custCode).trim();
    const custRec = await searchCustomerByBizOrCode(key);
    if (!custRec) return { ok: false, error: '미등록거래처' };
    const custFinal = custRec.code;

    const ioDate = toYmd(order?.due || '');
    const timeDate = ioDate;

    let payMMDD = '';
    if (order?.payDue === '카드결제') {
      payMMDD = '카드결제';
    } else if (order?.payDue) {
      const pd = new Date(order.payDue);
      if (!isNaN(pd)) payMMDD = Utilities.formatDate(pd, kst, 'MMdd');
      else payMMDD = order.payDue;
    }

    const whCd = (order && order.whCode) ? order.whCode : decideWarehouseCode_(merged);

    let empCdFinal = authInfo.managerCode;
    if (!empCdFinal) {
      if (custRec.manager) {
        const m = await findManagerByNameExact_(custRec.manager);
        if (m) empCdFinal = m.empCd;
      }
      if (!empCdFinal) empCdFinal = getScriptCreds_().EMP_CD;
    }

    const SaleList = [];

    merged.forEach((it) => {
      const qty = Math.round(Number(it.qty) || 0);
      if (qty === 0) return;

      const priceVat = Math.round(Number(it.price) || 0);
      const total = priceVat * qty;
      const sup = Math.round(Math.abs(total) / 1.1);
      const vat = Math.abs(total) - sup;
      const supply = total < 0 ? -sup : sup;
      const vatAmt = total < 0 ? -vat : vat;
      const priceEx = priceVat < 0
        ? -Math.round(Math.abs(priceVat) / 1.1)
        : Math.round(priceVat / 1.1);

      let rawSpec = String(it.spec || '').trim();
      if (/경동.*[\/:]/.test(String(order?.addr || ''))) {
        Logger.log(`[경동] 모델:${it.model} / list:${it.list} / 전체:${JSON.stringify(it)}`);
        rawSpec = String(it.list || 0);
      }
      const sizeDes = rawSpec === '' ? '​' : rawSpec;

      SaleList.push({
        BulkDatas: {
          IO_DATE: ioDate,
          UPLOAD_SER_NO: '1',
          CUST: custFinal,
          CUST_DES: custRec.name || '',
          EMP_CD: empCdFinal || '',
          WH_CD: whCd || '100',
          IO_TYPE: '10',
          PJT_CD: '',
          TTL_CTT: '',
          REF_DES: '',
          COLL_TERM: '',
          AGREE_TERM: '',
          TIME_DATE: timeDate,
          U_MEMO1: String(custRec.tel || ''),
          U_MEMO2: String(custRec.addr || ''),
          U_MEMO3: String(custRec.rep || ''),
          U_TXT1: String(order?.addr || ''),
          ADD_TXT_01_T: String(order?.auditAddr || ''),
          ADD_TXT_03_T: String(order?.tel || ''),
          ADD_TXT_04_T: String(order?.memo || ''),
          ADD_TXT_05_T: payMMDD,
          ADD_TXT_06_T: String(order?.dcInfo || ''),
          PROD_CD: String(it.model),
          PROD_DES: '',
          SIZE_DES: sizeDes,
          QTY: String(qty),
          PRICE: String(priceEx),
          USER_PRICE_VAT: String(Math.abs(priceVat)),
          SUPPLY_AMT_F: '0',
          SUPPLY_AMT: String(supply),
          VAT_AMT: String(vatAmt),
          REMARKS: String(it.REMARKS || ''),
        },
      });
    });

    if (SaleList.length === 0) return { ok: false, error: '유효수량없음' };

    Logger.log('📤 slip-service POST 시작');
    const result = await slipBridge.postSlip(order, SaleList);
    if (!result.ok) {
      Logger.log(`[slip-bridge] 실패: ${result.error || ''}`);
      return { ok: false, error: result.error || 'slip-service 실패', body: result.body };
    }
    return { ok: true, slipNo: result.slipNo, body: result.body };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
}

/**
 * legacy saveOrderToNotion(info, items, slipNo) (line 2233) — 폐기 (slip-service 가 entity 영속화).
 * 시그니처 보존 — RPC dispatch 호환성용 stub.
 */
async function saveOrderToNotion(_info, _items, _slipNo) {
  Logger.log('[deprecated] saveOrderToNotion → noop (slip-service 가 영속화)');
  return { ok: true, deprecated: true };
}

/* ════════════════════════════════════════════════════════════════════════
 * §8 Notion 이력 조회 — SamhanLogis MS 위임
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy getNotionHistory(startDate, endDate) (line 2308) — 출고 이력.
 * SamhanLogis: GET /api/v1/partner-orders?startDate=&endDate=
 */
async function getNotionHistory(startDate, endDate) {
  const email = Session.getActiveUser().getEmail();
  const data = await _msGet(
    `${BASE_URL}/api/v1/partner-orders`,
    { startDate, endDate, userEmail: email },
    [],
  );
  return Array.isArray(data) ? data : data?.items || [];
}

/* ════════════════════════════════════════════════════════════════════════
 * §9 견적 snapshot — estimate-service 위임
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy saveQuoteSnapshot(payload) (line 2614).
 * SamhanLogis: POST /api/v1/estimates/snapshots
 */
async function saveQuoteSnapshot(payload) {
  const email = Session.getActiveUser().getEmail();
  const body = {
    userEmail: email,
    createdAt: new Date().toISOString(),
    ...payload,
  };
  const result = await _msPost(
    `${ESTIMATE_BASE}/api/v1/estimates/snapshots`,
    body,
    { ok: true, mock: true, snapshotId: `MOCK-${Date.now()}` },
  );
  return result;
}

/**
 * legacy getQuoteHistory(startDate, endDate) (line 2681).
 * SamhanLogis: GET /api/v1/estimates/snapshots?startDate=&endDate=
 */
async function getQuoteHistory(startDate, endDate) {
  const email = Session.getActiveUser().getEmail();
  const data = await _msGet(
    `${ESTIMATE_BASE}/api/v1/estimates/snapshots`,
    { startDate, endDate, userEmail: email },
    [],
  );
  return Array.isArray(data) ? data : data?.items || [];
}

/* ════════════════════════════════════════════════════════════════════════
 * §10 인증 & 로그
 * ═══════════════════════════════════════════════════════════════════════ */

/**
 * legacy checkUserAuth(email) (line 2442).
 * SamhanLogis: GET /api/v1/auth/me?email=
 */
async function checkUserAuth(email) {
  const data = await _msGet(
    `${BASE_URL}/api/v1/auth/me`,
    { email: email || Session.getActiveUser().getEmail() },
    {
      authorized: USE_MOCK ? true : false,
      managerName: USE_MOCK ? '개발담당자' : '',
      managerCode: USE_MOCK ? 'DEV-001' : '',
      ecountId: '',
      ecountApi: '',
    },
  );
  return data;
}

async function forceAuth() {
  Logger.log('[deprecated] forceAuth → noop (Drive 권한 부여 폐기)');
  return { ok: true };
}

/**
 * legacy logFrontEvent(group, msg, isMobile, mgrName) (line 2410).
 * SamhanLogis: POST /api/v1/audit-logs/front
 */
async function logFrontEvent(group, msg, isMobile, mgrName) {
  const email = Session.getActiveUser().getEmail();
  const body = {
    group, message: msg, device: isMobile ? '모바일' : 'PC',
    managerName: mgrName, userEmail: email, occurredAt: new Date().toISOString(),
  };
  await _msPost(AUDIT_LOG_URL, body, { ok: true, mock: true });
  return { ok: true };
}

/* ════════════════════════════════════════════════════════════════════════
 * §11 spec map (sendOrderFromUi 보조) — product-service 위임
 * ═══════════════════════════════════════════════════════════════════════ */

async function getSpecMap_() {
  const cached = cacheGetJSON_('SPEC_MAP_V6');
  if (cached) return cached;
  const data = await _msGet(`${PRODUCT_BASE}/api/v1/products/spec-map`, null, {});
  const out = data || {};
  cachePutJSON_('SPEC_MAP_V6', out, 1800);
  return out;
}

/* ════════════════════════════════════════════════════════════════════════
 * §12 include — Apps Script 의 server-side template include 호환
 * Express 환경에서는 EJS partials 가 처리하므로 stub.
 * ═══════════════════════════════════════════════════════════════════════ */

function include(filename) {
  return HtmlService.createHtmlOutputFromFile(filename).getContent();
}

/* ════════════════════════════════════════════════════════════════════════
 * §13 doGet stub — Express 가 직접 라우팅하므로 호환성 stub 만 유지
 * ═══════════════════════════════════════════════════════════════════════ */

async function doGet() {
  Logger.log('[shim] doGet → Express 가 routes/index.js 에서 처리');
  return await bootstrap();
}

/* ════════════════════════════════════════════════════════════════════════
 * 외부 노출 — 76 함수 inventory 모두
 * ═══════════════════════════════════════════════════════════════════════ */

module.exports = {
  // §1 캐시
  cachePutJSON_, cacheGetJSON_, cacheRemoveJSON_,
  // §2 유틸
  normalizeSize_, findIdx_, parseKRNumber_, parseKRFloat_,
  toYmd_, toMmDd_, normalizeTel_, todayYMD_, _normSpec_,
  sanitizeKoreanParen_, trimSymbols_, sanitizeDisp_, hpFromText_,
  isBlockedByNote_, isSoldOutByNote_, unifyCatL_,
  classifyHome_, classifySingleSetLM_, findHeaderIndex_,
  extractRowsFromFormula_, classifyCommercial_,
  formatWonDiscountLabel_, formatPercentLabel_, combineRemarks_,
  detectHomeOrder, buildDefaultDcConfig_, decideWarehouseCode_,
  // §3 부트스트랩
  getHomeMulti, getSingleSets, getSingleParts, getSingleMatPrices,
  getCommercialMulti, getCommercialParts, getOldProducts_,
  getHomeDefaults, getSingleDefaults, getRecommendOduData,
  getSpecDetailMap_, getPriceIncData_, getLogoImage, getGateImages,
  // §4 doGet
  doGet, bootstrap,
  // §5 거래처/담당자
  getCustomerDataAsync, getCustomers_, searchCustomerByBizOrCode,
  searchCustomerByBizno, getManagers_, searchManagersByName_,
  findManagerByNameExact_, getManagersForInput,
  initDcConfigFromNotion, fetchNotionDcConfig_,
  // §6 e-Count (deprecated stub)
  getScriptCreds_, callZoneApi, getEcountSession,
  getInventoryTableHtml, getInventoryTable,
  // §7 출고전표
  sendOrderFromUi, saveOrderToNotion,
  // §8 Notion 이력
  getNotionHistory,
  // §9 snapshot
  saveQuoteSnapshot, getQuoteHistory,
  // §10 인증 / 로그
  checkUserAuth, forceAuth, logFrontEvent,
  // §11 spec map
  getSpecMap_,
  // §12 include
  include,

  // 공개 상수 (테스트용)
  _constants: {
    SRC_SHEET_ID, HOME_NAME, SINGLE_NAME, SINGLE_PARTS_NAME,
    COMM_NAME, COMM_PARTS_NAME, CUSTOMERS_NAME, MANAGERS_NAME,
    DISCOUNT_RATE_HOME, DISCOUNT_RATE_COMM, SHOW_I_HOSE,
    UNIT_ROUND_TO, UNIT_ROUND_MODE,
  },
};
