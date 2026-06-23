# 거래처코드(bizNo) 열 sweep 그룹4 개발 보고

## 범위
- `EstimateListPage`: nested `partnerBusinessNo` 표시를 제거하고 "거래처 코드" 열을 "거래처" 앞에 분리.
- `TaxInvoiceListPage`: nested `partnerBusinessNo` 표시를 제거하고 "거래처 코드" 열을 "거래처" 앞에 분리.
- `InboundInspectionListPage`: "거래처 코드" 열 추가 및 API summary 타입에 `partnerBusinessNo` 추가.

## C 정찰 결과
- `InboundInspection` 엔티티는 `slipId` / `slipNo`만 보유하며 partner UUID 또는 partnerCode 컬럼은 없다.
- 입고검수 상세 생성 경로는 이미 `slipId`로 slip-service 상세를 조회한다.
- slip-service `SlipDetailResponse`는 `businessNumber` snapshot을 제공한다.
- 따라서 inventory-service에 신규 `PartnerLookupClient`를 추가하지 않고, 기존 `SlipClient`가 `businessNumber`를 파싱해 `InboundInspectionSummaryResponse.partnerBusinessNo`로 전달한다.
- slip-service snapshot 조회 실패 시 목록 자체는 유지하고 거래처 보강 필드만 null로 두는 fail-soft 방식이다. UUID는 응답 필드에는 기존 `slipId`/`inspectionId`만 유지하며 화면 열에는 노출하지 않는다.

## 검증
- 통과: `clients/desktop`에서 `npm run typecheck`.
- 통과: `./gradlew :services:inventory-service:test`.

## 자기리뷰
- A/B 화면은 기존 거래처명 필터 로직을 변경하지 않았다.
- A/B 화면의 nested `partnerBusinessNo` 표시는 제거하고, `fontVariantNumeric: 'tabular-nums'`가 적용된 별도 열로 분리했다.
- C 화면은 `partnerBusinessNo ?? '—'`만 표시하며 UUID는 새 화면 열에 노출하지 않는다.
- inventory-service는 새 partner-service client를 추가하지 않았다. `InboundInspection`에 partner 식별자가 없어 직접 batch lookup 키가 없고, 이미 보유한 `slipId`로 slip-service snapshot의 `businessNumber`를 재사용하는 방식이 가장 좁다.
