# Phase 10 step-13 (PR-F2) — vendor 발주 OCR 이식 — 에어디자이너 + 제이시스템 (Tesseract)

> 본 dev-report 는 PR (`feature/integrated-phase-10-step-13-vendor-ocr`) 의 종합 작업 보고. PR #119 (PR-F1, W10-step-12) 머지로 GAS C/D 6건 중 vendor 외부 의존 0 인 2건 (알리고 sync mock + 운송사 reconcile) 이 native 이식 완료된 후, 사용자 분류 GAS C/D 잔여 OCR 의존 2건 (D 10번 에어디자이너 운송장/발주서 OCR + D 14번 제이시스템 발주서 OCR) 을 단일 통합 PR 로 native 이식. OCR 엔진 = Tesseract (사용자 결정, D-P10-21 재확인). 흡수 위치 = 신규 `services/ocr-service` 분리 보류, **`partner-order-service` 흡수** (PR-F2 진입 시점 결정 — 발주 도메인 일관성 우선).

## 1. 배경

PR #119 (PR-F1) 머지로 GAS C/D 6건 중 4건 native 이식 완료 (알리고 sync mock + 운송사 reconcile + 미분류 잔여 2건은 PR-F3 위임). 본 PR-F2 시점 진입 가능한 마지막 vendor OCR 2건:

- **GAS D 10번 — 에어디자이너 발주서 OCR** — vendor (에어디자이너) 가 발송한 PDF/이미지 발주서 → 운송장번호 / 거래처명 / 모델코드 / 수량 / 단가 OCR 추출 → `partner_order` draft 자동 생성. GAS 원본 = Google Apps Script + Vision API 호출.
- **GAS D 14번 — 제이시스템 발주서 OCR** — vendor ((주)제이시스템) 발송 발주서 OCR. 양식 차이 = "표 형식" (가로 컬럼 헤더 `모델 / 수량 / 단가 / 합계`).

위임 잔여 (PR-F3 이후):
- **GAS C 잔여 2건 (사용자 미분류)** — PR-F3 이후 분석.

## 2. 산출물

### 2-1. Phase A — DevOps 1 + Designer 1 + BE 1 (3 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `f4232ba` | DevOps | Tesseract 4.x native 설치 가이드 + production setup (5 files +315). `docs/dev-environment/tesseract-setup.md` 신규 (Windows / macOS / Ubuntu / Docker / EC2 m5.xlarge 5 환경 설치 절차 + `kor.traineddata` ~10MB 다운로드 + `TESSDATA_PREFIX` 환경변수 + Tesseract 4.x ↔ tess4j 5.x 호환 매트릭스). `.github/workflows/ci.yml` Linux runner 에 `apt-get install tesseract-ocr tesseract-ocr-kor` step 추가 (CI IT 가능). `services/partner-order-service/src/main/resources/application.yml` Tesseract 설정 4종 (`samhan.ocr.engine`, `samhan.ocr.tessdata-path`, `samhan.ocr.languages`, `samhan.ocr.timeout-ms`). `.gitignore` traineddata 대용량 binary 7건 무시. README.md Tesseract 설치 안내 link 추가. |
| `1a925ae` | Designer | `clients/desktop/src/renderer/routes/SalesVendorOrderUploadPage.tsx` + `.module.css` 신규 — `/sales/vendor-order/upload` (SALES/MANAGER/MASTER, 3-step wizard mock, 904 + 360 lines). Step 1 = vendor 라디오 (에어디자이너 / 제이시스템 / 자동 detect) + drag-drop 영역 (PDF/PNG/JPG, 파일당 최대 10MB) + "OCR 분석 시작" 버튼. Step 2 = 거래처 정보 카드 (partnerCode + 거래처명) + 라인 표 (모델코드 / 모델명 / 수량 / 시트 단가 / DC 단가 / 최종가 / source 칩 (CATALOG/OCR/MANUAL) / 매칭 실패 빨간 highlight) + suggestions warning bar + inline edit. Step 3 = 확정 review (PartnerOrder draft 등록 미리보기 + "발주서 등록" 버튼). `AppLayout.tsx` "영업 (SALES)" 그룹 entry "발주서 OCR 업로드" 신규 + `routes/index.tsx` 라우트 1건 등록. |
| `9874aa9` | BE | `partner-order-service` `vendor.ocr` + `vendor.parser` + `vendor.client` + `vendor.service` + `vendor.web` 패키지 신규 (28 files +2086, 25 src + 6 test). **OcrEngine 추상화** — `OcrEngine` interface + `MockOcrEngine` (preset key 매처, dev/test/CI fallback) + `TesseractOcrEngine` (tess4j 5.x JNI binding, `kor+eng` 다중 언어, timeout) + `OcrEngineConfig` (`@ConditionalOnProperty(name="samhan.ocr.engine", havingValue="tesseract")` + havingValue="mock" 양분기, **Tesseract 미설치 503 graceful fallback** 의무) + `OcrProperties` + `OcrException`. **vendor parser** — `VendorOrderParser` interface + `AirDesignerOrderParser` (keyword "에어디자이너" + 라인 정규식 `^\d+\.\s*(.+)\s*\[(.+)\]\s*(\d+)개\s*([\d,]+)원`) + `JSystemOrderParser` (keyword "제이시스템" + 표 형식 row 매처) + `ParsedVendorOrder` record + `VendorParserRegistry` (자동 detect = 첫 5줄 keyword score). **service** — `VendorOrderService` (multipart → OCR → parser → ProductCatalogLookup (시트 단가) → DcConfigClient (homeDiscount 적용) → PartnerLookupClient (거래처 검증) → response). **client** — `PartnerLookupClient` + `PartnerSummary` + `ProductCatalogLookupClient` (RestClient + X-Internal-Token + fail-soft empty Map). **controller** — `VendorOrderController` `POST /api/v1/admin/partner-order/vendor/upload` (multipart) + `POST .../vendor/confirm` (PartnerOrder 등록) + 3 DTO. `application.yml` OCR 설정 + `build.gradle` tess4j 5.13.0 의존성. **단위 25 case PASS** (Mock 4 + AirDesigner 6 + JSystem 5 + Registry 3 + Service 7) + **IT 5 case** (ApplicationContextLoadIT 1 + VendorOrderControllerIT 4, @MockBean 외부 client 격리). |

### 2-2. Phase B — Desktop FE 1 + QA 1 (2 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `194aec0` | FE | `clients/desktop/src/renderer/api/vendorOrderApi.ts` 신규 (210 lines) — `uploadVendorOrder()` (multipart FormData, `vendorHint` 옵션, `partnerCode` 옵션, `parsedLines / suggestions / partner / vendorName` 응답 매핑) + `confirmVendorOrder()` (확정 lines + 거래처 검증 + draft 등록). `SalesVendorOrderUploadPage.tsx` mock → 실 API 연결 (904 → 489 + 446 lines, 라인 표 inline edit + suggestions warning bar + Step 2 → Step 3 transition + 확정 후 redirect). desktop typecheck PASS (0 error). |
| `13676a2` | QA | `docs/qa/phase-10-step-13-vendor-ocr/scenarios.md` 신규 (299 lines, 15 case = 슬라이스 1 에어디자이너 5 + 슬라이스 2 제이시스템 5 + OCR 비활성 fallback 1 + 권한/UX/정합 4) + 단위 30 case 매핑 + 페르소나 5 (MASTER / MANAGER / SALES / ACCOUNTANT / DRIVER) + 회귀 위험 + 후속 backlog. **작동 캡처 3 PNG (Playwright headless)** — `working-vendor-order-step1-upload.png` (75,754 byte, vendor 라디오 + drag-drop) + `working-vendor-order-step2-preview.png` (128,022 byte, 거래처 정보 카드 + 라인 표 + suggestions) + `working-vendor-order-step3-confirm.png` (71,953 byte, 확정 review). `tools/manual-capture/capture-pr-f2.js` 신규 (292 lines, msedge → chromium fallback, mock preset 주입). `clients/desktop/src/renderer/api/mock.ts` `+116 lines` (vendor OCR mock fixture: 에어디자이너 / 제이시스템 / OCR fail / 매칭 실패 4 preset, capture 자동화 의존). |

### 2-3. GAS C/D 6건 매핑 — PR-F1 (#119, 2건) + **PR-F2 (본 PR, 2건 OCR)** + PR-F3 (잔여 2건)

| GAS 도구 | 분류 | PR | 산출 / 위임 사유 |
| --- | --- | --- | --- |
| 9. 알리고 자동 업로드 | C | PR-F1 (#119) | partner CSV export + notification sync (mock) |
| 10. 에어디자이너 OCR | D | **PR-F2 (본 PR)** | Tesseract OCR + AirDesignerOrderParser (라인 정규식) |
| 11. 운송사 실배차 비교 | D | PR-F1 (#119) | arologis VendorExcelParser + DispatchReconcileService |
| 14. 제이시스템 OCR | D | **PR-F2 (본 PR)** | Tesseract OCR + JSystemOrderParser (표 형식) |
| GAS C 잔여 2건 | C | PR-F3 이후 | 사용자 미분류 |

## 3. 검증

### 3-1. 풀빌드
- `./gradlew assemble -x test` → BUILD SUCCESSFUL (Korean path JDK 트랩 회피 — `feedback_korean_path_jdk`)

### 3-2. 단위 + IT — 30 case (BE 25 + IT 5) 전부 PASS

| Service | Test | Case 수 | 결과 |
| --- | --- | --- | --- |
| partner-order-service | `MockOcrEngineTest` | 4 | PASS |
| partner-order-service | `AirDesignerOrderParserTest` | 6 | PASS |
| partner-order-service | `JSystemOrderParserTest` | 5 | PASS |
| partner-order-service | `VendorParserRegistryTest` | 3 | PASS |
| partner-order-service | `VendorOrderServiceTest` | 7 | PASS |
| partner-order-service | `ApplicationContextLoadIT` | 1 | PASS |
| partner-order-service | `VendorOrderControllerIT` | 4 | PASS |

(QA scenarios § 5 매핑.)

### 3-3. Desktop typecheck
- `cd clients/desktop && npm run typecheck` → tsc PASS (0 error)

### 3-4. 작동 캡처 (Playwright headless, 3-step wizard)

| 파일 | 크기 | 검증 |
| --- | --- | --- |
| `docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step1-upload.png` | 75,754 byte | vendor 라디오 (에어디자이너 / 제이시스템 / 자동 detect) + drag-drop 영역 (PDF/PNG/JPG · 파일당 최대 10MB) + "OCR 분석 시작" 버튼 (file 0 → disabled) + warning bar |
| `docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step2-preview.png` | 128,022 byte | 거래처 정보 카드 (`AIRD-001` + "(주)에어디자이너") + 라인 표 4컬럼 (모델코드 / 수량 / 시트 단가 / source 칩) + 매칭 실패 빨간 highlight + suggestions warning bar + UUID 미노출 |
| `docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step3-confirm.png` | 71,953 byte | 확정 review (PartnerOrder draft 미리보기 + 합계 + "발주서 등록" 버튼) + 한국어 100% |

자동화 인프라 — `tools/manual-capture/capture-pr-f2.js` (msedge → chromium fallback) + mock preset 주입 (`clients/desktop/src/renderer/api/mock.ts` vendor OCR fixture 4종) + `?mockRole=MASTER` dev-only override (PR-F1 패턴 일관).

### 3-5. Korean path JDK 17 트랩 회피
Windows + 한글 경로 + JDK 17 환경에서 Testcontainers IT 자동 skip (memory `feedback_korean_path_jdk` / `feedback_testcontainers_windows_docker`). CI Linux runner 에서 실 IT 동작 검증 (`ApplicationContextLoadIT` + `VendorOrderControllerIT`).

### 3-6. PR-F1 회귀 가드 일관
- **`*Bean` suffix 의무** — `OcrEngineConfig` 의 mock/tesseract 양분기 모두 `@Bean` 명시 (`mockOcrEngineBean` / `tesseractOcrEngineBean`).
- **`ApplicationContextLoadIT`** — 본 PR-F2 신규 도입 (PR-F1 머지 후 회귀 가드 패턴 정착) — Spring context 부팅 + 25 외부 client `@MockBean` 격리 검증.

## 4. 후속 (PR-G1 이후)

- **PR-G1 — slip-service e-Count schema 보강 + API 제거** — 본 PR 머지 후 진입 예정 (사용자 명시). 이카운트 의존 제거 단계 (자체 분개 + 출고전표 자동 조회 100% 완성 후 schema 정리).
- **Tesseract 운영 OCR 정확도 80~90% — 후처리 정규화 보강** — 운송장번호 12자 hyphen 표준 / 모델코드 대소문자 / 단가 천단위 콤마 정규화. 본 PR scope = 라인 정규식 + 표 형식 매처 1차 정착, 운영 진입 시 OCR fail rate 측정 후 보강.
- **신규 vendor 양식 추가 시 parser 등록 패턴** — `VendorOrderParser` interface + `VendorParserRegistry.register()` 만 구현하면 자동 detect 진입 (등록 절차 dev-report § 후속 backlog).
- **`services/ocr-service` 분리 검토** — 본 PR-F2 시점 = `partner-order-service` 흡수 (발주 도메인 일관성). 운영 OCR 호출량 증가 시 별도 service (8098, 미정) 분리 + 비동기 큐 도입 검토.
- **인쇄 양식 — 발주서 확정 후 vendor 회신용 PDF / 인쇄 양식** — 사용자 Edge 캡처 → CSS-only 미세 조정 (`feedback_print_design_iteration`) iteration.
- **OCR 단가 / 시트 단가 일원화 — "종합견적서" 시트 단가 채택** — D-P10-22 결정. ProductCatalogLookupClient 가 호출하는 시트가 종합견적서 단가로 일원화 필요 (D-P10-22 § 영향).

## 5. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — 본 PR scope 신규 entity 0 (PartnerOrder draft 등록은 기존 entity 재사용).
- **Soft Delete 일관** — 모든 조회 `is_deleted = FALSE` 가드 (PartnerLookupClient / ProductCatalogLookupClient).
- **한국어 Javadoc** — OcrEngine / MockOcrEngine / TesseractOcrEngine / OcrProperties / VendorOrderService / VendorOrderController / 3 DTO / 2 Parser / VendorParserRegistry / 2 Client 전부 한국어 Javadoc.
- **ROLE 풀네임** — `SALES` / `MANAGER` / `MASTER` `@PreAuthorize` (memory `feedback_role_naming_full`).
- **IT 외부 client `@MockBean` 격리** — `VendorOrderControllerIT` + `ApplicationContextLoadIT` 가 PartnerLookupClient + ProductCatalogLookupClient + DcConfigClient + OcrEngine 모두 `@MockBean` lenient setup (memory `feedback_it_mockbean_external_clients`). PR-F1 회귀 가드 일관.
- **UUID 비공개** — 응답 DTO (`VendorOrderUploadResponse` / `VendorOrderConfirmResponse`) `partnerCode` + `partnerName` + `modelCode` + `vendorName` 노출, `partner_id` UUID 는 BE 내부 매핑 후 FE 미노출 (memory `feedback_uuid_no_user_visibility`).
- **partner_code snapshot 의무** — vendor 발주서 OCR 결과의 `partnerCode` 는 PartnerOrder draft 시점에 snapshot 으로 보존 (운영 회신 추적).
- **Tesseract 미설치 503 graceful fallback** — `OcrEngineConfig` `@ConditionalOnProperty` + `samhan.ocr.engine=mock` (default dev/test) ↔ `tesseract` (production) 양분기. native 라이브러리 미설치 환경에서 ApplicationContext 부팅 실패 회귀 차단 (운영자 503 안내).

## 6. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR 은 5 commits 단일 통합 PR (Phase A 3 + Phase B 2). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 7. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-G1 (slip-service e-Count schema 보강 + API 제거) 진입
