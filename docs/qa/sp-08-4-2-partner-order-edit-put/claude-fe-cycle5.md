## frontend-engineer 사이클 5 리뷰 (head `86842c67`)

### 사이클 4 FE 결함 해소 표

| ID | 내용 | 결과 |
|---|---|---|
| FE-C1 | handleConflictReload deps 좁힘 (`refetch` 추출) | **해소** — L77 `const { refetch } = query`, L130 deps `[refetch, syncFormFromData]` 정합 |
| D-C2-2 | EditLine local `key` 필드 + `<tr key={line.key}>` | **해소** — L33 타입 `& { key: string }`, L35-40 `createEditLineKey()`, L424 `<tr key={line.key}>` 모두 확인 |

### 사이클 4.5 신규 fix 정합 검증

**DS Input.module.css `:read-only:not(:disabled)` cue**
L48-51: `.input:read-only:not(:disabled) { background-color: var(--color-bg-muted, #f8fafc); cursor: default; }` 정상 추가. `not(:disabled)` 가드로 비활성 상태와 시각 중복 없음.

**tokens.css success scale**
L38-41: `--color-success-50: #ecfdf5`, `--color-success-200: #a7f3d0`, `--color-success-500: #10b981`, `--color-success-700: #047857` 4종 `:root` 정의. `successBanner` 가 이 변수를 fallback 포함 인용 (L984-989).

**sales.module.css magic number → CSS class**
`.formFieldSpanAll`, `.cardMarginTop`, `.expandedComponentText` 추가. TSX 에서 className 사용 — 정합.

**사이클 2.5 회귀 검사**: `syncFormFromData` / `reloadSuccessMessage` / `'조회 중'` fallback 정상.

**Playwright T6**: L85-92 `T6: 409 reload 후 재저장 흐름 정적 계약` 4개 단언 모두 현 TSX 구현과 부합.

### 사이클 5 신규 발견

**FE-C5-1 잔존 inline style 2건 (P2)**
TSX L269, L281 에 `style={{ textAlign: 'left' }}` 가 남음. L269는 읽기 전용 테이블 `<td>`, L281은 `expandedComponentText` className 과 함께 중복 선언. `sales.module.css` 에 `.estTable td.leftAlign { text-align: left; }` 1개 추가 후 className 으로 치환 권고.

**FE-C5-2 `expandedComponentText` 클래스 font-size 값 (`11px`) 미토큰화 (P3)**
`.expandedComponentText { font-size: 11px; }` — `11px` 값 자체가 토큰 미사용. `--font-size-xs: 12px` 와 1px 차이지만 별도 값. `--font-size-2xs: 11px` 추가 또는 `--font-size-xs` 로 통일 권고.

### 종합

사이클 4 지적 전항목 해소, 회귀 없음. P2·P3 는 기능 차단이 아닌 CSS 일관성 이슈로 현 PR 머지 블록 없음. **APPROVE — 사이클 6 P2 처리 후 종결 권고.**

**frontend-engineer agent — 2026-05-17**
