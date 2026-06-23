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

## Opus 5-agent 라운드 fix (Opus 직접)

4-static 리뷰(BE FINDINGS·FE CLEAN·Design FINDINGS·DevOps CLEAN, BLOCKING 0) + 라이브 QA 결과 반영:

1. **🔴 라이브 QA 적발(표시 포맷)**: 거래처 코드가 **하이픈 포함**(`102-81-12301`)으로 표시 → 개발책임자 지시/그룹1(`replace(/\D/g,'')`)·그룹2(partnerCode=digits) 규약은 **숫자만**. 3화면 모두 `partnerBusinessNo.replace(/\D/g, '')` 적용(하이픈 제거). 정적 4-리뷰가 못 잡고 **라이브 화면 캡처가 단독 적발**.
2. **Design P3**: InboundInspection `slipNo` 열에 `tabular-nums`+`fontWeight:500` render 추가(EstimateNo 일관).
3. **BE NOTE**: `SlipClient` businessNumber 다중키 파싱에 주석(실 키=`businessNumber`, 나머지=rename 대비 fallback).
4. **BE P2(N+1 기존부채)**: `InboundInspectionService.toSummaryResponse` Javadoc에 N+1 known-issue + fail-soft 명시(후속 스냅샷 백필/batch-detail 해소 예정).
- 수용(미수정): 헤더 '거래처' vs 그룹2 '거래처명' 통일(전사 후속), inspectorName 상시 null·IT slipDate 상수화·nonGoodsProduct productType(테스트 위생·기존).

## 라이브 QA (Docker, mock OFF, dev_master/MASTER)

실 스택(게이트웨이:8080, 재빌드 inventory-service, slip-service)에서 데스크톱 standalone 렌더러(:5175) dev_master 로그인 → 3화면 실 캡처 `docs/qa/partner-bizno-sweep-group4/`:
- **견적**(`01`): 거래처 코드(숫자) 열이 거래처명 앞, 실데이터(1927건 중) ✅
- **세금계산서**(`02`): 거래처 코드 숫자(하이픈 제거 확인: `1028112301`…) 거래처명 앞 ✅
- **입고검수**(`03`): "거래처 코드" 열 헤더 거래처명 앞 정위치 ✅. ⚠️ **시드 0건**(빈 목록) — bizNo-with-data 라이브 미표시, BE IT(`InboundInspectionControllerIT` partnerBusinessNo='1234567890' 단언)가 해석 경로 커버. 정직 보고(가짜 캡처 금지).
