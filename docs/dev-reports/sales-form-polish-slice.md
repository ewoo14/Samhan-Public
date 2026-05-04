# Sales Form UX Polish 슬라이스 — 개발 리포트

본 리포트는 5-team 신규 패턴 (Designer + BE + FE + QA + DevOps) 의 첫 디스패치 결과 누적 문서입니다.

> 본 슬라이스는 사용자 (개발책임자) 의 명시적 강조 — **"디자인 좀 잘 부탁해"** — 를 1급 요구사항으로 삼습니다.

---

## 0. 개발책임자 결정 사항 (Plan 단계)

### Q1~Q6 결정

| 번호 | 질문                                  | 결정 |
| ---- | ------------------------------------- | ---- |
| Q1   | 라인 1·2·3 넘버링 + 행 클릭 선택      | 채택 (이카운트 패턴) |
| Q2   | 라인 삭제 버튼 (현재 추가만 있음)     | 채택 (행 끝 ⊗ 아이콘) |
| Q3   | drag-and-drop 라이브러리              | A: `@dnd-kit/sortable` 채택 (react-beautiful-dnd 는 maintenance mode) |
| Q4   | 재고조회 batch endpoint               | A: `POST /inventory/balances/batch` (선택 N건 1회 호출) |
| Q5   | 디자인 토큰 적용 범위                 | B: 본 슬라이스 화면만 (SlipFormPage / StockBalanceModal / DispatchView) |
| Q6   | 재고조회 표시 방식                    | A: 모달 (page navigation 아님 — 작업 흐름 유지) |

### 추가 결정

- 작업지시서 (DispatchView): **세로 A4 (portrait)** 로 정정. 이미지 2 충실 반영.
- 디자인 철학: 모던 미니멀 + dense (Notion / Linear / 이카운트 영감)
- `react-hot-toast` 도입 추천 (행 삭제 undo)

---

## 1. Designer (Team-Sales-Form-Polish Designer)

### 1.1 산출물

| 파일 | 내용 |
| --- | --- |
| `docs/design/sales-form-polish-slice/README.md`         | 디자인 철학 + 영감 + 원칙 |
| `docs/design/sales-form-polish-slice/wireframes.md`     | SlipFormPage / StockBalanceModal / DispatchView ASCII art + mermaid |
| `docs/design/sales-form-polish-slice/tokens.md`         | 디자인 토큰 spec (현재 vs 신규 비교 + 적용 우선순위) |
| `docs/design/sales-form-polish-slice/components.md`     | 신규/변경 컴포넌트 4종 spec (LineRow / StockBalanceModal / DragHandle / DispatchView) |
| `docs/design/sales-form-polish-slice/ux-flow.md`        | 사용자 시나리오 + 키보드 단축키 + interaction 디테일 |
| `docs/design/sales-form-polish-slice/print-spec.md`     | A4 portrait/landscape 인쇄 spec (mm 단위) |

### 1.2 디자인 철학 핵심

- **모던 미니멀** + **dense ERP** — Notion / Linear 영감 + 이카운트 작업 흐름
- **4-base spacing scale** (4 / 8 / 12 / 16 / 24 / 32 / 48)
- **Pretendard** 한국어 typography + tabular-nums (숫자 자릿수 정렬 의무)
- **신규 색상 alias** 추가 (기존 토큰 그대로 유지, 본 슬라이스 화면만 신규 적용)
- **subtle elevation** — `0 1px 3px rgba(0,0,0,0.04)` 카드 / `0 8px 24px rgba(0,0,0,0.12)` 모달

### 1.3 핵심 컴포넌트 spec 요약

#### `<LineRow>` (신규)
- 9-column CSS grid: 체크박스 / drag handle / # / 모델명 / 품목명 / 수량 / 단가 / 합계 / 삭제
- 행 높이 40px, 자동 라인 번호 1·2·3, 수량/단가/합계 우측 정렬 + tabular-nums
- 5 states: default / hover / selected / dragging / error
- 키보드 단축키: Cmd+↑/↓ (drag 대안), Cmd+Backspace (삭제), Space (체크)

#### `<StockBalanceModal>` (신규)
- max-width 720px, max-height 80vh, overlay rgba(0,0,0,0.6)
- focus trap + Esc 닫기 + body scroll lock
- 셀 렌더 규칙: `> 0` 일반, `= 0` dim, `null` `-` dim
- batch endpoint: `POST /inventory/balances/batch`

#### `<DragHandle>` (신규)
- 24×40 영역 + Braille `⠿` 또는 lucide `<GripVertical />`
- cursor: grab → grabbing
- `@dnd-kit/sortable` listeners 부착

#### `<DispatchView>` (변경 — 가로→세로)
- `@page { size: A4 portrait; margin: 12mm }`
- 5칸 담당 박스 grid + 모델명/품목명 2줄 셀 + 60mm×40mm 서명 박스
- Pretendard 11pt 본문, thead 배경 `#F0F0F0`

### 1.4 신규 디자인 토큰 (간단 요약)

```css
--surface-app: #FAFBFC;
--surface-card: #FFFFFF;
--surface-selected: #EFF6FF;
--line-default: #E1E5EA;
--line-focus: #3B82F6;
--ink-primary: #1A1F2E;
--action-brand: #1E40AF;
--row-h: 40px;
--modal-max-w: 720px;
```

상세 — `docs/design/sales-form-polish-slice/tokens.md` 참조.

### 1.5 의존성 추천 (FE 가 설치)

- `@dnd-kit/core` ^6.1.0
- `@dnd-kit/sortable` ^8.0.0
- `@dnd-kit/utilities` ^3.2.2
- `react-hot-toast` (행 삭제 undo, optional)

### 1.6 FE 가 인용해야 할 핵심 spec (top 5)

1. `components.md` § 1 — `<LineRow>` props/states 표
2. `components.md` § 2 — `<StockBalanceModal>` 모달 구조
3. `tokens.md` § 1 — 신규 색상/spacing/dimension alias (적용 범위 SlipFormPage 한정)
4. `ux-flow.md` § 1.2 — drag-and-drop 시나리오
5. `print-spec.md` § 2 — DispatchView 세로 A4 CSS

### 1.7 검증 (QA 협조 필요)

- [ ] 라인 컬럼 우측 정렬 (수량/단가/합계) — tabular-nums 적용 확인
- [ ] 행 hover / selected 색상 일치
- [ ] drag 중 opacity 0.6 적용
- [ ] 재고 조회 모달 max-width 720px / overlay 60% black
- [ ] DispatchView 인쇄 시 A4 portrait, 여백 12mm
- [ ] 작업지시서 서명 박스 정확히 60mm × 40mm
- [ ] 모든 텍스트 Pretendard fallback (Noto Sans KR)

---

## 2. BE

(BE agent 가 채움)

> 예상 작업: `POST /inventory/balances/batch` endpoint 구현, productIds 배열 → 창고별 재고 + 합계 매핑 응답.

---

## 3. FE

### 3.1 산출물 요약

본 FE 작업은 Designer 가 작성한 6 spec 파일을 모두 정독한 후 항목별로 1:1 매핑하여 구현했습니다. spec 충실도 높은 polish 결과물.

| 영역 | 산출물 |
| --- | --- |
| 디자인 토큰 | `clients/web/design-system/src/tokens/tokens.css` 끝에 신규 alias 추가 (기존 토큰 무수정) |
| 신규 컴포넌트 | `<DragHandle>`, `<LineRow>`, `<LineTableHeader>`, `<StockBalanceModal>` (4종, 각 module.css + stories) |
| 메인 화면 | `SlipFormPage.tsx` 큰 리팩토링 — table grid + dnd-kit + 체크박스 + 합계 + 모달 트리거 |
| 인쇄 화면 | `DispatchView.tsx` 가로→세로 A4 정정 (이미지 2 충실 반영) |
| 전역 CSS | `clients/desktop/src/renderer/styles/global.css` 에 `.sales-form-polish` 섹션 + `.dispatch-page` 세로 spec 추가 |
| API 클라이언트 | `inventory.ts` 에 `fetchStockBalanceBatch` 추가 / `mock.ts` 에 batch endpoint mock 추가 |

### 3.2 Designer spec 매핑

| Designer spec 항목 | 구현 결과 |
| --- | --- |
| `tokens.md` § 1 신규 alias 7 그룹 | `tokens.css` 끝에 `:root { --surface-* / --line-* / --ink-* / --action-brand-* / --state-* / --space-row-* / --row-h / --modal-* / --motion-* }` 1:1 추가 |
| `components.md` § 1 `<LineRow>` (9-col grid, 5 states, 키보드) | `LineRow.tsx` + `LineRow.module.css` — 9-col grid 정확히 매치, default/hover/selected/dragging/error 5 states 모두 구현, `role="row"` + `aria-selected`, ARIA labels |
| `components.md` § 2 `<StockBalanceModal>` (max-w 720, focus trap) | `StockBalanceModal.tsx` — 720px max-w + 80vh max-h + overlay 0.6, focus trap + ESC + body scroll lock + backdrop click, 4 states (loading/empty/error/success), 셀 렌더 규칙 (>0 / =0 dim / null `-` dim) |
| `components.md` § 3 `<DragHandle>` (24×40, ⠿) | `DragHandle.tsx` — Braille `⠿` glyph, cursor grab→grabbing, dnd-kit attributes/listeners/setActivatorNodeRef 주입형 (design-system 패키지가 dnd-kit 에 결합되지 않음) |
| `components.md` § 4 `<DispatchView>` 세로 A4 | `DispatchView.tsx` 새 layout — 좌측 SAMSUNG/거래처/일련번호 + 우측 5칸 담당 grid (담당부서/담당자/출고인/검수인/결재 full) + 모델명+품목명 2줄 셀 + 60mm×40mm 서명 박스 |
| `wireframes.md` § 1 SlipFormPage 헤더+라인+합계 layout | `SlipFormPage.tsx` 전면 리팩토링 — 헤더 카드 (3+2 grid) + 라인 카드 (toolbar + table + 합계) + 저장 bar 분리 |
| `wireframes.md` § 1.4 헤더 그리드 (3 col / 2 col 1:2) | `.sfp-form-grid--3` / `.sfp-form-grid--2` (1fr 2fr) |
| `wireframes.md` § 2 모달 ASCII | `StockBalanceModal` 좌측 모델명 cell + 가운데 창고 columns + 우측 합계 columns matrix |
| `ux-flow.md` § 1.2 drag-and-drop | `@dnd-kit/sortable` DndContext + SortableContext + useSortable + arrayMove, 마우스 PointerSensor (4px activationConstraint) + KeyboardSensor (sortableKeyboardCoordinates) |
| `ux-flow.md` § 3 모델명 lookup interaction | onBlur trigger (Enter 키도 blur trigger) + 우측 spinner (`<Spinner size="xs">`) + 404 시 행 아래 빨간 메시지 + retry on re-blur |
| `ux-flow.md` § 4 재고조회 trigger | 헤더 [재고조회] 버튼 — 0 disabled / 1건 "재고조회" / N건 "선택 항목 재고조회 (N건)" 라벨 변화 |
| `print-spec.md` § 2 세로 A4 CSS | `global.css` `.dispatch-page` 186mm 폭 + 11pt Pretendard + thead `#F0F0F0` + 60×40mm 서명 박스 + `@page { size: A4 portrait; margin: 12mm }` (`@media print` 안) |

### 3.3 신규 NPM 의존성

- `@dnd-kit/core@^6.3.1` (npm 이 ^6.1.0 → 6.3.1 자동 선택)
- `@dnd-kit/sortable@^8.0.0`
- `@dnd-kit/utilities@^3.2.2`
- `react-hot-toast` 미설치 — 행 삭제 undo 는 본 슬라이스 미구현 (옵셔널 처리)

### 3.4 검증 결과

| 명령 | 결과 |
| --- | --- |
| `cd clients/web/design-system && npm run build` | OK — `dist/style.css` 29.4KB / `dist/index.js` 46.7KB (gzip 12.7KB) |
| `cd clients/web/design-system && npm run lint` | OK — 0 errors |
| `cd clients/desktop && npm run typecheck` | OK — 0 errors (`DraggableAttributes` → `Record<string, unknown>` cast 1회) |
| `cd clients/desktop && npm run lint` | OK — 0 errors |
| `cd clients/desktop && npm run build` | OK — `out/renderer/assets/index-*.js` 820KB (이전 ~720KB → +100KB, dnd-kit 영향) |

### 3.5 UUID 노출 가드 재확인

- `LineDraft.id` (drag-and-drop key) — `tmp-N` prefix 또는 서버 UUID. 화면에는 `data-line-number` 속성에 라인 번호만 노출. UUID 자체 미노출.
- `LineDraft.productId` — onBlur lookup 응답에 채워짐. 화면 미노출 (모델명 / 품목명만 표시).
- `<StockBalanceModal>` row.productId — React `key` 로만 사용, 모달 내 표시 영역에 노출 X.
- `fetchStockBalanceBatch(productIds)` — body 에만 사용, URL path / query 노출 X.

### 3.6 잠재 이슈 / 후속 슬라이스 권장

1. **번들 사이즈 +100KB** — `@dnd-kit` 3 패키지 합. tree-shaking 검증 필요. lazy import 도 검토.
2. **react-hot-toast 미도입** — 행 삭제 undo 는 즉시 제거 (Designer `ux-flow.md` § 1.3 권장 토스트 미적용). 사용자 요청 시 후속 슬라이스에서 추가.
3. **키보드 단축키 일부 미구현** — `Cmd+S` 저장, `Cmd+N` 라인 추가 (Designer `ux-flow.md` § 2.1) 는 본 슬라이스 미구현.
4. **창고 컬럼 라벨 자동 truncate** — `name.slice(0, 6)` fallback. 후속 슬라이스에서 풀 라벨 + tooltip 으로 개선 권장.
5. **인쇄 페이지 분할** — 라인 20건 이상 시 thead 자동 반복 + 서명 박스 마지막 페이지 — `@media print` 안의 `tr { page-break-inside: avoid }` + `thead { display: table-header-group }` 적용 완료.
6. **dark mode** — Designer `tokens.md` § 4 정책대로 본 슬라이스 미적용.

### 3.7 변경 파일 목록

```
clients/web/design-system/src/tokens/tokens.css                                          (+101 라인)
clients/web/design-system/src/components/DragHandle/DragHandle.tsx                       (신규)
clients/web/design-system/src/components/DragHandle/DragHandle.module.css                (신규)
clients/web/design-system/src/components/DragHandle/DragHandle.stories.tsx               (신규, 5 stories)
clients/web/design-system/src/components/DragHandle/index.ts                             (신규)
clients/web/design-system/src/components/LineRow/LineRow.tsx                             (신규)
clients/web/design-system/src/components/LineRow/LineRow.module.css                      (신규)
clients/web/design-system/src/components/LineRow/LineRow.stories.tsx                     (신규, 7 stories)
clients/web/design-system/src/components/LineRow/LineTableHeader.tsx                     (신규)
clients/web/design-system/src/components/LineRow/index.ts                                (신규)
clients/web/design-system/src/components/StockBalanceModal/StockBalanceModal.tsx         (신규)
clients/web/design-system/src/components/StockBalanceModal/StockBalanceModal.module.css  (신규)
clients/web/design-system/src/components/StockBalanceModal/StockBalanceModal.stories.tsx (신규, 5 stories)
clients/web/design-system/src/components/StockBalanceModal/index.ts                      (신규)
clients/web/design-system/src/index.ts                                                   (+4 export)

clients/desktop/package.json                                                             (+3 deps: @dnd-kit/*)
clients/desktop/src/renderer/api/inventory.ts                                            (+ fetchStockBalanceBatch)
clients/desktop/src/renderer/api/mock.ts                                                 (+ /inventory/balances/batch mock)
clients/desktop/src/renderer/routes/SlipFormPage.tsx                                     (전면 리팩토링)
clients/desktop/src/renderer/print/DispatchView.tsx                                      (가로→세로 정정)
clients/desktop/src/renderer/styles/global.css                                           (+ .sales-form-polish 섹션 + .dispatch-page 세로 spec)

docs/design/sales-form-polish-slice/*                                                    (Designer 산출물 cp from agent-ac0dd78bdba7f961e)
```

---

## 4. QA

### 4.1 산출물
- `services/inventory-service/src/test/java/.../it/StockBalanceBatchControllerIT.java` — 신규 IT 7 시나리오
- `services/inventory-service/src/test/resources/fixtures.http` — 시나리오 9 (배치 조회 5 case) 추가
- `docs/qa/sales-form-polish-slice/qa-report.md` — QA 리포트
- `docs/qa/sales-form-polish-slice/screenshots/*.png` — 화면 캡처 (PM 통합 후 FE 실시연 재캡처)

### 4.2 신규 IT 시나리오 (7건, BE batch endpoint 검증)
1. `batch_authenticated_returnsAllWarehousesPerProduct` — 200 + 각 product 의 balances 비어있지 않음
2. `batch_unauthenticated_returns403` — 헤더 없음
3. `batch_emptyList_returns400` — `@NotEmpty` validation
4. `batch_overLimit_returns400` — `@Size(max=100)` 101건
5. `batch_warehouseRole_returns200` — 모든 role 가능 검증
6. `batch_includesZeroBalanceWarehouses` — 입고 → 차감 후 잔량 0 row 응답 포함
7. `batch_excludesNeverInboundedWarehouses` — DB row 없는 창고 응답 미포함

### 4.3 회고 가드 (`feedback_pm_integration_build_check.md`) 적용
- 외부 RestClient `@MockBean` (ProductClient) ✓
- 반환 메서드 `when().thenAnswer()` (void 만 `doNothing()`) ✓
- 싱글턴 Testcontainers (`extends AbstractPostgresIT`, `@Testcontainers` 미사용) ✓
- ApiResponse `$.data.*` jsonPath ✓
- batch = mutation 없음 → CONFLICT 분기 없음, validation = 400, 미인증 = 403

### 4.4 FE 시연 캡처 (PM 통합 후 재캡처 의무)
PR #18 mock 모드 + Vite + Edge headless 패턴으로 PM 통합 단계에서 FE 실제 구현 시연 캡처. Designer wireframe 항목별 매칭 표 검증.

---

## 5. DevOps

### 5.1 인프라 변경 — 0건
- BE: `POST /inventory/balances/batch` 1 endpoint 추가 (gateway `/api/inventory/**` 라우트 하위 자동 노출)
- FE: NPM 의존성 3종 추가 (`@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities`) — bundle size +100kB
- 디자인 토큰 — Designer tokens.md 의 신규 alias `tokens.css` 끝에 append (기존 16 컴포넌트 회귀 위험 0)
- gateway / docker-compose / prometheus / grafana / ci.yml 모두 변경 0건

### 5.2 점검 결과
- `services/api-gateway/src/main/resources/application.yml:54-60` — `/api/inventory/**` 라우트 등재 ✓
- `infrastructure/docker-compose.yml` — 변경 불요 ✓
- `.github/workflows/ci.yml` — 변경 불요 ✓
- electron-builder.yml — 변경 불요 ✓

### 5.3 NPM 의존성 영향 (Q3=A `@dnd-kit/sortable`)
- 모두 MIT 라이선스 (호환)
- bundle size 영향 30~50kB → 실측 100kB (FE 보고: 720KB → 820KB)
- tree-shaking 검증 권장 (lazy import 도 검토)

### 5.4 인쇄 양식 보안 (PR #19 후속 권고 갱신)
- DispatchView 세로 변경만 — 보안 위험 동일
- 후속 슬라이스 권고: 인쇄 권한 분리 (SALES vs WAREHOUSE) + 워터마크 + SlipPrintEvent 감사 로그 + Electron PDF 차단

### 5.5 후속 슬라이스 권고 (우선순위)
1. Slip 2nd HISTORY (수정 사유 + 팀장 승인 + 시점별 복원)
2. **인쇄 보안 강화** (권한 분리 + 워터마크 + 감사 + PDF 차단)
3. Partner Service Q9 (BE 도메인 확장 — 전잔/후잔/할인율/감리주소)
4. Admin UUID 화면 (`/admin/system/objects` MASTER/DEVELOPER 한정)
5. **디자인 토큰 전 컴포넌트 점진 적용** (현재 SlipFormPage + 신규 4종만 → 16+ 컴포넌트 순차 migration)
6. Storybook GitHub Pages 배포 (사내 디자인 검토 가속)
7. 모바일 듀얼 앱 (Phase 6, 창고원/거래처 분리)

### 5.6 검토 산출물
- `docs/devops/sales-form-polish-review.md` — 8장 (인프라 + 빌드 + NPM + 인쇄 보안 + CI + 모니터링 + 후속 + Plan)

---

## 6. 통합 검증 (PM)

(PM 가 4-team 산출물 통합 후 채움)

---

## 7. 사용자 (개발책임자) QA 결과

(스크린샷 첨부 — `docs/qa/sales-form-polish-slice/*.png`)
