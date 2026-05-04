# Tokens — Slice A 신규 토큰

기존 `clients/web/design-system/src/tokens/tokens.css` 의 **3 그룹 신규 토큰** 추가.
기존 토큰은 그대로 유지 (visual regression 0).

---

## 1. 7-그룹 계정 카테고리 색상

한국 일반기업회계기준 (`project_korean_accounting.md`) 기준 7-그룹 색상 매핑.
회계 화면 (계정 트리 dot, 시산표 그룹 헤더, 분개 라인 hint) 전반에 사용.

```css
:root {
  /* ==========================================================================
   * accounting-slice-A — 7-그룹 계정 카테고리 색상
   * 한국 일반기업회계기준 분류 기반 (자산/부채/자본/매출/매출원가/판관비/기타)
   * ========================================================================== */

  /* 자산 (100) — 파랑: brand 계열, 실물/유동성 강조 */
  --account-category-asset:        #1E40AF;        /* dot, header text */
  --account-category-asset-bg:     #DBEAFE;        /* 그룹 헤더 배경 (subtle) */
  --account-category-asset-border: #93C5FD;

  /* 부채 (200) — 빨강: 의무/지급 강조 (state-danger 계열) */
  --account-category-liability:        #B91C1C;
  --account-category-liability-bg:     #FEE2E2;
  --account-category-liability-border: #FCA5A5;

  /* 자본 (300) — 녹: 안정/소유 강조 (state-success 계열) */
  --account-category-equity:        #047857;
  --account-category-equity-bg:     #D1FAE5;
  --account-category-equity-border: #6EE7B7;

  /* 매출 (400) — 주황: 수익 / 활성 (warning 톤이지만 positive) */
  --account-category-revenue:        #C2410C;
  --account-category-revenue-bg:     #FFEDD5;
  --account-category-revenue-border: #FDBA74;

  /* 매출원가 (500) — 회색: 중립 / 원가 */
  --account-category-cogs:        #4B5563;
  --account-category-cogs-bg:     #F3F4F6;
  --account-category-cogs-border: #D1D5DB;

  /* 판관비 (800) — 보라: 운영/관리 비용 */
  --account-category-sga:        #6D28D9;
  --account-category-sga-bg:     #EDE9FE;
  --account-category-sga-border: #C4B5FD;

  /* 영업외/법인세 (900) — 검정: 기타/조정 */
  --account-category-other:        #1F2937;
  --account-category-other-bg:     #F3F4F6;
  --account-category-other-border: #9CA3AF;
}
```

### 사용 예

```css
/* 트리 노드 좌측 dot */
.tree-node[data-category="ASSET"]::before {
  background: var(--account-category-asset);
}
.tree-node[data-category="ASSET"] .tree-group-header {
  background: var(--account-category-asset-bg);
  border-left: 3px solid var(--account-category-asset);
  color: var(--account-category-asset);
}
```

### enum 매핑

| BE enum (`AccountCategory`) | 그룹 코드 | 토큰 prefix |
|---|---|---|
| `ASSET` | 100 | `--account-category-asset` |
| `LIABILITY` | 200 | `--account-category-liability` |
| `EQUITY` | 300 | `--account-category-equity` |
| `REVENUE` | 400 | `--account-category-revenue` |
| `COGS` | 500 | `--account-category-cogs` |
| `SGA` | 800 | `--account-category-sga` |
| `OTHER` | 900 | `--account-category-other` |

---

## 2. Journal Status 색상 (3 variants)

`<JournalStatusBadge>` 신규 컴포넌트 색상.
`<SlipStatusBadge>` group/tier 패턴 답습 (편집/처리/취소 시각 구분).

```css
:root {
  /* ==========================================================================
   * accounting-slice-A — Journal Status (DRAFT / POSTED / REVERSED)
   * SlipStatusBadge group/tier 패턴 답습 (editable/process/canceled 매핑)
   * ========================================================================== */

  /* DRAFT — 회색 / 편집 가능 (slip editable tier-1 매핑) */
  --journal-status-draft:        var(--color-text-muted);     /* #4D5562 */
  --journal-status-draft-bg:     var(--color-bg-muted);       /* #EDF0F4 */
  --journal-status-draft-border: var(--color-border);         /* #D6DCE3 */

  /* POSTED — 녹 / 확정 (slip delivery tier-3 매핑 — strong success) */
  --journal-status-posted:        #047857;
  --journal-status-posted-bg:     #D1FAE5;
  --journal-status-posted-border: #6EE7B7;

  /* REVERSED — 회색 + line-through (slip canceled 매핑) */
  --journal-status-reversed:        var(--color-text-subtle); /* #6B7280 */
  --journal-status-reversed-bg:     var(--color-bg-muted);
  --journal-status-reversed-border: var(--color-border);
  /* + text-decoration: line-through */
}
```

### Badge 사이즈

기존 `<Badge>` / `<SlipStatusBadge>` 와 동일:
- `padding: 2px var(--space-2)` (4px / 8px)
- `border-radius: var(--radius-full)`
- `font-size: var(--font-size-xs)` (12px)
- `font-weight: var(--font-weight-medium)` (500)

---

## 3. 차변 / 대변 시각 구분

한국 회계 컨벤션 — 차변(좌, 검정) / 대변(우, 파랑).
분개 입력 폼, 분개 상세, 시산표 모두 일관 적용.

```css
:root {
  /* ==========================================================================
   * accounting-slice-A — 차변 / 대변 시각 구분
   * 한국 회계 관행 — 차변(검정/좌) / 대변(파랑/우)
   * ========================================================================== */

  /* 차변 (Debit) — 기본 ink color 답습 */
  --accounting-debit-color:    var(--ink-primary);         /* #1A1F2E */
  --accounting-debit-bg:       transparent;
  --accounting-debit-bg-hover: var(--surface-hover);

  /* 대변 (Credit) — brand action 답습 (자산 카테고리와 동일 — 의도적) */
  --accounting-credit-color:    var(--action-brand);       /* #1E40AF */
  --accounting-credit-bg:       transparent;
  --accounting-credit-bg-hover: var(--action-brand-subtle);/* #DBEAFE */

  /* 차/대 합계 일치/불일치 */
  --accounting-balance-ok:    var(--state-success);        /* #10B981 */
  --accounting-balance-ok-bg: var(--state-success-bg);     /* #D1FAE5 */
  --accounting-balance-ng:    var(--state-danger);         /* #EF4444 */
  --accounting-balance-ng-bg: var(--state-danger-bg);      /* #FEE2E2 */

  /* 숫자 셀 — 우정렬 + tabular-nums 강제 */
  --accounting-amount-font-feature: "tnum" 1, "lnum" 1;
  --accounting-amount-text-align:   right;
  --accounting-amount-padding-x:    var(--space-3);        /* 12px */
}
```

### 사용 예

```css
.journal-line .col-debit {
  color: var(--accounting-debit-color);
  text-align: var(--accounting-amount-text-align);
  font-feature-settings: var(--accounting-amount-font-feature);
}
.journal-line .col-credit {
  color: var(--accounting-credit-color);
  text-align: var(--accounting-amount-text-align);
  font-feature-settings: var(--accounting-amount-font-feature);
  font-weight: var(--font-weight-medium);
}
.journal-totals-row.balance-ok {
  background: var(--accounting-balance-ok-bg);
  color: var(--accounting-balance-ok);
}
.journal-totals-row.balance-ng {
  background: var(--accounting-balance-ng-bg);
  color: var(--accounting-balance-ng);
}
```

---

## 4. 인쇄 토큰 (sales-polish-2 답습)

분개장 / 시산표 인쇄는 기존 `--print-text-base / --print-thead-bg` 토큰 재사용.
신규 인쇄 토큰 없음 (`print-spec.md` 참조).

---

## 5. 토큰 적용 위치 (FE 구현 가이드)

| 토큰 | 적용 위치 |
|---|---|
| `--account-category-{*}` | AccountTreePage 그룹 헤더 dot/배경, TrialBalancePage 그룹 행, JournalForm AccountCodeSelect option label hint |
| `--journal-status-{*}` | `<JournalStatusBadge>` 컴포넌트 내부 only |
| `--accounting-debit-color` / `-credit-color` | JournalLineRow / JournalDetail 라인 표 셀, TrialBalancePage 차/대 컬럼 |
| `--accounting-balance-{ok,ng}` | JournalForm 합계 행, TrialBalancePage 총합계 행 |
| `--accounting-amount-font-feature` | 모든 금액 표시 셀 (강제 tabular-nums) |

---

## 6. 다크 테마

본 슬라이스 신규 토큰 다크 변형은 **Phase 2+ 정식 다크 마이그레이션 시 일괄 처리**.
현 시점에선 light 만 정의 (기존 토큰 다크 stub 도 동일 정책).
