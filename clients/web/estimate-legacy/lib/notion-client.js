/**
 * Notion API 직접 호출 클라이언트 — legacy UrlFetchApp.fetch('https://api.notion.com/v1/...') 1:1.
 *
 * legacy 가 사용하는 5+ DB:
 *  - NOTION_DB_ID_DC       = 193a1006d6588161a02cc8f196d7102b (DC config)
 *  - NOTION_DB_ID_ORDER    = 2eca1006d65880109d91c2e56fab28f4 (주문 이력)
 *  - NOTION_DB_ID_AUTH     = 198a1006d65880ddb510e0d525c5e9da (estimate) / 2dda1006d6588047b1bbc7c2660203c0 (order) (거래처 인증)
 *  - NOTION_DB_ID_SNAPSHOT = 33aa1006d6588087810ffaa7dc7f315c (스냅샷/로그)
 *
 * 환경변수:
 *  - NOTION_VERSION (기본 2025-09-03)
 *  - NOTION_TOKEN_DC / NOTION_TOKEN_ORDER / NOTION_TOKEN_AUTH / NOTION_TOKEN_SNAPSHOT
 *  - NOTION_TOKEN_QUOTE / NOTION_TOKEN_SEND / NOTION_TOKEN_SHIPPING / NOTION_TOKEN_LOG (옵션)
 *
 * 본 모듈은 axios 만 사용 (HTTPS POST 단순) — `@notionhq/client` SDK 도 가능하나,
 * legacy 가 전송하는 properties 의 raw shape 을 그대로 보존하기 위해 axios 가 더 안전하다.
 */

'use strict';

const axios = require('axios');

const NOTION_VERSION = process.env.NOTION_VERSION || '2025-09-03';
const BASE = 'https://api.notion.com/v1';
const TIMEOUT_MS = 15000;

/**
 * 토큰 종류별 axios instance map.
 */
const _clients = new Map();

/**
 * @param {string} tokenKind 'DC' | 'ORDER' | 'AUTH' | 'SNAPSHOT' | 'QUOTE' | 'SEND' | 'SHIPPING' | 'LOG'
 */
function _getClient(tokenKind) {
  if (_clients.has(tokenKind)) return _clients.get(tokenKind);
  const envKey = `NOTION_TOKEN_${tokenKind}`;
  const token = process.env[envKey];
  if (!token || token.startsWith('__REPLACE')) {
    throw new Error(
      `[notion-client] ${envKey} 미설정 — .env 에 실 토큰 입력 필요`,
    );
  }
  const ax = axios.create({
    baseURL: BASE,
    timeout: TIMEOUT_MS,
    validateStatus: () => true,
    headers: {
      Authorization: `Bearer ${token}`,
      'Notion-Version': NOTION_VERSION,
      'Content-Type': 'application/json',
    },
  });
  _clients.set(tokenKind, ax);
  return ax;
}

/**
 * 단일 page 생성 — legacy UrlFetchApp.fetch('https://api.notion.com/v1/pages', {payload}) 1:1.
 *
 * @param {string} tokenKind
 * @param {string} databaseId
 * @param {object} properties Notion property object
 * @returns {Promise<object>} Notion API 원본 응답
 */
async function createPage(tokenKind, databaseId, properties) {
  const ax = _getClient(tokenKind);
  const r = await ax.post('/pages', {
    parent: { database_id: databaseId },
    properties,
  });
  if (r.status >= 400) {
    throw new Error(
      `[notion-client] createPage 실패 (${r.status}): ${JSON.stringify(r.data)}`,
    );
  }
  return r.data;
}

/**
 * Database query — legacy `${BASE}/databases/${id}/query` 1:1.
 */
async function queryDatabase(tokenKind, databaseId, queryBody = {}) {
  const ax = _getClient(tokenKind);
  const r = await ax.post(`/databases/${databaseId}/query`, queryBody);
  if (r.status >= 400) {
    throw new Error(
      `[notion-client] queryDatabase 실패 (${r.status}): ${JSON.stringify(r.data)}`,
    );
  }
  return r.data;
}

/**
 * Data source query — Notion 2025-09-03 스펙 일부 호환.
 */
async function queryDataSource(tokenKind, dataSourceId, queryBody = {}) {
  const ax = _getClient(tokenKind);
  const r = await ax.post(`/data_sources/${dataSourceId}/query`, queryBody);
  if (r.status >= 400) {
    throw new Error(
      `[notion-client] queryDataSource 실패 (${r.status}): ${JSON.stringify(r.data)}`,
    );
  }
  return r.data;
}

/**
 * Database meta — legacy databaseRetrieve.
 */
async function retrieveDatabase(tokenKind, databaseId) {
  const ax = _getClient(tokenKind);
  const r = await ax.get(`/databases/${databaseId}`);
  if (r.status >= 400) {
    throw new Error(
      `[notion-client] retrieveDatabase 실패 (${r.status}): ${JSON.stringify(r.data)}`,
    );
  }
  return r.data;
}

/**
 * Generic raw call — apps-script-shim 의 UrlFetchApp 가 위임 사용.
 *
 * @param {string} url 절대 URL
 * @param {object} options { method, headers, body }
 * @returns {Promise<{status:number, data:any}>}
 */
async function rawCall(url, options = {}) {
  const tokenHeader = options.headers && options.headers.Authorization;
  const versionHeader = (options.headers && options.headers['Notion-Version']) || NOTION_VERSION;
  const r = await axios.request({
    url,
    method: (options.method || 'POST').toUpperCase(),
    headers: {
      Authorization: tokenHeader,
      'Notion-Version': versionHeader,
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    data: options.body,
    timeout: TIMEOUT_MS,
    validateStatus: () => true,
  });
  return { status: r.status, data: r.data };
}

function healthz() {
  return {
    ok: true,
    notionVersion: NOTION_VERSION,
    tokensConfigured: ['DC', 'ORDER', 'AUTH', 'SNAPSHOT', 'QUOTE', 'SEND', 'SHIPPING', 'LOG']
      .filter((k) => !!process.env[`NOTION_TOKEN_${k}`] && !process.env[`NOTION_TOKEN_${k}`].startsWith('__REPLACE')),
  };
}

module.exports = {
  createPage,
  queryDatabase,
  queryDataSource,
  retrieveDatabase,
  rawCall,
  healthz,
};
