# Components — Slice A 신규/확장 컴포넌트

## 0. 답습 매트릭스 (기존 21 컴포넌트 재사용)

| 화면 | 재사용 컴포넌트 | 비고 |
|---|---|---|
| 모든 화면 | `<AppLayout>` (sidebar + page header) | sales-polish-2 답습 |
| AccountTreePage | `<DataTable>`, `<Input>` (검색), `<Button>` | tree 펼침/접음은 row data prop 으로 처리 |
| JournalListPage | `<DataTable>`, `<Input>` (date), `<Select>` (status filter), `<Button>` (신규 분개) | 페이지 답습 |
| JournalFormPage | `<Card>` (헤더), `<DataTable>` (라인), `<Button>` (저장/확정), `<Modal>` (확정 confirm) | LineRow 답습 |
| JournalDetailPage | `<Card>`, `<DataTable>`, `<Button>` (역분개), `<Modal>` (역분개 confirm) | read-only |
| TrialBalancePage | `<DataTable>`, `<Select>` (월), `<Button>` (인쇄/엑셀) | 답습 |

신규 컴포넌트는 **4종**.

---

## 1. `<AccountCodeSelect>` (신규)

계정과목 검색 가능 select (autocomplete + 트리 표시).

### Props

```ts
interface AccountCodeSelectProps {
  /** 선택된 계정 코드 (6 digit) */
  value?: string;
  /** 변경 콜백 — 코드만 전달 (BE는 code 기반 lookup) */
  onChange: (code: string) => void;
  /** placeholder */
  placeholder?: string;          // default: "계정과목 선택"
  /** disabled (POSTED/REVERSED 분개 read-only) */
  disabled?: boolean;
  /** 카테고리 필터 (옵션 — 차변/대변 별 추천 계정 좁히기 용도) */
  filterCategory?: AccountCategory[];
  /** 잎 (isLeaf=true) 만 선택 가능 — default true */
  leafOnly?: boolean;
  /** 에러 상태 */
  error?: string;
  /** name (form 통합) */
  name?: string;
}
```

### 동작 사양

- **트리거**: 클릭 또는 focus 시 popover 열림 (모든 ~50 계정 트리)
- **검색**: 입력 시 코드 prefix 또는 계정명 부분일치 (대소문자 무시, debounce 200ms)
  - 예: `"임차"` → `805 임차료`
  - 예: `"805"` → `805 임차료`
- **표시**: 옵션 좌측 카테고리 dot (`--account-category-{*}` 토큰), 코드 (mono font), 계정명
- **키보드**: ↑/↓ 이동, Enter 선택, Esc 닫기
- **선택 후**: input 안에 `805 임차료` 형식 (코드 + 계정명, 코드는 mono)

### 표시 형식

```
┌─────────────────────────────────────┐
│ 805 임차료                       ▼ │
└─────────────────────────────────────┘
   ↓ (popover open)
┌─────────────────────────────────────┐
│ [검색: 임차_______________]         │
├─────────────────────────────────────┤
│ ⬤ 805  임차료              ← 강조  │
│ ⬤ 815  임차료(예금)                │
│                                     │
│ 카테고리: 판관비 (800)              │
└─────────────────────────────────────┘
```

### 접근성

- `role="combobox"` + `aria-expanded` + `aria-autocomplete="list"`
- 옵션 `role="option"` + `aria-selected`

---

## 2. `<JournalStatusBadge>` (신규)

분개 상태 (DRAFT / POSTED / REVERSED) 시각 구분 — `<SlipStatusBadge>` 패턴 답습.

### Props

```ts
interface JournalStatusBadgeProps {
  /** status enum */
  status: 'DRAFT' | 'POSTED' | 'REVERSED';
  /** size — 목록은 default, 상세 헤더는 large */
  size?: 'default' | 'large';
}
```

### 표시

| status | 라벨 | 색상 (토큰) | 추가 시각 |
|---|---|---|---|
| DRAFT | "DRAFT" | `--journal-status-draft-{bg,border,color}` | - |
| POSTED | "POSTED" | `--journal-status-posted-{bg,border,color}` | - |
| REVERSED | "REVERSED" | `--journal-status-reversed-{bg,border,color}` | `text-decoration: line-through` |

### 사이즈

- `default`: padding 2px 8px, font 12px (목록 표 셀)
- `large`: padding 4px 12px, font 14px, font-weight 600 (상세 헤더)

### 한국어 라벨 (옵션)

화면 컨텍스트에 따라 옵션:
- 영문 status (DRAFT/POSTED/REVERSED) — 기본 (개발/관리 친화)
- 한국어 (작성중/확정/역분개) — large 사이즈에 부가 표시 가능

→ Slice A 는 영문 status 만 사용 (slip-service `<SlipStatusBadge>` 와 일관).

---

## 3. `<MoneyInput>` (신규)

통화 콤마 자동 포맷팅 입력. KRW only (Q6 결정).

### Props

```ts
interface MoneyInputProps {
  /** 값 (number — 원 단위) */
  value: number | null;
  /** 변경 콜백 */
  onChange: (value: number | null) => void;
  /** placeholder */
  placeholder?: string;          // default: "0"
  /** 정렬 — 차/대 컬럼은 right */
  align?: 'left' | 'right';      // default: 'right'
  /** disabled (배타 입력 — 차변 입력 시 대변 disabled) */
  disabled?: boolean;
  /** 음수 허용 — default false (회계는 음수 미사용, reverse 분개도 양수) */
  allowNegative?: boolean;
  /** max (validation) */
  max?: number;                  // default: 999_999_999_999 (1조)
  /** 에러 상태 */
  error?: string;
  /** name */
  name?: string;
  /** onBlur (form validation trigger) */
  onBlur?: () => void;
}
```

### 동작 사양

- **입력**: 숫자 키만 허용 (콤마/소수점 등 자동 strip)
- **포맷팅**: `1234567` 입력 → display `1,234,567` (blur 시 정렬, focus 시 raw 유지)
  - PriceField 답습 (이미 콤마 패턴 존재)
- **단위**: 원 (KRW) 만 — placeholder 우측에 작은 "원" suffix 표시 (`--ink-tertiary`, font-size 12px)
- **빈 값**: `null` (BE는 BigDecimal nullable — 차/대 한쪽만 사용)
- **max**: 1조 초과 시 입력 차단 + tooltip "최대 999,999,999,999"

### 표시 형식

```
┌──────────────────────────┐
│             500,000  원  │   (focus 외 — 우정렬 + 콤마)
└──────────────────────────┘

┌──────────────────────────┐
│           500000      원 │   (focus — raw)
└──────────────────────────┘

┌──────────────────────────┐
│                  0    원 │   (disabled — 배타 입력 반대편)
└──────────────────────────┘
```

### CSS

- `font-feature-settings: var(--accounting-amount-font-feature)` (tabular-nums)
- `text-align: right` (default)
- 차변/대변 컬러 적용은 부모에서 (별도 prop 없음, 컬러는 col-debit/col-credit class 위임)

---

## 4. `<JournalLineRow>` (신규)

분개 라인 1행 (계정 select + 차/대 + 거래처 + 메모 + 삭제).
`<LineRow>` (sales) 답습 패턴이지만 회계 전용 컬럼 구성.

### Props

```ts
interface JournalLineRowProps {
  /** 라인 데이터 */
  line: JournalLineDraft;
  /** 라인 변경 콜백 */
  onChange: (next: JournalLineDraft) => void;
  /** 라인 삭제 콜백 */
  onDelete: () => void;
  /** 라인 번호 (#) — 1-based */
  lineNo: number;
  /** read-only (POSTED 분개 상세) */
  readonly?: boolean;
  /** validation 에러 */
  errors?: Partial<Record<keyof JournalLineDraft, string>>;
}

interface JournalLineDraft {
  accountCode: string | null;
  debitAmount: number | null;
  creditAmount: number | null;
  partnerId: string | null;       // UUID — 미노출
  partnerName: string | null;     // display only
  memo: string;
}
```

### Grid 레이아웃

```
grid-template-columns:
  24px         /* # 라인 번호 */
  180px        /* 계정 (AccountCodeSelect) */
  140px        /* 차변 (MoneyInput) */
  140px        /* 대변 (MoneyInput) */
  160px        /* 거래처 */
  1fr          /* 메모 */
  32px;        /* [✕] 삭제 */
gap: 8px;
align-items: center;
height: var(--row-h);    /* 40px */
```

### 배타 입력 규칙

- `debitAmount > 0` → `creditAmount` input disabled + 자동 `null`
- `creditAmount > 0` → `debitAmount` input disabled + 자동 `null`
- 양쪽 0 또는 null → 양쪽 활성

### read-only 모드

- 모든 input `disabled` + 배경 `--surface-subtle`
- `[✕]` 버튼 미노출
- AccountCodeSelect 는 표시 전용 (popover 비활성)

### 에러 표시

- 필드별 errors prop → 해당 셀 border `--state-danger`
- 빈 계정 / 차+대 모두 0 / 차+대 모두 입력 → row 자체 빨강 outline + tooltip 안내

---

## 5. (Reuse) `<DataTable>` 확장 — 트리 표시

기존 `<DataTable>` 에 트리 (펼침/접음) 표시 옵션 추가.
**별도 컴포넌트 신설 X** — DataTable row data 에 `treeLevel` / `expanded` / `hasChildren` prop 만 추가.

### 신규 prop

```ts
interface DataTableProps<T> {
  // ... existing
  treeColumn?: keyof T;          // 트리 indent 적용 컬럼 (예: 'name')
  treeLevelKey?: keyof T;        // row.treeLevel (0/1)
  treeExpandedKey?: keyof T;     // row.expanded
  treeHasChildrenKey?: keyof T;  // row.hasChildren
  onTreeToggle?: (row: T) => void;
}
```

### 시각 처리

- 트리 indent: `padding-left: calc(var(--space-3) * row.treeLevel)`
- ▸ / ▾ 아이콘: `hasChildren && treeLevel === 0` 일 때 좌측 표시
- 잎 (`treeLevel === 1`) 은 indent 만, 아이콘 없음

---

## 6. 컴포넌트 export 위치

```ts
// clients/web/design-system/src/index.ts
export * from './components/AccountCodeSelect';
export * from './components/JournalStatusBadge';
export * from './components/MoneyInput';
export * from './components/JournalLineRow';
```

스토리북 (`*.stories.tsx`) 4건 의무 (기존 컴포넌트 답습).

---

## 7. 컴포넌트 vs FE 구현 책임 분리

| 책임 | 위치 |
|---|---|
| design-system (디자인 시스템) | `<AccountCodeSelect>`, `<JournalStatusBadge>`, `<MoneyInput>`, `<JournalLineRow>` (presentation only) |
| FE accounting feature | `useAccounts()`, `useJournals()`, `useTrialBalance()` query hooks (TanStack Query) |
| FE pages | `AccountTreePage`, `JournalListPage`, `JournalFormPage`, `JournalDetailPage`, `TrialBalancePage` |
| 권한 | `<RouteGuard requiredRole={["ACCOUNTANT","MASTER"]}>` 답습 (auth-service 권한 enum) |
