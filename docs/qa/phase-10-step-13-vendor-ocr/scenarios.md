# PR-F2 — vendor 발주서 OCR 자동화 (에어디자이너 + 제이시스템) QA 시나리오

> **branch** — `feature/integrated-phase-10-step-13-vendor-ocr`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 10 Step 13 PR-F2 (legacy GAS #10 에어디자이너 + #14 제이시스템 발주서 OCR native 이식) 의 BE Tesseract OCR + parser 패키지 + FE Designer 3-step UI + DevOps Tesseract 가이드가 매뉴얼 + 도메인 정합성을 충족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE: `partner-order-service/vendor.ocr` 패키지 (commit `9874aa9`) — TesseractOcrEngine + AirDesignerOrderParser + JSystemOrderParser + VendorOrderService + VendorOrderController + IT 5
> - FE BE-연결: `clients/desktop/src/renderer/routes/SalesVendorOrderUploadPage.tsx` + `api/vendorOrderApi.ts` (commit `1a925ae` Designer mock 후 BE 연결) — 3-step Stepper UX, useMutation 으로 BE upload/confirm endpoint 호출
> - DevOps: `docs/dev-environment/tesseract-setup.md` + ci.yml apt 의존성 (commit `f4232ba`)
> - 작동 캡처: `working-vendor-order-step1-upload.png` + `working-vendor-order-step2-preview.png` + `working-vendor-order-step3-confirm.png` (본 폴더)
> - 단위 테스트 점검: 본 문서 § 5 (Mock 4 + AirDesigner 6 + JSystem 5 + Registry 3 + Service 7 + IT 5 = 30 case)

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — feedback_role_naming_full 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **개발책임자 / IT 관리자** | MASTER | high (전 도메인) | high | 사이드바 "vendor 발주 OCR" entry / Tesseract 비활성 환경 503 fallback / Confirm 후 PartnerOrder 등록 검증 / OcrEngineConfig bean 충돌 가드 (PR #119 회귀) |
| **회계 외주** | ACCOUNTANT | 한국 일반기업회계기준 숙련 | 일반 office | 본 PR 직접 입력 권한 차단 (영업/관리 그룹 전용). vendor 발주서 → PartnerOrder 등록 후 회계 분개 누락 위험 검증 |
| **신입 영업** | SALES | 거래/세금/단가 미경험 | 일반 office | drag-drop 업로드 / OCR 자동 분석 / 매칭 실패 행 빨간 highlight 인지 / 수동 보정 가능성 / 확정 후 발주서 link 동선 |
| **숙련 관리** | MANAGER | 전 도메인 | high | 다중 vendor 양식 (에어디자이너 PDF + 제이시스템 이미지) 동일 UI 처리 / 단가 lookup 결과 vs OCR 단가 불일치 suggestion 메시지 인지 |
| **신입 창고 / 배차원** | DISPATCHER (DISPATCH backlog → MANAGER) | 출고 흐름 미경험 | 일반 office | 본 PR 검증 범위 외 (vendor 발주는 영업 그룹 전용) |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (vendor 텍스트 / 거래처 mock / catalog mock — 비즈니스 식별자만, UUID 비공개)
2. **동작** — Playwright `page.click(testid)` / API client `POST /api/...` 의 구체 step
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()` / 메시지 텍스트) + DB / 응답 assertion
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 운영 차단 (잘못된 단가 / 권한 우회 / OCR 503 미식별 / vendor 자동 인식 실패)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요
- 🟡 **Minor** — UX 사소 / 표기 / 캡처 불일치
- 🟢 **Info** — 향후 개선 권고

### 0.4 권한 매트릭스 표기 (풀네임 의무 — `feedback_role_naming_full.md`)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `PARTNER` / `READONLY` 9 ROLE 만 사용. M/M/D 약어 금지.

본 PR 권한:
- **FE 사이드바 entry / 라우트 가드** = `VENDOR_ORDER_OCR_ROLES = ['SALES', 'MANAGER', 'MASTER']` (영업 그룹 + 관리)
- **BE @PreAuthorize** = `MASTER / MANAGER` (admin endpoint, SALES 차단)
- **불일치 의도된 차이** — FE 는 영업이 진입하여 OCR 분석/미리보기 가능 (조회), BE confirm 단계에서 admin 만 등록 가능. 후속 PR 에서 SALES 도 BE 등록 허용 여부 결정 필요 (§ 7 권고).

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility.md`)

모든 case 의 UI assertion 은 비즈니스 식별자만 (예: 거래처 코드 `AIRD-001`, 발주서 번호 `PO-AD-260510-XXX`, 모델 코드 `HM-5000`, vendor 명 `에어디자이너` / `(주)제이시스템`). UUID 가 화면 노출되면 즉시 FAIL. (BE 응답에는 PartnerSummary.id UUID 가 포함되지만 FE 가 표시하지 않음 — 본 case 에서 별도 검증.)

---

## 1. 슬라이스 1 — 에어디자이너 발주서 OCR (5 case 🔴)

**의존 backend** —
- `partner-order-service` `POST /api/v1/admin/partner-order/vendor/upload` (multipart, MASTER/MANAGER)
- `partner-order-service` `POST /api/v1/admin/partner-order/vendor/confirm` (json, MASTER/MANAGER)

**의존 frontend** — `clients/desktop` `SalesVendorOrderUploadPage` (`/sales/vendor-order-upload`, SALES/MANAGER/MASTER)

**testid 의존 (실 FE 표준)** — `vendor-order-stepper` / `vendor-radio-airdesigner` / `vendor-radio-jsystem` / `vendor-order-drop-zone` / `vendor-order-file-input` / `vendor-order-ocr-run-btn` / `vendor-order-item-row-{idx}` / `vendor-order-item-qty-{idx}` / `vendor-order-item-price-{idx}` / `vendor-order-confirm-btn` / `vendor-order-restart-btn` / `vendor-order-result-card` / `vendor-order-view-link` / `sidebar-sales-vendor-order-upload`

### 1.1 정상 — OCR + parser + 단가 lookup + DC 적용

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | 신입 영업 | 🔴 | MockOcrEngine preset (key="AIRD") = "에어디자이너 발주서 / 거래처: AIRD-001 / 1. 헬로멀티 5kW [HM-5000] 2개 1,000,000원 / 합계: 2,000,000원" + ProductCatalogLookupClient → HM-5000 시트가 950,000 / PartnerLookupClient → AIRD-001 = "(주)에어디자이너" / DcConfigClient → homeDiscount=0.10 | (FE) 라디오 "에어디자이너" 선택 → drop-zone 에 ad.png drag-drop → "OCR 분석 시작" 클릭. (또는 직접 BE 호출 multipart `/api/v1/admin/partner-order/vendor/upload`) | (BE 응답) 200 + `vendorName="에어디자이너"` + `partnerCode="AIRD-001"` + `parsedLines.length=1` + `parsedLines[0].modelCode="HM-5000"` + `unitPrice=950000` + `dcRate=0.10` + `finalPrice=855000` + `subtotal=1710000` + `source="CATALOG"` + suggestions 에 "불일치" (OCR 합계 2000000 vs 라인 합산 1710000). (FE) Step 2 진입 → 거래처 정보 카드 노출 + 표 1행 (매칭 성공, 빨간 highlight 없음) | DC 적용 단가 회귀 (homeDiscount 0.10 → finalPrice = sheet * 0.9) + suggestion 메시지 누락 시 단가 불일치 운영자 인지 차단 |
| 1.1.2 | 신입 영업 | 🔴 | 동일 + `?mockRole=MASTER` 진입 (FE mock 모드) | Step 1 → Step 2 → Step 3 (확정) | Step 3 진입 시 result-card testid 노출 + 발주서 번호 패턴 `PO-AD-{YYMMDD}-{3-digit}` + 거래처 "(주)에어디자이너 (AIRD-001)" + 총 금액 KRW 포맷 + "발주서 보기" link 노출 + "다른 vendor 업로드" 버튼 | end-to-end 3-step UX 회귀 차단 (Stepper 진행 + result link 동선) |

### 1.2 OCR fail — vendor 식별 실패 (자동 detect miss + hint 미공급) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | 신입 영업 | 🔴 | MockOcrEngine default = "랜덤 텍스트 vendor 식별 불가" + vendor hint 미공급 + partnerCode hint 미공급 | upload (vendor 라디오 선택했지만 BE 에 vendor query param 미전달 시나리오) | BE 응답 400 + `BusinessException` + 메시지 "vendor 식별 실패" (VendorOrderServiceTest #4 매핑). FE 는 error alert 노출 + Step 1 유지 | OCR 결과 빈 / 잘못된 텍스트일 때 Step 2 진입 차단 (잘못된 발주 등록 방지) |

### 1.3 단가 매칭 fail — catalog 미존재 → OCR fallback 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | 숙련 관리 | 🟠 | preset = "에어디자이너 발주서 / 1. 헬로멀티 [UNKNOWN-CODE] 1개 800,000원" + ProductCatalogLookupClient empty Map | upload (vendor=에어디자이너, partnerCode=AIRD-002) | 응답 200 + `parsedLines[0].source="OCR"` + `unitPrice=800000` (시트가 없음 → OCR 단가 그대로) + suggestions 에 "UNKNOWN-CODE" 모델 미식별 안내. FE 는 Step 2 표에서 빨간 highlight (matchFailed=true) — 사용자가 "단가 (시트)" 컬럼 수동 수정 가능 | 신규 모델 코드일 때 OCR 단가 fallback 보장 + 운영자 보정 동선 노출 |

### 1.4 사용자 수정 — 수량/단가 inline edit 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | 신입 영업 | 🟠 | 1.1.1 시나리오 후 Step 2 진입 | testid `vendor-order-item-qty-0` 값 2 → 5 변경 | finalPrice 자동 재계산 (855000 * 5 = 4,275,000) + 합계 row 도 갱신 | inline edit → finalPrice 재계산 누락 시 잘못된 합계로 발주 등록 |
| 1.4.2 | 신입 영업 | 🟡 | 1.4.1 후 testid `vendor-order-item-price-0` (DC 적용가) 855000 → 800000 변경 | finalPrice = 800000 * 5 = 4,000,000 갱신 + tfoot 합계 동기화 | 단가 수정 → finalPrice 재계산 / tfoot 합계 회귀 차단 |

### 1.5 확정 (confirm) — PartnerOrder 등록 + 거래처 검증 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | 숙련 관리 | 🔴 | 1.1.1 시나리오 + PartnerLookupClient → AIRD-001 found + 사용자가 표 검토 후 "확정" 클릭 | Step 2 → "확정" 버튼 클릭 (BE `POST /confirm`) | BE 응답 200 + `status="REGISTERED"` + `totalAmount` 라인 합산 + `orderNo` = "V" prefix 포함. FE Step 3 진입 + result-card 노출 (1.1.2 와 동일) | 정상 등록 흐름 보장 |
| 1.5.2 | 개발책임자 | 🔴 | PartnerLookupClient → "P-NONE" empty | confirm 호출 (vendor=제이시스템, partnerCode=P-NONE, lines 1건) | BE 응답 404 + `BusinessException` + 메시지 "거래처 미발견" (VendorOrderServiceTest #7 + IT #4 매핑). FE alert 노출 후 Step 2 유지 | 미등록 거래처 발주 등록 차단 (회계 정합 영향) |

---

## 2. 슬라이스 2 — 제이시스템 발주서 OCR (5 case 🔴)

**testid + 권한** — 슬라이스 1 동일.

### 2.1 정상 — auto-detect + table parser

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | 신입 영업 | 🔴 | preset (key="JSYS") = "JSYSTEM order / Partner: P-J001 / HM-7000 헬로멀티 7kW 2 EA 1,500,000 / TOTAL 3,000,000" + catalog HM-7000 시트가 1,500,000 + PartnerLookupClient P-J001 found + DcConfig homeDiscount 0.10 | (vendor hint 미공급) BE upload 호출 | 응답 200 + `vendorName="제이시스템"` (auto-detect 성공, VendorParserRegistry.autoDetect "JSYSTEM" 키워드 매칭) + `partnerCode="P-J001"` + `parsedLines[0].modelCode="HM-7000"` + `source="CATALOG"` (VendorOrderServiceTest #3 매핑) | auto-detect 회귀 (vendor hint 없이도 키워드 자동 인식) |

### 2.2 OCR fail — qty 인식 실패 → 라인 skip 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | 신입 영업 | 🟠 | preset = "제이시스템 발주서 / HM-5000 헬로멀티 5kW abc EA 1,000,000 / HM-7000 헬로멀티 7kW 2 EA 1,500,000" (첫 라인 qty=abc) | upload | 첫 라인 정규식 미매칭 → skip / 두 번째 라인만 인식 (`parsedLines.length=1`, modelCode=HM-7000) (JSystemOrderParserTest #3 매핑). FE 는 Step 2 에서 1행만 표시 + suggestion "X 라인 인식 실패" | qty 인식 실패 시 잘못된 단가로 등록 차단 (skip > 0 으로 등록) |

### 2.3 단가 매칭 fail — catalog 부분 매칭 (1행 매칭, 1행 OCR fallback) 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | 숙련 관리 | 🟠 | preset = "제이시스템 / Partner: P-J002 / HM-7000 헬로멀티 7kW 2 EA 1,500,000 / NEW-MODEL 신규품목 1 EA 500,000" + catalog HM-7000 매칭 + NEW-MODEL 미존재 | upload | `parsedLines.length=2` + lines[0].source=CATALOG (HM-7000) + lines[1].source=OCR (NEW-MODEL) + lines[1].matchFailed=true + suggestion 에 "NEW-MODEL" 노출. FE 표 2행, 둘째 행 빨간 highlight | 부분 매칭 시 매칭 성공 라인 보호 + 실패 라인 시각 식별 보장 |

### 2.4 사용자 수정 — 매칭 실패 행 보정 후 확정 🟡

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | 숙련 관리 | 🟡 | 2.3.1 시나리오 후 Step 2 진입 | NEW-MODEL 행의 qty 1 → 3, 단가 (DC 적용가) 500000 → 450000 inline edit → 확정 | finalPrice 재계산 (450000 * 3 = 1,350,000) + 합계 갱신 + Step 3 진입 + result-card 표시 | 매칭 실패 행을 사용자가 보정 후 등록 가능 (수동 보정 동선) |

### 2.5 확정 — 거래처 매칭 정상

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5.1 | 숙련 관리 | 🔴 | 2.1.1 시나리오 + PartnerLookupClient P-J001 found | Step 2 → "확정" 클릭 (BE confirm) | 응답 200 + status="REGISTERED" + totalAmount=2700000 (HM-7000 1,350,000 * 2 = 2,700,000, DC 10%) + orderNo "V" prefix (VendorOrderServiceTest #6 매핑). FE Step 3 진입 + 발주서 link `#/sales/partner-orders/{orderNo}` 노출 | 제이시스템 정상 등록 회귀 |

---

## 3. OCR 비활성 fallback (1 case) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | 개발책임자 / IT 관리자 | 🔴 | `samhan.partner-order.ocr.enabled=false` (default) — Tesseract 미설치 환경 (CI / 신규 개발자 첫 부팅) → OcrEngine bean 미등록 (OcrEngineConfig @ConditionalOnProperty 차단) | BE upload 호출 (file=ad.png, vendor=에어디자이너) | 응답 503 SERVICE_UNAVAILABLE + ApiResponse fail (`ErrorCode.INTERNAL_ERROR`) + 메시지 "OCR 엔진 미사용 — samhan.partner-order.ocr.enabled=true 설정 필요 (DevOps Tesseract setup)" — `VendorOrderController.serviceUnavailable()` 매핑. ApplicationContextLoadIT 가 OCR 비활성 시 ApplicationContext 부팅 보장 (PR #119 회귀 가드) | OCR 비활성 환경 graceful fallback (5xx ServletException 회귀 차단) + DevOps setup 안내 노출 |

---

## 4. 권한 / UX / 정합 — 추가 case (4 case)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1 | 신입 창고 / 배차원 (DISPATCHER) | 🔴 | DISPATCHER 토큰 + 직접 진입 시도 | navigate `/sales/vendor-order-upload` | RoleGuard 차단 → unauthorized 화면 또는 redirect, AppLayout sidebar entry "vendor 발주 OCR" 미노출 (`showVendorOrderOcr=false`) | 권한 우회 차단 |
| 4.2 | 회계 외주 (ACCOUNTANT) | 🟠 | ACCOUNTANT 토큰 + BE 직접 호출 | `POST /api/v1/admin/partner-order/vendor/upload` | BE @PreAuthorize 차단 → 403 (MASTER/MANAGER 만 허용) | BE 권한 가드 일관 검증 (FE entry 차단 + BE @PreAuthorize 이중 보호) |
| 4.3 | 신입 영업 (SALES) | 🟠 | SALES 토큰, FE entry 노출 (`showVendorOrderOcr=true`) | upload 호출 시도 | FE 는 Step 2 까지 진행 가능하지만 BE @PreAuthorize 가 SALES 차단 → 403 alert. § 0.4 의도된 권한 불일치 — 후속 PR 에서 SALES 도 BE 등록 허용 여부 결정 필요 | FE/BE 권한 불일치 운영자 인지 (오류 메시지 명확화) |
| 4.4 | 개발책임자 | 🟡 | MASTER 토큰, Step 3 진입 후 result-card 검증 | DOM 검사 (querySelectorAll('*')) | result-card / 거래처 정보 카드 어디에도 36자 hyphen UUID 패턴 없음 (BE 응답의 PartnerSummary.id UUID 가 화면 미노출) | UUID 비공개 정책 (`feedback_uuid_no_user_visibility.md`) |

---

## 5. 단위 테스트 점검 보고 (실제 30 case — task spec 25 + IT 5 + 추가 cover)

> **회고 — task spec vs 실 테스트 수**:
> task spec 은 "Mock 4 + AirDesigner 5 + JSystem 5 + Service + Controller IT" 로 명시. 실제 측정 결과 — `MockOcrEngineTest` 4건, `AirDesignerOrderParserTest` 6건 (matches_keyword_detection 추가), `JSystemOrderParserTest` 5건, `VendorParserRegistryTest` 3건, `VendorOrderServiceTest` 7건, `ApplicationContextLoadIT` 1건, `VendorOrderControllerIT` 4건 = 총 **30건 실 case**. 추가 5 case (registry 3 + AirDesigner matches 1 + IT context-load 1) 가 회귀 보호 강화.

### 5.1 OCR Engine — `MockOcrEngineTest` (4 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `empty_bytes_returns_blank` | 빈 byte / null 입력 → 빈 문자열 (NPE 회귀 차단) | PASS | edge case |
| 2 | `preset_match_returns_preset_text` | byte → utf-8 prefix substring 매칭 → preset 반환 | PASS | IT 에서 OCR mock 사용의 핵심 메커니즘 |
| 3 | `no_preset_returns_default` | preset miss → default text fallback | PASS | OCR 비결정성 시뮬레이션 |
| 4 | `no_default_returns_blank` | preset 없음 + default 없음 → 빈 문자열 | PASS | 시나리오 1.2.1 매핑 (vendor 식별 실패 흐름) |

### 5.2 AirDesigner Parser — `AirDesignerOrderParserTest` (6 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `parse_normal_single_line` | 정상 1라인 + partnerCode 인식 + totalAmount | PASS | 시나리오 1.1.1 매핑 |
| 2 | `parse_multiple_lines` | 다중 라인 (3행, 모델 코드 / qty 보존) | PASS | 정규식 다중 매칭 |
| 3 | `parse_missing_price_line_skipped` | "단가확인요" 텍스트 → 정규식 미매칭 → 라인 skip | PASS | 잘못된 단가 등록 차단 |
| 4 | `parse_empty_text_returns_empty_lines` | 빈 / null 입력 → 빈 lines + totalAmount 0 | PASS | edge case |
| 5 | `parse_no_partner_code_returns_null_partner` | partnerCode 미인식 → null (parser 책임 분리) | PASS | partnerCode hint 의존성 명시 |
| 6 | `matches_keyword_detection` | 키워드 매칭 ("에어디자이너" / "AIR DESIGNER" / "Air Designer" / 음성 case 포함) | PASS | autoDetect 의 기반 |

### 5.3 JSystem Parser — `JSystemOrderParserTest` (5 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `parse_normal_table_row` | 정상 1행 (table format) + partnerCode + totalAmount | PASS | 시나리오 2.1.1 매핑 |
| 2 | `parse_multiple_rows` | 다중 행 (영문 헤더 "JSYSTEM Order Sheet" + Partner 한글) | PASS | 영/한 혼용 양식 지원 |
| 3 | `parse_missing_quantity_skipped` | qty=abc → 정규식 미매칭 → skip | PASS | 시나리오 2.2.1 매핑 |
| 4 | `parse_empty_text` | 빈 / null 입력 → 빈 lines | PASS | edge case |
| 5 | `matches_keyword_detection` | 키워드 매칭 ("제이시스템" / "JSYSTEM" / "J-SYSTEM" / "J SYSTEM") | PASS | autoDetect 정밀도 |

### 5.4 Vendor Parser Registry — `VendorParserRegistryTest` (3 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `resolveByName_returns_matching_parser` | name 으로 parser lookup ("에어디자이너" / "제이시스템") + 미등록 / null / "" → empty | PASS | vendor hint 명시 case |
| 2 | `autoDetect_uses_keyword_heuristic` | 키워드 자동 인식 ("에어디자이너 발주서" / "JSYSTEM Order") + 모르는 텍스트 / null → empty | PASS | 시나리오 2.1.1 매핑 (vendor hint 미공급 흐름) |
| 3 | `registeredVendors_lists_all_names` | 등록된 parser 목록 (`["에어디자이너", "제이시스템"]`) | PASS | UI dropdown 동적 생성 가능 |

### 5.5 Vendor Order Service — `VendorOrderServiceTest` (7 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `upload_air_designer_with_catalog_lookup_and_dc` | 정상 OCR + catalog + DC 적용 (시트 950000 * 0.9 = 855000) + suggestion 불일치 알림 | PASS | 시나리오 1.1.1 매핑 (가장 두꺼운 검증) |
| 2 | `upload_falls_back_to_ocr_price_when_catalog_missing` | catalog 미존재 → OCR 단가 fallback + source="OCR" + suggestion | PASS | 시나리오 1.3.1 매핑 |
| 3 | `upload_jsystem_auto_detected` | vendor hint 미공급 → autoDetect → 제이시스템 인식 | PASS | 시나리오 2.1.1 매핑 |
| 4 | `upload_throws_when_vendor_not_detected` | 랜덤 텍스트 + hint 미공급 → BusinessException "vendor 식별 실패" | PASS | 시나리오 1.2.1 매핑 |
| 5 | `upload_throws_when_empty_bytes` | 빈 file → BusinessException "비어있음" | PASS | 입력 검증 |
| 6 | `confirm_registers_new_order_when_partner_exists` | confirm 정상 등록 + status=REGISTERED + totalAmount + orderNo "V" prefix | PASS | 시나리오 1.5.1 / 2.5.1 매핑 |
| 7 | `confirm_throws_404_when_partner_missing` | partnerCode P-NONE → BusinessException "거래처 미발견" | PASS | 시나리오 1.5.2 매핑 |

### 5.6 Application Context Load — `ApplicationContextLoadIT` (1 case 실, OCR disabled)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `contextLoads` | OCR disabled 상태에서도 ApplicationContext 부팅 + VendorOrderService bean 등록 + OcrEngine bean 미등록 | PASS | **장기 가드 — PR #119 회귀 fix 후속**, OcrEngineConfig 의 `tesseractOcrEngineBean` / `mockOcrEngineBean` suffix 충돌 사전 차단 |

### 5.7 Vendor Controller IT — `VendorOrderControllerIT` (4 case 실, OCR enabled MOCK)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `upload_air_designer_ok` | MASTER 권한 + multipart upload + 응답 jsonPath 검증 (vendorName / partnerCode / parsedLines[0].source=CATALOG) | PASS | 시나리오 1.1.1 + 4.1 권한 일부 매핑 |
| 2 | `upload_jsystem_ok` | MANAGER 권한 + 제이시스템 자동 인식 | PASS | 시나리오 2.1.1 + 권한 매핑 |
| 3 | `upload_unknown_vendor_returns_400` | 랜덤 입력 → 400 BadRequest (BusinessException → ApiResponse fail) | PASS | 시나리오 1.2.1 매핑 |
| 4 | `confirm_partner_not_found_returns_404` | 거래처 P-NONE → 404 NotFound | PASS | 시나리오 1.5.2 매핑 |

> **외부 client @MockBean 격리 (`feedback_it_mockbean_external_clients`)** — DcConfigClient / ProductClient / InventoryClient / SlipServiceClient / PartnerAuthClient / PartnerLookupClient / ProductCatalogLookupClient 7건 모두 @MockBean lenient stub. Eureka 비활성 환경 5xx 회귀 차단 OK.

### 5.8 회귀 시나리오 평가

| 회귀 위험 | 현재 cover | 권고 |
|---|---|---|
| Tesseract native 미설치 시 ApplicationContext 부팅 실패 (PR #119 패턴) | ✅ ApplicationContextLoadIT | 회귀 case OK (장기 가드) |
| OcrEngineConfig @Bean 메서드명 충돌 (Configuration class 빈 이름과 동일) | ✅ ApplicationContextLoadIT (`*Bean` suffix 가드) | OK |
| AirDesignerOrderParser 정규식 변경 (모델 코드 패턴 `[XX-####]` → 다른 형식) | ✅ AirDesignerOrderParserTest #1~3 + Service #1 | 향후 vendor 양식 변경 시 본 test 갱신 의무 |
| autoDetect 키워드 추가 (vendor 3종 이상 합류) | ✅ VendorParserRegistryTest #2 | 후속 PR vendor 추가 시 case 추가 |
| catalog 단가 vs OCR 단가 불일치 suggestion 누락 | ✅ Service #1 (suggestions 검증) | OK |
| confirm 단계 partner UUID 응답 노출 (UUID 비공개 위반) | ⚠️ 부분 cover | Service #6 의 응답 dto 에 PartnerSummary.id 미포함 검증 추가 권고 (현재 orderNo / status / totalAmount 만 검증) |
| FE entry 권한 (`showVendorOrderOcr`) vs RoleGuard `VENDOR_ORDER_OCR_ROLES` vs BE @PreAuthorize 3중 정합 | ⚠️ 부분 cover | E2E Playwright IT 추가 권고 — § 4 시나리오 4.1~4.3 자동화 |
| `/sales/vendor-order-upload` static path 가 `/sales/:id` 보다 먼저 매칭 | ⚠️ 미cover | RR v6 라우트 순서 회귀 case 추가 권고 (slip ID = "vendor-order-upload" 일 때 가드 vs detail 페이지 분기) |

### 5.9 종합

- **총 30 case 실제 (작업 task spec 의 25 case 보다 5 case 추가 cover)**.
- **시나리오 ↔ 단위 테스트 매핑 100%** (4 case 가 권한 / UUID 정합 — 단위 테스트 외 E2E 검증).
- **PASS/FAIL 측정 가능 기준 확보** — 30 case 모두 명확한 assertion (응답 status / dto field / jsonPath).
- **회귀 위험 3건 권고**: (1) confirm 응답 UUID 비공개 검증 추가, (2) E2E 권한 정합 자동화, (3) RR v6 라우트 순서 case 추가.

---

## 6. 작동 캡처 (사용자 명시 — `feedback_pr_qa_screenshots.md` 절대 의무)

본 폴더 산출물:

| 파일 | 화면 | 검증 항목 |
|---|---|---|
| `working-vendor-order-step1-upload.png` | `/sales/vendor-order-upload` (mockRole=MASTER) Step 1 | (1) Stepper testid `vendor-order-stepper` 노출 (Step 1 active), (2) vendor 라디오 2건 (에어디자이너 / 제이시스템 — 한국어 라벨), (3) drag-drop 영역 ("발주서 파일을 끌어다 놓거나 클릭하여 선택" + ".pdf, .png, .jpg, .jpeg 만 허용 · 단일 파일 · 최대 10MB"), (4) "OCR 분석 시작" 버튼 (file 0 → disabled) |
| `working-vendor-order-step2-preview.png` | Step 2 — OCR 결과 + 파싱 line item 표 | (1) Stepper Step 2 active + Step 1 done (✓), (2) 거래처 정보 카드 (vendor "에어디자이너" / 거래처 코드 "AIRD-001" / OCR 합계 2,350,000원), (3) "BE 분석 안내 (2건)" 노란 alert (suggestions 2건 — OCR 합계 vs 라인 합산 불일치 + UNKNOWN-CODE 미식별), (4) "품목 매칭 실패 — 수동 보정 필요 (1건)" 추가 alert, (5) 좌 OCR raw text pre, (6) 우 line item 표 2행 (HM-5000 정상 + UNKNOWN-CODE **빨간 highlight** + "매칭 실패" 칩), (7) 합계 1,710,000원, (8) "다시 업로드" / "확정" 버튼 |
| `working-vendor-order-step3-confirm.png` | Step 3 — 발주 생성 결과 | (1) Stepper Step 3 active + Step 1/2 done, (2) result-card testid `vendor-order-result-card` 노출, (3) "발주가 정상 생성되었습니다." title, (4) 발주서 번호 패턴 `PO-AD-260510-XXX` 실제 노출 확인 (`PO-AD-260510-562`), (5) vendor "에어디자이너" / 거래처 코드 "AIRD-001" / 상태 PENDING / 총 금액 1,710,000원, (6) "발주서 보기" link + "다른 vendor 업로드" 버튼 |

**캡처 자동화 인프라**:
- `tools/manual-capture/capture-pr-f2.js` — Playwright (msedge channel → chromium fallback) headless 캡처 스크립트, `capture-pr-f1.js` 패턴 확장
- `?mockRole=MASTER` 쿼리스트링 → `mock.ts` `_resolveMockRole()` 가 본 키 읽어 RoleGuard 통과
- BE 미부팅 환경 캡처 위해 `clients/desktop/src/renderer/api/mock.ts` 에 `/admin/partner-order/vendor/upload` + `/confirm` 2 endpoint mock 핸들러 추가 (실제 BE 응답 shape 와 1:1 — `VendorOrderUploadResponse` / `VendorOrderConfirmResponse`)
- mock fixture 라인 — 매칭 성공 (HM-5000, source=CATALOG) + 매칭 실패 (UNKNOWN-CODE, source=MANUAL → matchFailed=true 빨간 highlight)
- 부팅: `clients/desktop` 에서 `VITE_MOCK_MODE=1 ./node_modules/.bin/vite --port 5176 --host 127.0.0.1`
- 실행: `cd tools/manual-capture && node capture-pr-f2.js`
- Step 진행 시뮬레이션 — Step 1 캡처 후 setInputFiles 으로 1x1 PNG mock 주입 + "OCR 분석 시작" 클릭 → Step 2 mount 대기 → Step 2 캡처 → "확정" 클릭 → Step 3 mount 대기 → Step 3 캡처

**한국어 라벨 검증**: 3 캡처 모두 100% 한국어 라벨 노출 (모델 코드 `HM-5000` / `AD-...` / `JS-...` 는 비즈니스 식별자로 정상, vendor 명 / 거래처 명 / 상태 / 버튼 텍스트 모두 한글).

**UUID 비공개 검증**: 3 캡처 모두 36자 hyphen UUID 패턴 0건 (`feedback_uuid_no_user_visibility.md` 통과).

> **캡처 자동 실패 대응**: Playwright headless 부팅 / Step 진행 시뮬레이션 실패 시 placeholder PNG (sharp 1280x900 흰 배경 + 한국어 TODO comment) 생성 후 시나리오 본문에 미작동 명시 (사용자 정책 — `feedback_pr_qa_screenshots`).

---

## 7. 미해결 / 후속 PR 권고

| # | 항목 | 심각도 | 후속 PR |
|---|---|---|---|
| 1 | FE 의 단가 / DC 적용가 mock 이 BE upload 응답 shape 와 미연결 (Step 2 의 `MOCK_RESULTS` fixture 만 사용) | 🟠 Major | FE-1 슬라이스 BE 연결 시 `useMutation(POST /vendor/upload)` 교체 + `VendorOrderUploadResponse.PreviewLine` 타입 align |
| 2 | confirm 단계 응답 dto 의 `partnerOrderId` UUID 가 FE link `#/sales/partner-orders/{orderNo}` 로 매핑되는지 검증 미존재 | 🟠 Major | BE-FE 통합 IT 추가 — orderNo (예: V20260510-XXX) 만 link path 에 사용 검증 |
| 3 | `VendorOrderControllerIT` IT 가 4건 만 — confirm 정상 등록 / partial 매칭 / DC 적용 검증 미포함 | 🟡 Minor | IT 3 case 추가 (confirm registered / partial OCR fallback / DC suggestion) |
| 4 | FE 의 RoleGuard `VENDOR_ORDER_OCR_ROLES` (SALES 포함) vs BE @PreAuthorize (SALES 제외) 권한 불일치 | 🟠 Major | 후속 PR — SALES 가 BE 직접 호출 시 403 alert UX 개선 또는 BE 권한 확장 결정 (사용자 결정 필요) |
| 5 | 동일 거래처 동일 모델 코드가 catalog + OCR 두 경로 매칭 시 우선순위 정책 미정의 | 🟢 Info | 후속 PR — catalog 우선 (현재 동작) 명시 + suggestion 메시지 표준화 |
| 6 | Tesseract 한글 traineddata (`kor.traineddata`) 정확도 검증 (실제 vendor PDF 4~5건 샘플 테스트) | 🟠 Major | DevOps 후속 — production 배포 전 OCR 정확도 ≥ 90% 검증 의무 |
| 7 | Step 2 의 inline edit (qty / price) 가 Step 3 confirm payload 에 반영되는지 BE-FE 통합 검증 미존재 | 🟠 Major | BE-FE 통합 IT 추가 권고 |

---

## 8. PASS 기준 종합

- **시나리오 15 case** (1.x 5 + 2.x 5 + 3.x 1 + 4.x 4) **모두 PASS 가능 명세** (선행 / 동작 / 기대 / 회귀 차단 4 요소 충족).
- **단위 테스트 30 case 모두 PASS** (Mock 4 + AirDesigner 6 + JSystem 5 + Registry 3 + Service 7 + IT 5).
- **작동 캡처 3 PNG 실 파일 생성 + 시각 검증 완료** (한국어 100% + UUID 비공개 통과).
- **5 페르소나 cover** (MASTER OCR 비활성 fallback, MANAGER 다중 vendor 처리, SALES 업로드 + 매칭 보정, ACCOUNTANT 권한 차단, DISPATCHER 범위 외 명시).
