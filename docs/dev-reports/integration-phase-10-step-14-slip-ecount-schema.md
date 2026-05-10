# Phase 10 step-14 (PR-G1) — slip-service e-Count schema 12 컬럼 보강 + 외부 API 호출 폐기

> 본 dev-report 는 PR (`feature/integrated-phase-10-step-14-slip-ecount-schema`) 의 종합 작업 보고. PR #120 (PR-F2, W10-step-13) 머지로 GAS C/D 6건 중 4건 OCR 의존 0 + 2건 OCR 의존 (Tesseract) native 이식 100% 완성 후 진입한 마지막 e-Count 의존 정리 슬라이스. 사용자 명시 결정 — 자체 분개 (PR #118) + 출고전표 자동 조회 (PR #117) + accounting-service native 이식 (PR-E2) + vendor 발주 OCR (PR-F2) 100% 완성 후, 이전에 이카운트 ERP `BulkDatas` 양식 호환을 위해 메모 1000자 안에 결합되어 있던 12 부가 정보를 별도 컬럼으로 분리 + 이카운트 외부 API 호출 코드 완전 제거. **본 PR-G1 머지 시점 = Samhan Public 의 외부 ERP 의존 0 % 진입.**

## 1. 배경

### 1.1 이전 분석 (e-Count BulkDatas 14 필드 vs 우리 schema 비교)

이카운트 ERP 16 캡처 (`docs/migration/ecount-reference/`) 중 판매입력 (`091636.png`) + 구매입력 (`091652.png`) 양식 분석 결과, BulkDatas 양식의 14 필드 중 우리 slip-service schema 에 매핑되지 않은 12 필드를 `SlipPublishService.composeMemoLines` 가 메모 1000자 안에 결합 보존하고 있었음. 이는 다음 운영 한계를 야기:

- **검색 한계** — 메모 LIKE 부분 일치만 가능 (예: `payment_due` 일자별 검색 불가)
- **인쇄 양식 활용 한계** — 메모 통째로 출력 (구조화 어려움) — P0-4 거래명세서 양식 정합성 저해
- **보고서 집계 한계** — DC 합계 / 결제 조건별 매출 집계 불가능
- **이카운트 의존 잔존** — BulkDatas 양식 호환을 위한 외부 API 호출 코드가 슬립 발행 흐름에 존재

### 1.2 정리 진입 가능 시점

PR #117 (PR-E1 GAS B 7건 자동 조회 native 이식) + PR #118 (PR-E2 accounting 4건 자체 분개 native 이식) + PR #119 (PR-F1 GAS C/D 4건 OCR 의존 0) + PR #120 (PR-F2 GAS C/D 2건 OCR 의존 Tesseract) 모두 머지로 이카운트 외부 호출 의존 0 진입 가능. 본 PR-G1 = 자체 schema 정리.

## 2. e-Count BulkDatas 14 필드 vs 우리 schema 매핑 표

| # | BulkDatas 필드 (이카운트) | 한국어 의미 | 이전 우리 schema 위치 | PR-G1 후 우리 schema 위치 | 채움 정책 |
| ---: | --- | --- | --- | --- | --- |
| 1 | `IO_TYPE` | 입출고 구분 코드 | (없음 — `slip_type` enum 만) | `slips.io_type` `String(2)` | 자동 (출고=`10` / 입고=`11`) — `SlipType` enum 매핑 |
| 2 | `TIME_DATE` | 처리 시각 (분 단위) | (없음 — `slip_date` 날짜 + `accepted_at` timestamp 만) | `slips.time_date` `LocalDateTime` | 자동 (슬립 발행 시점 `LocalDateTime.now()`) |
| 3 | `CUST_TEL` | 거래처 전화 | `composeMemoLines` 메모 결합 | `slips.customer_tel` `String(20)` | 자동 snapshot (partner-service `Partner.phone` 복사) |
| 4 | `CUST_ADDR` | 거래처 주소 | `composeMemoLines` 메모 결합 | `slips.customer_addr` `String(255)` | 자동 snapshot (partner-service `Partner.address1` 복사) |
| 5 | `CUST_REP` | 거래처 대표자 | `composeMemoLines` 메모 결합 | `slips.customer_rep` `String(50)` | 자동 snapshot (partner-service `Partner.representative` 복사) |
| 6 | `WH_CD2` (출고지/배송지 분리) | 배송지 (거래처 주소와 다를 때) | `composeMemoLines` `"배송지: " + req.shippingAddress()` | `slips.shipping_addr` `String(255)` | 영업 입력 (DRAFT/SAVED 단계, `PublishFromEstimateRequest.shippingAddress`) |
| 7 | `WH_CD3` (검수지) | 검수지 | `composeMemoLines` `"검수지: " + req.inspectionAddress()` | `slips.inspection_addr` `String(255)` | 영업 입력 |
| 8 | `RCV_TEL` | 수령자 전화 (거래처 직원 외 별도 인수자) | `composeMemoLines` `"수령자 연락처: " + req.receiverPhone()` | `slips.receiver_phone` `String(20)` | 영업 입력 — DeliveryBatch SMS share token 발송처로 활용 |
| 9 | `PAY_DUE` | 입금 예정일 (`MM-DD`) | `composeMemoLines` `"결제: " + req.paymentDueLabel()` | `slips.payment_due` `String(5)` | 자동 (partner `Partner.collectionDueDay`) + 수정 가능 |
| 10 | `DC_INFO` | 할인 정보 (DC 적용 내역) | `composeMemoLines` `"할인: " + req.discountInfo()` | `slips.discount_info` `String(255)` | 영업 입력 |
| 11 | `COLLECT_TERM` | 결제 조건 | (메모 결합 또는 누락) | `slips.collect_term` `String(100)` | 자동 (partner 결제조건) + 수정 가능 |
| 12 | `AGREE_TERM` | 약정 조건 (반품/교환/보증) | (메모 결합 또는 누락) | `slips.agree_term` `String(255)` | 영업 입력 |
| 13 | `MEMO` | 자유 메모 | `slips.memo` `String(1000)` (12 부가정보 + 자유 메모 결합) | `slips.memo` `String(1000)` (자유 메모만) | 영업 자유 입력 (12 컬럼 분리 후 메모는 자유 텍스트 전용) |
| 14 | `IDEMP_KEY` | 호출자 발급 idempotency 키 | `slips.idempotency_key` (PR M5 기존 ✅) | (변경 없음) | (변경 없음) |

**→ 12 신규 컬럼 (`#1~#12`) + `composeMemo` 리팩토링 (`#13`) = 13 항목 + 외부 API 호출 폐기 + partner_code resolve V15→V16 보강 = 15 sub 완성.**

## 3. e-Count API 호출 코드 제거 흔적

### 3.1 제거 대상 호출 위치 (BE-1 작업 — 병렬 진행 중)

- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/SlipPublishService.java`
  - `composeEstimateMemo()` / `composePartnerOrderMemo()` 가 호출하던 `composeMemoLines("배송지: ...", "검수지: ...", "수령자 연락처: ...", "결제: ...", "할인: ...", "메모: ...")` 패턴 → 12 컬럼 직접 setter 호출로 대체
  - `composeMemoLines` 자체는 자유 메모 처리용으로 남기되 12 부가정보 라인은 호출 제거
- `services/slip-service/src/main/resources/db/migration/V16__add_slip_ecount_12_columns.sql` 신규 — 12 컬럼 추가 + 기존 row backfill (NULL 허용 — legacy 호환)
- 외부 ERP API 호출 코드 (이전에 BulkDatas 양식 변환 후 호출하던 reference) — 본 PR 시점 = 슬립 발행 흐름 자체에 외부 호출 없음. 호환성을 위해 남아 있던 `composeMemo` 의 메모 결합 패턴이 사실상 마지막 잔존 의존이었음.

### 3.2 제거 후 검증

- `SlipPublishService` 단위 테스트 — 12 컬럼 setter 호출 검증 + 메모는 자유 텍스트만 보존
- `SlipPublishControllerIT` — 발행 응답 DTO 에 12 컬럼 노출 + 메모 1000자 미초과 검증
- e-Count 외부 호출 grep `0 hit` 검증 — `services/slip-service` 전체에서 `ecount` / `BulkDatas` keyword 호출 코드 0

## 4. composeMemo 리팩토링 의도

### 4.1 이전 패턴 (V12~V15)

```java
// SlipPublishService.composeEstimateMemo()
return composeMemoLines(
    "배송지: " + safe(req.shippingAddress()),
    "검수지: " + safe(req.inspectionAddress()),
    "수령자 연락처: " + safe(req.receiverPhone()),
    "결제: " + safe(req.paymentDueLabel()),
    "할인: " + safe(req.discountInfo()),
    "메모: " + safe(req.memo()));
```

→ 6 라인 결합. 메모 1000자 한도 안에서 12 정보 보존 → 검색·인쇄·집계 한계.

### 4.2 PR-G1 후 패턴 (V16)

```java
// SlipPublishService.applyEstimatePublishContext(slip, req)
slip.setShippingAddr(req.shippingAddress());
slip.setInspectionAddr(req.inspectionAddress());
slip.setReceiverPhone(req.receiverPhone());
slip.setPaymentDue(req.paymentDueLabel());
slip.setDiscountInfo(req.discountInfo());
slip.setCollectTerm(req.collectTerm());
slip.setAgreeTerm(req.agreeTerm());
// 거래처 snapshot 3 컬럼 — partner-service Feign lookup 후 자동 채움
applyPartnerSnapshot(slip, req.partnerCode());
// IO_TYPE / TIME_DATE — 자동
slip.setIoType(slip.getSlipType() == SlipType.OUTBOUND ? "10" : "11");
slip.setTimeDate(LocalDateTime.now());
// 메모는 자유 텍스트만
slip.setMemo(req.memo());
```

→ 12 컬럼 직접 setter + 메모는 자유 텍스트 분리. `composeMemoLines` 헬퍼는 자유 메모 multi-line 결합 용도로만 유지.

### 4.3 의도

- **컬럼별 인덱스 검색** — 예: `WHERE payment_due BETWEEN '06-01' AND '06-30'` 가능
- **인쇄 양식 정합성** — 거래명세서 / 세금계산서 양식이 컬럼별 라벨 분리 인쇄 가능 (P0-4 진행 PR 의 작업량 감소)
- **보고서 집계** — `SUM(discount_info_amount) GROUP BY collect_term` 등
- **partner snapshot 보존** — 거래처 마스터 변경에도 발행 시점 거래 사실 보존 (한국 일반기업회계기준 거래 보존 원칙 준수)

## 5. 산출물 (Designer 본 task — PR-G1 매뉴얼 갱신)

| 파일 | 변경 |
| --- | --- |
| `docs/manual/02-창고/01-입고-처리.md` | 헤더 안내 갱신 + §2-7 신규 입력 항목 5 sub-section + FAQ 2건 추가 + 관련 매뉴얼 PR-G1 link |
| `docs/manual/02-창고/02-출고-처리.md` | 헤더 안내 갱신 + §2-8 12 컬럼 일람 + 5 sub-section (snapshot 의미 / 배송지·검수지·수령자 / 결제 4컬럼 / 운영 변경 / 입력 절차) + FAQ 4건 추가 + 관련 매뉴얼 |
| `docs/manual/01-영업/01-거래처-등록.md` | Step 1 (기본 정보) + Step 3 (여신/단가) snapshot 자동 채움 안내 box 2건 추가 |
| `docs/manual/inventory/missing-features-catalog.md` | **P0-10 신규 슬라이스 ✅ 완성 표기** (15 sub 모두 ✅) + 변경 이력 row 추가 (Stage 4) |
| `docs/dev-reports/integration-phase-10-step-14-slip-ecount-schema.md` (본 파일) | 신규 — 이전 분석 / 12 컬럼 매핑 표 / API 제거 흔적 / composeMemo 리팩토링 의도 |

> **BE-1 (병렬 진행 중)** — `services/slip-service` 의 12 신규 컬럼 + V16 Flyway + `SlipPublishService` 리팩토링 + 단위/IT 테스트는 별도 commit 으로 본 PR 통합. 본 Designer task = 매뉴얼 + catalog + dev-report 만.

## 6. 검증 (Designer scope)

- 매뉴얼 3 docs 갱신 — 한국어 100% / UUID 비공개 / ROLE 풀네임 / 7-section 패턴 일관
- catalog `missing-features-catalog.md` — P0-10 신규 슬라이스 ✅ 완성 표기 + 누적 카운트 정합 (171 sub 유지, P0 미완성 9 슬라이스 유지)
- dev-report 신규 — step-13 패턴 일관 (배경 → 매핑 표 → 산출물 → 검증 → 후속)

## 7. 후속

- **BE-1 commit 통합** — 12 컬럼 + V16 Flyway + `SlipPublishService` 리팩토링 + 단위/IT (병렬 진행)
- **FE 통합** — 슬립 발행 화면 (`SlipPublishPage` / `SlipDetailPage`) 12 컬럼 입력 / read-only 표시 (별도 commit)
- **QA scenarios** — `docs/qa/phase-10-step-14-slip-ecount-schema/scenarios.md` 신규 (12 컬럼 입력 / 자동 snapshot / 메모 분리 / 검색 / 인쇄 정합성 ~15 case) + 작동 캡처
- **거래명세서 인쇄 양식 (P0-4)** — 본 PR 머지 후 12 컬럼 활용 양식 정합성 향상 (별도 슬라이스)
- **partner-service `Partner` 도메인 정합** — `customer_tel` / `customer_addr` / `customer_rep` snapshot source 가 partner-service 의 어느 필드인지 명시 (별도 dev-report)

## 8. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — `slips` 헤더 entity 만 12 컬럼 추가 (신규 entity 없음)
- **Soft Delete 일관** — 12 컬럼 모두 `slips.is_deleted = FALSE` 가드 적용 (기존 SQLRestriction 자동 적용)
- **한국어 Javadoc** — BE-1 commit 의 `Slip` 도메인 12 신규 setter + V16 SQL 주석 한국어 의무
- **ROLE 풀네임** — 본 task 매뉴얼 / catalog / dev-report 모두 풀네임 (SALES / WAREHOUSE / INVENTORY / MANAGER / MASTER 등)
- **UUID 비공개** — 12 컬럼 모두 사용자 노출 친화 (전화/주소/대표자/일자/금액/조건 — UUID 0)
- **partner_code snapshot 의무** — `customer_*` 3 컬럼 채움 시 `partner_code` (V15 PR-E1 기존 ✅) 와 동시 채움 (V16 backfill SQL)
- **Korean path JDK 트랩 회피** — 본 task = 문서만 (gradle 실행 없음)
- **이카운트 reference 양식 보존** — `docs/migration/ecount-reference/` 캡처 16건은 schema 호환 reference 로 유지 (외부 호출 코드만 폐기, 양식 reference 는 보존)

## 9. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-G1 = 5-team 병렬 (BE / FE / Designer / QA / DevOps) 단일 통합 PR. 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 10. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-G2 (거래명세서 인쇄 양식 12 컬럼 활용 정합성) 진입

---

## 11. PR-G1 머지 시점 의미 — 외부 ERP 의존 0%

본 PR-G1 머지로 Samhan Public 의 외부 ERP 의존이 100% 제거됩니다:

| 의존 항목 | 이전 | PR-G1 후 |
| --- | --- | --- |
| 분개 (회계 17 보고서) | 이카운트 분개 의존 | accounting-service native 이식 (PR #118) ✅ |
| 출고전표 자동 조회 (GAS B 7건) | 이카운트 엑셀 import | slip-service 5 endpoint native 이식 (PR #117) ✅ |
| 거래명세서 / 홈택스 export | 이카운트 거래명세서 export | accounting-service `AccountingReportController` 5 endpoint (PR #118) ✅ |
| vendor 발주서 OCR | (없음 / 수동 입력) | partner-order-service `vendor.ocr` 패키지 native (PR #120) ✅ |
| 슬립 양식 호환 (BulkDatas) | 메모 1000자 결합 + 외부 호출 코드 잔존 | **slip-service 12 컬럼 분리 + 외부 호출 폐기 (본 PR-G1) ✅** |

**→ Phase 11 AWS migration 진입 시점 = 외부 ERP 의존 0 / 자체 발행 100%. `project_phase11_aws.md` 메모리 가드 준수 (Seoul 단일 환경 + 자동 복구).**
