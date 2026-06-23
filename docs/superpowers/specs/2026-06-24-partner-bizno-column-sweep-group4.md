# 거래처코드(bizNo) 열 sweep — 잔여 화면 (그룹4) 설계서

> 작성 2026-06-24 (Opus 기획). 연관: 거래처코드 sweep 에픽([[project_... ]]), 선행 그룹1 #578(회계 6보고서)·그룹2(판매/주문)·그룹3(아로로지스) 완료.
> 개발책임자 지시: **거래처명 표시 전 화면에 거래처코드(bizNo 숫자, 하이픈 제거) 열을 거래처명 앞에** 추가. UUID 미노출.

## 1. 스코프 (잔여 3화면)

코드 대조 검증(2026-06-23)으로 그룹2/3 은 이미 완료 확인. 실제 잔여 = 데스크톱 3화면:

| # | 화면 | 현 상태 | 작업 |
|---|---|---|---|
| A | `EstimateListPage` | `partnerBusinessNo` 가 거래처명 **밑 nested**(회색 소형) | **별도 "거래처 코드" 열을 거래처명 앞에** 분리(데이터 가용, FE-only) |
| B | `TaxInvoiceListPage` | 동일(nested) | 동일(FE-only) |
| C | `InboundInspectionListPage` | `partnerName` 만, bizNo **없음** | inventory-service 요약에 bizNo 추가 + FE 열(BE+FE) |

표준 패턴 = 그룹2 `SalesPartnerOrderListPage`(별도 "거래처 코드"/"거래처명" 2열, 코드 앞).

## 2. A·B — Estimate / TaxInvoice (FE-only)

- 현 `partnerName` 열의 nested `partnerBusinessNo` 표시를 제거하고, **"거래처 코드" 열을 "거래처" 열 앞에 신규** 추가.
- "거래처 코드" 렌더: `row.partnerBusinessNo`(숫자, 하이픈 제거 형태 가정 — 기존 nested 와 동일 소스) / 없으면 `—`. `fontVariantNumeric:'tabular-nums'`.
- "거래처" 열은 `partnerName` 만(nested 제거).
- 데이터 변경 0(이미 `partnerBusinessNo` 존재). UUID 미노출 유지.

## 3. C — InboundInspection (BE + FE)

### BE (inventory-service)
- `InboundInspectionListPage` 데이터 소스 = inventory-service 입고검수 목록 요약. 현 요약 DTO 에 `partnerName` 만 존재 → **`partnerBusinessNo` 추가**.
- 해석: InboundInspection 이 보유한 **거래처 식별자(UUID 또는 partnerCode)**로 bizNo 조회. **#578 패턴 답습** — inventory-service 에 `PartnerLookupClient`(accounting `findByPartnerIdsBatch` 동급) 존부 확인 후:
  - 있으면 배치 조회로 `partnerId→bizNo` 매핑, 요약에 채움.
  - 없으면 accounting `PartnerLookupClient` 패턴(`POST /internal/partners/find-by-ids-batch` 또는 동급 partner-service 엔드포인트, bizNo 반환, UUID 미노출)으로 신규 client 추가(계약테스트 동반 — [[restclient-contract-test-false-green]]).
- partner UUID 가 InboundInspection 에 없으면(이름만 저장) → 조회 키 부재. 이 경우 **partnerCode/사업자 식별자 저장 여부 확인** 후 가능한 키로 해석. (Codex 정찰 후 결정 — 불가 시 사유 명시·부분 구현.)

### FE
- `InboundInspectionListPage` 에 "거래처 코드" 열을 "거래처" 앞에 추가(`row.partnerBusinessNo ?? '—'`). `InboundInspectionSummary` 타입에 `partnerBusinessNo: string | null` 추가.

## 4. 비목표 (YAGNI)

- 거래처명 검색/필터 로직 변경 없음. 정렬 변경 없음. 기존 열/동작 무회귀.
- bizNo 형식 변환(하이픈 등) 신규 도입 안 함 — 기존 소스값 그대로(그룹2 일관).

## 5. 검증 / QA (라이브 — 라운드마다 인라인)

- FE 타입검증 `npm run typecheck`([[feedback_desktop_typecheck_command]]) + 변경 모듈 빌드.
- BE(C): inventory-service 변경 모듈 전체 test + bizNo client 계약테스트.
- **라이브 QA(라운드마다)**: 데스크톱 standalone 렌더러(mock off, 게이트웨이:8080, dev_master) 로 3화면 진입 → **거래처 코드 열이 거래처명 앞에 표시되는 실사용자 화면 스크린샷** 인라인 게시. 실 시드 거래처 bizNo 노출 확인.

## 6. 산출물
- FE: EstimateListPage·TaxInvoiceListPage·InboundInspectionListPage + inboundInspectionApi 타입
- BE: inventory-service 입고검수 요약 bizNo 해석(+필요 시 PartnerLookupClient+계약테스트)
- dev-report `docs/dev-reports/2026-06-24-partner-bizno-column-sweep-group4.md`
- 라이브 QA `docs/qa/partner-bizno-sweep-group4/`
