/**
 * 견적 finalize → SamhanLogis slip-service 즉시 출고전표 생성 bridge.
 *
 * legacy estimate Code.js sendOrderFromUi (line 1762-1967) 가 e-Count proxy
 * (`http://152.69.228.109:3000/proxy/ecount/sale`) 호출로 SaleList POST 했던
 * 동작을, B2 마이그레이션 결정 (DECISIONS Phase 6 v4 후속 정정 §) 에 따라
 * SamhanLogis slip-service 의 `POST /api/v1/slips` 호출로 1:1 대체한다.
 *
 * 사용자 명시 요건 (estimate-app v2 사양 §4):
 *   "견적 finalize → SamhanLogis slip-service POST /api/v1/slips
 *    (즉시 출고전표 자동 생성) — legacy 가 e-Count /sale 호출했던 동작 그대로"
 *
 * 환경변수:
 *   - SLIP_SERVICE_URL: slip-service base URL (기본 http://localhost:8084)
 *   - SAMHAN_API_BASE_URL: gateway URL (SLIP_SERVICE_URL 미지정시 fallback)
 *   - USE_MOCK_FALLBACK: 'true' 면 endpoint 미구현시 mock slipNo 반환 (dev only)
 */

'use strict';

const axios = require('axios');
const { Logger } = require('./apps-script-shim');

const SLIP_BASE =
  process.env.SLIP_SERVICE_URL ||
  process.env.SAMHAN_API_BASE_URL ||
  'http://localhost:8084';
const USE_MOCK = String(process.env.USE_MOCK_FALLBACK || 'true').toLowerCase() === 'true';

/**
 * legacy SaleList[].BulkDatas 형태의 row 들을 slip-service POST body 로 변환.
 *
 * legacy 단일 row 필드 → slip-service line 필드 매핑:
 *   PROD_CD     → productCode      (모델 코드)
 *   PROD_DES    → productName      (선택)
 *   SIZE_DES    → spec             (스펙 표기)
 *   QTY         → qty              (수량)
 *   PRICE       → unitPriceExVat   (VAT 제외 단가)
 *   USER_PRICE_VAT → unitPriceVat  (VAT 포함 단가)
 *   SUPPLY_AMT  → supplyAmount
 *   VAT_AMT     → vatAmount
 *   REMARKS     → remarks
 *
 * legacy header (BulkDatas 첫 row 의 IO_DATE/CUST/EMP_CD/WH_CD 등) 은
 * slip-service 의 root level 필드로 승격한다 — slip-service Slip entity 가
 * 단일 출고전표를 표현하므로 동일 IO_DATE/CUST 의 모든 line 은 한 slip 에 묶인다.
 *
 * @param {object} legacyOrder — sendOrderFromUi(data) 의 raw input
 * @param {Array<object>} saleList — legacy 가 만든 SaleList (BulkDatas 배열)
 * @returns {object} slip-service POST body
 */
function buildSlipRequest(legacyOrder, saleList) {
  if (!Array.isArray(saleList) || saleList.length === 0) {
    throw new Error('slip-bridge: saleList 비어있음');
  }
  const head = saleList[0].BulkDatas || {};

  return {
    sourceType: 'ESTIMATE',
    estimateNumber: legacyOrder.estimateNumber || null,
    ioDate: head.IO_DATE,                 // yyyyMMdd
    timeDate: head.TIME_DATE,
    partnerCode: head.CUST,               // 거래처 코드
    partnerName: head.CUST_DES || '',
    employeeCode: head.EMP_CD,            // 담당자 코드
    warehouseCode: head.WH_CD,
    ioType: head.IO_TYPE || '10',
    shippingAddress: head.U_TXT1 || '',
    inspectionAddress: head.ADD_TXT_01_T || '',
    receiverPhone: head.ADD_TXT_03_T || '',
    memo: head.ADD_TXT_04_T || '',
    paymentDueLabel: head.ADD_TXT_05_T || '',
    discountInfo: head.ADD_TXT_06_T || '',
    customerTel: head.U_MEMO1 || '',
    customerAddr: head.U_MEMO2 || '',
    customerRep: head.U_MEMO3 || '',

    lines: saleList.map((row, idx) => {
      const b = row.BulkDatas || {};
      return {
        lineNo: idx + 1,
        productCode: b.PROD_CD,
        productName: b.PROD_DES || '',
        spec: b.SIZE_DES || '',
        qty: Number(b.QTY) || 0,
        unitPriceExVat: Number(b.PRICE) || 0,
        unitPriceVat: Number(b.USER_PRICE_VAT) || 0,
        supplyAmount: Number(b.SUPPLY_AMT) || 0,
        vatAmount: Number(b.VAT_AMT) || 0,
        remarks: b.REMARKS || '',
      };
    }),
  };
}

/**
 * slip-service `POST /api/v1/slips` 호출.
 *
 * @param {object} legacyOrder — sendOrderFromUi(data) 의 input (header 필드 추출용)
 * @param {Array<object>} saleList — legacy SaleList (line per BulkDatas)
 * @returns {Promise<{ok:boolean, slipNo?:string, body:object}>}
 */
async function postSlip(legacyOrder, saleList) {
  const url = `${SLIP_BASE}/api/v1/slips`;
  let body;
  try {
    body = buildSlipRequest(legacyOrder, saleList);
  } catch (e) {
    return { ok: false, error: e.message, body: null };
  }

  Logger.log(`[slip-bridge] POST ${url} (lines=${body.lines.length})`);

  try {
    const resp = await axios.post(url, body, {
      timeout: 15000,
      validateStatus: () => true,
    });
    if (resp.status >= 200 && resp.status < 300) {
      const slipNo =
        (resp.data && (resp.data.slipNo || resp.data.slipNumber)) ||
        `SLP-${Date.now()}`;
      return { ok: true, slipNo: String(slipNo), body: resp.data };
    }
    Logger.log(`[slip-bridge] non-2xx status=${resp.status} body=${JSON.stringify(resp.data)}`);
    if (USE_MOCK) {
      const mockSlip = `MOCK-${Date.now()}`;
      Logger.log(`[slip-bridge] USE_MOCK_FALLBACK → returning mock slipNo=${mockSlip}`);
      return { ok: true, slipNo: mockSlip, body: { mock: true, originalStatus: resp.status } };
    }
    return { ok: false, error: `HTTP ${resp.status}`, body: resp.data };
  } catch (err) {
    Logger.log(`[slip-bridge] axios error ${err.message}`);
    if (USE_MOCK) {
      const mockSlip = `MOCK-${Date.now()}`;
      Logger.log(`[slip-bridge] USE_MOCK_FALLBACK (network error) → returning mock slipNo=${mockSlip}`);
      return { ok: true, slipNo: mockSlip, body: { mock: true, error: err.message } };
    }
    return { ok: false, error: err.message, body: null };
  }
}

module.exports = {
  buildSlipRequest,
  postSlip,
};
