/**
 * [D-R8-9] lineId 계약 마커 — 전표(매입/매출)/견적 저장 payload 공용 스탬프.
 *
 * <p><b>계약</b>: BE `SlipUpdateRequest.lineIdContract` · `UpdateEstimateRequest.lineIdContract`.
 * 마커가 없는 요청은 서버가 <b>구 클라이언트</b>로 판정해 400 으로 거부한다("앱을 업데이트해
 * 주세요"). 즉 이 스탬프가 빠지면 해당 저장 경로는 <b>전부 400</b> 이 된다.
 *
 * <p><b>왜 서버가 마커를 요구하나</b>: 신규 라인의 `lineId` 는 정상적으로 null 이고, 전 라인을
 * 새 라인으로 교체하는 저장은 lineId 가 0개다. 그래서 서버는 <b>라인만 보고는</b> "계약을 아는
 * 클라이언트가 새 라인만 보낸 것" 과 "계약을 모르는 구 클라이언트가 통째로 보낸 것" 을 구분할 수
 * 없다. 마커는 클라이언트가 <i>자기 자신에 대해</i> 하는 선언이라 그 구분을 라인과 무관하게
 * 성립시킨다. (구 클라이언트의 통째 PUT 은 R8-QA-1 에서 세트 계보를 전량 파괴했다.)
 *
 * <p><b>🔴 왜 호출자가 아니라 api 함수가 스탬프하나</b>: 마커 누락은 <b>조용히</b> 깨지지 않는다 —
 * 400 으로 시끄럽게 깨진다. 그러나 그 시끄러움은 <b>런타임</b>에나 온다. 저장 경로를 새로 추가한
 * 사람이 마커를 기억해야 한다면 언젠가 잊고, 그 사실은 라이브에서야 드러난다. 스탬프를 payload
 * 를 실제로 보내는 <b>단일 길목</b>(updatePurchaseSlip / updateSalesSlip / updateEstimate)에 두면
 * 호출자는 마커의 존재조차 알 필요가 없고, <b>잊을 수도 없다</b>. 그래서 호출자 타입
 * (`SlipUpdateRequest` / `UpdateEstimateRequest`)에는 이 필드를 <b>노출하지 않는다</b>.
 */

/** BE 가 계약 선언으로 인정하는 유일한 값. */
export const LINE_ID_CONTRACT_MARKER = true as const

/** 마커가 얹힌 wire payload — 호출자 타입에는 없고 전송 직전에만 존재한다. */
export type WithLineIdContract<T> = T & { lineIdContract: true }

/**
 * 저장 payload 에 계약 마커를 얹는다.
 *
 * <p>호출자 body 의 `lineIdContract` 는 무시하고 항상 `true` 로 덮는다 — 마커는 클라이언트
 * 버전에 대한 사실이지 호출자가 고를 수 있는 옵션이 아니다.
 */
export function withLineIdContract<T extends object>(body: T): WithLineIdContract<T> {
  return { ...body, lineIdContract: LINE_ID_CONTRACT_MARKER }
}
