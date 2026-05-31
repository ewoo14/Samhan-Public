## Codex 5-agent 사이클 1 2a 통합 리뷰 (head `a29bc83e`)

> tech-manager 통합 — Codex BE / FE / Designer / QA / DevOps cross-check. Read-only 정적 검토. 사용자 6회차/7회차 정책: PR 내 모든 문제 본 PR 사이클 안에서 해결 + 멈춤 없이 자동 진행.

### Claude 발견 평가 종합

| Claude 항목 | Codex 평가 | 사유 |
|---|---|---|
| BE-1 (audit ordering) | valid + fix 정합 | `after = summarize(saved)` |
| BE-2 (catch 범위) | valid + fix 정합 | validateLines try 외부 + IAE catch 제거 |
| BE-3 (endpoint) | **invalid 동의** | gateway StripPrefix=2 + slip-service `/slips` 컨벤션 |
| BE-4 (guard 단일화) | valid, **fix 부분 부정합** | 도메인 메서드 `requireEditable` → INBOUND 순서로 OUTBOUND 비편집 상태 403 대신 409. P2 신규 |
| BE-5 (Bean Validation) | valid, **fix 부정합** | `@Valid @RequestBody` + LineRequest validation → 422 SLIP_UPDATE_INVALID_LINE 계약 깨짐. P1 신규 |
| BE-6, N1, N2 | valid + fix 정합 | AssertJ containsOnly / String.join / Javadoc 정상 |
| F-1~F-6 | valid + fix 정합 | 일부 F-5 신규 P1 (addPurchaseLine productId 빈 문자열) |
| D-C1-1 | **부분 해결** | tokens.css 추가 OK, **TS 토큰 index.ts mirror 누락** (Designer Major 신규) |
| D-C1-2/3/5 | 해결 | className 분리, .td-right, Spinner 정상 |
| D-C1-4 (PNG 02) | **미해결** | 한글 mojibake — QA 증적 부적합 (Designer Major 신규) |
| QA D-01 | PASS | $.data.version=1 단언 정합 |
| QA D-02 | **FAIL** | 실 9 @Test method (`grep -c @Test` = 9). dev-report 8 정정이 reverse — 9 로 되돌리기 필요 |
| QA D-03 | over-engineering but harmless | 주석 추가 |
| BE-6 | PASS | extracting().containsOnly(1) AssertJ |
| DevOps MINOR-1 | 후속 슬라이스 정당 | orphanRemoval=false slip-service 전수 범위 초과 |
| DevOps MINOR-2 | valid + fix 정합 | MICROS truncation. SP-08-4-2 follow-up 권장 |
| DevOps INFO | 제거 확인 | trailing whitespace |

### Codex 자체 신규 발견 (사이클 2 필수)

#### P1 — Blocker (사이클 2 fix 필수)

| # | 출처 | 위치 | 내용 |
|---|---|---|---|
| C1 | BE | `SlipUpdateController.java:42` + `SlipUpdateRequest.java:48` + `GlobalExceptionHandler.java:27` | Bean Validation + `@Valid @RequestBody` → quantity=0 라인 `MethodArgumentNotValidException` 400 INVALID_INPUT 응답 가능. **422 SLIP_UPDATE_INVALID_LINE 계약 깨짐**. GlobalExceptionHandler 에서 `MethodArgumentNotValidException` 의 LineRequest field 위반을 422 매핑 또는 LineRequest 검증 service 수동 유지 |
| C2 | BE | `Slip.java:658, 686` | 도메인 메서드가 `requireEditable()` 먼저 호출 후 `slipType != INBOUND` check → OUTBOUND + 비편집 상태 시 403 대신 409. **수정**: 도메인 메서드 ordering (slipType check → requireEditable) 또는 service guard 복구 |
| C3 | FE | `SlipDetailPage.tsx:1914 addPurchaseLine()` | 신규 라인 `productId: ''` 빈 문자열. modal 에 product lookup UI 없음 → BE `LineRequest.productId @NotNull UUID` 저장 실패. **수정**: 본 PR 에서 "행 추가" 제거 (기존 라인 복제/삭제만 허용) — 향후 SP-08-5-3+ 에서 product lookup UX 추가. (사용자 6회차 정책 — PR 내 결함 모두 해결, 후속 백로그 금지) |
| C4 | Designer | `clients/web/design-system/src/tokens/index.ts:37-40` | TS 토큰 warning/danger scale mirror 누락 — CSS/TS source 불일치. design-system 소비자 `tokens.colors` 사용 시 scale 미노출 |
| C5 | Designer/QA | `docs/qa/sp-08-5-2-purchase-slip-edit-put/screenshots/02-purchase-edit-conflict-banner.png` | 한글 mojibake — QA 증적 부적합. UUID 노출 X. **수정**: PowerShell unicode escape 정합 재확인 후 재생성 (`feedback_powershell_utf8_writes` 메모리 참조) |
| C6 | QA | `docs/dev-reports/sp-08-5-2-purchase-slip-edit-put.md §6` | dev-report 8 tests 정정이 reverse — 실 `SlipUpdateIT` 9 @Test method. **수정**: 9 로 되돌리기 |
| C7 | QA | `clients/desktop/playwright/sp-08-5-2-purchase-slip-edit-put/sp-08-5-2-purchase-slip-edit-put.spec.ts:26` | T1 spec `slip.getSlipType() != SlipType.INBOUND` 문자열 단언 — 1c BE-4 service guard 제거 후 fail 가능. **수정**: 도메인 `Slip.updateHeader`/`replaceLines` INBOUND guard 기준 갱신 |

#### P2/Minor/Nit (사이클 2 fix 권고)

| # | 출처 | 위치 | 내용 |
|---|---|---|---|
| C-N1 | BE | `SlipUpdateRequest.java:40` | Javadoc lineId nullable 언급, record 에 lineId 필드 없음 — 문서 제거 |
| C-N2 | FE | `purchaseUpdateMutation.onSuccess` | `setPurchaseIsConflict(false)` 명시 호출 누락. 성공 + modal open 초기화 시 reset |
| C-N3 | Designer | `SlipDetailPage.tsx:1804,1814,1892` | 매입 수정 modal 내 spacing/overflow inline style 잔존 — `.purchase-edit-*` 클래스 정리 |
| C-N4 | Designer | `global.css:700` `.td-right` | 전역 generic — `.slip-line-table .td-right` scope 좁힘 |
| C-N5 | QA | Playwright T1 | 1c 변경 핵심 (validateLines 외부, after=summarize(saved), MICROS truncation) 정적 단언 부재 |
| C-N6 | QA | Playwright T2 | `purchaseUpdatedAt` state 회귀 방지 단언 누락 |
| C-N7 | QA | Playwright T3 | `purchaseIsConflict` boolean state 회귀 단언 누락 |
| C-N8 | QA | Playwright T5 | 라인 add/remove 신규 UX 검증 누락 (C3 fix 후 add 제거 시 remove 만 검증) |
| C-N9 | QA | dev-report §6 | Playwright 5 case 실행/정적 검증 결과 누락 |
| C-N10 | DevOps | `.gitattributes` | EOL `* text=auto eol=lf` 부재 (Windows churn) — 본 PR blocking X, 후속 권고 |
| C-N11 | DevOps | SP-08-4-2 `PartnerOrderUpdateService.verifyVersion` | exact `isEqual()` MICROS 미적용 — cross-service 일관성 후속 follow-up |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| Codex BE | 사이클 2 필요 (P1 1건 + P2 1건) |
| Codex FE | 사이클 2 필요 (P1 1건) |
| Codex Designer | **CHANGES REQUESTED** (Major 2건 — TS mirror + PNG mojibake) |
| Codex QA | 사이클 2 필요 (D-02 reverse FAIL + T1 stale MEDIUM + PNG MEDIUM) |
| Codex DevOps | **APPROVE** (CI/Flyway/secret blocking 없음, 후속 follow-up) |

### TM 결정 (사용자 6/7회차 정책 — PR 내 모든 결함 사이클 2 fix + 자동 머지)

- **종합**: Claude 1c fix 가 대부분 정합하나 **신규 7 P1 + 6 P2/Nit** 발견. 사이클 2 fix 진행 후 머지.
- **사이클 2 = 2c Codex fix 단계 (5회차 워크플로우)**: Codex CLI MCP `sandbox=workspace-write` 위임 — 책임 분담: Codex 자체 발견 P1 7건 + Claude valid 미처리 보완.
- **2c Codex fix 후보**:
  1. **C1 BE P1 (Bean Validation 422 계약)**: `LineRequest` Bean Validation 어노테이션 제거 (또는 `GlobalExceptionHandler` 에서 LineRequest field violation 을 422 SLIP_UPDATE_INVALID_LINE 으로 매핑) — **선택: 어노테이션 제거 + service `validateLines` 수동 검증 유지 (단순 + 계약 보존)**
  2. **C2 BE P2 (도메인 ordering)**: `Slip.updateHeader`/`replaceLines` 메서드 첫 줄 `slipType != INBOUND → throw new BusinessException(...)` 으로 ordering 변경 (requireEditable 앞)
  3. **C3 FE P1 (행 추가 제거)**: `addPurchaseLine` 함수 + "행 추가" 버튼 제거 (`×` remove 만 유지). product lookup UX 는 SP-08-5-3+ 후속. 단 사용자 6회차 "본 PR 안 해결" 정책 적용 — 본 PR scope 는 매입 수정 PUT 이므로 add 제거가 정합 (lookup UX 는 별도 슬라이스 scope).
  4. **C4 Designer Major (TS mirror)**: `clients/web/design-system/src/tokens/index.ts` 에 `warningScale`/`dangerScale` (또는 동등 구조) mirror 추가
  5. **C5 PNG mojibake**: `scripts/regen-sp-08-5-2-shot2.ps1` 의 Malgun Gothic 한글 렌더 정합 재확인 + System.Drawing PowerShell unicode escape 검증 + 재생성. 또는 PNG 04 가드 mock 처럼 fallback 적용.
  6. **C6 QA D-02 reverse**: dev-report §6 "8 tests" → "9 tests" 되돌리기
  7. **C7 QA Playwright T1 stale**: T1 spec `slip.getSlipType()` 단언 도메인 메서드 위치 (`Slip.updateHeader` body 검색) 또는 service Javadoc 기준으로 갱신
  8. **C-N1~N9 보강** (사이클 2 여유 fix): LineRequest Javadoc 정리, onSuccess setPurchaseIsConflict, modal inline style, .td-right scope, Playwright 회귀 단언, dev-report Playwright 5 case 결과
- **DevOps APPROVE** — `.gitattributes` (C-N10), PartnerOrderUpdateService MICROS (C-N11) 는 후속 슬라이스
- **2c Codex fix 완료 후**: 사이클 2 진입 — Claude 5-agent 재리뷰 (head C) → cross-check → 0 P0/P1 도달 시 PM 자동 머지 (사용자 7회차)
- **CI green 확인** 후 머지

**tech-manager — 2026-05-18**
