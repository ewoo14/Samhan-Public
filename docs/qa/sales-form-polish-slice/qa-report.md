# Sales Form UX Polish 슬라이스 — QA Report

> **슬라이스**: sales-form-polish-slice | **base commit**: `b5583b7` | **worktree branch**: `worktree-agent-a117189b11eb7318e`
> **작성**: QA (Team-Sales-Form-Polish) | **회고 가드 적용**: PR #16 / #17 / #18 / #19

본 슬라이스는 **BE 신규 1 endpoint** + **FE 큰 리팩토링 (SlipFormPage + StockBalanceModal + DispatchView 세로 A4)** + **Designer spec 6 파일 (모던 미니멀 디자인 토큰)** 의 5-team 첫 디스패치.

QA 산출물:

1. BE IT 신규 7 시나리오 (`StockBalanceBatchControllerIT`)
2. fixtures.http 시나리오 9 (배치 조회) 5 case 추가
3. FE 시연 캡처 10 화면 (Designer wireframe 충실 반영 mock-up)
4. dev-report § 4 QA 섹션 채움

---

## 1. BE IT 신규 — `POST /inventory/balances/batch` × 7 시나리오

### 1.1 신규 IT 파일

`services/inventory-service/src/test/java/com/samhanair/logis/inventory/it/StockBalanceBatchControllerIT.java`

| # | 시나리오 메서드 | 입력 | 기대 응답 | 검증 jsonPath |
| - | --------------- | ---- | --------- | ------------- |
| 1 | `batch_authenticated_returnsAllWarehousesPerProduct` | productA (HQ+VH 입고) + productB (HQ 입고), SALES role | 200 | `$.data` size 2, `$.data[*].balances` 모두 길이 ≥ 1 |
| 2 | `batch_unauthenticated_returns403` | 헤더 없음 | 403 | (status 만) |
| 3 | `batch_emptyList_returns400` | `productIds: []` | 400 | (`@NotEmpty` validation) |
| 4 | `batch_overLimit_returns400` | productIds 101건 | 400 | (`@Size(max=100)` validation) |
| 5 | `batch_warehouseRole_returns200` | WAREHOUSE role + 1 product | 200 | `$.data` size 1, balances ≥ 1 |
| 6 | `batch_includesZeroBalanceWarehouses` | 입고 100 → 차감 100 → batch 조회 | 200 | `$.data[0].balances[?(@.warehouseCode=='HQ-001')]` 존재 + totalQty=0 |
| 7 | `batch_excludesNeverInboundedWarehouses` | VH 만 입고, batch 조회 | 200 | VH-001 row 존재, HQ-001 row size=0 |

### 1.2 BE 시그니처 가정 (PM 통합 단계 컴파일 검증 의무)

QA IT 가 가정하는 시그니처:

| 항목 | 시그니처 | 미반영 시 영향 |
| ---- | -------- | -------------- |
| Request DTO | `BatchBalancesRequest(@NotEmpty @Size(max=100) List<UUID> productIds)` | IT 컴파일 실패 또는 시나리오 3/4 가 422 등 다른 status 반환 |
| Response DTO | `ProductBalanceResponse(UUID productId, String modelName, List<StockBalanceResponse> balances, int total)` | jsonPath `$.data[*].balances[*]` 실패 |
| Endpoint | `@PostMapping("/inventory/balances/batch")` on StockController 또는 신규 컨트롤러 | 모든 시나리오 404 |
| 권한 | `@PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','SALES','WAREHOUSE','INVENTORY','ACCOUNTANT')")` 또는 `@PreAuthorize("isAuthenticated()")` | 시나리오 1/5 가 403 (권한 부족) |

PM 통합 단계 사전 빌드 (memory `feedback_pm_integration_build_check.md`):
```
gradlew :services:inventory-service:compileTestJava
```
컴파일 실패 시 BE 가 다른 시그니처로 출시 — QA 가 즉시 동기화.

### 1.3 권한 매트릭스 — `POST /inventory/balances/batch` × 7-tier

batch 는 read-only 재고 조회 → **모든 인증 role 가능** + 미인증 403.

| Role | 기대 | IT 적용 |
| ---- | ---- | ------- |
| MASTER | 200 | (시나리오 1 SALES 와 동일 가정 — 별도 IT 없음) |
| MANAGER | 200 | (시나리오 1 SALES 와 동일 가정) |
| DEVELOPER | 200 | (시나리오 1 SALES 와 동일 가정) |
| SALES | 200 | ✓ 시나리오 1, 6, 7 |
| WAREHOUSE | 200 | ✓ 시나리오 5 |
| INVENTORY | 200 | (시나리오 5 WAREHOUSE 와 동일 가정) |
| ACCOUNTANT | 200 | (시나리오 5 WAREHOUSE 와 동일 가정) |
| (미인증) | 403 | ✓ 시나리오 2 |
| (빈 리스트) | 400 | ✓ 시나리오 3 |
| (101건) | 400 | ✓ 시나리오 4 |

본 IT 는 SALES + WAREHOUSE 두 role 만 직접 시연. 나머지 5 role 은 BE `@PreAuthorize` 표현식이 7-tier 모두 포함하는지로 검증 (정적 검증).

### 1.4 회고 가드 체크리스트 (PR #16 / #17 / #18 — memory `feedback_pm_integration_build_check.md`)

| 가드 | 적용 위치 | 상태 |
| ---- | --------- | ---- |
| 외부 RestClient `@MockBean` 의무 | `StockBalanceBatchControllerIT` ProductClient `@MockBean` | ✓ |
| void 메서드만 `doNothing()`, 반환 메서드는 `when().thenReturn()` / `thenAnswer()` | `productClient.requireExists` / `lookup` 모두 ProductSummary 반환 → `thenAnswer` | ✓ |
| BusinessException ErrorCode 분기 (NOT_FOUND vs CONFLICT) 정확 가정 | batch 는 mutation 없음 → CONFLICT 분기 없음. validation 400 (NotEmpty/Size) ≠ NOT_FOUND/CONFLICT | ✓ |
| 한국어 메시지 substring 검증만 | 본 IT 는 메시지 미검증 (status + jsonPath 만) | ✓ |
| ApiResponse 래핑 → `$.data.*` jsonPath | 모든 200 IT | ✓ |
| 싱글턴 Testcontainers (`@Testcontainers` 미사용) | `StockBalanceBatchControllerIT extends AbstractPostgresIT` | ✓ |
| ProductClient `@MockBean` 격리 (외부 호출 차단) | ✓ | ✓ |

---

## 2. fixtures.http 갱신

`services/inventory-service/src/test/resources/fixtures.http` — 시나리오 9 신규 추가:

| 시나리오 | endpoint | 인증 | 기대 |
| -------- | -------- | ---- | ---- |
| 9-a | `POST /inventory/balances/batch` (3 productIds) | SALES | 200 + `$.data[*].balances[*]` |
| 9-b | (동일) | (none) | 403 |
| 9-c | (productIds: []) | SALES | 400 NotEmpty |
| 9-d | (productIds 101건) | SALES | 400 Size(max=100) |
| 9-e | (1 productId) | WAREHOUSE | 200 (모든 role 가능 검증) |

총 **5 시나리오 추가**.

---

## 3. FE 시연 캡처 — 10 화면 (Designer wireframe 충실 반영)

### 3.1 캡처 환경

- 도구: msedge headless (`--headless=new`), `--window-size=1280,1024` (form/modal) / `794,1200` (dispatch portrait) / `1123,820` (invoice landscape)
- 위치: `docs/qa/sales-form-polish-slice/screenshots/`
- 기반: Designer spec (`tokens.md` / `wireframes.md` / `components.md` / `print-spec.md`) 충실 반영 정적 HTML mock-up
- 토큰: `_tokens.css` 에 Designer `tokens.md` § 1 신규 alias 17개 그대로 인용 (`--surface-app/card/subtle/hover/selected`, `--line-default/hover/focus/selected`, `--ink-primary/secondary/tertiary`, `--action-brand/hover/active/subtle`, `--row-h: 40px`, `--modal-max-w: 720px` 등)

### 3.2 캡처 결과 표 — Designer wireframe 충실도

| # | 파일 | Designer wireframe 항목 | 충실도 |
| - | ---- | ------------------------ | ------ |
| 01 | `01_form_empty.png` | wireframes.md § 1.1 전체 layout (헤더 정보 카드 + 빈 라인 1행 + totals-bar 0건) | ✓ 헤더 3-cols + 거래처/메모 1:2 + 라인 1 빈 행 + placeholder "예: AJ040RXH4BC1" + 합계 0건 |
| 02 | `02_form_with_lines.png` | wireframes.md § 1.1 라인 4행 (3 입력 + 1 빈 자동 추가) + totals 4,389,000 | ✓ AJ040/MWR-WE10N/PC1NW 3건 입력 + 4행 빈 행 자동 + 합계 ₩4,389,000 (= 3,990,000 × 1.1) |
| 03 | `03_row_hover.png` | wireframes.md § 1.3 row state Hover (surface-hover #F4F6F8) | ✓ 라인 1 행 배경 #F4F6F8 |
| 04 | `04_row_selected.png` | wireframes.md § 1.3 row state Selected (surface-selected #EFF6FF + 좌측 4px line-selected) | ✓ 라인 2 행 배경 #EFF6FF + 좌측 4px 파란 띠 + 체크박스 ☑ + 헤더 "재고조회" 버튼 활성화 |
| 05 | `05_stock_modal_multi.png` | wireframes.md § 2.1 layout (max-w 720, overlay rgba(0,0,0,0.6)) + § 2.2 셀 렌더링 규칙 | ✓ overlay 60% black + 모달 width 720 + 헤더 "재고 조회" + 선택 품목 3 bullet + balance table (본사/차량1/위탁/가상/합계) + dim 0/- 처리 + 안내 푸터 |
| 06 | `06_row_dragging.png` | wireframes.md § 1.3 Dragging (opacity 0.6 + box-shadow elev-popover) + ux-flow.md § 1.2 mermaid | ✓ 라인 4 (DAK-150D) opacity 0.6 + 그림자 + drag handle 색 ink-primary + cursor grabbing |
| 07 | `07_dispatch_portrait.png` | wireframes.md § 3.1 ASCII art + print-spec.md § 2 A4 portrait | ✓ SAMSUNG 좌 + 5칸 담당 박스 grid (담당부서/담당자 row1, 출고인/검수인 row2, 결재 full row3) + 본사창고 박스 + 라인 표 (월/일/모델명+품목명 2줄/규격/수량) + 총합계 17 + 배송지/연락처/특이사항 3 박스 + 한국어 안내 + 빨간 경고 + 60×40 서명 박스 2개 |
| 08 | `08_invoice_landscape.png` | print-spec.md § 3 (변경 없음 — 가로 A4 유지) | ✓ A4 landscape + "변경 없음 (기존 가로 A4 유지)" badge + 거래명세서 표준 layout |
| 09 | `09_line_row_states.png` | components.md § 1.3 LineRow 5 states 표 | ✓ default/hover/selected/dragging/error 5 + 보너스 loading (spinner) — 6 states |
| 10 | `10_stock_modal_story.png` | components.md § 2.3 StockBalanceModal 4 states 표 | ✓ loading (spinner) / empty (centered text-tertiary) / error (state-danger banner) / success (3건 batch) — 2×2 grid Storybook |

### 3.3 디자인 토큰 적용 검증 (사람 검토)

캡처 사람 검토:

- **`--surface-app: #FAFBFC`** — 앱 배경 (모든 SlipForm 캡처에서 살짝 회색조 적용 ✓)
- **`--surface-card: #FFFFFF`** — 카드/모달 배경 (헤더 카드 / 라인 카드 / 모달 panel ✓)
- **`--surface-hover: #F4F6F8`** — 03 캡처 라인 1 hover ✓
- **`--surface-selected: #EFF6FF`** — 04 캡처 라인 2 selected + 05 캡처 선택된 3행 ✓
- **`--line-selected: #3B82F6`** — 04 캡처 라인 2 좌측 4px 파란 띠 ✓
- **`--ink-primary: #1A1F2E`** — 모든 본문 텍스트 ✓
- **`--ink-tertiary: #8A95A4`** — placeholder, dim 0/- 셀 ✓
- **`--action-brand: #1E40AF`** — 저장 버튼 (primary blue) ✓
- **`--row-h: 40px`** — 라인 행 높이 일관 ✓
- **`--modal-max-w: 720px`** — 05 캡처 모달 width ✓
- **`--overlay-bg: rgba(0,0,0,0.6)`** — 05 캡처 60% black overlay ✓
- **font-variant-numeric: tabular-nums** — 수량/단가/합계 셀 자릿수 정렬 ✓
- **box-shadow elev-card / elev-modal** — 카드 / 모달 elevation 차이 ✓
- **A4 portrait 12mm 여백** — 07 캡처 dispatch (DispatchView) ✓
- **서명 박스 60×40mm** — 07 캡처 dispatch ✓

### 3.4 UUID 노출 검증 (사람 검토)

10 캡처 사람 검토 결과 — **UUID 노출 0건**:

- 01~06 SlipForm: 거래처명 (`(주)윌리-정현수`), 모델명 (`AJ040RXH4BC1`), 창고 코드+이름 (`HQ-001 본사창고`), 품목명, 수량/단가/합계 — 모두 한국어/영문 도메인 식별자
- 05 stock 모달: 모델명 + 품목명 + 창고명 (본사/차량1/위탁/가상) — UUID 없음
- 07 dispatch: 전표번호 `2026/06/02 - 4` (날짜+seqNo, UUID 아님), 모델명 — UUID 없음
- 08 invoice: 전표번호 `2026/06/02 - 4`, 거래처명 — UUID 없음
- 09 LineRow stories: 모델명 / 한국어 라벨 — UUID 없음
- 10 Modal stories: 모델명 / 창고명 — UUID 없음

자동 grep 불가 (PNG 바이너리). 사람 검토 결과만 기록. 36자 hex+`-` 패턴 미발견 — `feedback_uuid_no_user_visibility.md` 가드 충족.

### 3.5 본 캡처의 한계 (FE 미반영 슬라이스)

> **중요**: 본 worktree 는 BE/FE/Designer/DevOps 코드 변경 금지 슬라이스 — FE 실제 구현 미반영.
> 따라서 본 캡처는 **Designer spec 충실 반영 정적 HTML mock-up** 기반이며, FE 실제 구현은 별개.
> FE 팀이 실제 구현 출시 후 동일 시나리오 캡처를 다시 찍어 mock-up 과 시각 비교 검증 필요 (visual regression).

검증 불가 항목 (FE 실제 구현 후 사람 검증 필요):
- 키보드 단축키 (Cmd+↑/↓ drag 대안, Cmd+Backspace 삭제, Space 체크) — 정적 캡처 무관
- transition 120ms ease-out (hover/focus/selected) — 정적 캡처 무관
- focus trap + Esc + body scroll lock (StockBalanceModal) — 정적 캡처 무관
- `@dnd-kit/sortable` 실제 drag 동작 (06 캡처는 시각 mock 만)
- aria-live="polite" lookup 결과 안내 (스크린리더) — 정적 캡처 무관

---

## 4. 잠재 이슈

### 4.1 BE 시그니처 가정 (필수 동기화)

QA IT 가 가정하는 시그니처는 PM 통합 단계 컴파일 검증으로 즉시 확인:

1. `com.samhanair.logis.inventory.web.dto.BatchBalancesRequest` 미존재 시 IT 컴파일 실패 (현재 ObjectMapper 로 Map 직렬화 → BatchBalancesRequest 역직렬화 의존 X — IT 자체는 컴파일 OK)
2. `ProductBalanceResponse` 의 `balances` 필드명이 다르게 출시되면 (예: `perWarehouse`, `rows`) jsonPath `$.data[*].balances[*]` 가 빈 배열 반환 → assertion 실패. UX flow.md § 4.2 는 `perWarehouse: Record<warehouse, qty>` 표현인데 PM 명시는 `balances[]` 배열 — **BE ↔ Designer 시그니처 정합 검증 필요**
3. `@PreAuthorize` 표현식이 7-tier 모두 포함 안 하면 시나리오 1/5 403 반환 — 본 IT 는 SALES + WAREHOUSE 만 시연하므로 다른 role 권한 검증 누락 가능
4. validation 400 시 응답 envelope 가 ApiResponse 가 아닌 표준 Spring `MethodArgumentNotValidException` 응답 → status 400 만 검증해서 가드 OK

### 4.2 FE 미반영 위반 (본 worktree)

- 본 worktree (`b5583b7` 기준) 에 FE 실제 구현 부재 — 캡처는 Designer spec mock-up
- FE 팀 실제 PR 머지 후 재캡처 + visual diff 비교 의무

### 4.3 BE ↔ Designer 응답 schema 불일치 가능성

- Designer `ux-flow.md` § 4.2: `perWarehouse: Record<warehouseCode, number | null>` (객체)
- PM 명시 IT jsonPath: `$.data[*].balances[*]` (배열)
- 두 표현 모두 가능하나 BE/FE/Designer 한 표현으로 통일 필요. 본 IT 는 PM 명시를 ground truth 로 채택 (`balances[]` 배열).

### 4.4 가상창고 표현 (사용자 요구)

Designer wireframe `wireframes.md` § 2.2: 가상창고는 `null` → `-` dim 표시 (재고 차감 대상 외). BE 응답에서 가상창고 row 를 명시적 null balance 로 내려줄지, 아예 row 자체를 제외할지 결정 필요. 본 IT 시나리오 7 은 "입고 이력 없는 창고는 응답 미포함" 가정 — 가상창고가 별도 처리 필요하면 추가 시나리오 (예: `batch_includesVirtualWarehouseAsNull`) 신설 권장.

---

## 5. 통합 검증 메모 (PM 으로)

PM 통합 단계 권장 사항:

```bash
# 1. BE 컴파일 검증 (의무)
gradlew :services:inventory-service:compileTestJava

# 2. Docker 가용 환경에서 IT 실행
gradlew :services:inventory-service:test --tests "*StockBalanceBatchControllerIT*"

# 3. 풀빌드 (BE 의도적 변경 시 QA IT drift 확인)
gradlew :services:inventory-service:test
```

`feedback_korean_path_jdk.md`: 한글 path 환경에서는 `gradle test` 가 실패할 수 있음. 그 경우 `assemble` 만 또는 별도 머신.

---

## 6. 캡처 인라인

### 6.1 SlipFormPage — 초기 빈 화면 (01)

![form empty](screenshots/01_form_empty.png)

### 6.2 SlipFormPage — 3 라인 입력 후 (02)

![form with lines](screenshots/02_form_with_lines.png)

### 6.3 LineRow — Hover 상태 (03)

![row hover](screenshots/03_row_hover.png)

### 6.4 LineRow — Selected 상태 (04)

![row selected](screenshots/04_row_selected.png)

### 6.5 StockBalanceModal — 3건 batch 조회 (05)

![stock modal multi](screenshots/05_stock_modal_multi.png)

### 6.6 LineRow — Dragging 상태 (06)

![row dragging](screenshots/06_row_dragging.png)

### 6.7 DispatchView — 세로 A4 작업지시서 (07)

![dispatch portrait](screenshots/07_dispatch_portrait.png)

### 6.8 InvoiceView — 가로 A4 거래명세서 (변경 없음, 08)

![invoice landscape](screenshots/08_invoice_landscape.png)

### 6.9 LineRow — 5 states Storybook (09)

![line row states](screenshots/09_line_row_states.png)

### 6.10 StockBalanceModal — 4 stories Storybook (10)

![stock modal story](screenshots/10_stock_modal_story.png)
