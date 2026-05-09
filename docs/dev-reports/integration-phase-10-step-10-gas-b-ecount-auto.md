# Phase 10 step-10 (PR-E1) — GAS B 11건 이식 — 이카운트 엑셀 → 출고전표 자동 조회

> 본 dev-report 는 PR (`feature/integrated-phase-10-step-10-gas-b-ecount-auto`) 의 종합 작업 보고. PR #115 (W10-step-9 = 시트 흐름 보강 + 노션 4 CSV 이식 + partner_code 매핑 정정) + #116 (PR-E 진입 전 선행 R2 + BE-E PartnerLookupClient 실 구현) 머지 후, 사용자 명시 GAS B 카테고리 11건 중 7건 (DPS비교 / 가배차 / 미배차 / 지방가배차 / 내일자전표 / 전표정리 / 배차안내 SMS) 을 Samhan Public native 로 이식.

## 1. 배경

Samhan Public 운영 GAS 11 도구 카테고리 (사용자 분류 B) 는 모두 이카운트 엑셀 export → GAS sheet 업로드 → 가공 패턴. PR-D (시트/노션) + PR #116 (R2 parsedKakaoSeq + BE-E PartnerLookupClient) 머지 후 본 PR-E1 에서 11건 중 7건을 native 이식:

- **이식 후 외부 의존 0** — slip-service `slips` 테이블 자동 조회 (이카운트 엑셀 의존 0)
- **DPS 입고 비교** 만 사용자 명시 보존 — 이카운트 DPS 엑셀 업로드 패턴 유지 (창고 측 표준 운영 절차)
- **PR #115 산출 활용** — REGION (광역 분류) / CHAT (단톡방 매핑) / BLOCK (발송금지) 통합

잔여 4건 (원장 / 거래명세서 / 계산서 / 일마감) 은 accounting-service 도메인 — PR-E2 별도 슬라이스.

## 2. 산출물

### 2-1. Phase A — Backend 4 + Designer 1 (5 commits)

| Commit | Role | 산출 |
| --- | --- | --- |
| `1f85605` | Designer | `clients/desktop/src/renderer/print/NextDaySlipView.tsx` (+CSS Module 247 line) — 단톡방별 섹션 + 거래처/슬립 표 + @media print A4 세로 + page-break-after 옵션. Malgun Gothic, 1차 mock (사용자 Edge 캡처 검토 후 2~5차 iteration 예정) |
| `4b14084` | BE-2 (inventory-service) | DPS 입고 비교 endpoint 2종 — `POST /warehouse/audit/dps-compare` (multipart, SLIP/ITEM 단위) + `GET /warehouse/audit/dps-compare/template` (양식 다운로드). SlipServiceClient (Feign) + DpsExcelParser + DpsCompareService 매칭 알고리즘 + RowMismatch 분류 (QUANTITY/PARTNER/NOT_FOUND). 단위 테스트 14 case |
| `0c512d5` | BE-4 (notification-service) | 배차안내 SMS batch 발송 2-step — `POST /admin/notifications/dispatch-batch/preview` (dryRun) + `/send` (실 발송). MessageTemplateService + DispatchBatchPreview/SendService + SlipServiceClient/BlockedPartnerLookupClient interface (Noop placeholder). 단위 테스트 12 case (Preview 5 / Send 4 / Template 3) |
| `e5dc20f` | BE-3 (arologis-service) | 가배차/미배차/지방가배차 3 endpoint — `GET /admin/arologis/dispatches/pre-classify` (REGION 분류 + 권역 그룹핑) + `/unassigned` (slip - dispatch left join 시뮬레이션) + `/regional` (광역 prefix 17 시도 분류). SlipServiceClient (skeleton-mode 토글) + PreClassify/Unassigned/Regional Service. 단위 테스트 14 case + IT 4 파일 SlipServiceClient `@MockBean` 격리 |
| `281415f` | BE-1 (slip-service) | BE-A0 `GET /slips` 5 query param 확장 (from/to/partnerCode/driverPhone/regionGroup) + BE-A5 `GET /slips/next-day-image-data` (출고전표 + chat + block + region 5 way join) + BE-A6 `GET /slips/cleanup` (정합성 flag + 그룹핑). V15 Flyway (slips.partner_code + classified_region_group + 인덱스 3종). NotificationChatRoomClient + PartnerBlockClient (Feign + graceful fallback). 단위 테스트 16 case |

### 2-2. Phase B — Desktop FE 6 (5 commits, FE-1 두 commit 분할)

| Commit | Role | 산출 |
| --- | --- | --- |
| `d163caa` | FE-1+2+6 통합 (multi-agent race) | `arologisDispatchApi.ts` 신규 + `dispatchSmsApi.ts` 신규 + `AppLayout.tsx` 사이드바 entry 4건 추가 (가배차/미배차/지방가배차/배차안내 SMS) + `routes/index.tsx` 라우트 등록 + `ArologisPreClassifyPage.tsx` (가배차 + 권역 분류) + `DispatchSmsPage.tsx` (preview + send 2-step UI). **commit 메시지 표기는 "FE-1 DPS" 이지만 실제 변경은 FE-1 (사이드바/라우트) + FE-2 (가배차 UI) + FE-6 (SMS UI) 통합** — 메시지 정합 정정 보류 (rebase 회피, 본 PR body 명시) |
| `b935b88` | FE-1 + FE-4 흡수 | `dpsCompareApi.ts` 신규 + `InventoryDpsComparePage.tsx` (DPS 입고 비교 — 날짜 + groupBy 토글 + 엑셀 업로드 + 양식 다운로드 + mismatch 표 reason 색상 + CSV 다운로드). 사이드바/라우트 등록은 직전 d163caa 통합 변경에 흡수됨 |
| `7fa9f9b` | FE-5 | `slipCleanupApi.ts` + `SlipCleanupPage.tsx` (전표 정리 — from/to 필터 + status 그룹 + flag 색상 + "원본 슬립 보기" link + CSV) + 사이드바 entry "전표 정리" |
| `230f510` | FE-4 | `nextDaySlipApi.ts` + `NextDaySlipPage.tsx` (`/sales/next-day-slip`, date 필터 + 단톡방 요약 + 인쇄 link) + `/print/next-day-slip` route (Designer NextDaySlipView 통합) |
| `fbab185` | FE-3 | `ArologisUnassignedPage.tsx` (`/arologis/unassigned`, date 필터 + 미배차 slip 표 + "수동 배차로 이동" link) + `ArologisManualDispatchPage.tsx` query param 자동 채움 (date/slipNo/partnerCode/partnerName/address — 첫 차량 첫 정차) + CSV (Excel 한글 호환 UTF-8 BOM + RFC 4180 escape) |

### 2-3. GAS B 11건 매핑 (PR-E1 = 7건 / PR-E2 = 4건)

| GAS 도구 | 본 PR 처리 | 산출 |
| --- | --- | --- |
| 1. DPS비교 | PR-E1 | `BE-2` `POST /warehouse/audit/dps-compare` + `FE-1` `/warehouse/dps-compare` |
| 2. 가배차 | PR-E1 | `BE-3` `GET /admin/arologis/dispatches/pre-classify` + `FE-2` `/arologis/pre-classify` (d163caa 흡수) |
| 3. 미배차 | PR-E1 | `BE-3` `GET /admin/arologis/dispatches/unassigned` + `FE-3` `/arologis/unassigned` |
| 4. 지방가배차 | PR-E1 | `BE-3` `GET /admin/arologis/dispatches/regional` + `FE-2` 내부 토글 (d163caa) |
| 5. 내일자전표 이미지 | PR-E1 | `BE-1` `GET /slips/next-day-image-data` + `FE-4` `/sales/next-day-slip` + Designer NextDaySlipView |
| 6. 전표정리 | PR-E1 | `BE-1` `GET /slips/cleanup` + `FE-5` `/sales/slip-cleanup` |
| 7. 배차안내 SMS | PR-E1 | `BE-4` `POST /admin/notifications/dispatch-batch/{preview,send}` + `FE-6` `/dispatch/sms` (d163caa 흡수) |
| 8. 원장 | PR-E2 | accounting-service ledger 도메인 |
| 9. 거래명세서 | PR-E2 | accounting-service statement 도메인 |
| 10. 계산서 | PR-E2 | accounting-service tax invoice 도메인 |
| 11. 일마감 | PR-E2 | accounting-service daily close 도메인 |

## 3. 검증

### 3-1. 풀빌드
- `./gradlew assemble -x test` → BUILD SUCCESSFUL (95 actionable tasks)

### 3-2. 단위 테스트 — 56 case 신규
- `:services:slip-service:test` PASS — `SlipServiceListSpecTest 7` + `NextDaySlipImageServiceTest 4` + `SlipCleanupServiceTest 5`
- `:services:inventory-service:test` PASS — `DpsCompareServiceTest 9` + `DpsExcelParserTest 5`
- `:services:notification-service:test` PASS — `DispatchBatchPreviewServiceTest 5` + `DispatchBatchSendServiceTest 4` + `MessageTemplateServiceTest 3`
- `:services:arologis-service:test` PASS — `PreClassifyServiceTest 5` + `RegionalServiceTest 4` + `UnassignedServiceTest 5`

### 3-3. Desktop typecheck
- `cd clients/desktop && npm run typecheck` → tsc PASS (0 error)

### 3-4. Korean path JDK 17 트랩 회피
Windows + 한글 경로 + JDK 17 환경에서 Testcontainers IT 자동 skip (memory `feedback_korean_path_jdk` / `feedback_testcontainers_windows_docker`). CI Linux runner 에서 실 IT 동작 검증.

## 4. 후속 (PR-E2)

- accounting-service 4 도메인 (ledger / statement / tax invoice / daily close) — GAS B 8~11번 native 이식
- DPS 입고 비교 엑셀 업로드 패턴은 사용자 명시 보존 (창고 표준 운영 절차)
- 인쇄 양식 NextDaySlipView 2~5차 iteration (사용자 Edge 캡처 검토 후, memory `feedback_print_design_iteration`)

## 5. 제약 / 가드 일관

- BaseEntity 7 audit fields 의무 (V15 영향 — partner_code / classified_region_group 는 column 추가만, audit field 보존)
- Soft Delete 일관 (V15 partial index `is_deleted = FALSE` 가드)
- 한국어 Javadoc — 모든 신규 코드
- ROLE 풀네임 (MASTER / MANAGER / DISPATCH / SALES / WAREHOUSE / INVENTORY) 일관, 약어 금지
- IT 외부 client `@MockBean` 격리 — arologis IT 4 파일 SlipServiceClient mock 추가 (memory `feedback_it_mockbean_external_clients`)
- partner-service Internal endpoint X-Internal-Token 가드 일관
- UUID 비공개 가드 — slipNo / partnerCode / partnerName / driverPhone / chatRoomName / address / 시도만 사용자 노출, 모든 \*\_id (UUID) 미노출 (memory `feedback_uuid_no_user_visibility`)
- partner_code snapshot 의무 (V15 신규 컬럼 — slips 의 거래처 식별자 비공개 정합)

## 6. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR 은 11 commits 단일 통합 PR (Phase A 5 + Phase B 6 = 10 commits 실제, FE-1 두 분할 흡수 1) + 5-team 리뷰. 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 7. d163caa commit 메시지 정합 안내 (multi-agent race)

`d163caa` commit message 본문은 "PR-E1 FE-1 DPS 입고 비교 page" 표기이지만 실제 변경 = `arologisDispatchApi.ts` + `dispatchSmsApi.ts` + `AppLayout.tsx` 사이드바 4건 + `routes/index.tsx` + `ArologisPreClassifyPage.tsx` + `DispatchSmsPage.tsx` (FE-1 사이드바/라우트 + FE-2 가배차 + FE-6 SMS 통합). 다중 FE agent 동시 작업 race 결과로 통합 commit 가 단일 메시지로 push 된 사례. **메시지 rebase 정정은 진행하지 않음** (기존 SHA 보존 + PR body 명시 보완) — memory `feedback_integrated_pr_pattern` 의 "fix 후속 PR 금지" 와 일관.
