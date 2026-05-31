## frontend-engineer 사이클 6 리뷰 (head `bb28b2e6`)

### 사이클 5 FE 결함 해소 표

| ID | 내용 | 상태 |
|---|---|---|
| FE-C5-1 | L269/L281 inline `style={{ textAlign: 'left' }}` 제거 + `.tdLeft` / `.expandedComponentText` className 전환 | 해소 — `sales.module.css` L237-239 `.estTable td.tdLeft { text-align: left; }` 신규 정의, TSX 양 지점 inline style 잔존 0건 |
| FE-C5-2 | `.expandedComponentText font-size: 11px` → 토큰화 | 해소 — L1006 `font-size: var(--font-size-xs, 11px)` 적용 |

### 사이클 6 신규 발견

**FE-C6-1 (중) — `--font-size-xs` fallback 값 토큰 정의와 불일치**

`tokens.css` L63 기준 `--font-size-xs: 12px`. `.expandedComponentText` fallback 은 `11px`. 토큰 주입 환경 12px, 미주입(fallback) 환경 11px 로 엇갈림. fallback 을 `12px` 통일 또는 의도가 11px 이면 별도 token (`--font-size-2xs: 11px`) 신설 필요.

**FE-C6-2 (경) — `className="numeric"` 전역 class 사용 (CSS module scope 외)**

`SalesPartnerOrderDetailPage.tsx` L272-273 `<td className="numeric">` 는 CSS module scope 거치지 않는 전역 클래스명. `sales.module.css` 의 `.numeric` 정의는 `.listTable td.numeric` (L435) 컨텍스트에만 존재, 현재 `estTable` tbody 셀에 우측 정렬 실질 미적용. `styles['numeric']` 또는 신규 `.estTable td.numericCol` 추가 권고.

### 종합

FE-C5-1/C5-2 해소 완료. 사이클 6 신규 결함 2건 중 FE-C6-2 는 납품가/소계 열 정렬 실패이므로 우선 수정 권장. FE-C6-1 은 토큰 합의 트래킹 필요.

**APPROVE 조건부** — FE-C6-2 수정 후 사이클 7 불필요 (단순 className 전환). FE-C6-1 은 별도 합의.

**frontend-engineer agent — 2026-05-17**
