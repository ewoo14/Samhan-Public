# data-testid 누락 백로그 — frontend-engineer agent

매뉴얼 캡처 (`tools/manual-capture/`) 가 정확한 박스/화살표 어노테이션을 합성하려면 desktop / mobile-staff 의 핵심 element 에 `data-testid` 속성이 필요하다. 누락 시 Playwright 의 `boundingBox()` 가 selector 를 해석하지 못해 어노테이션이 skip 된다.

본 문서는 `capture.config.json` 의 `screens[]` 가 참조하는 selector 의 누락 현황을 추적한다. frontend-engineer agent 가 슬라이스별로 추가 후 본 문서를 갱신.

## 우선순위 1 — Stage 1 캡처 화면 (즉시 필요)

### `LoginPage` (`clients/desktop/src/renderer/routes/LoginPage.tsx`)

| selector | element | 상태 |
|----------|---------|------|
| `[data-testid="login-id-input"]` | 로그인 ID `Input` | 누락 |
| `[data-testid="login-password-input"]` | 비밀번호 `Input` | 누락 |
| `[data-testid="login-submit-button"]` | 로그인 `Button` (type=submit) | 누락 |

**fallback**: `capture-desktop.js` 의 `performLogin` 은 `input[type="text"]` / `input[type="password"]` / `button[type="submit"]` 으로 폴백 — Stage 1 캡처는 동작하나 명시적 testid 권장.

### `AppLayout` (`clients/desktop/src/renderer/components/AppLayout.tsx`)

| selector | element | 상태 |
|----------|---------|------|
| `[data-testid="sidebar-sales"]` | `<NavLink to="/sales">판매조회</NavLink>` | 누락 |
| `[data-testid="sidebar-warehouses"]` | `<NavLink to="/warehouses">창고</NavLink>` | 누락 |
| `[data-testid="sidebar-purchases"]` | `<NavLink to="/purchases">구매조회</NavLink>` | 누락 |
| `[data-testid="sidebar-transfers"]` | `<NavLink to="/transfers">재고이동</NavLink>` | 누락 |
| `[data-testid="sidebar-link-dispatch"]` | `<NavLink to="/sales/link-dispatch">링크발송</NavLink>` | 누락 |
| `[data-testid="sidebar-accounting-accounts"]` | 회계 그룹 — 계정과목 (ACCOUNTANT/MASTER) | 누락 |
| `[data-testid="sidebar-accounting-journals"]` | 회계 그룹 — 분개장 | 누락 |
| `[data-testid="sidebar-accounting-balances"]` | 회계 그룹 — 시산표 | 누락 |
| `[data-testid="sidebar-logout"]` | 우상단 로그아웃 `Button` | 누락 |
| `[data-testid="header-user-name"]` | 우상단 사용자명 표시 | 누락 |
| `[data-testid="header-page-title"]` | 동적 페이지 제목 (`usePageTitleStore`) | 누락 |

## 우선순위 2 — Stage 2 캡처 화면 예정

### `SlipListPage` (출고전표 / 입고전표 목록)

| selector | element |
|----------|---------|
| `[data-testid="slip-list-table"]` | 전표 목록 table |
| `[data-testid="slip-list-add-button"]` | 신규 작성 버튼 |
| `[data-testid="slip-list-search-input"]` | 검색 input |
| `[data-testid="slip-list-status-filter"]` | 상태 필터 select |

### `SlipFormPage` (전표 작성)

| selector | element |
|----------|---------|
| `[data-testid="slip-form-partner-select"]` | 거래처 검색/선택 |
| `[data-testid="slip-form-warehouse-select"]` | 창고 select |
| `[data-testid="slip-form-line-add"]` | 품목 라인 추가 |
| `[data-testid="slip-form-submit"]` | 등록 버튼 |

### `WarehousesPage`

| selector | element |
|----------|---------|
| `[data-testid="warehouse-list-table"]` | 창고 목록 table |
| `[data-testid="warehouse-add-button"]` | 창고 추가 버튼 |
| `[data-testid="warehouse-edit-button"]` | row 별 편집 버튼 (`[data-testid="warehouse-row-{id}-edit"]`) |

### `SalesEstimateListPage` / `EstimateLegacyWebviewPage`

| selector | element |
|----------|---------|
| `[data-testid="estimate-list-new-button"]` | 견적서 신규 작성 |
| `[data-testid="estimate-legacy-webview"]` | webview placeholder |
| `[data-testid="estimate-print-button"]` | 인쇄 버튼 |

### accounting-slice-A — `JournalListPage` / `JournalFormPage` / `TrialBalancePage`

| selector | element |
|----------|---------|
| `[data-testid="journal-list-table"]` | 분개장 목록 |
| `[data-testid="journal-form-debit-line"]` | 차변 라인 input |
| `[data-testid="journal-form-credit-line"]` | 대변 라인 input |
| `[data-testid="journal-form-confirm"]` | 확정 버튼 |
| `[data-testid="trial-balance-month-select"]` | 월 선택 |
| `[data-testid="trial-balance-table"]` | 시산표 table |

## 우선순위 3 — mobile-staff (Expo)

### Login + Drawer

| selector | element |
|----------|---------|
| `[data-testid="mobile-login-id"]` | 모바일 로그인 ID |
| `[data-testid="mobile-login-password"]` | 모바일 로그인 PW |
| `[data-testid="mobile-login-submit"]` | 로그인 버튼 |
| `[data-testid="mobile-drawer-toggle"]` | ▼ 페이지 메뉴 토글 |
| `[data-testid="mobile-drawer-menu"]` | 13 메뉴 dropdown |

(Stage 2 에서 mobile-staff 화면 정의가 capture.config.json 에 추가될 때 본 섹션 갱신.)

## 추가 가이드

- `data-testid` 값은 **kebab-case**, **slice-prefix** (`sidebar-*`, `slip-list-*`, `warehouse-*`).
- React Testing Library 의 `getByTestId` 와 호환 — 향후 단위 테스트에서도 재사용.
- Designer 가 wireframe 의 element 명을 그대로 testid 로 채택 (디자인 시스템 일관성).
- 누락 시 `capture-desktop.js` 가 `[warn] selector 미발견` 출력 — CI 에서 경고 수집 가능 (Stage 3).

## 갱신 절차

1. frontend-engineer agent 가 슬라이스별 PR 에서 testid 추가
2. 본 문서 표의 "상태" 컬럼을 "완료 (PR #N)" 로 갱신
3. 매뉴얼 캡처 작성자가 `node capture-desktop.js` 재실행하여 어노테이션 검증
