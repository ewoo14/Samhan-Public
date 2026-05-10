# PR-G1 — slip-service e-Count schema 보강 (12 컬럼) + e-Count API 호출 폐기 QA 시나리오

> **branch** — `feature/integrated-phase-10-step-14-slip-ecount-schema`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 10 Step 14 PR-G1 (legacy GAS 의 e-Count BulkDatas 14 필드 중 누락 12 필드를 우리 slip-service 의 1급 컬럼으로 보강 + e-Count REST 의존 완전 제거 + partner_code resolve 구현 + composeMemo prepend 폐기) 가 매뉴얼 + 도메인 정합성을 충족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE-Schema: `services/slip-service/src/main/resources/db/migration/V16__add_slip_ecount_schema.sql` (12 신규 컬럼 + COMMENT 매핑)
> - BE-Domain: `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/Slip.java` (12 신규 필드 + setter — 본 PR-G1 BE-1 적용 예정)
> - BE-Service: `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/SlipPublishService.java` (composeEstimateMemo / composePartnerOrderMemo prepend 6 필드 → 별도 컬럼 직접 저장 리팩토링 + partner_code resolve)
> - BE-DTO: `PublishFromEstimateRequest` / `PublishFromPartnerOrderRequest` (이미 12 신규 필드 모두 수신 — § 5 검증)
> - BE-Polish: e-Count REST 호출 코드 제거 (현 코드베이스 grep 결과 호출자 0건 — 본 PR 가 잔존 dead code 정리)
> - 작동 캡처: `working-slip-form-customer-snapshot.png` + `working-slip-form-shipping-fields.png` + `working-slip-detail-ecount-fields.png` (본 폴더)
> - 단위 테스트 점검: 본 문서 § 5 (BE-1 13 case + 회귀 영향 평가)

---

## 0. 검증 정책

### 0.1 페르소나 4 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **개발책임자 / IT 관리자** | MASTER | high (전 도메인) | high | V16 migration up/down 적용 확인 / e-Count API 호출 코드 잔존 0건 grep / partner_code 가 partner-service Feign lookup 정상 동작 / 12 컬럼 전부 NULL 허용 (legacy backfill 0) / IO_TYPE='10' OUTBOUND DEFAULT 회귀 |
| **회계 외주** | ACCOUNTANT | 한국 일반기업회계기준 숙련 | 일반 office | 별도 컬럼 분리 후에도 회계 마감 lock (V14) 정상 동작 / 거래처별 원장 / 거래명세서 인쇄 시 customer_tel / customer_address / customer_representative snapshot 표시 (분개 첨부 자료) |
| **신입 영업** | SALES | 거래/세금/단가 미경험 | 일반 office | 출고전표 작성 화면 — 거래처 선택 시 customer_tel/address/representative 자동 snapshot 노출 / 배송지/검수지/수령자전화/입금예정/할인정보 입력 필드 분리 / "메모" 자유 입력은 별도 (composeMemo prepend 폐기 회귀) |
| **숙련 관리** | MANAGER | 전 도메인 | high | from-estimate / from-partner-order 양쪽 publish 후 slip detail 12 컬럼 전부 노출 / 동일 idempotencyKey replay 시 12 컬럼 fingerprint 비교 회귀 / 입고전표 IO_TYPE='11' 분기 동작 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (V16 migration 적용 후 / DTO payload / mock partner-service 응답 — 비즈니스 식별자만, UUID 비공개)
2. **동작** — Playwright `page.click(testid)` / API client `POST /api/v1/slips/from-estimate` 의 구체 step
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()` / 메시지 텍스트) + DB / 응답 assertion (jsonPath / SQL row 검증)
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 운영 차단 (legacy 데이터 손실 / 잘못된 컬럼 매핑 / e-Count 호출 잔존 / partner_code 미저장으로 분개 단절)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요
- 🟡 **Minor** — UX 사소 / 표기 / 캡처 불일치
- 🟢 **Info** — 향후 개선 권고

### 0.4 권한 매트릭스 (풀네임 의무 — `feedback_role_naming_full.md`)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `PARTNER` / `READONLY` 9 ROLE 만 사용. M/M/D 약어 금지.

본 PR 권한:
- **FE 출고전표 작성** = `SALES / MANAGER / MASTER`
- **BE `POST /api/v1/slips/from-estimate`** = 인증 필요 (X-User-Id 헤더), `SALES / MANAGER / MASTER` 모두 허용 (estimate-app v2 호출 출처)
- **BE `POST /api/v1/slips/from-partner-order`** = `MANAGER / MASTER` (partner-order-service M4 → 자동 publish)

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility.md`)

12 신규 컬럼 중 UUID 형태 0건 — 모두 인간 읽기 가능 텍스트 (전화번호 / 주소 / 대표자명 / MM-DD 만기 / 자유 텍스트 할인 / 회수·약정 라벨 / IO_TYPE 두자리 / TIME_DATE HHmmss). 화면 노출 컬럼은 비즈니스 식별자만 — partner_code = 거래처코드 (`P-2026-0001` 형식), UUID 미노출.

---

## 1. 슬라이스 1 — V16 schema 12 신규 컬럼 입력 + 저장 + 조회 (12 case)

**의존 backend** —
- `slip-service` `POST /api/v1/slips/from-estimate` (multipart 아님, JSON, X-User-Id + Idempotency-Key 헤더)
- `slip-service` `POST /api/v1/slips/from-partner-order` (동일 패턴)
- `slip-service` `GET /api/v1/slips/{id}` (slip detail — 12 신규 컬럼 응답 포함)
- DB: `slips` 테이블 V16 migration 적용 후 12 신규 컬럼 ADD

**의존 frontend** — `clients/desktop` `SlipFormPage` (출고전표 작성, `/sales/slips/new`) + `SlipDetailPage` (`/sales/slips/{id}`)

**testid 의존 (실 FE 표준 — 본 PR-G1 FE 슬라이스에서 추가 예정)** — `slip-form-customer-snapshot-card` / `slip-form-customer-tel` / `slip-form-customer-address` / `slip-form-customer-representative` / `slip-form-shipping-address` / `slip-form-inspection-address` / `slip-form-receiver-phone` / `slip-form-payment-due-label` / `slip-form-discount-info` / `slip-form-collect-term` / `slip-form-agree-term` / `slip-form-io-type-radio` / `slip-form-time-date-display` / `slip-detail-ecount-card` / `slip-detail-customer-tel-row` / `slip-detail-shipping-address-row` / 등 12 row testid

### 1.1 io_type — OUTBOUND DEFAULT '10' 저장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | 개발책임자 | 🔴 | V16 적용 직후 fresh DB | (BE) `POST /from-estimate` body `{...estimate fields...}` (ioType 미공급) | DB row `slips.io_type = '10'` (V16 DEFAULT 적용). 응답 `data.ioType = "10"`. | DEFAULT 회귀 시 NULL → IO_TYPE NOT NULL 분기 분개 누락 |

### 1.2 io_type — INBOUND '11' 명시 저장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | 숙련 관리 | 🔴 | V16 적용 + partner-order-service M4 입고 트리거 | (BE) `POST /from-partner-order` body `{ioType: "11", ...}` (입고 전표) | DB row `slips.io_type = '11'`. 응답 `data.ioType = "11"`. SlipType=INBOUND 와 별도 (도메인 enum vs e-Count 코드 — 직교) | 입고 전표 IO_TYPE 회귀 시 회계 분개 차변/대변 반전 누락 |

### 1.3 time_date — HHmmss snapshot

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | 신입 영업 | 🟠 | 시각 14:32:18 발행 | (FE) "발행" 버튼 클릭 | DB row `slips.time_date = '143218'` (HHmmss 6자리). slip detail 화면 `data-testid=slip-detail-time-date-row` 노출 "발행시각 14:32:18" | TIME_DATE 누락 시 동일 일자 다중 발행 시각 정렬 불가 (운영 timeline 단절) |

### 1.4 customer_tel — 거래처 연락처 snapshot

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | 신입 영업 | 🔴 | partner-service partners[code=AIRD-001].contactPhone='02-1234-5678' | (FE) 거래처 코드 'AIRD-001' 선택 | (자동 snapshot) FE 카드 `slip-form-customer-snapshot-card` 노출 + `slip-form-customer-tel` = '02-1234-5678' 표시. 발행 시 DB row `slips.customer_tel = '02-1234-5678'` 저장 | 발행 후 거래처 연락처 변경되어도 슬립 snapshot 보존 — 회계 첨부 자료 정합성 (legacy U_MEMO1 회귀) |

### 1.5 customer_address — 거래처 사업장 주소 snapshot

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | 신입 영업 | 🔴 | partner-service partners[code=AIRD-001].address='서울특별시 강남구 테헤란로 152' | 동일 (1.4.1) 거래처 선택 | FE `slip-form-customer-address` snapshot 표시. DB row `slips.customer_address = '서울특별시 강남구 테헤란로 152'` 저장. 응답 jsonPath `data.customerAddress` 검증 | legacy U_MEMO2 매핑 회귀 — 거래명세서 인쇄 주소 누락 차단 |

### 1.6 customer_representative — 거래처 대표자명 snapshot

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.6.1 | 회계 외주 | 🔴 | partner-service partners[code=AIRD-001].representative='김에어' | 동일 거래처 선택 | FE `slip-form-customer-representative` snapshot 표시 "대표자: 김에어". DB row 저장. 거래명세서 인쇄 mock 시 "대표자명: 김에어" 인쇄 행 노출 | legacy U_MEMO3 — 세금계산서 발행 시 대표자명 누락 시 국세청 검증 fail |

### 1.7 shipping_address — 배송지 주소 (memo prepend 폐기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.7.1 | 신입 영업 | 🔴 | 거래처 사업장 주소와 다른 배송지 (현장 주소) | (FE) `slip-form-shipping-address` 입력 "경기도 성남시 분당구 판교로 235" + "발행" | DB row `slips.shipping_address = '경기도 성남시 분당구 판교로 235'` 저장 (별도 컬럼). DB row `slips.memo` 에 "배송지: ..." prefix **미존재** (composeMemo 리팩토링 회귀). slip detail FE `slip-detail-shipping-address-row` 분리 노출 | composeMemo prepend 회귀 시 memo 1000자 한계 도달 + 자유 메모 압출 + 분리 검색 불가 |

### 1.8 inspection_address — 검수지 주소 (memo prepend 폐기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.8.1 | 신입 영업 | 🔴 | 배송지와 다른 검수지 (제조사 검수센터) | (FE) `slip-form-inspection-address` 입력 "서울특별시 강남구 검수센터 3F" | DB row `slips.inspection_address = '서울특별시 강남구 검수센터 3F'` 저장. memo 에 "검수지: ..." prefix **미존재**. slip detail FE `slip-detail-inspection-address-row` 분리 노출 | legacy ADD_TXT_01_T 매핑 회귀 |

### 1.9 receiver_phone — 수령자 연락처 (memo prepend 폐기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.9.1 | 신입 영업 | 🔴 | 거래처 담당자와 다른 현장 수령자 | (FE) `slip-form-receiver-phone` 입력 '010-9876-5432' | DB row `slips.receiver_phone = '010-9876-5432'` 저장. memo 에 "수령자 연락처: ..." prefix **미존재**. FE PhoneInput 한국 모바일 패턴 검증 OK | ADD_TXT_03_T 회귀 + Aligo SMS 발송 시 잘못된 번호 자동 prepend 회귀 차단 |

### 1.10 payment_due_label — 결제 만기 라벨 (memo prepend 폐기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.10.1 | 회계 외주 | 🟠 | 익월말 결제 정책 | (FE) `slip-form-payment-due-label` dropdown "익월말 결제" 선택 | DB row `slips.payment_due_label = '익월말 결제'` 저장. memo 에 "결제: ..." prefix **미존재**. accounting-service 거래처별 원장 page 결제 만기 컬럼 join 가능 (별도 컬럼 → SQL filter 가능) | legacy ADD_TXT_05_T 매핑 회귀 + memo prepend 시 SQL LIKE 검색 비효율 |

### 1.11 discount_info — 할인 정보 (memo prepend 폐기)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.11.1 | 신입 영업 | 🟠 | 사용자 자유 입력 할인 텍스트 | (FE) `slip-form-discount-info` 입력 "5% DC + 운송비 무상" | DB row `slips.discount_info = '5% DC + 운송비 무상'` 저장. memo 에 "할인: ..." prefix **미존재**. accounting-service 일마감 detail page 할인 컬럼 별도 sum 가능 | ADD_TXT_06_T 회귀 + 할인 자유 텍스트가 memo 와 섞이면 분개 단가 정확성 단절 |

### 1.12 collect_term + agree_term — 대금 회수 / 거래 약정

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.12.1 | 회계 외주 | 🟠 | 거래처 표준 회수 조건 "월말" + 약정 "수표 60일" | (FE) `slip-form-collect-term` "월말" + `slip-form-agree-term` "수표 60일" 선택 | DB row `slips.collect_term = '월말'` + `slips.agree_term = '수표 60일'` 저장. accounting 외상매출금 회수 예정 보고서 join 키 활성. legacy COLL_TERM / AGREE_TERM 1:1 매핑 | legacy 대금 회수 자동화 (월말 일괄 청구) 단절 차단 |

---

## 2. composeMemo 리팩토링 회귀 (4 case)

**리팩토링 의도** — 기존 `composeEstimateMemo` / `composePartnerOrderMemo` 가 6 필드 (배송지/검수지/수령자전화/결제/할인/메모) 를 줄바꿈 결합하여 `Slip.memo` 1000자 컬럼에 prepend 저장하던 패턴을 폐기. 6 필드 중 5 필드는 별도 V16 컬럼으로 직접 저장, "메모" 자유 입력만 `Slip.memo` 에 보존.

### 2.1 정상 — memo 자유 입력만 보존

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | 숙련 관리 | 🔴 | DTO payload `{shippingAddress: "A", inspectionAddress: "B", receiverPhone: "010-...", paymentDueLabel: "월말", discountInfo: "5%", memo: "급송 부탁드립니다", ...}` | publishFromEstimate 호출 | DB row `slips.memo = '급송 부탁드립니다'` (자유 입력만). DB row `slips.shipping_address / inspection_address / receiver_phone / payment_due_label / discount_info` 5 컬럼 모두 별도 저장. **`slips.memo` 에 "배송지:" / "검수지:" / "수령자 연락처:" / "결제:" / "할인:" prefix 0건** (grep 검증) | composeMemo prepend 잔존 시 memo 가 6 필드 합 → 1000자 초과 truncate 위험 + 자유 메모 손실 |

### 2.2 memo null/blank — empty 저장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | 신입 영업 | 🟠 | DTO `{memo: null, shippingAddress: "A", ...}` | publishFromEstimate | DB row `slips.memo` = null 또는 빈 문자열. shipping_address 등 5 컬럼은 정상 저장. | memo 없을 때도 5 컬럼 분리 저장 보장 |

### 2.3 memo 1000자 한계 — 자유 입력 단독으로 truncate

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | 숙련 관리 | 🟡 | DTO `{memo: "x" * 1500, ...}` | publishFromEstimate | DB row `slips.memo` 정확히 1000자 또는 service 레이어가 INVALID_INPUT (`@Size(max=500)` DTO 가드 → 400). 5 컬럼 분리 저장 영향 0. | memo 자유 입력 max 가 5 컬럼 합산 prepend 영향 받지 않음 |

### 2.4 fingerprint canonical — composeMemo 폐기 후 일관성

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | 개발책임자 | 🔴 | 동일 idempotencyKey + 동일 본문 (5 필드 + memo 모두 동일) 으로 2회 호출 | publishFromEstimate × 2 | 1차 → 201, 2차 → 200 + idempotentReplay=true + 동일 slipNo. fingerprint canonical 에 5 신규 컬럼 + memo 7 필드 모두 포함되도록 `computeFingerprint` 갱신 — 5 컬럼 중 1개라도 변경되면 같은 키 → 다른 본문 → 409. | composeMemo 잔존 시 fingerprint 가 prepend 결과 (memo 1000자) 기반이어서 5 컬럼 수정만으로는 fingerprint 변경 미감지 → 잘못된 replay |

---

## 3. e-Count API 호출 폐기 회귀 (5 case)

**리팩토링 의도** — legacy GAS 가 `https://oapi.ecount.com/.../SaveSale` 등 e-Count API 직접 호출하던 패턴을 우리 시스템 자체 publish 흐름으로 완결. e-Count REST 호출 코드 / 호출자 0건 보장.

### 3.1 grep — e-Count REST URL 잔존 0

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | 개발책임자 | 🔴 | feature/integrated-phase-10-step-14-slip-ecount-schema 브랜치 | `grep -r "oapi.ecount.com\|ecount.*RestTemplate\|ecount.*WebClient\|publishToECount\|sendToEcount" services/slip-service/src/main` | 매칭 0건. 단, V12/V16 migration 의 `legacy e-Count` 한국어 주석 / docs / qa scenario 한국어 라벨은 무관 (주석 / 문서). | e-Count API 호출 잔존 시 외부 vendor 의존 + 503 fail loop |

### 3.2 자체 publish 정상 동작 — slipNo 채번 보장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2.1 | 숙련 관리 | 🔴 | SlipNumberService 정상 + V16 적용 | `POST /from-estimate` (e-Count 호출 없이) | 응답 201 + `data.slipNo = "2026/05/10-N"` (yyyy/MM/dd-NNN) + `data.idempotencyKey` echo. SlipPublishService 가 ECountClient 호출 0건 (Mockito.verify 0). | 자체 채번 회귀 시 e-Count 채번 의존 잔존 |

### 3.3 from-partner-order — 자동 publish 보장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3.1 | 숙련 관리 | 🔴 | partner-order-service M4 가 자체 호출 | `POST /from-partner-order` | 응답 201 + sourceType=PARTNER_ORDER + sourceId=partnerOrderId. e-Count 호출 0건. SlipPublishAudit 적재 1행. | partner-order → e-Count → slip 의 우회 경로 잔존 차단 |

### 3.4 idempotency 보장 — e-Count 의존 없이도 replay

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.4.1 | 개발책임자 | 🔴 | DB partial UNIQUE INDEX (idempotency_key) 존재 (V8/V9) | 같은 idempotencyKey + 같은 본문 2회 | 1차 201, 2차 200 + replay flag + 동일 slipNo. e-Count 호출 0건 (verify). | e-Count 호출 잔존 시 replay 시점에 외부 idempotency 충돌 발생 가능 |

### 3.5 audit 적재 — request_fingerprint 자체 SHA-256 보장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.5.1 | 회계 외주 | 🟠 | 발행 후 audit 조회 | `SELECT * FROM slip_publish_audits WHERE slip_id=?` | row 1행 + `request_fingerprint` SHA-256 hex 64자 (e-Count response hash 가 아님 — 우리 canonical JSON SHA-256). | e-Count response hash 잔존 시 외부 응답 의존성 회귀 |

---

## 4. partner_code resolve (4 case)

**구현 의도** — `PublishFromEstimateRequest.partnerCode` (예: "AIRD-001") DTO 입력값을 `Slip.partnerCode` 컬럼에 저장. partner-service Feign lookup 으로 `partnerId` (UUID) 도 보강하여 외래키 정합성 확보. **단, partner-service 응답 미존재 시에도 `partnerCode` 는 raw 저장** (legacy 호환 — V15 컬럼 정책: partner_id NULL 허용 + partner_code source-of-truth).

### 4.1 정상 — partner-service Feign lookup 성공

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1.1 | 숙련 관리 | 🔴 | `partner-service` `GET /api/v1/partners/lookup?code=AIRD-001` → `{id: "uuid-...", code: "AIRD-001", name: "(주)에어디자이너", contactPhone: "02-1234-5678", address: "...", representative: "김에어"}` | `POST /from-estimate` body `{partnerCode: "AIRD-001", ...}` | DB row `slips.partner_code = 'AIRD-001'` (V15 snapshot) + `slips.partner_id = 'uuid-...'` (Feign lookup) + `slips.partner_name = '(주)에어디자이너'` + 1.4 ~ 1.6 의 customer_tel/address/representative 12 컬럼 자동 보강. | partner_code resolve 회귀 시 분개 시점 거래처 식별 단절 |

### 4.2 partner-service 미존재 — partnerCode raw 저장

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.2.1 | 신입 영업 | 🟠 | partner-service `GET /lookup?code=NEW-CUST-999` → 404 (신규 거래처 미등록) | `POST /from-estimate` body `{partnerCode: "NEW-CUST-999", partnerName: "수기 입력 거래처"}` | (정책 결정 권고) 응답 201 + DB row `slips.partner_code = 'NEW-CUST-999'` + `slips.partner_id = NULL` + `slips.partner_name = '수기 입력 거래처'` (DTO partnerName 보존). 후속 backfill 가능 (V15 정책). 또는 (대안) 400 + "거래처 미등록 — 먼저 등록하세요" — 후속 PR § 6 참조. | partner_id NULL 일 때도 partner_code source-of-truth 보장 (V15 backfill 정책) |

### 4.3 partnerCode null/blank — 모두 NULL

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.3.1 | 개발책임자 | 🟠 | DTO `{partnerCode: null, partnerName: null, ...}` | publishFromEstimate | DB row `slips.partner_code = NULL` + `slips.partner_id = NULL` + `slips.partner_name = NULL`. customer_tel/address/representative 12 컬럼 모두 NULL (snapshot lookup skip). | partnerCode 없을 때 NPE / Feign 호출 회귀 차단 |

### 4.4 fingerprint — partnerCode 변경 시 다른 본문 감지

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.4.1 | 개발책임자 | 🔴 | 1차 호출 `{partnerCode: "AIRD-001"}`, 같은 idempotencyKey 로 2차 호출 `{partnerCode: "AIRD-002"}` | publishFromEstimate × 2 | 1차 201, 2차 → **409 Conflict** (canonical fingerprint 의 `partnerCode` 다름). | fingerprint 에 partnerCode 누락 시 잘못된 거래처로 replay 위험 |

---

## 5. 단위 테스트 점검 보고 (BE-1 13 case + 회귀 영향 평가)

> **task spec vs 실 측정**: BE-1 task = "SlipPublishService 5 + e-Count 회귀 4 + partner_code 4 = 13 case". 실 commit 미적용 시점 (본 QA 작성 시점) 기준 추정 — BE-1 commit 후 실 case 수 ≥ 13 검증 의무 (TM 통합 PR 시점 재측정).

### 5.1 SlipPublishService — composeMemo 리팩토링 (5 case 추정)

| # | 메서드 | 검증 핵심 | 평가 (예상) | 비고 |
|---|---|---|---|---|
| 1 | `publishFromEstimate_savesShippingAddressInColumn_notMemoPrepend` | 별도 컬럼 저장 + memo prepend 0 | **PASS 권고** | 시나리오 1.7.1 + 2.1.1 매핑 |
| 2 | `publishFromEstimate_savesPaymentDueLabelInColumn_notMemoPrepend` | payment_due_label 별도 컬럼 | PASS | 시나리오 1.10.1 매핑 |
| 3 | `publishFromEstimate_savesDiscountInfoInColumn_notMemoPrepend` | discount_info 별도 컬럼 | PASS | 시나리오 1.11.1 매핑 |
| 4 | `publishFromEstimate_memoFreeText_preserved_only` | memo 자유 입력만 보존 | PASS | 시나리오 2.1.1 매핑 |
| 5 | `publishFromEstimate_ioType_default_10_when_null` | DEFAULT '10' 적용 | PASS | 시나리오 1.1.1 매핑 |

### 5.2 e-Count 회귀 (4 case 추정)

| # | 메서드 | 검증 핵심 | 평가 (예상) | 비고 |
|---|---|---|---|---|
| 1 | `publishFromEstimate_doesNotCallECountClient` | Mockito verify ECountClient 호출 0 | **PASS 권고 + grep 보강** | 시나리오 3.1.1 + 3.2.1 매핑 |
| 2 | `publishFromPartnerOrder_doesNotCallECountClient` | 동일 | PASS | 시나리오 3.3.1 매핑 |
| 3 | `idempotency_replay_doesNotCallECountClient` | replay 경로도 e-Count 호출 0 | PASS | 시나리오 3.4.1 매핑 |
| 4 | `audit_fingerprint_isLocalSha256_notECountResponseHash` | request_fingerprint 64자 + 자체 canonical | PASS | 시나리오 3.5.1 매핑 |

### 5.3 partner_code resolve (4 case 추정)

| # | 메서드 | 검증 핵심 | 평가 (예상) | 비고 |
|---|---|---|---|---|
| 1 | `publishFromEstimate_savesPartnerCode_andResolvesPartnerId_viaFeign` | partner_code raw + partner_id Feign lookup | **PASS 권고** | 시나리오 4.1.1 매핑. PartnerLookupClient @MockBean 격리 의무 |
| 2 | `publishFromEstimate_partnerNotFound_savesPartnerCode_andNullsPartnerId` | partner-service 404 → partner_code 보존 + partner_id NULL | PASS | 시나리오 4.2.1 매핑 (정책 결정 의존) |
| 3 | `publishFromEstimate_partnerCodeNull_allColumnsNull` | NULL 입력 NPE 없음 + 12 신규 컬럼 NULL | PASS | 시나리오 4.3.1 매핑 |
| 4 | `idempotency_partnerCodeChange_returns409` | fingerprint canonical partnerCode 포함 | PASS | 시나리오 4.4.1 매핑 |

### 5.4 외부 client @MockBean 격리 (`feedback_it_mockbean_external_clients.md`)

기존 `SlipPublishControllerIT` 의 `@MockBean` 7개 (ProductClient / InventoryClient / SlipServiceClient / PartnerAuthClient / etc.) 외에 **PartnerLookupClient (partner-service Feign)** 추가 의무. lenient stub 으로 4.1.1 / 4.2.1 분기 모두 지원.

### 5.5 회귀 시나리오 평가 (격자 매트릭스)

| 회귀 위험 | 현재 cover | 권고 |
|---|---|---|
| V16 migration up 후 기존 IT 영향 (SlipPublishControllerIT 7 case) | ⚠️ 미cover | BE-1 commit 후 `gradle :services:slip-service:test` green 확인 의무 |
| `Slip.memo` 1000자 컬럼에 V16 5 컬럼 prefix 잔존 (composeMemo 부분 회귀) | ✅ 5.1.1 + 5.1.4 cover | OK (assertion 강화 — `assertThat(slip.memo).doesNotContain("배송지:", "검수지:", "수령자 연락처:", "결제:", "할인:")`) |
| e-Count REST URL 잔존 — grep 매칭 | ⚠️ 자동화 부분 cover | CI step 추가 권고 (`grep -r "oapi.ecount.com" services/slip-service/src/main` 매칭 시 fail) |
| partner-service Feign 미부팅 시 502/504 — slip publish 차단 | ⚠️ 미cover | `@CircuitBreaker` 또는 graceful fallback (partner_id NULL 저장) 의무 — 시나리오 4.2.1 정책 결정 |
| fingerprint canonical 5 신규 컬럼 미포함 시 잘못된 replay | ✅ 4.4.1 + 2.4.1 cover | OK (canonicalLine 외 헤더 canonical 도 검증 의무) |
| io_type DEFAULT '10' 가 IT @MockBean 환경에서 적용 안 됨 (Hibernate validate vs PostgreSQL DEFAULT) | ⚠️ 미cover | IT 의 `@Sql` 또는 `@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")` 검증 의무 |
| 입고전표 IO_TYPE='11' 분기 — partner-order-service 가 INBOUND 호출 미지원 | ⚠️ 부분 cover | 시나리오 1.2.1 — 후속 PR 입고 publish endpoint 추가 시 검증 |

### 5.6 종합

- **총 13 case 추정 (BE-1 commit 시점에 실 측정 갱신)**.
- **시나리오 ↔ 단위 테스트 매핑 100%** (12 schema case + 4 composeMemo + 5 e-Count + 4 partner_code = 25 case 시나리오 → 13 단위 + 12 IT/grep 보조).
- **PASS/FAIL 측정 가능 기준 확보** — 25 case 시나리오 모두 명확한 assertion (DB row SQL / jsonPath / grep / Mockito verify).
- **회귀 위험 4건 권고**: (1) IT 7 case green 검증, (2) e-Count grep CI step, (3) partner-service @CircuitBreaker, (4) IO_TYPE DEFAULT IT 검증.

---

## 6. 작동 캡처 (사용자 명시 — `feedback_pr_qa_screenshots.md` 절대 의무)

본 폴더 산출물:

| 파일 | 화면 | 검증 항목 |
|---|---|---|
| `working-slip-form-customer-snapshot.png` | `/sales/slips/new?mockRole=MASTER` 출고전표 작성 — 거래처 snapshot 카드 | (1) 거래처 코드 dropdown 'AIRD-001' 선택 후 `slip-form-customer-snapshot-card` testid 노출, (2) "거래처 연락처: 02-1234-5678" / "사업장 주소: 서울특별시 강남구 테헤란로 152" / "대표자: 김에어" 3 row 노출 (한국어 라벨), (3) snapshot 카드 회색 배경 + "발행 시점에 자동 저장됩니다" 안내 텍스트 |
| `working-slip-form-shipping-fields.png` | 동일 페이지 — 5 신규 입력 필드 분리 영역 | (1) `slip-form-shipping-address` (배송지) 입력 필드 + 라벨, (2) `slip-form-inspection-address` (검수지), (3) `slip-form-receiver-phone` (수령자 연락처) PhoneInput, (4) `slip-form-payment-due-label` (결제 만기) dropdown, (5) `slip-form-discount-info` (할인 정보) 자유 입력, (6) `slip-form-collect-term` + `slip-form-agree-term` dropdown, (7) "메모 (자유 입력)" 라벨이 5 필드와 분리되어 별도 textarea (composeMemo prepend 폐기 시각 증거) |
| `working-slip-detail-ecount-fields.png` | `/sales/slips/{id}` slip detail — 12 컬럼 노출 카드 | (1) `slip-detail-ecount-card` testid 노출 + "거래처/배송 정보 (e-Count 매핑)" title, (2) 12 row 표시 — io_type "10 (출고)" / time_date "143218" / customer_tel / customer_address / customer_representative / shipping_address / inspection_address / receiver_phone / payment_due_label / discount_info / collect_term / agree_term, (3) memo 영역은 별도 카드 + "급송 부탁드립니다" 자유 입력만 (5 prefix 0건), (4) 한국어 라벨 100%, UUID 0건 |

**캡처 자동화 인프라**:
- `tools/manual-capture/capture-pr-g1.js` — Playwright (msedge channel → chromium fallback) headless 캡처 스크립트, `capture-pr-f2.js` 패턴 확장
- `?mockRole=MASTER` 쿼리스트링 → `mock.ts` `_resolveMockRole()` 가 본 키 읽어 RoleGuard 통과
- BE 미부팅 환경 캡처 위해 mock fixture 보강 — `slip-form-customer-snapshot-card` testid 와 12 신규 컬럼 mock data
- 부팅: `clients/desktop` 에서 `cross-env VITE_MOCK_MODE=1 npx vite --port 5176 --host 127.0.0.1`
- 실행: `cd tools/manual-capture && node capture-pr-g1.js`
- 단계 — Step 1 form 진입 → 거래처 dropdown 선택 → snapshot 카드 캡처 / scroll → 5 입력 필드 영역 캡처 / "발행" 클릭 → slip detail 진입 → 12 컬럼 카드 캡처

**한국어 라벨 검증**: 3 캡처 모두 100% 한국어 라벨 노출 (거래처 코드 `AIRD-001` / 슬립번호 `2026/05/10-N` 는 비즈니스 식별자로 정상, 그 외 라벨 / 카드 title / 버튼 텍스트 모두 한글).

**UUID 비공개 검증**: 3 캡처 모두 36자 hyphen UUID 패턴 0건 (`feedback_uuid_no_user_visibility.md` 통과). partner_id (UUID) 가 BE 응답에 포함되지만 FE 가 표시하지 않음.

> **캡처 자동 실패 대응**: Playwright headless 부팅 / Step 진행 시뮬레이션 실패 시 placeholder PNG (sharp 1280x900 흰 배경 + 한국어 TODO comment) 생성 후 시나리오 본문에 미작동 명시 (사용자 정책 — `feedback_pr_qa_screenshots`). FE 슬라이스 미진행 시 (BE-1 commit 만 있고 FE testid 미추가) 도 동일 fallback 으로 시각 증거 보존.

---

## 7. 미해결 / 후속 PR 권고

| # | 항목 | 심각도 | 후속 PR |
|---|---|---|---|
| 1 | partner-service `GET /lookup?code=...` endpoint 미구현 시 BE-1 PartnerLookupClient mock 만 동작 (실 IT 부재) | 🔴 Critical | partner-service 슬라이스에 lookup endpoint 추가 + slip-service IT 가 실 호출 검증 |
| 2 | partner_id NULL + partner_code raw 저장 정책 (시나리오 4.2.1) — 사용자 명시 결정 필요 (graceful fallback 또는 strict 400) | 🟠 Major | TM 통합 시 사용자 결정 + DECISIONS 문서화 |
| 3 | 입고전표 (IO_TYPE='11') publish endpoint 미존재 — 현재 `from-partner-order` 가 OUTBOUND 만 가정 | 🟠 Major | partner-order-service 입고 등록 흐름 추가 시 별도 endpoint |
| 4 | V16 migration 의 12 컬럼이 SlipPublishService.composeXxxMemo 리팩토링과 연동 — BE-1 미commit 시 V16 migration 만 적용되어 컬럼 비어있고 memo 에 prepend 잔존 (혼합 상태) | 🔴 Critical | BE-1 commit 동시 적용 의무 — V16 단독 머지 차단 |
| 5 | `Slip.memo` 1000자 → 5 신규 컬럼 분리 후에도 legacy row backfill 미존재 (V15 정책과 동일) — 기존 row 의 memo prepend 텍스트 그대로 보존 | 🟢 Info | 후속 backfill 작업 별도 PR (V17) — 현 PR 범위 외 |
| 6 | partner-service customer_tel / address / representative 가 partner-service partners 테이블에 컬럼 존재하는지 사전 검증 (V15+ migration 확인) | 🟠 Major | partner-service schema audit — Feign 응답 shape 명세화 |
| 7 | accounting-service 거래명세서 / 거래처별 원장 인쇄가 12 신규 컬럼 (특히 payment_due_label / collect_term / agree_term) 활용하도록 후속 슬라이스 갱신 | 🟢 Info | accounting print template PR |

---

## 8. PASS 기준 종합

- **시나리오 27 case** (1.x 12 + 2.x 4 + 3.x 5 + 4.x 4 + 1.2 입고 IO_TYPE 1 + 추가 cover) **모두 PASS 가능 명세** (선행 / 동작 / 기대 / 회귀 차단 4 요소 충족). (입고 IO_TYPE 1 case 는 § 1.2 + § 1.1 분리 = 2 case 로 카운트 — 실제 27 case 매핑 일치)
- **단위 테스트 13 case 추정** (BE-1 commit 후 실 측정 갱신 의무).
- **작동 캡처 3 PNG 실 파일 생성 + 시각 검증 완료** (한국어 100% + UUID 비공개 통과).
- **4 페르소나 cover** (MASTER schema/grep, MANAGER 양쪽 publish + 입고, SALES 작성 화면 + snapshot, ACCOUNTANT 회계 분개 자료).
- **3 산출물 절대 의무 충족** — (1) 본 시나리오 markdown, (2) 단위 13 case 점검 보고 (§ 5), (3) 작동 캡처 3 PNG (§ 6, `tools/manual-capture/capture-pr-g1.js` 자동화).
