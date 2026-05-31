## Claude 5-agent 사이클 1 통합 리뷰 (head `b67c9ed5`)

> tech-manager 통합 — BE/FE/Designer/QA/DevOps. SP-08-5-5 매입 전표 인쇄 양식. 사용자 6/7회차 정책.

### CI 상태

**20/20 SUCCESS** (Frontend Desktop + Playwright + GitGuardian + 백엔드 그룹 모두 PASS).

### 결함 종합 표 (CRITICAL → HIGH/MEDIUM → LOW)

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | Designer | **CRITICAL (D1)** | `PurchaseSlipPrintPage.tsx` 153~173 + `global.css` | 라인테이블 spec 8컬럼 (No./품목명/규격/수량/단가/공급가액/부가세/적요) vs 구현 6컬럼 — DOM/CSS 불일치. CSS `col-spec/col-vat/col-supply/col-memo` 클래스만 정의, `<th>/<td>` 미사용. **수정**: `<thead>`에 4개 컬럼 추가 + 데이터 행 td 매핑 + `<tfoot>` colSpan 갱신 |
| 2 | DevOps | MEDIUM (D2) | PR body spec | 동일 결함 — Designer D1 의 연동 |
| 3 | QA | MEDIUM (D1) | 동일 | `feedback_print_design_iteration` 정상 범위 — iteration 2회차 fix |
| 4 | BE | **MEDIUM (B-01)** | `SlipDetailResponse.java` vs `slip.ts SlipDetail.ownerFullName` | `ownerFullName` 필드 BE 응답 부재 → FE `slip.ownerFullName ?? '-'` 항상 `-` 표시 (담당자 영역 누락). **수정**: BE `SlipDetailResponse` 에 `ownerFullName` 추가 (createdBy fallback 또는 user-service lookup) |
| 5 | FE | MEDIUM (D3) | `global.css` `@media print` 블록 | `tr { page-break-inside: avoid }` + `thead { display: table-header-group }` + `tfoot { display: table-footer-group }` + `-webkit-print-color-adjust: exact` 누락 — 배경색 인쇄 안 됨 + tbody 페이지 경계 잘림 |
| 6 | Designer | HIGH (D3) | `@page :first` 외 전체 `@page` 선언 누락 | 다중 페이지 인쇄 시 2페이지 이후 용지 회귀. `@page { size: A4 portrait; margin: 0; }` 추가 |
| 7 | Designer | HIGH (D4) | `purchase-print-totals` `align-self: flex-end` | `@media print` 만 — 화면 미리보기 시 좌측 정렬. base 스타일 또는 `margin-left: auto` |
| 8 | Designer | HIGH (D2) | 인쇄 명조 폰트 | legacy GAS 분위기 — `@media print { font-family: 'Batang', 'Malgun Gothic', serif }` (Edge 캡처 비교 후 결정 — iteration) |
| 9 | QA | MEDIUM (D2) | 헤더 레이아웃 | spec 중앙 vs 구현 좌우 2-panel — iteration 정합 |
| 10 | QA | MEDIUM (D3) | 거래처 2열 그리드 미구현 | spec 좌(거래처명/사업자번호/대표자/전화) + 우(입고창고/담당자/주소) 7 필드 vs 구현 단열 3 필드 |
| 11 | Designer | MEDIUM (D5) | `<tfoot>` colSpan | D1 fix 와 연동 (6→7→8 colSpan 갱신) |
| 12 | Designer | MEDIUM (D6) | `.purchase-print-page` font-size | 기본 9pt 선언 부재 (parent 11pt 상속) |
| 13 | FE | MEDIUM (D2) | JSDoc-구현 불일치 | `createdAt/By` 4 필드 명시 vs 미구현 |
| 14 | FE | MEDIUM (D1) | warehousesQuery 에러 처리 | 로딩/실패 시 `-` fallback — 명시적 에러 또는 로딩 |
| 15 | FE | LOW (D8) | Playwright PRINT_COMPONENT_CANDIDATES | 실제 경로 `print/PurchaseSlipPrintPage.tsx` 누락 — 잘못된 파일에 PASS 위험 |
| 16 | FE | LOW (D4/D5/D6/D7) | RoleGuard / PAGE_LINE_LIMIT / 검수일자 dev-report / line UUID key | 후속 슬라이스 가능 |
| 17 | BE | LOW (B-02) | warehouse name snapshot | 별도 API 호출 — P2 이월 |
| 18 | Designer | MINOR (D7/D8) | 30행 분할 + 헤더 정렬 | iteration |
| 19 | DevOps | P3 (D1/D4) | components.md 문서 불일치 + listWarehouses | 후속 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (MEDIUM B-01 ownerFullName) |
| FE | 사이클 2 필요 (MEDIUM 3 + LOW 5) |
| Designer | **CHANGES REQUESTED** (CRITICAL D1 + HIGH 3 + MEDIUM/MINOR) |
| QA | 사이클 2 필요 (MEDIUM 3 — iteration 정합) |
| DevOps | 사이클 2 필요 (P2 D2 — Designer D1 연동) |

### TM 결정 (사용자 6/7회차 정책 — 1c 일괄 fix iteration 2)

**1c Claude fix 후보 (CRITICAL + HIGH/MEDIUM 우선)**:

1. **D1 CRITICAL** (Designer/QA/DevOps): 라인테이블 8컬럼 — TSX `<thead>` + `<tbody>` + `<tfoot>` + global.css `col-*` 클래스 일관 수정
2. **B-01 MEDIUM** (BE): `SlipDetailResponse` `ownerFullName` 필드 추가 + Service `from(slip)` 매핑 (BaseEntity createdBy fallback 또는 user-service lookup)
3. **D3 MEDIUM** (FE): `@media print` 블록 4 선언 추가 (`page-break-inside`/`display: table-header-group`/`table-footer-group`/`color-adjust: exact`)
4. **Designer D3 HIGH**: `@page { size: A4 portrait; margin: 0; }` 전체 선언 추가
5. **Designer D4 HIGH**: `.purchase-print-totals` `margin-left: auto` base 스타일
6. **Designer D2 HIGH**: 인쇄 명조 폰트 — Edge 캡처 비교 어려우므로 보수적으로 `Pretendard` 유지 + iteration 후속 시 결정 (1c skip 가능)
7. **iteration MEDIUM** (QA D2/D3 + Designer D5/D6): 헤더 중앙 정렬 + 거래처 2열 그리드 + colSpan + font-size 9pt
8. **FE D2 MEDIUM**: JSDoc 정정 (createdAt/By 제거 또는 명시)
9. **FE D8 LOW**: Playwright PRINT_COMPONENT_CANDIDATES 경로 추가
10. **PNG 4장 재생성**: D1 8컬럼 + 헤더 + 거래처 2열 그리드 반영

**1c skip (후속 슬라이스 또는 P2 이월)**:
- FE D1 (warehousesQuery 로딩/실패 처리) — 후속
- FE D4 (RoleGuard) — 기존 InboundView 동일 컨벤션
- FE D5 (PAGE_LINE_LIMIT 30 — 후속 다중 페이지 분할)
- FE D6/D7 (dev-report 검수일자/line UUID key 주석)
- BE B-02 (warehouse name snapshot) — P2
- DevOps D1/D4 (components.md 동기화/listWarehouses) — 후속

**CI green 유지 확인** (head B push 후) + **Codex 2a review** 진행.

**tech-manager — 2026-05-18**
