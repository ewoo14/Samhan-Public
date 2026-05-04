# Phase 4 Accounting Service — Slice A Designer 산출물

> **PR 후보**: PR #27 — accounting-service (port 8087, accounting_db) MVP
> **작성**: 2026-05-05 Designer agent
> **Plan 인용**: `docs/dev-reports/accounting-slice-A/plan.md` §1~§9
> **사용자 확정** (Plan §7): 수동 분개만, ACCOUNTANT/MASTER 권한, AR/AP/자동분개 deferred

본 디렉토리는 **회계 서비스 MVP (계정과목 트리 + 분개장 + 시산표)** 의 Designer (5-team) 산출물입니다.
FE 팀은 본 산출물의 spec 을 인용해 구현하며, BE/QA/DevOps 팀도 wireframe / interaction flow / 신규 도메인 필드를 참고합니다.

---

## 0. 디자인 정책 — 기존 디자인 시스템 답습 의무

본 슬라이스는 **신규 디자인 언어를 도입하지 않습니다.** 기존 21 컴포넌트 + `tokens.css`
(`sales-form-polish-slice` / `sales-polish-2-slice` Designer 산출물) 를 충실히 답습합니다.

### 답습 매트릭스

| 영역 | 답습 대상 | 본 슬라이스 처리 |
|---|---|---|
| 색상 / surface | `--surface-app/card/subtle`, `--ink-primary/secondary/tertiary`, `--action-brand` | 그대로 사용 |
| Page Header | `--page-header-h: 56px`, `--page-title-size: 20px` (sales-polish-2 토큰) | 회계 화면 전체 적용 |
| Sidebar | 220px, `.sidebar-item.active` 좌측 3px 강조 (notification-slice-B 패턴) | "회계" 그룹 신규 추가 |
| 표 | `<DataTable>` + `--row-h: 40px / --row-h-thead: 44px` | JournalListPage / TrialBalancePage 답습 |
| Status Badge | `<SlipStatusBadge>` group/tier 패턴 | `<JournalStatusBadge>` 신규 (3 variants) |
| 라인 입력 표 | `<LineRow>` 10-col grid, `<DragHandle>`, `--col-line-no: 24px` | `<JournalLineRow>` 신규 (회계 전용 7-col) |
| 통화 입력 | `<PriceField>` 콤마 포맷팅 패턴 | `<MoneyInput>` 신규 (KRW only — Q6) |
| 인쇄 | `--print-text-base / --print-thead-bg` (sales-polish-2) | 분개장/시산표 인쇄 답습 |

### 신규 토큰 (3 그룹) — `tokens.md` 상세

1. **`--account-category-{asset/liability/equity/revenue/cogs/sga/other}`** — 7-그룹 색상 (한국 기업회계기준)
2. **`--journal-status-{draft/posted/reversed}`** — 3-status badge 색상
3. **`--accounting-debit-color` / `--accounting-credit-color`** — 차/대 시각 구분 (한국 회계 컨벤션)

### 신규 컴포넌트 (4종) — `components.md` 상세

- `<AccountCodeSelect>` — 계정과목 검색 가능 select (autocomplete + 트리 표시)
- `<JournalStatusBadge>` — DRAFT / POSTED / REVERSED 3 variants
- `<MoneyInput>` — 통화 콤마 포맷팅 입력 (KRW only)
- `<JournalLineRow>` — 분개 라인 1행 (계정 select + 차/대 + 거래처 + 메모)

---

## 1. 산출 파일 목록

### Spec (6 파일)

1. `README.md` (본 파일) — Slice A 디자인 요약 + 기존 디자인 시스템 답습 정책
2. `wireframes.md` — 사이드바 + 5 화면 (AccountTree / JournalList / JournalForm / JournalDetail / TrialBalance) ASCII wireframe
3. `tokens.md` — 신규 토큰 3 그룹 (account-category / journal-status / debit/credit)
4. `components.md` — 신규 컴포넌트 4종 + 기존 답습 매트릭스
5. `ux-flow.md` — 시나리오 4 (분개입력→POST / 역분개 / 시산표 / 권한가드)
6. `print-spec.md` — 인쇄 양식 (분개장 period 별 / 시산표 월별)

### Mock HTML (5 파일) + 캡처 (5 파일)

| 파일 | 화면 | 캡처 |
|---|---|---|
| `mocks/01_account_tree.html` | 계정과목 트리 (7-그룹 ~50 계정) | `screenshots/01_account_tree.png` |
| `mocks/02_journal_list.html` | 분개장 목록 (status filter + 5 행) | `screenshots/02_journal_list.png` |
| `mocks/03_journal_form.html` | 분개 입력 폼 (헤더 + 4 라인 + 차/대 합계) | `screenshots/03_journal_form.png` |
| `mocks/04_journal_detail.html` | POSTED 분개 상세 + [역분개] 버튼 | `screenshots/04_journal_detail.png` |
| `mocks/05_trial_balance.html` | 시산표 (월별 / 7-그룹 합계) | `screenshots/05_trial_balance.png` |

각 mock 1300×900 Edge headless 캡처 (`feedback_pr_qa_screenshots.md` 의무).

---

## 2. 핵심 디자인 결정

### 2.1 7-그룹 색상 (한국 일반기업회계기준)

| 그룹 | 코드 | 색상 | 토큰 |
|---|---|---|---|
| 자산 | 100 | 파랑 #1E40AF | `--account-category-asset` |
| 부채 | 200 | 빨강 #D6504A | `--account-category-liability` |
| 자본 | 300 | 녹 #10B981 | `--account-category-equity` |
| 매출 | 400 | 주황 #E9A53D | `--account-category-revenue` |
| 매출원가 | 500 | 회색 #6B7280 | `--account-category-cogs` |
| 판관비 | 800 | 보라 #7C3AED | `--account-category-sga` |
| 영업외/법인세 | 900 | 검정 #22272F | `--account-category-other` |

### 2.2 차/대 시각 구분

- **차변 (검정 #1A1F2E)** — 한국 회계 관행상 좌측 / 기본 ink color
- **대변 (파랑 #1E40AF)** — 우측 / brand action color (자산 색상과 동일 — 의도적 일치)
- POSTED 분개의 차/대 모두 `font-variant-numeric: tabular-nums` 강제 (정렬 일관성)

### 2.3 POSTED read-only 시각

- POSTED / REVERSED 분개 form 진입 시 모든 input `disabled` + `--surface-subtle` 배경
- 헤더에 `<JournalStatusBadge>` 큰 형태 배치 + "확정된 분개입니다. 수정 불가" 안내
- 액션 버튼 `[POST]` 사라지고 `[역분개 (REVERSE)]` 만 노출 (status 별 분기)

### 2.4 UUID 미노출 원칙 (`feedback_uuid_no_user_visibility.md`)

- `journal.id` UUID 미노출 — `journalNo` (yyyyMMdd-N) 만 사용자 노출
- `accountBalance.id` UUID 미노출 — `accountCode` (6 digit) 만 노출
- URL `/accounting/journals/:id` 의 id 도 사용자 보이는 위치엔 journalNo 로 표시

### 2.5 권한 가드 (Q9 = ACCOUNTANT/MASTER)

- SALES / DRIVER / MANAGER / WAREHOUSE 등 회계 권한 미보유 시 사이드바 "회계" 그룹 자체 미노출
- 직접 URL 진입 시 `<RouteGuard requiredRole={["ACCOUNTANT","MASTER"]}>` → 403 페이지

---

## 3. 회고 가드 적용 체크리스트

- [x] `feedback_pr_qa_screenshots.md` — Designer mock 캡처 5종 의무 (PR 본문 인라인)
- [x] `feedback_uuid_no_user_visibility.md` — UUID 미노출, journalNo / accountCode 만 표시
- [x] `feedback_korean_commits.md` — 모든 산출 한국어 (label / placeholder / 안내문)
- [x] `feedback_role_naming_full.md` — 권한 풀네임 (ACCOUNTANT / MASTER) 일관 사용

---

## 4. 다음 단계

본 Designer 산출물 인용 → 5-team parallel 디스패치 (BE / FE / QA / DevOps + Designer 산출물 인용)
→ PM 통합 → PR #27 발행.
