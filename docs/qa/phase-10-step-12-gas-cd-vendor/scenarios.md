# PR-F1 — 알리고 주소록 sync + 운송사 실배차 비교 QA 시나리오

> **branch** — `feature/integrated-phase-10-step-12-gas-cd-vendor`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 10 Step 12 GAS CD-vendor 슬라이스 (legacy GAS 9번 알리고 자동 업로드 + 11번 운송사 실배차 비교) 의 backend endpoint + frontend mock UI 가 매뉴얼 + 도메인 정합성을 충족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE-1: `partner-service/PartnerAligoExportService` + `notification-service/AligoAddressBookSyncService` (commit `f3b313a`)
> - BE-2: `arologis-service/VendorExcelParser` + `DispatchReconcileService` (commit `bb30725`)
> - FE Designer mock: `clients/desktop/src/renderer/routes/admin/AligoAddressBookPage.tsx` + `ArologisDispatchReconcilePage.tsx` (commit `2a1f71f`)
> - 작동 캡처: `working-aligo-address-book.png` + `working-dispatch-reconcile.png` (본 폴더)
> - 단위 테스트 점검: 본 문서 § 4 (BE-1 13 + BE-2 15 = 28 case)

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — feedback_role_naming_full 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **개발책임자 / IT 관리자** | MASTER | high (전 도메인) | high | 알리고 주소록 sync 단일 클릭 진입 / 운송사 비교 admin 진입 / blocked 거래처 자동 제외 검증 |
| **회계 외주** | ACCOUNTANT | 한국 일반기업회계기준 숙련 | 일반 office | 운송사 비교 결과 CSV 다운로드 → 매출 마감 정합 검증 (FALSE_LEFT/FALSE_RIGHT 회계 누락 식별) |
| **신입 영업** | SALES | 거래/세금/단가 미경험 | 일반 office | 알리고 주소록 진입 권한 차단 (MASTER 전용) |
| **신입 창고 / 배차원** | DISPATCHER (DISPATCH backlog → MANAGER) | 출고 흐름 미경험 | 일반 office | 운송사 vendor 엑셀 drag-drop 업로드 / 다중 vendor 동시 비교 / 시각 차이 허용 범위 |
| **배송 기사** | DRIVER | 운전/운송 경력 | 모바일 only | 본 PR 검증 범위 외 (mobile UI 미포함) |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (거래처 / dispatch 시드, vendor 엑셀 mock, 비즈니스 식별자만 — UUID 비공개)
2. **동작** — Playwright `page.click(testid)` / API client `POST /api/...` 의 구체 step
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()` / 메시지 텍스트) + DB / 응답 assertion
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기 (스테이지 3 일관)

- 🔴 **Critical** — fail 시 운영 차단 (잘못된 회계 / 권한 우회 / 데이터 손실 / vendor 누락 미식별)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요
- 🟡 **Minor** — UX 사소 / 표기 / 캡처 불일치
- 🟢 **Info** — 향후 개선 권고

### 0.4 권한 매트릭스 표기 (풀네임 의무 — `feedback_role_naming_full.md`)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `PARTNER` / `READONLY` 9 ROLE 만 사용. M/M/D 약어 금지.

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility.md`)

모든 case 의 UI assertion 은 비즈니스 식별자만 (예: 거래처 코드 `P-2026-0001`, 슬립번호 `S20260510-0001`, vendor 명 `CJ대한통운`). UUID 가 화면 노출되면 즉시 FAIL.

---

## 1. 슬라이스 1 — 알리고 주소록 자동 동기화 (BE-1 + FE) — 5 case 🔴

**의존 backend** —
- `partner-service` `GET /api/v1/partners/admin/aligo/csv` (PartnerAligoExportService)
- `notification-service` `POST /api/v1/notify/aligo/address-book/sync` (AligoAddressBookSyncService)

**의존 frontend** — `clients/desktop` `AligoAddressBookPage` (`/admin/aligo-address-book`, AdminLayout MASTER 가드)

**testid 의존 (실 FE 표준)** — `admin-aligo-group-filter` / `admin-aligo-sync-btn` / `admin-aligo-preview-table` / `admin-aligo-row-{partnerCode}` / `admin-aligo-result-added` / `admin-aligo-result-updated` / `admin-aligo-result-skipped` / `admin-aligo-result-failed` / `admin-nav-aligo-address-book`

### 1.1 CSV export 정상 (활성 거래처 + group1 → 알리고 양식 4컬럼)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | 개발책임자 | 🔴 | seed: `P-2026-0001` (`(주)에어뱅크`, mobile=`010-1234-5678`, group1=`VIP거래처`, ACTIVE) + `P-2026-0002` (`주식회사 삼성이엔지`, mobile=`01098765432`, group1=`일반거래처`, ACTIVE), blocked 0 | `GET /api/v1/partners/admin/aligo/csv` (MASTER 토큰) | 응답 200 + Content-Type `text/csv;charset=utf-8` + 첫 3 byte UTF-8 BOM (`0xEF 0xBB 0xBF`) + 헤더 `그룹명,이름,휴대폰,비고\r\n` + row `VIP거래처,(주)에어뱅크,01012345678,[P-2026-0001]` + row `일반거래처,주식회사 삼성이엔지,01098765432,[P-2026-0002]` | 알리고 콘솔 직접 import 호환 (BOM 누락 시 한글 깨짐 / 헤더 오정렬 시 reject) |
| 1.1.2 | 개발책임자 | 🔴 | 1.1.1 + group1 `null` 인 거래처 1건 (`P-2026-0099`, `(주)그룹없음`) 추가 | 동일 호출 | 응답 row 에 `기본,(주)그룹없음,01012345678,[P-2026-0099]` 포함 (group1 null → "기본") | group fallback 보장 |

### 1.2 차단 거래처 자동 제외 (BlockedPartner partner_code 매칭 row skip) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | 개발책임자 | 🔴 | seed: `P-2026-0001` (활성) + `P-2026-0002` (`차단대상거래처`, mobile=`01033334444`) + BlockedPartner row (`partnerCode='P-2026-0002'`, reason='MANUAL') | `GET /api/v1/partners/admin/aligo/csv` | 응답 200 + body 에 `(주)에어뱅크` 포함 + `차단대상거래처` 미포함 + `[P-2026-0002]` 미포함 | 차단 거래처 알리고 노출 차단 (실수 발송 방지) |

### 1.3 전화 정규화 (mobile 우선, +82 → 0, 비숫자 제거, prefix 검증) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | 개발책임자 | 🔴 | 다양한 전화 형식 거래처 8건 — `010-1234-5678` / `010 1234 5678` / `(010)1234.5678` / `+82-10-1234-5678` / `00821012345678` / `02-1234-5678` (지역) / `070-1234-5678` (인터넷) / `010-12-3` (짧음) | `GET /api/v1/partners/admin/aligo/csv` | 응답 row 4건 (앞 4 형식만 → 전부 `01012345678`), 지역/인터넷/짧은 번호 3건 row 제외 (정규화 실패 → row skip) | 알리고 발송 실패 (잘못된 prefix) 차단 |
| 1.3.2 | 개발책임자 | 🟠 | mobile null + phone (휴대폰) 인 거래처 `P-2026-0010` (`휴대폰만보유`, phone=`010-9999-8888`) | 동일 호출 | row `기본,휴대폰만보유,01099998888,[P-2026-0010]` 포함 (phone fallback) | mobile 미공급 거래처 보호 |

### 1.4 chunk 50 분할 (120 contact → 3 chunk: 50/50/20) 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회규 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | 개발책임자 | 🟠 | mock CsvSourceClient 가 120 contact 반환 + AligoAddressBookClient mock 이 success(N) 반환 | `POST /api/v1/notify/aligo/address-book/sync` | 응답 `added=120, updated=0, skipped=0, failed=[]` + AligoAddressBookClient.uploadChunk 3회 호출 (size 50/50/20) | 알리고 API rate limit 회피 (chunk 50 = 알리고 권장 상한) |

### 1.5 429 backoff 재시도 + 최종 성공 / 소진 시 failed 누적 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | 개발책임자 | 🟠 | mock CsvSourceClient 50 contact + mock AligoAddressBookClient: 429 / 429 / success(50) 순차 응답 | sync 호출 | 응답 `added=50, failed=[]` + uploadChunk 3회 호출 (재시도 2회 포함) | 일시적 rate limit 자가 복구 |
| 1.5.2 | 개발책임자 | 🔴 | mock 10 contact + mock client 항상 429 (BACKOFF_MAX_RETRIES 소진) | sync 호출 | 응답 `added=0, failed=[1건 — "chunk#1 ... 429 ..."]` + uploadChunk 4회 (1 + 3 retry) | 영구 차단 시 운영자 인지 (실패 누락 차단) |

---

## 2. 슬라이스 2 — 운송사 실배차 비교 (BE-2 + FE) — 6 case 🔴

**의존 backend** —
- `arologis-service` `POST /api/v1/arologis/dispatch/reconcile` (multipart, DispatchReconcileService + VendorExcelParser)

**의존 frontend** — `clients/desktop` `ArologisDispatchReconcilePage` (`/arologis/dispatch-reconcile`, DISPATCH/MANAGER/MASTER)

**testid 의존 (실 FE 표준)** — `reconcile-upload-area` / `reconcile-file-input` / `reconcile-from` / `reconcile-to` / `reconcile-run-btn` / `reconcile-status-filter` / `reconcile-csv-btn` / `reconcile-result-table` / `reconcile-row-{slipNo}`

### 2.1 TRUE — 양쪽 매칭 (mismatch 0) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | 신입 창고 / 배차원 | 🔴 | dispatch seed: `2026-05-09` 일자 1건 (slipNo=214, partnerName=`삼한`) + vendor 엑셀 `CJ대한통운.xlsx` (헤더 `운송장번호 / 접수일자 / 접수시간 / 업체명`, row `214 / 2026-05-09 / 09:30 / 삼한`) | upload area 에 file drag → from `2026-05-01` / to `2026-05-31` 입력 → "비교 실행" 클릭 | 응답 200 + `matchedCount=1` + `mismatchedRows=[]` + `dispatchCount=1` + `vendorRowCount=1` + `vendorCount=1`, UI 결과 chip `일치 1` | 정상 매칭 흐름 |

### 2.2 FALSE_LEFT — 우리 dispatch 만 (운송사 누락) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | 신입 창고 / 배차원 | 🔴 | dispatch seed slipNo=999 (`우리만`) + vendor 엑셀 row `100 / 2026-05-09 / 다른슬립` (slipNo 일치 X) | 비교 실행 | 응답 `matchedCount=0` + `mismatchedRows` 2건 (FALSE_LEFT slipNo=999, reason 포함 "운송사 엑셀 누락" + FALSE_RIGHT slipNo=100), UI 결과 chip `일치 0 / 운송사 누락 1 / 우리 누락 1` + status filter `운송사 누락` 선택 시 row 1건만 노출 | 운송사 발송 누락 식별 (영업 매출 손실 차단) |

### 2.3 FALSE_RIGHT — vendor 만 (우리 dispatch 누락) 🔴

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | 신입 창고 / 배차원 | 🔴 | dispatch 0건 + vendor 엑셀 row `R-1 / 2026-05-09 / vendor만` | 비교 실행 | 응답 `mismatchedRows` 1건 (FALSE_RIGHT, slipNo=R-1, vendorName=CJ, reason "자체 dispatch 누락") | 자체 시스템 dispatch 누락 식별 (회계 자동 매출 분개 차단) |

### 2.4 다중 vendor 통합 (2 vendor 엑셀 동시 처리) 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | 신입 창고 / 배차원 | 🟠 | dispatch seed slipNo=214 + vendor 엑셀 2개 (CJ대한통운.xlsx 헤더 `운송장번호/접수일자/접수시간` row 214, 롯데.xlsx 헤더 `예약번호/발송일자/발송시간` row L-100) | 두 파일 drag-drop → 비교 실행 | 응답 `vendorCount=2`, `vendorRowCount=2`, `matchedCount=1` (214) + FALSE_RIGHT 1건 (L-100, vendorName=`롯데`), UI 업로드 파일 목록에 두 파일 명 + 추정 vendor 표기 | 다중 vendor 양식 동시 처리 보장 |

### 2.5 빈 결과 (dispatch 0 + vendor 0) 🟡

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5.1 | 신입 창고 / 배차원 | 🟡 | dispatch 0건 + vendor 엑셀 헤더만 (row 0) | 비교 실행 | 응답 `matchedCount=0`, `mismatchedRows=[]`, `dispatchCount=0`, `vendorRowCount=0` (비활성 응답 — 200) | 빈 입력 graceful 처리 (NPE / 5xx 회귀 차단) |

### 2.6 헤더 매처 fail (영문 양식) — partial parse 허용 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.6.1 | 신입 창고 / 배차원 | 🟠 | dispatch seed slipNo=214 + vendor 엑셀 2개 (CJ.xlsx 정상 헤더 row 214, Unknown.xlsx 영문 헤더 `WaybillNo/PickupDate` row X-1) | 비교 실행 | 응답 `vendorCount=1` (Unknown 빈 list 라 미카운트), `vendorRowCount=1`, `matchedCount=1` (CJ 214 매칭), `mismatchedRows=[]` (예외 없이 partial 진행) | 1개 vendor 양식 미지원이어도 다른 vendor 결과 보장 (전체 fail 회귀 차단) |

### 2.7 인자 검증 — files null / from > to 🟠

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.7.1 | 신입 창고 / 배차원 | 🟠 | files 없이 호출 | API 호출 | 응답 400 + BusinessException ErrorCode `INVALID_INPUT` + 메시지 "vendor 엑셀 파일을 1개 이상 업로드하세요" UI alert | 빈 입력 우회 가드 |
| 2.7.2 | 신입 창고 / 배차원 | 🟠 | files 1건 + from `2026-05-31` / to `2026-05-01` (역순) | 비교 실행 | 응답 400 + `INVALID_INPUT` | 잘못된 날짜 범위 우회 가드 |

---

## 3. 권한 / UX 정합 — 추가 case (3 case)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1 | 신입 영업 (SALES) | 🔴 | SALES 토큰 + admin 진입 시도 | navigate `/admin/aligo-address-book` | RoleGuard 차단 → unauthorized 화면 또는 redirect, AdminLayout entry 자체 미노출 | 권한 우회 차단 |
| 3.2 | 회계 외주 (ACCOUNTANT) | 🟠 | ACCOUNTANT 토큰 | navigate `/arologis/dispatch-reconcile` | RoleGuard `ARO_DISPATCH_RECONCILE_ROLES = [DISPATCH, MANAGER, MASTER]` 차단 → unauthorized | 회계 권한 분리 |
| 3.3 | 개발책임자 | 🟡 | MASTER 토큰, AligoAddressBookPage 진입 | row `P001234` 의 testid 검증 | testid `admin-aligo-row-P001234` 노출 + UUID 미노출 (row 어디에도 36자 hyphen UUID 패턴 없음) | UUID 비공개 정책 (`feedback_uuid_no_user_visibility.md`) |

---

## 4. 단위 테스트 점검 보고 (BE-1 13 + BE-2 15 = 28 case 실제)

> **회고 — javadoc 표기 vs 실 메서드 수 불일치**:
> `AligoAddressBookSyncServiceTest` javadoc 은 "4 case" 로 명시되어 있으나 실제 `@Test` 메서드 6건. `DispatchReconcileServiceTest` javadoc 은 "6 case + 인자 검증 2" 로 명시되어 있으나 실제 9건 (extractVendorName 추가). 회고 — javadoc 갱신 권고 (수치 정확성).

### 4.1 BE-1 — partner-service `PartnerAligoExportServiceTest` (7 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `exportAligoCsv_normal_emitsRowsWithUtf8BomAndHeader` | UTF-8 BOM + 헤더 + 정상 row 2건 | PASS | 시나리오 1.1.1 매핑 |
| 2 | `exportAligoCsv_blockedPartner_excludedFromOutput` | BlockedPartner 매칭 row skip | PASS | 시나리오 1.2.1 매핑 |
| 3 | `normalizeMobilePhone_variousFormats_returnsCanonical` | 8 변형 정규화 + 휴대폰 prefix 검증 + 길이 검증 + null/blank | PASS | 시나리오 1.3.1 매핑 (가장 두꺼운 정규화 검증) |
| 4 | `exportAligoCsv_phoneFallback_usedWhenMobileBlank` | mobile null → phone fallback / 지역번호 row skip | PASS | 시나리오 1.3.2 매핑 |
| 5 | `exportAligoCsv_userNotedStrikethroughFilters_areNotApplied` | 신용정보 / 전자소송 / 폐업의심 filter 미적용 (status 만 적용) | PASS | 사용자 명시 strikethrough 회귀 차단 — 향후 filter 추가 PR 시 본 test 폐기 의무 |
| 6 | `exportAligoCsv_utf8BomPresent_atFirstThreeBytes` | 빈 결과여도 BOM + 헤더 emit | PASS | 시나리오 1.1.1 보강 (edge case) |
| 7 | `csvField_specialCharacters_quotedAndEscaped` | CSV escape (콤마/따옴표/개행/null) | PASS | RFC 4180 호환 |

### 4.2 BE-1 — notification-service `AligoAddressBookSyncServiceTest` (6 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `sync_120contacts_splitsIntoThreeChunks_50_50_20` | chunk 50 분할 (120 → 50/50/20) + uploadChunk 3회 검증 | PASS | 시나리오 1.4.1 매핑 |
| 2 | `sync_429ResponseTwiceThenSuccess_retriesWithBackoff` | 429 2회 → backoff → 최종 success(50) | PASS | 시나리오 1.5.1 매핑 |
| 3 | `sync_429ExhaustedAllRetries_recordsFailed` | BACKOFF_MAX_RETRIES 소진 → failed 누적 + 호출 횟수 (1 + retries) | PASS | 시나리오 1.5.2 매핑 |
| 4 | `sync_partialFailure_chunkSucceedsAndChunkFails_accumulatesBoth` | 첫 chunk success + 둘째 chunk HTTP 500 → added=50, failed[0] 에 sample memo 포함 | PASS | partial fail 시 다른 chunk 결과 보장 |
| 5 | `sync_mockClient_emptyCsv_returnsEmptyResponseWithoutInvoking` | CsvSource 빈 list → uploadChunk 미호출 + 빈 응답 | PASS | edge case |
| 6 | `sync_mockClient_dryRunResponse_isPassedThrough` | mock dryRun 응답 통과 (added=size, http=200) | PASS | mock 통합 검증 |

### 4.3 BE-2 — arologis-service `VendorExcelParserTest` (6 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `헤더매처_접수시간_접수일자_정상_parse` | CJ대한통운 가정 헤더 (접수일자/접수시간/업체명) | PASS | 시나리오 2.1.1 매핑 |
| 2 | `헤더매처_발송일자_발송시간_정상_parse` | 롯데 가정 헤더 (예약번호/발송일자/발송시간) | PASS | 시나리오 2.4.1 보강 |
| 3 | `헤더매처_출고일_정상_parse` | 한진 가정 헤더 (송장번호/출고일/거래처명, 시간 헤더 부재 → null 허용) | PASS | partial column 허용 |
| 4 | `헤더매처_2층_헤더_row0_그룹_row1_컬럼_정상_parse` | legacy GAS 11번 2층 헤더 패턴 (row 0=그룹, row 1=컬럼) | PASS | legacy GAS 호환 의무 |
| 5 | `헤더매처_미인식_vendor_양식_빈리스트_partial_parse_허용` | 영문 양식 → 빈 list (예외 X) | PASS | 시나리오 2.6.1 매핑 |
| 6 | `parse_엑셀_형식_오류_BusinessException_INVALID_INPUT` | 잘못된 byte → BusinessException | PASS | 잘못된 input graceful 처리 |

### 4.4 BE-2 — arologis-service `DispatchReconcileServiceTest` (9 case 실)

| # | 메서드 | 검증 핵심 | 평가 | 비고 |
|---|---|---|---|---|
| 1 | `left_join_TRUE_양쪽_매칭` | matchedCount=1, mismatchedRows=[] | PASS | 시나리오 2.1.1 매핑 |
| 2 | `left_join_FALSE_LEFT_vendor_누락` | FALSE_LEFT (slipNo=999) + FALSE_RIGHT (slipNo=100) 양쪽 mismatch | PASS | 시나리오 2.2.1 매핑 |
| 3 | `left_join_FALSE_RIGHT_dispatch_누락` | dispatch 0건, vendor 만 → FALSE_RIGHT 1건 | PASS | 시나리오 2.3.1 매핑 |
| 4 | `left_join_빈_결과` | dispatch 0 + vendor row 0 → 빈 응답 | PASS | 시나리오 2.5.1 매핑 |
| 5 | `left_join_다중_vendor_통합` | CJ + 롯데 동시 처리, vendorCount=2 | PASS | 시나리오 2.4.1 매핑 |
| 6 | `left_join_partial_parse` | Unknown 헤더 미인식 시에도 CJ 결과 보장 | PASS | 시나리오 2.6.1 매핑 |
| 7 | `reconcile_files_empty` | files 빈 list → BusinessException INVALID_INPUT | PASS | 시나리오 2.7.1 매핑 |
| 8 | `reconcile_from_after_to` | from > to → INVALID_INPUT | PASS | 시나리오 2.7.2 매핑 |
| 9 | `extractVendorName_split` | 파일명 → vendor 식별자 추출 (`CJ대한통운_2026-05.xlsx` → `CJ대한통운`) | PASS | 다중 vendor UI 추정 vendor 일관 |

### 4.5 회귀 시나리오 평가

| 회귀 위험 | 현재 cover | 권고 |
|---|---|---|
| 알리고 chunk size 변경 (50 → 100) 시 rate limit | ✅ 1.4.1 + BE-1 #1 | 회귀 case OK |
| BlockedPartner 쿼리 누락 (조회 미실행) | ✅ 1.2.1 + BE-1 #2 | 회귀 case OK |
| 정규화 regex 변경 (휴대폰 prefix `010|011|016|017|018|019` 제한) | ✅ 1.3.1 + BE-1 #3 (8 변형) | 향후 prefix 추가 시 BE-1 #3 갱신 의무 |
| 헤더 매처 keyword 변경 (vendor 양식 추가) | ✅ 2.6.1 + BE-2 parser #1~5 | OK |
| dispatch ↔ vendor slipNo 비교 키 변경 (Long vs String) | ⚠️ 부분 cover | DispatchReconcileServiceTest 의 slipNo Long seed 가 `String.valueOf` 변환을 가정 — 향후 slipNo 타입 변경 시 BE-2 #1 명시 검증 추가 권고 |
| 다중 vendor 통합 시 vendor 이름 충돌 | ⚠️ 미cover | 후속 PR 권고 — 동일 파일명 prefix (`CJ_2026-05.xlsx`, `CJ_2026-06.xlsx`) 두 vendor 동시 업로드 시 vendor 식별자 합산 vs 분리 검증 미공개 |
| CSV BOM 환경별 호환 (Windows Excel + macOS Numbers) | ✅ BE-1 #6 | OK |

### 4.6 종합

- **총 28 case 실제 (작업 task spec 의 24 case 보다 4 case 추가 cover)** — javadoc 누락 case 들이 실제로는 추가 보호 효과 제공.
- **시나리오 ↔ 단위 테스트 매핑 100%** (3 case 가 권한 / UUID 정합 — 단위 테스트 외 E2E 검증).
- **PASS/FAIL 측정 가능 기준 확보** — 28 case 모두 명확한 assertion (응답 status / DB row / UI testid).
- **회규 위험 2건 권고**: (1) javadoc case 수 갱신, (2) slipNo 타입 변경 시 BE-2 #1 명시 검증 추가.

---

## 5. 작동 캡처 (사용자 명시 — `feedback_pr_qa_screenshots.md` 절대 의무)

본 폴더 산출물:

| 파일 | 화면 | 검증 항목 |
|---|---|---|
| `working-aligo-address-book.png` | `/admin/aligo-address-book` (MASTER) | (1) AdminLayout 좌측 사이드바 "관리자 (MASTER 전용)" 표기, (2) 거래처 미리보기 표 6건 (P001234~P001239), (3) 그룹 dropdown ("전체"), (4) "동기화 실행" 버튼, (5) "발송금지" badge (P001238 남해상사 row), (6) group badge (SF벤더/신용정보/일반), (7) "본 화면은 PR-F1 1차 mock" warning bar, (8) UUID 미노출 (모든 식별자 = `P00...` 코드) |
| `working-dispatch-reconcile.png` | `/arologis/dispatch-reconcile` (DISPATCH/MANAGER/MASTER) | (1) "운송사 실배차 비교" header, (2) drag-drop 업로드 영역 (".xlsx 만 허용 · 파일당 최대 5MB · 다중 업로드 지원"), (3) 시작일/종료일 date input (default `2026-05-10`), (4) "비교 실행" 버튼 (files 0 → disabled), (5) "본 화면은 PR-F1 1차 mock" warning bar |

**캡처 자동화 인프라**:
- `tools/manual-capture/capture-pr-f1.js` — Playwright (msedge channel → chromium fallback) headless 캡처 스크립트
- `clients/desktop/src/renderer/api/mock.ts` 의 `_resolveMockRole()` — `?mockRole=MASTER` 쿼리스트링 dev-only override (AdminLayout MASTER 가드 통과용)
- 부팅: `clients/desktop` 에서 `cross-env VITE_MOCK_MODE=1 npx vite --port 5176`
- 실행: `cd tools/manual-capture && node capture-pr-f1.js`

**한국어 라벨 검증**: 두 캡처 모두 100% 한국어 라벨 노출 (영문 라벨 0건, 단 vendor 명 `CJ대한통운/롯데/한진` + 거래처 코드 `P00...` 는 비즈니스 식별자로 정상).

**UUID 비공개 검증**: 두 캡처 모두 36자 hyphen UUID 패턴 0건 (`feedback_uuid_no_user_visibility.md` 통과).

---

## 6. 미해결 / 후속 PR 권고

| # | 항목 | 심각도 | 후속 PR |
|---|---|---|---|
| 1 | FE Aligo `partnerCode` (P001234~) 가 mock 만 — BE 실 partnerCode (`P-2026-0001`) 와 fixture 불일치 | 🟡 Minor | FE-1 슬라이스 BE 연결 시 mock data 정렬 |
| 2 | FE Reconcile UI 가 BE response shape 미연결 (mock rows 만 노출) — `vendorCount/matchedCount/mismatchedRows` 필드 mapping TODO | 🟠 Major | FE-2 슬라이스 BE 연결 시 type alignment + summary chip 매핑 |
| 3 | `AligoAddressBookSyncServiceTest` javadoc "4 case" 표기 (실제 6) | 🟢 Info | 본 PR 또는 후속 PR 에서 javadoc 갱신 |
| 4 | `DispatchReconcileServiceTest` javadoc "6 + 2 case" 표기 (실제 9) | 🟢 Info | 본 PR 또는 후속 PR 에서 javadoc 갱신 |
| 5 | 동일 vendor 다중 파일 (`CJ_2026-05.xlsx` + `CJ_2026-06.xlsx`) 합산 정책 미정의 | 🟡 Minor | 후속 PR — 운영 도입 시 BE merge / 분리 정책 결정 후 case 추가 |
| 6 | Aligo blocked 거래처 동기화 결과 chip "제외 N" 의 N 값이 `skipped` field 와 매핑되는지 BE-FE 통합 IT 미존재 | 🟠 Major | FE-1 슬라이스 IT 추가 권고 |

---

## 7. PASS 기준 종합

- **시나리오 14 case** (1.x 5 + 2.x 6 + 3.x 3) **모두 PASS 가능 명세** (선행 / 동작 / 기대 / 회귀 차단 4 요소 충족).
- **단위 테스트 28 case 모두 PASS** (BE-1 13 + BE-2 15).
- **작동 캡처 2 PNG 실 파일 생성 + 시각 검증 완료** (한국어 100% + UUID 비공개 통과).
- **5 페르소나 cover** (MASTER 알리고/reconcile, ACCOUNTANT CSV, SALES 권한 차단, DISPATCHER reconcile, DRIVER 범위 외 명시).
