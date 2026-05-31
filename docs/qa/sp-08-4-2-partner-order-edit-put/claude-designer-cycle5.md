## designer 사이클 5 리뷰 (head `86842c67`)

### 사이클 4 Designer 잔존 해소 표

| 항목 | 내용 | 판정 |
|------|------|------|
| D-C2-2 (line key) | `EditLine` 타입에 `key: string` 추가 + `createEditLineKey()` + `toEditLines()` 적용 — table row 재조합 시 input 포커스 유실 없음 | 해소 |
| Designer P1 (readOnly cue) | `Input.module.css` `.input:read-only:not(:disabled)` — `background-color: var(--color-bg-muted, #f8fafc)` + `cursor: default` 적용. readOnly 필드와 편집 필드 시각 분리 확인 | 해소 |
| Designer Nit (--color-success-*) | `tokens.css` L38-41 — `--color-success-50/200/500/700` scale 4종 정의 확인. `successBanner` 가 `var(--color-success-50/200/700)` 정상 인용 | 해소 |
| D-C1-1~3 (inline magic style) | `formFieldSpanAll / cardMarginTop / expandedComponentText` 3종 CSS 클래스 분리 완료. `className` 조합 방식으로 전환 | **부분 해소** — 하단 신규 발견 참조 |

### 사이클 5 신규 발견

| 등급 | 위치 | 내용 |
|------|------|------|
| Nit | `SalesPartnerOrderDetailPage.tsx` L269 | `<td style={{ textAlign: 'left' }}>` — 품목명 셀에 inline style 잔존. 사이클 4 inline magic 제거 범위에서 누락. `estTable td` 기본 `text-align: center` 대비 품목명 left-align 은 타당하나, `sales.module.css` 에 `.tdLeft` 클래스 추가하여 일관성 유지 권장. |
| Nit | `SalesPartnerOrderDetailPage.tsx` L281 | `<td className={styles['expandedComponentText']} style={{ textAlign: 'left' }}>` — `expandedComponentText` 클래스 적용과 동시에 inline `textAlign` 잔존. 클래스 내에 `text-align: left` 를 직접 포함하면 inline style 불필요. |

두 Nit 모두 동일한 `<tbody>` 조회 전용 행의 left-align 처리 누락으로, 기능·접근성 영향은 없음. CSS 모듈 일관성(design-system 원칙: inline style 금지) 위반에 해당하므로 사이클 6 이내 해소 권장.

### 종합

사이클 4.5 major 결함(D-C2-2 / P1 / Nit / D-C1-1~3)은 전원 설계 의도에 맞게 해소됨. `--color-success-*` token scale 정합, `readOnly` 시각 분리, `EditLine` key 안정성 — 세 항목 모두 production 품질 기준 충족.

신규 발견 2건은 모두 Nit 등급(조회 전용 `td` 2개의 inline `textAlign`)이며 기능·a11y 영향 없음. 해당 항목을 사이클 6 에서 `sales.module.css` 에 `.tdLeft { text-align: left }` 한 줄 추가로 일괄 제거 가능.

**APPROVE** (Nit 2건 — 사이클 6 cleanup 권장, 머지 블로킹 아님)

**designer agent — 2026-05-17**
