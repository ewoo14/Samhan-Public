# Phase 10 step-12 (PR-F1) — GAS C/D 일부 이식 — 알리고 sync (mock) + 운송사 실배차 비교

> 본 dev-report 는 PR (`feature/integrated-phase-10-step-12-gas-cd-vendor`) 의 종합 작업 보고. PR #117 (W10-step-10 = PR-E1) + PR #118 (W10-step-11 = PR-E2) 머지로 GAS B 11건 native 이식 100% 완성 후, 사용자 분류 GAS C/D 6건 중 vendor 외부 의존 0 인 2건 (C 9번 알리고 자동 업로드 + D 11번 운송사 실배차 비교) 을 단일 통합 PR 로 native 이식. OCR 의존 2건 (D 10번 에어디자이너 + D 14번 제이시스템 운송장 OCR) 은 PR-F2 별도 슬라이스 위임 (Tesseract 채택, 사용자 결정).

## 1. 배경

Samhan Public 운영 GAS 도구 사용자 분류 C/D 6건 중 본 PR-F1 시점 진입 가능한 2건:

- **GAS C 9번 — 알리고 주소록 자동 업로드** — 거래처 마스터 → 알리고 콘솔 동기화 (단톡방 발송 / SMS 발송 운영). 외부 의존 = 알리고 API (실 spec 사용자 입수 전 단계 — mock 안내).
- **GAS D 11번 — 운송사 실배차 비교** — 우리 dispatch (`arologis_db.dispatches`) ↔ 운송사 vendor 엑셀 (CJ대한통운 / 롯데 / 한진 등) 양방향 mismatch 식별. 외부 의존 0 (vendor 가 엑셀 파일 발송, 본 PR 시점 = 다중 파일 drag-drop 업로드 + POI 헤더 매처).

위임 4건 (PR-F2 ~ PR-F3 이후):
- **GAS D 10번 — 에어디자이너 운송장 OCR** — PDF/이미지 → 운송장번호 / 거래처명 / 일자 OCR. PR-F2 위임. OCR 엔진 = Tesseract (사용자 결정).
- **GAS D 14번 — 제이시스템 운송장 OCR** — PDF/이미지 OCR. PR-F2 위임 (PR-F2 시점 동시 진입).
- **GAS C 잔여 2건 (사용자 미분류)** — PR-F3 이후 분석.

## 2. 산출물

### 2-1. Phase A — Backend 2 + Designer 1 (3 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `2a1f11f` | Designer | `clients/desktop/src/renderer/routes/admin/AligoAddressBookPage.tsx` 신규 — `/admin/aligo-address-book` (AdminLayout MASTER 가드, 거래처 미리보기 표 + 그룹 dropdown + "동기화 실행" 버튼 + 결과 chip 4종 added/updated/skipped/failed + "본 화면은 PR-F1 1차 mock" warning bar). `clients/desktop/src/renderer/routes/ArologisDispatchReconcilePage.tsx` 신규 — `/arologis/dispatch-reconcile` (DISPATCH/MANAGER/MASTER, drag-drop 다중 업로드 영역 + 시작/종료일 input + "비교 실행" + status filter chip + CSV 다운로드 버튼). `AdminLayout.tsx` "관리자 (MASTER 전용)" 그룹 entry "알리고 주소록 sync" 신규 + `routes/index.tsx` 라우트 2건 등록. |
| `f3b313a` | BE-1 (partner + notification) | `partner-service/PartnerAligoExportService` 신규 — UTF-8 BOM CSV export (헤더 4컬럼 `그룹명,이름,휴대폰,비고\r\n` + 비고 `[partnerCode]`) + `BlockedPartner` matching row skip + 휴대폰 정규화 (`+82-10-...` / `00821012345678` / `010-1234-5678` / `010 1234 5678` / `(010)1234.5678` 8 변형) + prefix 검증 (`010\|011\|016\|017\|018\|019` 만 허용) + group1 fallback ("기본") + RFC 4180 escape, 단위 7 case PASS. `PartnerAdminController` `GET /api/v1/partners/admin/aligo/csv` (MASTER 가드). `notification-service/AligoAddressBookSyncService` 신규 — chunk 50 분할 + 429 backoff retry (`BACKOFF_MAX_RETRIES`) + partial fail 누적 (운영자 인지) + dryRun 응답 통과, 단위 6 case PASS. `AligoAddressBookClient` interface + `MockAligoAddressBookClient` (dryRun added=size, http=200) + `AligoCsvSourceClient` interface + `NoopAligoCsvSourceClient` + `RestClientAligoCsvSourceClient` (partner-service `GET /api/v1/partners/admin/aligo/csv` 호출 + X-Internal-Token + RFC 4180 parse). `AligoAddressBookController` `POST /api/v1/notify/aligo/address-book/sync` (MASTER 가드) + `AligoAddressBookSyncResponse` DTO. **알리고 실 RestClient 본문 = TODO skeleton** — 사용자 spec 입수 후 채움 + `samhan.notification.aligo.address-book.dry-run=false` 토글로 운영 활성. |
| `bb30725` | BE-2 (arologis) | `arologis-service/VendorExcelParser` 신규 — POI 4 vendor 헤더 매처 (CJ대한통운 `접수일자/접수시간/업체명` + 롯데 `예약번호/발송일자/발송시간` + 한진 `송장번호/출고일/거래처명` + 2층 헤더 row0 그룹/row1 컬럼 GAS 11번 호환 패턴) + 영문 양식 미인식 vendor 빈 list 반환 (예외 X) partial parse + `BusinessException INVALID_INPUT` (잘못된 byte 가드), 단위 6 case PASS. `DispatchReconcileService` 신규 — left join TRUE (양쪽 매칭) / FALSE_LEFT (우리 dispatch 만, vendor 누락 — 영업 매출 손실 차단) / FALSE_RIGHT (vendor 만, 자체 dispatch 누락 — 회계 자동 매출 분개 차단) 분류 + 다중 vendor 통합 (vendorCount 카운트) + partial parse (1개 vendor 미지원이어도 다른 vendor 결과 보장) + 인자 검증 (`files null` / `from > to` → `INVALID_INPUT`), 단위 9 case PASS. `DispatchReconcileController` `POST /api/v1/arologis/dispatch/reconcile` (multipart + DISPATCH/MANAGER/MASTER 가드) + `DispatchReconcileResponse` / `MismatchedRow` DTO. `DispatchRepository` 신규 (기간 + status `COMPLETED` 조회). `build.gradle` POI 5.2.5 (`poi` + `poi-ooxml`) 의존성 추가. `application.yml` 운송사 reconcile 설정 4종 추가. |

### 2-2. Phase B — Desktop FE 1 + QA 1 (2 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `a7fa95a` | FE | `clients/desktop/src/renderer/api/aligoAddressBookApi.ts` 신규 — partner-service CSV preview (binary 다운로드 + UTF-8 BOM parse) + notification-service sync (POST + dryRun 토글) 2 client. `clients/desktop/src/renderer/api/dispatchReconcileApi.ts` 신규 — multipart 다중 업로드 + 시작/종료일 ISO + status filter (CSV 다운로드 binary). `AligoAddressBookPage.tsx` mock → 실 API 연결 (preview 표 + 동기화 trigger + 결과 chip mapping). `ArologisDispatchReconcilePage.tsx` mock → 실 API 연결 (response shape `vendorCount/matchedCount/mismatchedRows` 매핑 + status filter chip + CSV 다운로드). `clients/desktop/src/renderer/api/mock.ts` `_resolveMockRole()` 신규 — `?mockRole=MASTER` dev-only override (capture 자동화 AdminLayout 가드 통과용). desktop typecheck PASS (0 error). |
| `368d608` | QA | `docs/qa/phase-10-step-12-gas-cd-vendor/scenarios.md` 신규 — 14 case (슬라이스 1 알리고 5 + 슬라이스 2 reconcile 6 + 권한/UUID 3) + 단위 테스트 28 case 매핑 + 페르소나 5 (MASTER / ACCOUNTANT / SALES / DISPATCHER / DRIVER) + 회귀 위험 7건 + 후속 6건. `working-aligo-address-book.png` + `working-dispatch-reconcile.png` Playwright 작동 캡처 2 PNG (한국어 100% + UUID 비공개 통과). `tools/manual-capture/capture-pr-f1.js` 신규 — Playwright headless 캡처 자동화 스크립트 (msedge channel → chromium fallback). |

### 2-3. GAS C/D 6건 매핑 — PR-F1 (2건) + PR-F2 (2건 OCR) + PR-F3 (2건 잔여)

| GAS 도구 | 분류 | PR | 산출 / 위임 사유 |
| --- | --- | --- | --- |
| 9. 알리고 자동 업로드 | C | **PR-F1 (본 PR)** | partner CSV export + notification sync (mock) |
| 10. 에어디자이너 운송장 OCR | D | PR-F2 | Tesseract OCR 의존 (사용자 결정) |
| 11. 운송사 실배차 비교 | D | **PR-F1 (본 PR)** | arologis VendorExcelParser + DispatchReconcileService |
| 14. 제이시스템 운송장 OCR | D | PR-F2 | Tesseract OCR 의존 (사용자 결정) |
| GAS C 잔여 2건 | C | PR-F3 이후 | 사용자 미분류 |

## 3. 검증

### 3-1. 풀빌드
- `./gradlew assemble -x test` → BUILD SUCCESSFUL (Korean path JDK 트랩 회피 — `assemble` 만 로컬, IT 는 CI Linux runner)

### 3-2. 단위 테스트 — 28 case (BE-1 13 + BE-2 15) 전부 PASS

| Service | Test | Case 수 | 결과 |
| --- | --- | --- | --- |
| partner-service | `PartnerAligoExportServiceTest` | 7 | PASS |
| notification-service | `AligoAddressBookSyncServiceTest` | 6 | PASS |
| arologis-service | `VendorExcelParserTest` | 6 | PASS |
| arologis-service | `DispatchReconcileServiceTest` | 9 | PASS |

(QA 시나리오 § 4 매핑: BE-1 13 case 가 슬라이스 1 알리고 5 case + BE-2 15 case 가 슬라이스 2 reconcile 6 case 모두 1:1 매핑.)

### 3-3. Desktop typecheck
- `cd clients/desktop && npm run typecheck` → tsc PASS (0 error)

### 3-4. 작동 캡처 (Playwright headless)
- `working-aligo-address-book.png` (100,847 byte) — `/admin/aligo-address-book` MASTER 진입 + 거래처 미리보기 6건 + 그룹 dropdown + "동기화 실행" + "발송금지" badge + group badge + warning bar + UUID 미노출
- `working-dispatch-reconcile.png` (63,047 byte) — `/arologis/dispatch-reconcile` + drag-drop 영역 (".xlsx 만 허용 · 파일당 최대 5MB · 다중 업로드 지원") + 시작/종료일 + "비교 실행" 버튼 (files 0 → disabled) + warning bar
- 자동화 인프라 — `tools/manual-capture/capture-pr-f1.js` (msedge → chromium fallback) + `cross-env VITE_MOCK_MODE=1 npx vite --port 5176` + `?mockRole=MASTER` 쿼리스트링 dev-only override

### 3-5. Korean path JDK 17 트랩 회피
Windows + 한글 경로 + JDK 17 환경에서 Testcontainers IT 자동 skip (memory `feedback_korean_path_jdk` / `feedback_testcontainers_windows_docker`). CI Linux runner 에서 실 IT 동작 검증.

## 4. 후속 (PR-F2 이후)

- **PR-F2** — GAS D 운송장 OCR 2건 (10번 에어디자이너 + 14번 제이시스템) — Tesseract 4.x + tess4j JNI binding + `kor.traineddata` 동반 (~10MB) + 운송장번호 12자 hyphen 표준 정규화 + 거래처명 / 일자 추출. 신규 `services/ocr-service` (8098) 또는 `arologis-service` 흡수 — PR-F2 진입 시점 결정.
- **알리고 실 RestClient 활성** — 사용자 알리고 API spec 입수 시점 `RestClientAligoAddressBookClient` 본문 채움 + `samhan.notification.aligo.address-book.dry-run=false` 토글 + 운영 진입 (X-API-Key + 단톡방 token).
- **운송사 vendor sample 다양화** — 본 PR 시점 = CJ대한통운 / 롯데 / 한진 / 2층 헤더 4 vendor 매처. 운영 진입 시점 추가 vendor (우체국 / 로젠 등) 헤더 sample 입수 시 매처 keyword 확장.
- **인쇄 양식 iteration** — 운송사 reconcile 결과 CSV 외 PDF / 인쇄 양식 도입 권고 (사용자 Edge 캡처 → CSS-only 미세 조정 `feedback_print_design_iteration`).
- **동일 vendor 다중 파일 합산 정책** — `CJ_2026-05.xlsx` + `CJ_2026-06.xlsx` 동시 업로드 시 vendor 식별자 합산 vs 분리 정책 미정의 — 운영 도입 시 결정 후 case 추가 (QA scenarios 권고 #5).
- **단위 테스트 javadoc case 수 갱신** — `AligoAddressBookSyncServiceTest` javadoc "4 case" (실제 6) + `DispatchReconcileServiceTest` javadoc "6 + 2 case" (실제 9) — 본 PR 또는 후속 PR 에서 갱신 (QA scenarios 권고 #3 / #4).
- **신용정보 / 전자소송 / 폐업의심 strikethrough filter** — 본 PR 시점 미적용 (status=ACTIVE 만 적용). 향후 filter 추가 PR 시 BE-1 #5 test (`exportAligoCsv_userNotedStrikethroughFilters_areNotApplied`) 폐기 의무.

## 5. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — 본 PR scope 의 신규 entity 0 (read-only). DispatchRepository / 알리고 client 들 모두 read-only.
- **Soft Delete 일관** — 모든 조회 `is_deleted = FALSE` 가드.
- **한국어 Javadoc** — PartnerAligoExportService + AligoAddressBookSyncService + VendorExcelParser + DispatchReconcileService + 4 Controller + 5 DTO + 5 Client 전부 한국어 Javadoc.
- **ROLE 풀네임** — `MASTER` (알리고) + `DISPATCHER/MANAGER/MASTER` (reconcile) `@PreAuthorize` (memory `feedback_role_naming_full`).
- **IT 외부 client `@MockBean` 격리** — AligoAddressBookClient / AligoCsvSourceClient 본 PR scope IT 미진입 (단위 테스트 우선). 후속 SpringBootTest 진입 시점 의무 (memory `feedback_it_mockbean_external_clients`).
- **UUID 비공개** — 응답 DTO (AligoAddressBookSyncResponse / DispatchReconcileResponse / MismatchedRow) 전부 partnerCode + partnerName + slipNo + vendorName 만 노출, `*_id` UUID 미노출 (memory `feedback_uuid_no_user_visibility`).
- **partner_code snapshot 의무** — 알리고 CSV 비고 컬럼 `[partnerCode]` 명시 (운영자 회신 추적).
- **알리고 mock 안내** — 본 PR 시점 dryRun 응답, 실 spec 입수 후 dryRun=false 활성. PR body + dev-report § 4 명시.

## 6. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR 은 5 commits 단일 통합 PR (Phase A 3 + Phase B 2 = 5 commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 7. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 (개발책임자) 본인 머지
6. 머지 후 연관 Issue 즉시 close (memory `feedback_issue_close_after_pr`)
