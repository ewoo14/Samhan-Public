/**
 * Google Sheets 직접 read 클라이언트 — Service Account JWT 인증 + in-memory cache.
 *
 * legacy Apps Script 의 SpreadsheetApp.openById(SRC_SHEET_ID).getSheetByName(name).getDataRange().getValues()
 * 호출을 동등 결과로 반환하기 위한 Node.js 측 helper.
 *
 * 환경변수:
 *  - GOOGLE_SERVICE_ACCOUNT_KEY: Service Account JSON 키 파일의 절대 경로 (권장)
 *  - GOOGLE_SA_KEY_JSON_BASE64: 옵션 — JSON 전체를 base64 로 인코딩한 단일 문자열
 *  - SHEET_CACHE_TTL_SEC: 시트 caching TTL (기본 300초 = 5분)
 *
 * 캐시 정책 (M-LEGACY-V2 §4.1):
 *  - TTL 5분 (단가/품목은 분 단위 변경 빈도 낮음)
 *  - 메모리 한계: 시트 27탭 * 평균 1MB ≈ 30MB (카페24 1G 한도 내 안전)
 *  - 무효화: clearCache() (POST /rpc/clearSheetCache)
 */

'use strict';

const fs = require('fs');
const { google } = require('googleapis');

const TTL_MS = (parseInt(process.env.SHEET_CACHE_TTL_SEC || '300', 10) || 300) * 1000;

const cache = new Map(); // key=`${spreadsheetId}!${range}` → { value, expireAt }
let _sheetsClient = null;
let _authClient = null;

/**
 * Service Account JWT 인증 클라이언트 (singleton).
 * 우선순위: GOOGLE_SERVICE_ACCOUNT_KEY (파일 path) → GOOGLE_SA_KEY_JSON_BASE64.
 */
function _getAuth() {
  if (_authClient) return _authClient;

  let credentials;
  const keyPath = process.env.GOOGLE_SERVICE_ACCOUNT_KEY;
  const keyB64 = process.env.GOOGLE_SA_KEY_JSON_BASE64;

  if (keyPath && fs.existsSync(keyPath)) {
    credentials = JSON.parse(fs.readFileSync(keyPath, 'utf8'));
  } else if (keyB64) {
    credentials = JSON.parse(Buffer.from(keyB64, 'base64').toString('utf8'));
  } else {
    throw new Error(
      '[google-sheets-client] Service Account 키 미설정 — GOOGLE_SERVICE_ACCOUNT_KEY 또는 GOOGLE_SA_KEY_JSON_BASE64 필요',
    );
  }

  _authClient = new google.auth.JWT({
    email: credentials.client_email,
    key: credentials.private_key,
    scopes: ['https://www.googleapis.com/auth/spreadsheets.readonly'],
  });
  return _authClient;
}

function _getSheets() {
  if (_sheetsClient) return _sheetsClient;
  _sheetsClient = google.sheets({ version: 'v4', auth: _getAuth() });
  return _sheetsClient;
}

/**
 * 시트 read — getDataRange().getValues() 와 동등.
 * @param {string} spreadsheetId
 * @param {string} sheetName 탭 이름 (legacy 의 SheetByName 인자)
 * @returns {Promise<Array<Array<any>>>} 2차원 배열 (legacy values 와 shape 동일)
 */
async function readSheet(spreadsheetId, sheetName) {
  const range = `'${sheetName}'!A1:ZZ`;
  const cacheKey = `${spreadsheetId}!${range}`;
  const now = Date.now();

  const hit = cache.get(cacheKey);
  if (hit && hit.expireAt > now) return hit.value;

  const sheets = _getSheets();
  const resp = await sheets.spreadsheets.values.get({
    spreadsheetId,
    range,
    valueRenderOption: 'UNFORMATTED_VALUE',
    dateTimeRenderOption: 'FORMATTED_STRING',
  });
  const values = resp.data.values || [[]];

  cache.set(cacheKey, { value: values, expireAt: now + TTL_MS });
  return values;
}

/**
 * 캐시 전체 무효화 (sheet schema 변경 시).
 */
function clearCache() {
  cache.clear();
}

/**
 * 헬스체크 — Service Account 키 존재 여부 + 클라이언트 초기화 가능 여부.
 */
function healthz() {
  try {
    _getAuth();
    return { ok: true, cacheSize: cache.size, ttlMs: TTL_MS };
  } catch (e) {
    return { ok: false, error: String(e.message || e) };
  }
}

module.exports = { readSheet, clearCache, healthz };
