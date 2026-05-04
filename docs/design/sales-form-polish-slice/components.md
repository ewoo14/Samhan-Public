# Component Spec — Sales Form Polish 슬라이스

본 문서는 본 슬라이스의 신규/변경 컴포넌트 spec 을 정의합니다. FE agent 는 본 spec 에 정확히 일치하는 props / states / visual 을 구현해야 합니다.

---

## 1. `<LineRow>` — 전표 라인 행 (신규, SlipFormPage 내부)

### 1.1 개요

전표 라인 1줄을 표시하는 dense table row 컴포넌트. 체크박스 + drag handle + 자동 라인 번호 + 모델명 input + 품목명 read-only + 수량/단가/합계 + 삭제 버튼.

### 1.2 Props

```typescript
interface LineRowProps {
  /** 1부터 시작하는 사용자 표시용 라인 번호 (drag 시 자동 갱신). */
  lineNumber: number;

  /** 행 데이터 (LineDraft). */
  line: LineDraft;

  /** 행 선택 여부 — 체크박스 + 행 배경 색에 동시 반영. */
  selected: boolean;

  /** 선택 변경 콜백 (체크박스 toggle 또는 행 클릭). */
  onSelect: (selected: boolean) => void;

  /** 모델명 input 변경 (입력 도중 매 keystroke). */
  onModelNameChange: (value: string) => void;

  /** 모델명 onBlur — 백엔드 lookup 호출. */
  onModelNameBlur: (value: string) => void | Promise<void>;

  /** 수량 변경. */
  onQuantityChange: (value: string) => void;

  /** 단가 변경. */
  onUnitPriceChange: (value: string) => void;

  /** 행 삭제 — undo toast 후 실제 제거. */
  onDelete: () => void;

  /** @dnd-kit/sortable useSortable() 의 attributes + listeners 를 그대로 전달. */
  dragHandleProps: {
    attributes: Record<string, unknown>;
    listeners: Record<string, unknown> | undefined;
    setActivatorNodeRef: (node: HTMLElement | null) => void;
  };

  /** drag 진행 중 (transform 적용 시 opacity 변화). */
  isDragging: boolean;

  /** 첫 행 + 행이 1건 뿐일 때 삭제 disable (UX: 빈 폼 방지). */
  canDelete: boolean;
}

interface LineDraft {
  productId: string | null;
  modelName: string;
  productName: string;
  quantity: string;
  unitPrice: string;
  lineSum: string;          // computed: quantity × unitPrice (read-only)
  lookupError: string | null;
  lookupLoading: boolean;
}
```

### 1.3 Visual variants & states

| State        | 배경                | 좌측 border        | 비고                              |
| ------------ | ------------------- | ------------------ | --------------------------------- |
| default      | `--surface-card`    | none (1px transparent) | -                             |
| hover        | `--surface-hover`   | none               | cursor: default (drag handle 만 grab) |
| selected     | `--surface-selected`| 4px `--line-selected` | 좌측에 4px 파란 띠              |
| selected+hover | `--surface-selected` (살짝 어둡게 — `#E0EAFB`) | 4px `--line-selected` | -                       |
| dragging     | `--surface-card`    | none               | opacity 0.6, box-shadow `--elev-popover` |
| error (모델명) | `--surface-card`    | none               | 모델명 input border `--state-danger` + 행 아래 12px 빨간 메시지 |
| loading (lookup) | `--surface-card`| none               | 모델명 input 우측 spinner 12px |

### 1.4 layout (CSS grid)

```css
.line-row {
  display: grid;
  grid-template-columns:
    var(--col-checkbox)    /* 40 */
    var(--col-drag)        /* 24 */
    var(--col-line-no)     /* 24 */
    minmax(120px, 2fr)     /* 모델명 */
    minmax(120px, 2fr)     /* 품목명 */
    var(--col-qty)         /* 80 */
    var(--col-price)       /* 120 */
    var(--col-sum)         /* 100 */
    var(--col-delete);     /* 32 */
  align-items: center;
  height: var(--row-h);    /* 40px */
  padding: 0 var(--space-row-x);
  border-bottom: 1px solid var(--line-default);
  transition: background var(--motion-hover);
}
```

### 1.5 키보드 / 접근성

| 키        | 동작                                       |
| --------- | ------------------------------------------ |
| `Tab`     | 셀 간 이동 (체크박스 → 모델명 → 수량 → 단가) |
| `Space`   | 체크박스 토글 (focus 시)                   |
| `Enter`   | 모델명 input → blur (lookup trigger)       |
| `Cmd+↑/↓` | 행 순서 위/아래 (Q3=A: drag 외 키보드도 지원) |
| `Delete`  | 행 삭제 (선택된 행, 확인 다이얼로그 없음)  |

ARIA:
- `<tr role="row" aria-selected={selected}>`
- 체크박스: `<input type="checkbox" aria-label="라인 ${lineNumber} 선택">`
- drag handle: `<button aria-label="라인 ${lineNumber} 드래그">⠿</button>`
- 삭제 버튼: `<button aria-label="라인 ${lineNumber} 삭제">⊗</button>`

### 1.6 micro-interaction

- 체크 ☐ → ☑ 시 배경색 transition `--motion-hover`
- 모델명 lookup 중 input 우측 spinner fade-in
- lookup success 시 품목명 셀 fade-in (200ms)
- lookup error 시 행 아래 빨간 메시지 slide-down (180ms)
- 삭제 버튼 hover 시 빨간색 transition 120ms

### 1.7 사용 예시

```tsx
<table className="line-table">
  <thead>
    <tr>
      <th><input type="checkbox" /></th>
      <th></th>
      <th>#</th>
      <th>모델명</th>
      <th>품목명</th>
      <th className="num">수량</th>
      <th className="num">단가</th>
      <th className="num">합계</th>
      <th></th>
    </tr>
  </thead>
  <tbody>
    {lines.map((line, idx) => (
      <LineRow
        key={line.id}
        lineNumber={idx + 1}
        line={line}
        selected={selectedIds.has(line.id)}
        onSelect={(s) => toggleSelect(line.id, s)}
        // ... etc
      />
    ))}
  </tbody>
</table>
```

---

## 2. `<StockBalanceModal>` — 재고 조회 모달 (신규)

### 2.1 개요

라인 행 체크박스에서 선택한 1건 또는 N건 의 productId 를 batch 로 백엔드에 조회 → 창고별 재고 + 합계를 표 형태로 표시.

### 2.2 Props

```typescript
interface StockBalanceModalProps {
  /** 모달 open 여부. */
  open: boolean;

  /** 닫기 콜백 (× 클릭 / overlay 클릭 / Esc). */
  onClose: () => void;

  /** 조회 대상 라인 (선택된 항목들). */
  selectedLines: Array<{
    productId: string;
    modelName: string;
    productName: string;
  }>;

  /** 백엔드 응답 — null 이면 로딩 중, [] 이면 데이터 없음. */
  rows: StockBalanceRow[] | null;

  /** 조회 실패 시 에러 메시지. */
  error: string | null;
}

interface StockBalanceRow {
  productId: string;
  modelName: string;
  /** 창고 코드 → 재고 수량 (재고 0 이면 0, 가상창고는 null). */
  perWarehouse: Record<string, number | null>;
  /** 합계 (가상창고 제외). */
  total: number;
}
```

### 2.3 Visual variants & states

| State    | 표현                                                          |
| -------- | ------------------------------------------------------------- |
| loading  | 헤더 아래 spinner 24px + "조회 중..." text-secondary          |
| empty    | 표 영역에 "재고 데이터가 없습니다" centered text-tertiary     |
| error    | 표 위 빨간 banner: `<div className="error-banner">${error}</div>` |
| success  | 표 + 안내 푸터 메시지                                          |

### 2.4 layout

```css
.stock-modal {
  position: fixed;
  inset: 0;
  background: var(--overlay-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
  animation: fadeIn var(--motion-modal);
}
.stock-modal__panel {
  width: min(720px, 90vw);
  max-height: var(--modal-max-h);
  background: var(--surface-card);
  border-radius: var(--radius-modal);
  box-shadow: var(--elev-modal);
  display: flex;
  flex-direction: column;
}
.stock-modal__header {
  height: 56px;
  padding: 0 var(--space-card);
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line-default);
}
.stock-modal__body {
  padding: var(--space-card);
  overflow-y: auto;
  flex: 1;
}
.stock-modal__footer {
  height: 56px;
  padding: 0 var(--space-card);
  display: flex;
  align-items: center;
  justify-content: flex-end;
  border-top: 1px solid var(--line-default);
}
```

### 2.5 표 셀 렌더링 함수

```tsx
function StockCell({ value }: { value: number | null }) {
  if (value === null) return <td className="num dim">-</td>;
  if (value === 0)   return <td className="num dim">0</td>;
  return <td className="num">{value.toLocaleString()}</td>;
}
```

`.num`: `text-align: right; font-variant-numeric: tabular-nums`
`.dim`: `color: var(--ink-tertiary)`

### 2.6 키보드 / 접근성

- `Esc`: 모달 닫기
- focus trap: 모달 내에서만 Tab 순환 (focus-trap-react 권장 또는 자체 구현)
- ARIA: `role="dialog" aria-modal="true" aria-labelledby="stock-modal-title"`
- 모달 open 시 body scroll lock

---

## 3. `<DragHandle>` — drag 핸들 (신규, LineRow 내부)

### 3.1 개요

`@dnd-kit/sortable` 의 listener 를 부착하는 작은 24×40 영역. 6점 dot icon (`⠿` Braille pattern dots-12345678) 또는 lucide-react `<GripVertical />`.

### 3.2 Props

```typescript
interface DragHandleProps {
  /** dnd-kit useSortable() 의 listeners. */
  listeners: Record<string, unknown> | undefined;
  /** dnd-kit useSortable() 의 attributes. */
  attributes: Record<string, unknown>;
  /** drag 활성화 ref (키보드 접근성). */
  setActivatorNodeRef: (node: HTMLElement | null) => void;
  /** ARIA label. */
  label: string;
}
```

### 3.3 Visual

| State     | 색상                | cursor   |
| --------- | ------------------- | -------- |
| default   | `--ink-tertiary`    | grab     |
| hover     | `--ink-secondary`   | grab     |
| dragging  | `--ink-primary`     | grabbing |

```css
.drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: var(--col-drag);
  height: var(--row-h);
  color: var(--ink-tertiary);
  cursor: grab;
  user-select: none;
  background: transparent;
  border: none;
}
.drag-handle:hover { color: var(--ink-secondary); }
.drag-handle:active { cursor: grabbing; color: var(--ink-primary); }
```

---

## 4. `<DispatchView>` 세로 A4 (변경 — 기존 가로면 정정)

### 4.1 개요

기존 `clients/desktop/src/renderer/print/DispatchView.tsx` 가 가로 A4 형태였던 것을 **세로 A4 (portrait)** 로 변경. 이미지 2 (사용자 제공 양식) 충실 반영.

### 4.2 Props (변경 없음)

기존 `useParams<{ id: string }>` + `getSlip(id)` 그대로 유지. 추가 props 없음.

### 4.3 Layout 변경 사항

| 영역          | 기존 (가로)              | 신규 (세로)                                         |
| ------------- | ------------------------ | --------------------------------------------------- |
| `@page` size  | `A4 landscape`           | `A4 portrait`                                       |
| 헤더          | 좌-우 양분 (브랜드 / 메타) | 상단 SAMSUNG 좌, 우측 5칸 담당 박스 grid          |
| 라인 표       | 6 col (월/일/모델/품목/규격/수량) | 동일하지만 모델명+품목명 1셀 합치고 2줄 (이미지 2 매치) |
| 배송지/연락처 | 단일 section             | 3 분리: 배송지 / 연락처 / 특이사항                |
| 서명          | 2 sign box               | 동일 — 60mm × 40mm 명시                            |

### 4.4 신규 CSS 클래스

```css
@page { size: A4 portrait; margin: 12mm; }

.dispatch-page {
  width: 186mm;            /* A4 width 210mm - 12mm * 2 */
  font-family: 'Pretendard', 'Noto Sans KR', sans-serif;
  font-size: 11pt;
  line-height: 1.4;
  color: #000;
}

.dispatch-header {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: start;
  gap: 8mm;
  margin-bottom: 6mm;
}

.dispatch-roles {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid #000;
  width: 80mm;             /* 우측 상단 박스 */
}
.dispatch-role-box {
  border: 1px solid #000;
  padding: 2mm 3mm;
  min-height: 12mm;
}
.dispatch-role-box.full { grid-column: 1 / -1; min-height: 18mm; }

.dispatch-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 4mm;
}
.dispatch-table th,
.dispatch-table td {
  border: 1px solid #000;
  padding: 2mm 3mm;
  font-size: 10pt;
}
.dispatch-table th { background: #f0f0f0; font-weight: 600; }
.dispatch-table .num { text-align: right; font-variant-numeric: tabular-nums; }
.dispatch-table .product-cell {
  /* 모델명 + 품목명 2줄 */
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.dispatch-table .product-cell .model-name { font-weight: 600; }
.dispatch-table .product-cell .product-name { color: #333; font-size: 9pt; }

.dispatch-delivery,
.dispatch-contact,
.dispatch-memo {
  margin-top: 4mm;
  border: 1px solid #000;
  padding: 3mm;
}

.dispatch-notice {
  margin-top: 4mm;
  text-align: center;
  font-size: 11pt;
}

.dispatch-warning {
  margin-top: 2mm;
  text-align: center;
  font-size: 10pt;
  color: #c00;
}

.dispatch-signatures {
  margin-top: 6mm;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8mm;
}
.dispatch-sign-box {
  border: 1px solid #000;
}
.dispatch-sign-label {
  padding: 2mm 3mm;
  border-bottom: 1px solid #000;
  font-size: 10pt;
  font-weight: 600;
}
.dispatch-sign-area {
  width: 60mm;
  height: 40mm;
}
```

### 4.5 화면 (preview) 인쇄 미리보기

화면에서는 상단 toolbar (`.no-print`) 에 [상세로 돌아가기] [인쇄] 버튼 노출. 인쇄 시 `.no-print { display: none !important; }` 로 숨김.

---

## 5. 기존 컴포넌트 변경 영향도

| 컴포넌트            | 변경                                       |
| ------------------- | ------------------------------------------ |
| `Card`              | 변경 없음 — padding 기본값 그대로          |
| `Button`            | 변경 없음 — variant/size 기존 사용         |
| `FormField`         | 변경 없음                                  |
| `WarehouseSelector` | 변경 없음                                  |
| `Modal`             | 신규 `StockBalanceModal` 이 wrap 해서 사용 |
| `DataTable`         | 신규 LineTable 은 별도 (LineRow 직접 grid) |
| `Spinner`           | LineRow lookup 시 12px 사용                |

> **중요**: 기존 design-system 컴포넌트 자체는 본 슬라이스에서 변경하지 않습니다. SlipFormPage 와 신규 컴포넌트만 신규 토큰 적용.

---

## 6. 예외 / 디테일

### 6.1 빈 행 처리

마지막 행이 모두 비어있으면 자동으로 빈 행 1개 유지 (이카운트 패턴). 사용자가 명시적으로 추가 버튼 눌러도 1개 더 추가됨.

### 6.2 합계 계산

```typescript
// SlipFormPage 내 useMemo
const totals = useMemo(() => {
  const valid = lines.filter(l => l.productId && Number(l.quantity) > 0);
  const supply = valid.reduce(
    (sum, l) => sum + Number(l.quantity) * Number(l.unitPrice || 0),
    0,
  );
  const vat = Math.round(supply * 0.1);
  return {
    count: valid.length,
    supply,
    vat,
    total: supply + vat,
  };
}, [lines]);
```

### 6.3 undo toast (행 삭제)

행 삭제 시 즉시 제거 + 화면 우하단 toast:
```
삭제됨 — 라인 3 [실행 취소]
                (5초 후 자동 사라짐)
```

> 본 슬라이스에서는 `react-hot-toast` 또는 자체 toast 사용. FE 가 `react-hot-toast` 도입 추천.
