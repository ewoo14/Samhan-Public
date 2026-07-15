/**
 * 단가 출처(priceSource) 공용 판정 — 전표(SlipFormPage)/견적(EstimateFormPage) 동일 semantics 보장.
 *
 * R4-F1: 두 페이지가 각자 자동채움 판정을 들고 있다가 견적만 구식 조건으로 남아,
 * 품목 교체 시 이전 품목의 단가·REMEMBERED 마커가 새 품목으로 승계되는 비대칭 결함이
 * 발생했다(마커 거짓 + 저장 시 가격기억 오염). 판정을 단일 헬퍼로 공유해 구조적으로
 * 재발을 차단한다.
 */

/** 라인 단가 출처 — design-system LineDraft.priceSource / 견적 DraftLine.priceSource 와 동일 union. */
export type LinePriceSource = 'REMEMBERED' | 'CATALOG' | 'USER' | null | undefined

/** 자동채움 유래 단가 여부 — CATALOG(판매가, D-R4-1: 실체 = product.sellingPrice) 또는 REMEMBERED(거래처 최근단가). */
export const isAutoPriceSource = (source: LinePriceSource): boolean =>
  source === 'CATALOG' || source === 'REMEMBERED'

/**
 * 품목 선택/교체 시 단가 자동채움 허용 여부 — 전표/견적 공용(R3 전표 fix semantics).
 *
 * 사용자가 직접 입력(USER)한 단가만 불가침이다. 빈 단가('0'/공백)는 물론, 이전 품목의
 * 자동채움 단가(CATALOG/REMEMBERED)도 새 품목 기준으로 재채움(판매가 + 가격기억 재조회)해야
 * 품목만 바뀌고 단가·마커가 남는 데이터 오염을 막는다.
 */
export const shouldAutoFillPrice = (source: LinePriceSource, unitPrice: string): boolean =>
  source !== 'USER' && (!unitPrice || unitPrice === '0' || isAutoPriceSource(source))
