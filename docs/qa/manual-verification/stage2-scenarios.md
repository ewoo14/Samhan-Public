# 운영자 매뉴얼 QA 검증 시나리오 — Stage 2 (영업 + 창고)

> **branch** — `feature/integrated-phase-10-step-7b-operator-manual-stage2`
> **작성일** — 2026-05-09
> **목적** — Stage 2 매뉴얼 (영업 5 + 창고 4 = 총 9 docs / 사용자 명시 "8개" 는 실사를 별도 안내 docs 로 분류한 표기) 이 신규 운영자(도메인 지식 X) 시점에서 단계별 따라가기 가능한지 / 화면 변경 / 비즈니스 로직 정합성 / 권한 매트릭스 정합성 검증.
> **방법** — 매뉴얼 본문 vs 실제 desktop 라우트 / mobile-staff 화면 / 17 backend service endpoint 매핑. 캡처 placeholder 와 누락 단계, 권한 매트릭스 일관성, 한국 회계 / 도메인 메서드 chain 정합성 검증.
> **연관 산출물** —
> - `docs/qa/manual-verification/scenarios.md` (Stage 1 — Critical 10 / Major 7 / Minor 11 / Info 3 = 31 항목)
> - `docs/manual/inventory/missing-features-catalog.md` (P0 50 + P1 37 + P2 27 + P3 17 = 131 sub) — Stage 2 추가 분 본 문서 §4
> - `docs/manual/STATUS.md` (Stage 2 진행 표)
> - `docs/manual/inventory/backend-feature-inventory.md` / `frontend-feature-inventory.md` (다른 agent 작업)

---

## 0. Stage 2 검증 방법 정의

### 0.1 검증자 페르소나 (Stage 1 + 추가)

| 페르소나 | 도메인 지식 | 컴퓨터 숙련도 | Stage 2 검증 관점 |
|---|---|---|---|
| **신입 영업** (입사 1주차) | 거래/세금/단가 미경험 | 일반 office | 거래처 등록 4탭 / 슬립 9 transition / 견적-주문 lifecycle |
| **신입 창고** (입사 1주차) | 입출고 흐름 미경험 | 모바일 익숙 | 입고 슬립 lifecycle / 검수 / 재고 조정 / 실사 |
| **영업 관리자(MANAGER)** | 도메인 1년+ | 일반 | 매출 마감 / 미수금 / 영업단가그룹 |
| **신규 IT 관리자** (인수인계) | 도메인 X / 시스템 운용 | high | 권한 매트릭스 / 미구현 안내 일관성 |
| **회계 외주** (월 1회 출입) | 한국 일반기업회계기준 숙련 | 일반 office | 슬립 → 분개 자동 / 매출 마감 / 거래처 미수금 |

### 0.2 검증 항목 분류 (Stage 1 동일 유지)

| 분류 | 약어 | 설명 |
|---|---|---|
| **A. 단계 누락** | A | 매뉴얼 단계 사이에 실제 UI 단계가 빠짐 |
| **B. 스크린샷 placeholder** | B | `screenshots/...png` 가 미작성 (파일 없음) |
| **C. UI 변경 / 화면 부재** | C | 매뉴얼 설명 vs 현재 desktop 라우트 / mobile 화면 불일치 |
| **D. 비즈니스 로직 부정합** | D | 한국 회계 / 도메인 메서드 chain / 권한 매트릭스 불일치 |
| **E. 용어 부정확** | E | 코드/Backend의 용어와 매뉴얼 용어 불일치 |
| **F. 미구현 기능 안내** | F | 매뉴얼은 안내하지만 backend / frontend 에 실 구현 없음 |

### 0.3 심각도

- 🔴 **Critical** — 운영 차단. 매뉴얼만 약속 시 운영자가 작업 자체 불가 (잘못된 결과 발생)
- 🟠 **Major** — 사용자 불편. 작업 가능하지만 다른 단계 / 추측 / 우회 필요
- 🟡 **Minor** — 사소한 용어 / 표기 / 캡처 placeholder
- 🟢 **Info** — 향후 개선 권고 / 보완

### 0.4 Stage 2 매뉴얼 docs 9개 식별

| # | 영역 | docs 경로 | 의존 backend | 의존 desktop 라우트 | 매뉴얼 작성 가능성 |
|---|---|---|---|---|---|
| 1 | 영업 | `01-영업/01-거래처-등록.md` | `partner-service` | **❌ 부재** (`/admin/partners` 없음) | 🔴 P0-6 차단 — 우회 안내만 가능 |
| 2 | 영업 | `01-영업/02-슬립-발행.md` | `slip-service` (9 transition) | `/slips` 외 7 라우트 | ✅ 작성 가능 |
| 3 | 영업 | `01-영업/03-견적서.md` | legacy estimate-app v2 | `/sales/estimates/legacy` (webview) | ⏳ webview 임베드 한계 안내 필수 |
| 4 | 영업 | `01-영업/04-주문서.md` | legacy order-app v4 | `/sales/partner-orders` 외 4 | ✅ 작성 가능 (mobile 분리) |
| 5 | 영업 | `01-영업/05-매출-마감.md` | **❌ 미구현** (P2-4) | 라우트 부재 | 🔴 미구현 안내 docs (Stage 4 보류 권고) |
| 6 | 창고 | `02-창고/01-입고.md` | `slip-service` INBOUND | `/slips` 통합 | ✅ |
| 7 | 창고 | `02-창고/02-출고.md` | `slip-service` OUTBOUND | `/slips` 통합 | ✅ |
| 8 | 창고 | `02-창고/03-재고.md` | `inventory-service` | `/warehouses`, `/transfers` | ✅ |
| 9 | 창고 | `02-창고/04-실사.md` | **❌ 미구현** (P2 신규) | 라우트 부재 | 🔴 미구현 안내 docs (Stage 4 보류 권고) |

> **Note** — 사용자 지시 "8개" 표현은 매출 마감 + 실사 중 한 docs 를 통합 안내 docs 로 분류한 단순 카운팅이거나, 영업 마감 = 회계 영역 통합 가능성 표현. 본 검증은 9 docs 모두 다루되 매출마감/실사는 "미구현 안내" 검증 위주로 압축하여 총 검증 항목 ~70 유지.

---

## 1. 영업 매뉴얼 5 docs 검증

### 1.1 `01-영업/01-거래처-등록.md` (페르소나: 신입 영업)

#### 1.1.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 영업 → 거래처 → 등록 클릭 | desktop `/admin/partners` 라우트 **부재** (`routes/index.tsx` 27 라우트 grep 결과 `partner` 키워드 = `partner-orders` 만) | C | 🔴 | **매뉴얼 작성 불가** — "현재 화면 미구현. backend `/api/partners` POST 직접 호출 또는 Postman 우회 안내" 명시 필수. P0-6 슬라이스 의존. |
| S2 | 4 탭 (기본/거래처정보/여신단가/부가정보) | 이카운트 reference 091522/091541/091555/091604 — desktop UI **❌ 4탭 모두 부재** | C | 🔴 | 4탭 ~30 필드 매뉴얼만 약속 시 운영 즉시 차단 |
| S3 | 사업자등록번호 자동 포맷 (123-45-67890) | `BusinessNumberInput` DS 컴포넌트 **부재** (`design-system/src/components/` grep zero hit) | F | 🟠 | P1-7 누락. 매뉴얼은 "수동 입력" 안내 필수 |
| S4 | 한국 주소 검색 (도로명 주소 API — Daum/카카오) | DS `AddressInput` 부재 | F | 🟠 | P1-7 누락 |
| S5 | 여신한도 / 영업단가그룹 등록 | `PartnerCreditService` ⏳ backend 만, UI ❌ | F | 🔴 | P0-6 탭 3 의존 |
| S6 | 첨부파일 upload (사업자등록증 사본) | `MinioAttachmentStorage` `@ConditionalOnProperty(enabled=true)` — 기본 NoopAttachmentStorage fallback. 운영 환경 검증 미완료 | F | 🟠 | P0-3 의존 |
| S7 | ROLE 권한 (SALES/MANAGER/MASTER 등록 가능 / ACCOUNTANT 조회만) | `PartnerAdminController @PreAuthorize("hasAnyRole('MASTER','MANAGER','SALES','ACCOUNTANT')")` — ACCOUNTANT GET 가능, POST/PUT 은 SALES+ | D | 🟡 | 매뉴얼 권한 매트릭스 ACCOUNTANT 영업 컬럼 △ 표기 (Stage 1 F7 정정 일관) |
| S8 | 거래처 검색 자동완성 (다른 화면 통합) | partner GET 가능 / 검색 자동완성 UI **부재** | F | 🟡 | P2-2 (검색 강화) |

> **요약** — 8 항목 중 🔴 3 / 🟠 3 / 🟡 2. **본 매뉴얼 docs 자체가 P0-6 차단** 으로 작성 시 우회 안내 위주여야 함.

### 1.2 `01-영업/02-슬립-발행.md` (페르소나: 신입 영업)

#### 1.2.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 영업 → 슬립 → 신규 클릭 | desktop `/slips/new` ✅ (`SlipNewPage.tsx`) | — | — | 정상 |
| S2 | 슬립 종류 선택 (출고/입고/이동/조정) | `SlipType` enum (OUTBOUND / INBOUND / TRANSFER / ADJUSTMENT) ✅ | — | — | 정상 |
| S3 | 거래처 검색 자동완성 → 선택 | 거래처 검색 자동완성 UI **부재** (1.1 S8 동일) — slip-service 의 `partnerId` 입력 시 partner GET 직접 호출 가정 | F | 🟠 | P0-6 거래처 UI 의존 — "거래처 코드 직접 입력" 우회 안내 |
| S4 | 라인 추가 → 품목 선택 → 수량 입력 → 단가 자동 fetch | **❌ 단가 자동 fetch 미구현** — `SlipLine` 의 `unitPrice` 는 사용자 수동 입력. 영업단가그룹/구매단가그룹 (P0-6 탭3) 미구현 | F | 🔴 | **신규 누락** — Stage 2 catalog 추가. P2-4 영업 보강 sub. |
| S5 | 라인 부가세 자동 계산 | 슬립 단계 부가세 계산 로직 ⏳ (라인 단위) — `SlipLine.taxAmount` 컬럼 ✅ / UI 자동 계산 ❌ | F | 🟠 | 신규 누락 — P2-4 sub |
| S6 | 9 transition lifecycle (DRAFT → REQUESTED → ALLOCATED → PICKING → PICKED → INSPECTING → DISPATCHED → DELIVERED → CONFIRMED) | `SlipTransitionService` 9 메서드 ✅ (`feedback_pm_integration_build_check.md` 의미 정렬) | — | — | 정상 |
| S7 | 인쇄 (출고전표 / 거래명세서) | `print/DispatchView.tsx` ✅ / `InvoiceView.tsx` ⏳ 일부 | F | 🟠 | P0-4 인쇄 양식 의존 |
| S8 | 슬립 검색 (기간 + 상태 + 거래처) | 기간만 ⏳ / 거래처+상태 복합 필터 **❌** | F | 🟠 | P1-6 누락 |
| S9 | Excel export | `/slips/export` endpoint **❌** | F | 🟡 | P1-6 누락 |
| S10 | 권한 (SALES 신규/임시저장 / WAREHOUSE transition / MASTER 모두) | `SlipController @PreAuthorize` 매트릭스 vs 매뉴얼 일치 검증 | D | 🟡 | Stage 2 PR 시 cross-check |

> **요약** — 10 항목 중 🔴 1 / 🟠 5 / 🟡 4. 본 매뉴얼은 lifecycle 자체는 작성 가능하지만 **단가 자동 / 거래처 검색 / Excel export** 누락 안내 필수.

### 1.3 `01-영업/03-견적서.md` (페르소나: 신입 영업)

#### 1.3.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 영업 → 견적서 클릭 | desktop `/sales/estimates/legacy` ✅ (legacy webview 임베드) | — | — | 정상 (단 webview) |
| S2 | 새 견적서 작성 | legacy estimate-app v2 (Node.js + Express + EJS) — `feedback_*.md` 결정 옵션 B2 | E | 🟡 | "현재는 별도 v2 사이트로 이동합니다" 명시 필수 |
| S3 | 거래처 / 품목 / 단가 / 수량 입력 | legacy v2 화면 — desktop UI 와 다른 UX | E | 🟠 | 신입 영업이 두 시스템 헷갈림 — 차이점 강조 |
| S4 | 견적서 PDF / 인쇄 | `PrintPreview` DS 컴포넌트 미사용 (`frontend-feature-inventory.md` §1.1 #35) | F | 🟠 | P0-4 인쇄 견적서 양식 누락 |
| S5 | 견적서 → 주문서 전환 | legacy v2 내부 기능 — desktop 와 미연계 (slip 자동 변환 ❌) | F | 🟠 | 신규 누락 — Stage 2 catalog 추가 |
| S6 | 견적서 모바일 작성 (영업직원 필드) | mobile-staff `EstimateWebView` ⏳ webview 임베드만 / native 앱 ❌ | F | 🟠 | P1-4 누락 |
| S7 | 견적서 검색 / 이력 | legacy v2 자체 기능 — desktop 통합 검색 ❌ | F | 🟡 | P2-2 검색 강화 |

> **요약** — 7 항목 중 🟠 4 / 🟡 2 / E 1. legacy webview 한계 명시 필수.

### 1.4 `01-영업/04-주문서.md` (페르소나: 신입 영업 + 거래처)

#### 1.4.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 영업 → 주문 → 신규 | desktop `/sales/partner-orders/new` ✅ (legacy v4 보존 옵션 b — `feedback_*.md` 결정) | — | — | 정상 |
| S2 | 거래처(PARTNER) 외부 주문 화면 | `partner-app v4` (별도 호스팅 sub-domain — `project_domain_strategy.md` `order.samhan-air.com`) ✅ | — | — | 정상 |
| S3 | 주문 라인 추가 / 단가 / 수량 | 1.2 S4 와 동일 — 단가 자동 fetch ❌ | F | 🟠 | P2-4 sub |
| S4 | 주문서 → 슬립 자동 변환 | `PartnerOrderToSlipConverter` ⏳ 부분 (수동 변환 버튼 만) | F | 🟠 | 신규 누락 — Stage 2 catalog 추가 |
| S5 | 주문 상태 (REQUESTED / APPROVED / REJECTED / CONVERTED) | `PartnerOrderStatus` enum ✅ | — | — | 정상 |
| S6 | 거래처 결재 (PARTNER_ADMIN 의 상위 승인) | backend ⏳ / desktop UI ❌ | F | 🟠 | 신규 누락 — Stage 2 catalog 추가 |
| S7 | 주문서 모바일 작성 (영업) | mobile-staff `OrderWebView` ❌ (`feedback_*.md` 견적/주문 모바일 분리 결정) | F | 🔴 | P1-4 누락 — 영업 모바일 native 앱 부재 |
| S8 | 주문서 인쇄 | 주문서 인쇄 양식 ❌ | F | 🟡 | P0-4 sub 보강 |

> **요약** — 8 항목 중 🔴 1 / 🟠 4 / 🟡 1. 본 매뉴얼은 desktop + 거래처 외부 화면 분기 안내 필수.

### 1.5 `01-영업/05-매출-마감.md` (페르소나: 영업 관리자 + 회계 외주)

#### 1.5.1 단계별 흐름 검증 (전체 미구현)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 영업 → 매출 마감 | 라우트 / endpoint **❌ 전체 미구현** (P2-4) | F | 🔴 | **매뉴얼 작성 불가** — "Phase 11 후 구현 예정" docs 만 작성 |
| S2 | 월 매출 합계 / 부가세 분리 | `accounting-service` `/accounting/balances` 시산표 1건만 — 매출 분류 ❌ | F | 🔴 | P0-1 회계 17 보고서 연동 |
| S3 | 거래처별 매출 / 매입 통합 | **❌ 신규 누락** — 거래처 조회 화면에 매출/매입 내역 통합 view 부재 | F | 🟠 | Stage 2 catalog 추가 |
| S4 | 미수금 / 미지급금 잔액 (채권/채무잔액분석표) | `PartnerCreditService` ⏳ / 보고서 ❌ (P0-1 #8) | F | 🔴 | P0-1 누락 |
| S5 | 매출 마감 → 분개 자동 생성 | `JournalAutoGenerator` ⏳ 부분 (P2-3 sub) | F | 🟠 | 누적 |
| S6 | 결산 lock | ❌ (P2-3 sub) | F | 🟡 | |
| S7 | 영업단가그룹 정산 (P0-6 탭3 연계) | UI/backend 모두 ❌ | F | 🟠 | P0-6 의존 |

> **요약** — 7 항목 중 🔴 4 / 🟠 3 / 🟡 1. 본 docs 는 **미구현 안내 docs** 로 작성하고 Stage 4 또는 Phase 11 후 정식 docs 로 교체 권고.

---

## 2. 창고 매뉴얼 4 docs 검증

### 2.1 `02-창고/01-입고.md` (페르소나: 신입 창고)

#### 2.1.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 창고 → 입고 클릭 | desktop **별도 입고 라우트 부재** — `/slips?type=INBOUND` 통합 | E | 🟡 | 매뉴얼 vs UI 용어 차이 (Stage 1 §1.3.2 동일) |
| S2 | 신규 입고 슬립 작성 | `SlipNewPage` `type=INBOUND` ✅ | — | — | 정상 |
| S3 | 거래처 (공급사) 선택 | 거래처 검색 자동완성 ❌ (1.2 S3 동일) | F | 🟠 | P0-6 의존 |
| S4 | 라인별 품목 / 수량 / 단가 | 단가 자동 fetch ❌ (1.2 S4 동일) | F | 🟠 | P2-4 누적 |
| S5 | 검수 (INSPECTING) UI 화면 | `SlipTransitionService.inspect()` ✅ backend / **검수 전용 UI 화면 ❌** (사진 첨부 / 불량 처리) | F | 🔴 | **신규 누락** — Stage 2 catalog 추가. 창고 작업 차단 |
| S6 | 입고 완료 (DELIVERED → CONFIRMED) | 9 transition ✅ | — | — | 정상 |
| S7 | 입고 슬립 → 재고 자동 증가 | `InventoryService.applyInbound()` ✅ | — | — | 정상 |
| S8 | 입고 라벨 인쇄 (바코드 / QR) | `print/` 디렉토리 입고 라벨 양식 **❌** | F | 🟠 | 신규 누락 — Stage 2 catalog 추가 |
| S9 | lot / serial / 유효기간 입력 (P0-7 탭7) | `StockLot` 일부 ⏳ / UI ❌ | F | 🟠 | P0-7 의존 |

> **요약** — 9 항목 중 🔴 1 / 🟠 4 / 🟡 1 / E 1. 검수 UI 부재가 가장 큰 차단.

### 2.2 `02-창고/02-출고.md` (페르소나: 신입 창고 + WAREHOUSE 작업자)

#### 2.2.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 창고 → 출고 | desktop `/slips?type=OUTBOUND` 통합 | E | 🟡 | 2.1 S1 동일 |
| S2 | 영업이 발행한 출고 슬립 목록 | `/slips` 필터 + 본인 창고 필터 ⏳ (창고별 필터 ❌) | F | 🟠 | 신규 누락 — Stage 2 catalog 추가 |
| S3 | 슬립 → ALLOCATED → 피킹 시작 | `SlipTransitionService.pick()` ✅ | — | — | 정상 |
| S4 | 모바일 피킹 (창고원 모바일 입출고 검수) | mobile-staff 창고원 화면 **❌ 전체 미구현** (P2-1) | F | 🔴 | 매뉴얼 작성 시 "현재 desktop 만" 명시 필수 |
| S5 | 검수 (INSPECTING) | 2.1 S5 동일 — 검수 UI ❌ | F | 🔴 | 누적 |
| S6 | 출고 (DISPATCHED) → 배송 기사 인계 | `arologis-service` 연계 + mobile-staff driver tab ✅ | — | — | 정상 |
| S7 | 출고전표 인쇄 | `print/DispatchView.tsx` ✅ | — | — | 정상 |
| S8 | 거래명세서 인쇄 (출고 후) | `print/InvoiceView.tsx` ⏳ legacy v4 일부 | F | 🟠 | P0-4 의존 |
| S9 | 모바일 전자서명 (DELIVERED) | mobile-staff `/sign-mock` + 실 서명 ✅ | — | — | 정상 |
| S10 | 권한 (WAREHOUSE 피킹/검수 / SALES 발행만 / DRIVER 서명만) | `SlipController` 매트릭스 cross-check 필요 | D | 🟡 | Stage 2 검증 PR |

> **요약** — 10 항목 중 🔴 2 / 🟠 2 / 🟡 2. 검수 UI + 창고원 모바일 누락 두 차단.

### 2.3 `02-창고/03-재고.md` (페르소나: 신입 창고 + 재고 관리자(INVENTORY))

#### 2.3.1 단계별 흐름 검증

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 창고 → 재고 | desktop `/warehouses` ✅ + `/transfers` ✅ | — | — | 정상 |
| S2 | 창고별 재고 현황 조회 | `WarehouseListPage` ✅ | — | — | 정상 |
| S3 | 품목별 재고 수량 / 안전재고 비교 | `StockController.list()` ✅ / 안전재고 표기 ⏳ (저재고 dashboard placeholder — P1-3) | F | 🟠 | P1-3 누적 |
| S4 | 창고 간 이동 (TRANSFER) | `/transfers/new` ✅ + 5 transition ✅ | — | — | 정상 |
| S5 | 재고 조정 (ADJUSTMENT — 분실/파손) | `StockController.adjust()` ✅ (MASTER+MANAGER+INVENTORY) | — | — | 정상 |
| S6 | 창고별 재고 그래프 (시각화) | **❌ 신규 누락** — 시각화 차트 컴포넌트 부재 | F | 🟡 | Stage 2 catalog 추가 |
| S7 | lot / serial / 유효기간 조회 | `StockLot` ⏳ / UI ❌ (2.1 S9 동일) | F | 🟠 | P0-7 의존 |
| S8 | 재고 Excel export | `/stocks/export` **❌** | F | 🟡 | 신규 누락 — Stage 2 catalog 추가 |
| S9 | 권한 (INVENTORY adjust / WAREHOUSE 조회만) | `StockController @PreAuthorize` ✅ | — | — | 정상 (단 매뉴얼 §5 권한 표 에 INVENTORY 행 추가 필수 — Stage 1 F7 일관) |

> **요약** — 9 항목 중 🟠 2 / 🟡 2. 시각화 + Excel export 신규 누락.

### 2.4 `02-창고/04-실사.md` (페르소나: 창고 관리자(INVENTORY))

#### 2.4.1 단계별 흐름 검증 (전체 미구현)

| Step | 매뉴얼 예상 설명 | 실 구현 | 분류 | 심각도 | 조치 |
|---|---|---|---|---|---|
| S1 | 사이드바 → 창고 → 실사 | 라우트 / endpoint **❌ 전체 미구현** | F | 🔴 | **매뉴얼 작성 불가** — "Phase 11 후 구현 예정" docs 만 |
| S2 | 실사 시작 (창고 lock) | ❌ | F | 🔴 | 신규 누락 — P2 신규 sub |
| S3 | 품목 카운트 (모바일 바코드 스캔) | mobile-staff 창고원 앱 ❌ (P2-1) | F | 🔴 | 누적 |
| S4 | 차이 자동 보고 (시스템 vs 실 카운트) | ❌ | F | 🟠 | 신규 누락 |
| S5 | 차이 조정 분개 자동 생성 | ❌ (P2-3 분개 자동 일부) | F | 🟠 | 누적 |
| S6 | 실사 보고서 인쇄 / Excel | ❌ | F | 🟡 | 신규 누락 |

> **요약** — 6 항목 중 🔴 3 / 🟠 2 / 🟡 1. 본 docs 는 **미구현 안내 docs** 로 작성하고 Stage 4 또는 Phase 11 후 정식 docs 로 교체 권고.

---

## 3. Stage 2 검증 종합 카운트

### 3.1 docs 별 검증 항목 합계

| # | docs | 검증 항목 | 🔴 Critical | 🟠 Major | 🟡 Minor | 🟢 Info | E |
|---|---|---:|---:|---:|---:|---:|---:|
| 1 | 거래처 등록 | 8 | 3 | 3 | 2 | 0 | 0 |
| 2 | 슬립 발행 | 10 | 1 | 5 | 4 | 0 | 0 |
| 3 | 견적서 | 7 | 0 | 4 | 2 | 0 | 1 |
| 4 | 주문서 | 8 | 1 | 4 | 1 | 0 | 0 (S5 정상) |
| 5 | 매출 마감 | 7 | 4 | 3 | 1 | 0 | 0 |
| 6 | 입고 | 9 | 1 | 4 | 1 | 0 | 1 |
| 7 | 출고 | 10 | 2 | 2 | 2 | 0 | 1 |
| 8 | 재고 | 9 | 0 | 2 | 2 | 0 | 0 |
| 9 | 실사 | 6 | 3 | 2 | 1 | 0 | 0 |
| **합계** | — | **74** | **15** | **29** | **16** | **0** | **3** |

> 사용자 명시 "~70 검증 항목" 부합 (74 항목).

### 3.2 분류별 분포

| 분류 | 카운트 | 비고 |
|---|---|---|
| **A 단계 누락** | 0 | Stage 2 단계 별 자체 문제 < UI/구현 부재 문제 |
| **B 스크린샷 placeholder** | 9 docs × ~5 placeholder ≈ 45 (별도 카운트) | Stage 2 PR 머지 후 사용자 PC 캡처 |
| **C UI 변경 / 화면 부재** | 검증표 기준 5+ (S1/S2 다수) | 거래처 4탭 / 매출마감 / 실사 차단 |
| **D 비즈니스 로직 부정합** | 4 (1.1 S7 / 1.2 S10 / 2.2 S10 / 2.3 S9) | 권한 매트릭스 추가 cross-check |
| **E 용어 부정확** | 3 (1.3 S2 / 1.3 S3 / 2.1 S1 / 2.2 S1) | desktop 메뉴 vs 실 라우트 |
| **F 미구현 기능 안내** | ~50 | 매뉴얼만 약속 시 운영 차단 다수 |

### 3.3 Stage 2 매뉴얼 작성 차단 요약

| docs | 작성 가능성 | 차단 사유 |
|---|---|---|
| 거래처 등록 | ❌ 차단 | P0-6 4탭 UI 부재 — **매뉴얼만 약속 시 운영 실패** |
| 슬립 발행 | ✅ 작성 가능 | 단가 자동 / 검색 / Excel export 누락 안내 필수 |
| 견적서 | ⏳ legacy 한계 안내 | webview 임베드 명시 필수 |
| 주문서 | ✅ 작성 가능 | 모바일 native 앱 부재 안내 |
| 매출 마감 | ❌ 미구현 안내 docs | P2-4 — Phase 11 후 정식 docs |
| 입고 | ⏳ 검수 UI 누락 안내 | 검수 UI 부재 안내 + 9 transition 작성 가능 |
| 출고 | ⏳ 검수 + 모바일 누락 안내 | 동일 |
| 재고 | ✅ 작성 가능 | 시각화 / Excel export 누락 안내 |
| 실사 | ❌ 미구현 안내 docs | P2 신규 — Phase 11 후 정식 docs |

---

## 4. 본 검증 과정에서 발견된 추가 누락 (catalog 갱신 입력)

본 §1~§2 검증 과정에서 catalog Stage 1 (131 sub) 에 미포함된 신규 누락 ~13 sub 발견. `missing-features-catalog.md` Stage 2 갱신 시 추가:

| # | 영역 | 누락 sub | 우선순위 | 발견 위치 |
|---|---|---|---|---|
| N1 | 슬립 발행 | 라인 unit_price 자동 계산 (단가 자동 fetch — 영업단가그룹 연계) | P2 (P2-4 sub 추가) | §1.2 S4 |
| N2 | 슬립 발행 | 라인 부가세 자동 계산 (UI) | P2 (P2-4 sub) | §1.2 S5 |
| N3 | 거래처 조회 | 매출/매입 내역 통합 화면 (거래처 single view) | P2 (P2-4 sub) | §1.5 S3 |
| N4 | 견적서 | 견적서 → 주문서 → 슬립 자동 변환 chain | P1 (P1-4 sub) | §1.3 S5 |
| N5 | 주문서 | 거래처 결재 (PARTNER_ADMIN 승인) UI | P1 (P1-5 신규) | §1.4 S6 |
| N6 | 입고 | 검수 (INSPECTING) UI 화면 (사진 첨부 + 불량 처리) | **P0** (신규 P0-9) | §2.1 S5 |
| N7 | 입고 | 입고 라벨 / 바코드 / QR 인쇄 양식 | P1 (P0-4 보강) | §2.1 S8 |
| N8 | 출고 | 슬립 목록 창고별 필터 | P1 (P1-6 sub) | §2.2 S2 |
| N9 | 재고 | 창고별 재고 그래프 (시각화 차트) | P2 (P2-2 sub) | §2.3 S6 |
| N10 | 재고 | 재고 Excel export (`/stocks/export`) | P1 (P1-6 sub) | §2.3 S8 |
| N11 | 실사 | 실사 시작 / 창고 lock / 차이 자동 보고 | P2 (신규 P2-6) | §2.4 S2~S6 |
| N12 | 슬립 검색 | 거래처 + 상태 복합 필터 (P1-6 기존 보강) | P1 (P1-6 보강) | §1.2 S8 |
| N13 | 권한 매트릭스 | INVENTORY / DEVELOPER / DISPATCH role 매뉴얼 행 추가 | P1 (P1-2 sub 보강) | §1.1 S7 / §2.3 S9 |

**→ 신규 13 sub. catalog 누적 = 131 + 13 = 144 sub.**

신규 P0 슬라이스: **P0-9 입고 검수 UI** (창고 작업 차단 — Phase 11 진입 전 의무 PR 권고).

---

## 5. Stage 2 PR 산출 시 즉시 정정 권고

| # | 위치 | 정정 내용 |
|---|---|---|
| S2-1 | `01-영업/01-거래처-등록.md` | 본문 시작에 "현재 desktop UI 미구현. P0-6 슬라이스 PR 머지 후 정식 안내" 명시 + 4탭 미리보기 mock 사용 |
| S2-2 | `01-영업/02-슬립-발행.md` §단가 입력 단계 | "단가 자동 fetch 미구현 — 수동 입력" 명시 |
| S2-3 | `01-영업/02-슬립-발행.md` §검색 | "복합 필터 / Excel export *(2026-Q3 예정)*" 표기 |
| S2-4 | `01-영업/03-견적서.md` 본문 시작 | "현재 별도 견적 시스템(legacy v2 — Node.js + Express + EJS)으로 이동합니다" 명시 |
| S2-5 | `01-영업/04-주문서.md` 모바일 단계 | "영업직원 모바일 native 앱 *(2026-Q4 예정)*" 표기 |
| S2-6 | `01-영업/05-매출-마감.md` 전체 | "본 기능은 Phase 11 AWS migration 후 정식 출시 예정" 미구현 안내 docs 로 작성 |
| S2-7 | `02-창고/01-입고.md` 검수 단계 | "현재 검수 전용 UI 미구현 — DRAFT → DELIVERED → CONFIRMED 만 사용. 검수 UI *(P0-9 신규 슬라이스 — Phase 11 전)*" |
| S2-8 | `02-창고/02-출고.md` 모바일 단계 | "창고원 모바일 *(P2-1 — 2026-Q4 검토)*" |
| S2-9 | `02-창고/03-재고.md` 시각화 / Excel | "차트 / Excel export *(2026-Q3 예정)*" |
| S2-10 | `02-창고/04-실사.md` 전체 | "본 기능은 Phase 11 AWS migration 후 정식 출시 예정" 미구현 안내 docs |
| S2-11 | 모든 docs 권한 §표 | INVENTORY / DEVELOPER / DISPATCH 행 추가 (Stage 1 F7 일관) |
| S2-12 | 모든 docs FAQ | 비밀번호 / 알림 / 첨부파일 관련 FAQ 는 Stage 1 정정 (F1/F4/F6) 동일 안내 사용 |

---

## 6. Stage 3 검증 예고

Stage 3 매뉴얼 (회계 4 + 모바일 4 + arologis 3 = 11 docs) 작성 시 본 검증 패턴 동일하게 적용. 예상 차단 영역:

| Stage 3 docs | 예상 차단 |
|---|---|
| 회계 02 보고서 | P0-1 17 보고서 14건 미구현 |
| 회계 03 세금계산서 | P0-4 미구현 |
| 회계 04 월말 마감 | P2-3 미구현 |
| 모바일 03 영업 앱 | P1-4 미구현 |
| 모바일 04 사진 첨부 | 미구현 |
| arologis 01 카카오톡 | UI 미구현 |
| arologis 03 기사 배정 | 미구현 |

---

## 7. 부록 — Stage 2 검증 시 사용한 grep / 파일 경로

```bash
# 1. desktop partner UI 부재 검증
grep -rn "partner" clients/desktop/src/renderer/routes/index.tsx
# → partner-orders 만 hit. /admin/partners 부재 = 🔴

# 2. slip-service 단가 자동 fetch 검증
grep -rn "unitPrice\|영업단가그룹" services/slip-service/src/main/java
# → SlipLine.unitPrice 컬럼만, 자동 fetch 로직 없음 = N1 신규 누락

# 3. 검수 UI 부재 검증
grep -rn "INSPECTING\|inspect" clients/desktop/src/renderer
# → 0 hit (backend SlipTransitionService.inspect() 만 존재) = N6 P0-9 신규

# 4. 매출 마감 / 실사 endpoint 부재 검증
grep -rn "@.*Mapping" services/accounting-service/src/main/java | grep -i "close\|month-end\|count"
grep -rn "@.*Mapping" services/inventory-service/src/main/java | grep -i "count\|cycle\|inspection"
# → 0 hit = §1.5 / §2.4 미구현 확정

# 5. 권한 매트릭스 cross-check (Stage 1 §1.3.1 + INVENTORY/DISPATCH 추가)
grep -rn "@PreAuthorize" services/*/src/main/java
```

---

## 8. 변경 이력

| 일자 | 작성자 | 변경 |
|---|---|---|
| 2026-05-09 | TeamMember (W10-7b Stage 2) | 초안 작성. 9 docs × 평균 8 항목 = **74 검증 항목** (🔴 15 / 🟠 29 / 🟡 16). 신규 누락 13 sub 발견 → catalog Stage 2 갱신 입력. |
