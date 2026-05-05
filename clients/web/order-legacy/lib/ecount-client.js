/**
 * e-Count 출고전표 proxy 클라이언트 — legacy UrlFetchApp.fetch 1:1.
 *
 * legacy 호출 패턴 (estimate Code.js line 1565 / 1593 / 1912 등):
 *   POST http://152.69.228.109:3000/proxy/ecount/zone     → zone (회사 zone 조회)
 *   POST http://152.69.228.109:3000/proxy/ecount/login    → SESSION_ID 발급
 *   POST http://152.69.228.109:3000/proxy/ecount/sale     → 출고전표 (Sale)
 *   POST http://152.69.228.109:3000/proxy/ecount/saleorder → 주문 (SaleOrder, partner-order 전용)
 *   POST http://152.69.228.109:3000/proxy/ecount/inventory → 재고 조회
 *
 * 응답 schema (legacy 가 의존):
 *   - sale: { Status, Data: { SuccessCnt, SlipNos: [...], FailCnt, ResultDetails: [...] } }
 *   - login: { Status, Data: { SESSION_ID, ... } }
 *
 * 환경변수:
 *  - ECOUNT_ENDPOINT (기본 http://152.69.228.109:3000)
 *  - ECOUNT_COM_CODE / ECOUNT_USER_ID / ECOUNT_API_CERT_KEY
 *
 * 멱등성 (Idempotency):
 *  - sendSale(payload, { idempotencyKey }) 호출 시 5분 TTL in-memory dedupe map 으로 중복 발송 방지
 */

'use strict';

const axios = require('axios');

const ENDPOINT = process.env.ECOUNT_ENDPOINT || 'http://152.69.228.109:3000';
const TIMEOUT_MS = 20000;
const SESSION_TTL_MS = 5 * 60 * 1000;
const IDEMP_TTL_MS = 5 * 60 * 1000;

const ax = axios.create({
  baseURL: ENDPOINT,
  timeout: TIMEOUT_MS,
  validateStatus: () => true,
});

// session cache: key=`${comCode}:${userId}` → { sessionId, zone, expireAt }
const _sessionCache = new Map();
// idempotency: key=string → { result, expireAt }
const _idempCache = new Map();

/**
 * 재시도 헬퍼 (1s/3s/9s exponential).
 */
async function _withRetry(fn, attempts = 3) {
  let lastErr;
  for (let i = 0; i < attempts; i++) {
    try {
      const r = await fn();
      if (r && r.status >= 500) {
        lastErr = new Error(`HTTP ${r.status}`);
        await _sleep(Math.pow(3, i) * 1000);
        continue;
      }
      return r;
    } catch (e) {
      lastErr = e;
      await _sleep(Math.pow(3, i) * 1000);
    }
  }
  throw lastErr || new Error('e-Count retry exhausted');
}

function _sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * /proxy/ecount/zone — 회사 zone (region 식별) 조회.
 * legacy callZoneApi(comCode) 1:1.
 */
async function getZone(comCode) {
  const r = await _withRetry(() =>
    ax.post('/proxy/ecount/zone', { COM_CODE: comCode }),
  );
  return r.data;
}

/**
 * /proxy/ecount/login — SESSION_ID 발급. 세션 5분 cache.
 * legacy getEcountSession(authInfo) 1:1.
 */
async function login({ comCode, userId, apiCertKey, zone } = {}) {
  const cc = comCode || process.env.ECOUNT_COM_CODE;
  const uid = userId || process.env.ECOUNT_USER_ID;
  const key = apiCertKey || process.env.ECOUNT_API_CERT_KEY;
  const cacheKey = `${cc}:${uid}`;
  const now = Date.now();

  const hit = _sessionCache.get(cacheKey);
  if (hit && hit.expireAt > now) return hit;

  let z = zone;
  if (!z) {
    const zoneResp = await getZone(cc);
    z = zoneResp && zoneResp.Data ? zoneResp.Data.ZONE : null;
  }

  const r = await _withRetry(() =>
    ax.post('/proxy/ecount/login', {
      COM_CODE: cc,
      USER_ID: uid,
      API_CERT_KEY: key,
      LAN_TYPE: 'ko-KR',
      ZONE: z,
    }),
  );
  const sessionId = r.data && r.data.Data && r.data.Data.Datas
    ? r.data.Data.Datas.SESSION_ID
    : (r.data && r.data.Data ? r.data.Data.SESSION_ID : null);
  if (!sessionId) {
    throw new Error(`[ecount] login 실패: ${JSON.stringify(r.data)}`);
  }
  const ent = { sessionId, zone: z, expireAt: now + SESSION_TTL_MS, raw: r.data };
  _sessionCache.set(cacheKey, ent);
  return ent;
}

/**
 * /proxy/ecount/sale — 출고전표 발송. legacy 의 sale 호출 1:1.
 *
 * @param {Array<object>} saleList SaleList 배열 (legacy 가 만든 그대로)
 * @param {object} opts { sessionId, zone, idempotencyKey }
 * @returns {Promise<object>} legacy 응답 schema 그대로 (Data.SlipNos[0] 위치 보존)
 */
async function sendSale(saleList, opts = {}) {
  const idempKey = opts.idempotencyKey;
  const now = Date.now();

  if (idempKey) {
    const hit = _idempCache.get(idempKey);
    if (hit && hit.expireAt > now) return hit.result;
  }

  let { sessionId, zone } = opts;
  if (!sessionId || !zone) {
    const sess = await login();
    sessionId = sess.sessionId;
    zone = sess.zone;
  }

  const r = await _withRetry(() =>
    ax.post('/proxy/ecount/sale', {
      SESSION_ID: sessionId,
      ZONE: zone,
      payload: { SaleList: saleList },
    }),
  );

  if (idempKey) {
    _idempCache.set(idempKey, { result: r.data, expireAt: now + IDEMP_TTL_MS });
  }
  return r.data;
}

/**
 * /proxy/ecount/saleorder — 주문서. partner-order 전용.
 */
async function sendSaleOrder(saleOrderList, opts = {}) {
  let { sessionId, zone } = opts;
  if (!sessionId || !zone) {
    const sess = await login();
    sessionId = sess.sessionId;
    zone = sess.zone;
  }
  const r = await _withRetry(() =>
    ax.post('/proxy/ecount/saleorder', {
      SESSION_ID: sessionId,
      ZONE: zone,
      payload: { SaleOrderList: saleOrderList },
    }),
  );
  return r.data;
}

/**
 * /proxy/ecount/inventory — 재고 조회.
 */
async function getInventory(payload, opts = {}) {
  let { sessionId, zone } = opts;
  if (!sessionId || !zone) {
    const sess = await login();
    sessionId = sess.sessionId;
    zone = sess.zone;
  }
  const r = await _withRetry(() =>
    ax.post('/proxy/ecount/inventory', {
      SESSION_ID: sessionId,
      ZONE: zone,
      payload,
    }),
  );
  return r.data;
}

/**
 * 일반화된 proxy 호출 — apps-script-shim.UrlFetchApp 가 위임 사용.
 *
 * @param {string} subPath e.g. '/proxy/ecount/sale'
 * @param {object} body
 * @param {object} options retry/timeout
 */
async function rawProxy(subPath, body, options = {}) {
  const r = await _withRetry(
    () => ax.post(subPath, body, { timeout: options.timeout || TIMEOUT_MS }),
    options.attempts || 3,
  );
  return r.data;
}

function healthz() {
  return { ok: true, endpoint: ENDPOINT, sessions: _sessionCache.size };
}

module.exports = {
  getZone,
  login,
  sendSale,
  sendSaleOrder,
  getInventory,
  rawProxy,
  healthz,
};
