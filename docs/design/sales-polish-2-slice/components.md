# Component Spec — Slice A

본 문서는 Slice A 의 신규/변경 컴포넌트 spec 입니다. FE agent 는 본 spec 에 정확히 일치하는 props / states / visual 을 구현해야 합니다.

---

## 1. `<ProgressBar>` — 전표 진행 단계 (신규)

### 1.1 개요

`SlipDetailPage` 상단에 표시되는 10단계 + 분기 (REJECTED/CANCELED) 시각화 컴포넌트. 사용자 피드백 #1 ("라이프사이클 표현 모호") 해결.

### 1.2 Props

```typescript
interface ProgressBarProps {
  /** 현재 전표 상태. */
  currentStatus: SlipStatus;

  /** 분기 사유 (REJECTED 시 반려 사유, CANCELED 시 취소 사유). */
  branchReason?: string;

  /** 단계별 transition timestamp (BE 응답 — done 노드 상세 hover 시 표시). */
  history?: Array<{
    status: SlipStatus;
    transitionedAt: string;  // ISO 8601
    actorFullName?: string;
  }>;

  /** 라벨 클릭 시 콜백 (옵션 — done 노드 클릭 시 history 모달 등). */
  onStepClick?: (status: SlipStatus) => void;
}

type SlipStatus =
  | 'DRAFT'        // 작성
  | 'SAVED'        // 저장
  | 'SENT'         // 전송
  | 'ACCEPTED'     // 수락 (출고인 자동)
  | 'PROCESSING'   // 처리
  | 'INSPECTING'   // 검수 (검수인 자동) — 신규
  | 'COMPLETED'    // 완료
  | 'SHIPPING'     // 배송중
  | 'DELIVERED'    // 배송완료
  | 'CONFIRMED'    // 확정
  | 'REJECTED'     // 분기
  | 'CANCELED';    // 분기
```

### 1.3 단계 정의 (상수)

```typescript
const PROGRESS_STEPS: Array<{ status: SlipStatus; label: string }> = [
  { status: 'DRAFT',      label: '작성' },
  { status: 'SAVED',      label: '저장' },
  { status: 'SENT',       label: '전송' },
  { status: 'ACCEPTED',   label: '수락' },
  { status: 'PROCESSING', label: '처리' },
  { status: 'INSPECTING', label: '검수' },  // 신규
  { status: 'COMPLETED',  label: '완료' },
  { status: 'SHIPPING',   label: '배송' },
  { status: 'DELIVERED',  label: '배송완료' },
  { status: 'CONFIRMED',  label: '확정' },
];
```

### 1.4 Visual states (단계별)

| State        | 노드 visual                                  | 라벨                              |
| ------------ | -------------------------------------------- | --------------------------------- |
| `done`       | ● `--progress-step-bg-done` 파란 채움        | primary text, 일반 weight         |
| `current`    | ● 흰 배경 + 2px `--action-brand` 외곽선      | brand color, **600 weight**       |
| `todo`       | ○ 흰 배경 + 2px `--line-default` 외곽선      | tertiary text, 일반 weight        |
| `rejected`   | ⊗ `--state-danger` 빨강 채움 + 흰 X icon     | danger color, **600 weight**      |
| `canceled`   | ⊗ `--ink-tertiary` 회색 채움 + 흰 X icon     | tertiary color, **600 weight**    |

### 1.5 분기 동작

- `currentStatus === 'REJECTED'`:
  - 마지막 done 단계 (SENT 또는 ACCEPTED) 이후 ⊗ 빨간 노드 + 라벨 "반려"
  - REJECTED 이후 단계는 모두 todo (회색 ○) 표시
  - `branchReason` 있으면 ProgressBar 아래 `<p class="branch-reason danger">반려 사유: {reason}</p>` 표시
- `currentStatus === 'CANCELED'`:
  - 마지막 done 단계 (DRAFT/SAVED/SENT) 이후 ⊗ 회색 노드 + 라벨 "취소"
  - CANCELED 이후 단계는 모두 todo
  - `branchReason` 있으면 `<p class="branch-reason muted">취소 사유: {reason}</p>` 표시

### 1.6 layout (10단계 / 1줄)

```
gap = (container_width - 10 × node_size) / 9 connections
container_width 가 좁을 경우 (모바일 시) 자동 줄바꿈 X — overflow-x: auto + min-width: 720px

flex container:
.progress-track {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 0;
  min-width: 720px;
  overflow-x: auto;
}

.progress-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--progress-step-gap);  /* 4px */
  flex-shrink: 0;
}

.progress-line {
  flex: 1;
  height: var(--progress-line-width);
  margin-top: calc(var(--progress-step-size) / 2);  /* 노드 중앙 정렬 */
  background: var(--progress-line-color-todo);
  /* done → done : --progress-line-color-done */
}
```

### 1.7 micro-interaction

- 단계 노드 hover: cursor pointer (onStepClick 있을 때만), tooltip 표시 (`{label} — {actorFullName} {transitionedAt}`)
- 현재 노드: 미세한 pulse 애니메이션 (선택사항 — `2s ease-in-out infinite alternate` opacity 0.8 ↔ 1)
- transition: 단계 변경 시 done 노드 채움 애니메이션 (`transition: background 200ms ease-out`)

### 1.8 접근성

- `role="progressbar"`
- `aria-valuemin=1 aria-valuemax=10 aria-valuenow={currentIndex+1}`
- 각 노드 `aria-label="{label} — {state}"` (예: `"수락 — 완료"`)
- 분기 ⊗ 노드 `aria-label="반려"` 또는 `"취소"`

---

## 2. `<AppHeader>` — 동적 화면명 (갱신)

### 2.1 개요

`AppLayout` 우측 본문 상단의 `<header>` 영역을 갱신. 기존 `<h2>업무 화면</h2>` 고정 → 라우트별 동적 화면명. 사용자 피드백 #2 해결.

### 2.2 Props

```typescript
// AppLayout 내부 — header 영역만 분리
interface AppHeaderProps {
  /** 현재 화면명 (usePageTitle 훅 또는 zustand store 에서 읽음). */
  title: string;

  /** 화면명 우측 bracket meta (예: slipNo "2026/05/04-1"). */
  meta?: string;

  /** 우측 user chip + logout 버튼 — 기존 로직 유지. */
  user: { fullName: string; role: string };
  onLogout: () => void;
}
```

### 2.3 layout

```
┌────────────────────────────────────────────────────────────────────────┐
│  [h2] 화면명  [span.meta] [bracket]                user · ROLE [logout] │
│  pad-x 24                                          pad-x 24             │
└────────────────────────────────────────────────────────────────────────┘
height: var(--page-header-h)  /* 56px */
border-bottom: var(--page-header-border)  /* 1px solid line-default */
background: var(--page-header-bg)  /* white */
```

### 2.4 CSS

```css
.app-header {
  height: var(--page-header-h);
  background: var(--page-header-bg);
  border-bottom: 1px solid var(--line-default);
  padding: 0 var(--page-header-pad-x);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.app-header h2 {
  font-size: var(--page-title-size);     /* 20px */
  font-weight: var(--page-title-weight); /* 600 */
  color: var(--page-title-color);
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--page-title-meta-gap);       /* 8px */
}

.app-header h2 .meta {
  font-size: var(--page-title-meta-size);   /* 14px */
  color: var(--page-title-meta-color);      /* secondary */
  font-weight: 400;
}
```

### 2.5 `usePageTitle()` 훅 spec

```typescript
// stores/pageTitle.ts (신규 zustand store)
interface PageTitleState {
  title: string;
  meta?: string;
  setPageTitle: (next: { title: string; meta?: string }) => void;
}

export const usePageTitleStore = create<PageTitleState>((set) => ({
  title: '',
  meta: undefined,
  setPageTitle: (next) => set(next),
}));

// hooks/usePageTitle.ts
export function usePageTitle(title: string, meta?: string) {
  const setPageTitle = usePageTitleStore((s) => s.setPageTitle);
  useEffect(() => {
    setPageTitle({ title, meta });
    return () => setPageTitle({ title: '', meta: undefined });
  }, [title, meta, setPageTitle]);
}
```

### 2.6 라우트별 사용 예

```typescript
// SalesListPage.tsx
function SalesListPage() {
  usePageTitle('판매조회');
  // ...
}

// SlipFormPage.tsx (new mode)
function SlipFormPage() {
  usePageTitle('새 출고전표');
  // ...
}

// SlipDetailPage.tsx
function SlipDetailPage() {
  const { slip } = useSlipQuery({ id });
  usePageTitle('출고전표 상세', slip?.slipNo);
  // ...
}

// DispatchPrintPage.tsx
function DispatchPrintPage() {
  const { slip } = useSlipQuery({ id });
  usePageTitle('출고전표 작업지시서', slip?.slipNo);
  // ...
}
```

### 2.7 빈 title fallback

`title` 이 빈 문자열인 경우 (라우트 전환 race condition) `"업무 화면"` fallback 표시 (기존 동작).

---

## 3. `<LineRow>` — 규격 컬럼 추가 (갱신)

### 3.1 개요

1차 슬라이스의 9-col `<LineRow>` 를 10-col 로 갱신. 모델명 / 품목명 사이에 **규격** 컬럼 신규 추가. 사용자 피드백 #4 해결.

### 3.2 Props 변경 사항

```typescript
interface LineRowProps {
  // ... 기존 props 유지

  /** 규격 변경 (입력 도중 매 keystroke). 신규. */
  onSpecificationChange: (value: string) => void;
}

interface LineDraft {
  productId: string | null;
  modelName: string;
  productName: string;
  specification: string;     // 신규 — varchar(50)
  quantity: string;
  unitPrice: string;
  lineSum: string;
  lookupError: string | null;
  lookupLoading: boolean;
}
```

### 3.3 grid 변경 (9 → 10 컬럼)

```css
.line-row {
  display: grid;
  grid-template-columns:
    var(--col-checkbox)      /* 40px  체크박스 */
    var(--col-drag)          /* 24px  drag handle */
    var(--col-line-no)       /* 24px  # */
    minmax(160px, 2fr)       /* flex  모델명 input */
    minmax(160px, 2fr)       /* flex  품목명 read-only */
    100px                    /* NEW   규격 input */
    var(--col-qty)           /* 80px  수량 */
    var(--col-price)         /* 120px 단가 */
    var(--col-sum)           /* 100px 합계 */
    var(--col-delete);       /* 32px  삭제 */
  height: var(--row-h);      /* 40px */
  align-items: center;
}
```

### 3.4 규격 input visual

```css
.line-row .spec-input {
  width: 100%;
  height: 28px;
  padding: 0 8px;
  border: 1px solid var(--line-default);
  border-radius: var(--radius-input);  /* 4px */
  font-size: var(--font-row);          /* 14px */
  background: var(--surface-card);
  color: var(--ink-primary);
  text-align: left;
  transition: border var(--motion-focus);
}

.line-row .spec-input:hover {
  border-color: var(--line-hover);
}
.line-row .spec-input:focus {
  outline: 2px solid var(--line-focus);
  outline-offset: -1px;
  border-color: var(--line-focus);
}
.line-row .spec-input::placeholder {
  color: var(--ink-tertiary);  /* "예: 220V" */
}
```

### 3.5 Visual states (1차 계승)

기존 5 states (default / hover / selected / dragging / error) 모두 유지. 규격 input 의 hover/focus 는 모델명 input 과 동일.

### 3.6 thead 갱신

```typescript
const COLUMNS = [
  { key: 'check',   label: '',     width: '40px',  align: 'center' },
  { key: 'drag',    label: '',     width: '24px',  align: 'center' },
  { key: 'no',      label: '#',    width: '24px',  align: 'center' },
  { key: 'model',   label: '모델명', width: 'minmax(160px,2fr)', align: 'left' },
  { key: 'product', label: '품목명', width: 'minmax(160px,2fr)', align: 'left' },
  { key: 'spec',    label: '규격',  width: '100px', align: 'left' },     // NEW
  { key: 'qty',     label: '수량',  width: '80px',  align: 'right' },
  { key: 'price',   label: '단가',  width: '120px', align: 'right' },
  { key: 'sum',     label: '합계',  width: '100px', align: 'right' },
  { key: 'delete',  label: '',     width: '32px',  align: 'center' },
];
```

### 3.7 검증

- [ ] 규격 input 폭 정확히 100px
- [ ] placeholder "예: 220V" 표시
- [ ] maxLength 50 (DB column varchar(50) 일치)
- [ ] 빈 값 허용 (저장 시 NULL/empty 모두 허용)
- [ ] thead "규격" 컬럼 라벨 정확

---

## 4. `<DispatchView>` — A4 portrait 정정 (갱신)

### 4.1 개요

1차 슬라이스의 DispatchView 를 갱신. 결재란 1×5 horizontal + 라인 표 7-col + 본문 14pt + 서명 80×35mm. 사용자 피드백 #3, #5, #6, #7, #8, #9 모두 처리.

상세 CSS spec 은 `print-spec.md` 에 정의. 본 절에서는 컴포넌트 props/구조만.

### 4.2 Props

```typescript
interface DispatchViewProps {
  slip: SlipDetail;
  partner: Partner;
  warehouse: Warehouse;
  /** ACCEPTED 시점 자동 채워진 출고인. 미도달 시 undefined. */
  dispatcher?: { fullName: string; signedAt: string };
  /** INSPECTING 시점 자동 채워진 검수인. 미도달 시 undefined. */
  inspector?: { fullName: string; signedAt: string };
  /** 담당부서 (BE 가 사용자 부서 lookup 후 전달). */
  ownerDepartment?: string;
  /** 담당자 (slip.createdBy 의 fullName). */
  ownerFullName?: string;
}

interface SlipDetail {
  slipNo: string;
  slipDate: string;        // YYYY-MM-DD
  partnerName: string;
  lines: Array<{
    productId: string;
    modelName: string;
    productName: string;
    specification: string | null;  // 신규
    quantity: number;
  }>;
  shippingAddress: string;
  contactPhone: string;
  remarks: string;
}
```

### 4.3 컴포넌트 구조 (JSX)

```typescript
<div className="dispatch-page">
  <header className="dispatch-header">
    <div className="dispatch-brand">
      <span className="logo">SAMSUNG</span>
      <span className="partner-name">{partner.name}</span>
      <span className="slip-no">{slip.slipNo}</span>
    </div>
    <div className="dispatch-roles">
      {/* 1×5 grid */}
      <RoleCell label="담당부서" value={ownerDepartment} />
      <RoleCell label="담당자"   value={ownerFullName} />
      <RoleCell label="출고인"   value={dispatcher?.fullName} time={dispatcher?.signedAt} />
      <RoleCell label="검수인"   value={inspector?.fullName}   time={inspector?.signedAt} />
      <RoleCell label="결재"     value="*" />
    </div>
  </header>

  <table className="dispatch-table">
    <thead>
      <tr>
        <th className="col-month">월</th>
        <th className="col-day">일</th>
        <th className="col-model">모델명</th>     {/* 신규: 분리 */}
        <th className="col-product">품목명</th>   {/* 신규: 분리 */}
        <th className="col-spec">규격</th>
        <th className="col-qty">수량</th>
      </tr>
    </thead>
    <tbody>
      {slip.lines.map(line => (
        <tr key={line.productId}>
          <td>{getMonth(slip.slipDate)}</td>
          <td>{getDay(slip.slipDate)}</td>
          <td className="model-cell">{line.modelName}</td>
          <td className="product-cell">{line.productName}</td>
          <td>{line.specification || '-'}</td>
          <td className="qty">{line.quantity}</td>
        </tr>
      ))}
    </tbody>
    <tfoot>
      <tr>
        <td colSpan={5}>합계</td>
        <td className="qty">{totalQty}</td>
      </tr>
    </tfoot>
  </table>

  <section className="dispatch-section">
    <p><span className="label">배송지:</span><span className="content">{slip.shippingAddress}</span></p>
    <p><span className="label">연락처:</span><span className="content">{slip.contactPhone}</span></p>
    <p><span className="label">특이사항:</span><span className="content">{slip.remarks}</span></p>
    <p className="depart-notice">출발 전 확인: 차량 / 적재 / 동선</p>
  </section>

  <div className="dispatch-signatures">
    <div className="dispatch-sign-box">
      <div className="dispatch-sign-label">용달기사 서명</div>
      <div className="dispatch-sign-area">
        <span className="placeholder">(서명 대기 — Slice C)</span>
      </div>
    </div>
    <div className="dispatch-sign-box">
      <div className="dispatch-sign-label">인수자 서명</div>
      <div className="dispatch-sign-area">
        <span className="placeholder">(서명 대기 — Slice C)</span>
      </div>
    </div>
  </div>
</div>
```

### 4.4 `<RoleCell>` 내부 컴포넌트

```typescript
interface RoleCellProps {
  label: string;
  value?: string;
  time?: string;  // ISO 8601 — HH:mm 만 표시
}

function RoleCell({ label, value, time }: RoleCellProps) {
  return (
    <div className="dispatch-role-cell">
      <div className="dispatch-role-label">{label}</div>
      <div className="dispatch-role-value">
        <span className="name">{value || ''}</span>
        {time && <span className="time">{formatTime(time)}</span>}
      </div>
    </div>
  );
}

function formatTime(iso: string): string {
  // "2026-05-04T14:32:18+09:00" → "14:32"
  return iso.slice(11, 16);
}
```

### 4.5 CSS — `<RoleCell>`

```css
.dispatch-role-cell {
  border: 1px solid #000;
  display: flex;
  flex-direction: column;
  width: var(--print-approval-w-actual);  /* 36mm */
  height: var(--print-approval-h);        /* 22mm */
}

.dispatch-role-label {
  height: var(--print-approval-label-h);  /* 5mm */
  background: var(--print-approval-label-bg);  /* #F0F0F0 */
  font-size: var(--print-text-md);  /* 12pt */
  font-weight: 600;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid #000;
}

.dispatch-role-value {
  height: var(--print-approval-value-h);  /* 17mm */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dispatch-role-value .name {
  font-size: var(--print-text-md);  /* 12pt */
  font-weight: 600;
  max-width: calc(var(--print-approval-w-actual) - 4mm);
  overflow: hidden;
  text-overflow: ellipsis;
}

.dispatch-role-value .time {
  font-size: var(--print-text-xs);  /* 9pt */
  color: #555;
  margin-top: 1mm;
}
```

### 4.6 검증 (QA)

- [ ] 결재란 5칸 균등 폭 (~36mm)
- [ ] 결재란 라벨 영역 5mm + 회색 배경
- [ ] 결재란 값 영역 17mm + 이름/시각 중앙 정렬
- [ ] 출고인 셀 — `dispatcher.fullName` + `formatTime(dispatcher.signedAt)` 표시
- [ ] 검수인 셀 — `inspector.fullName` + `formatTime(inspector.signedAt)` 표시
- [ ] 미도달 단계 (예: ACCEPTED 전) — 출고인 셀 빈 값
- [ ] 라인 표 7-col (월/일/모델/품/규격/수량 + footer 합계)
- [ ] 모델명/품목명 한 행 좌우 (2줄 셀 X)
- [ ] 마지막 빈 열 X
- [ ] 본문 14pt (배송지/연락처/특이사항)
- [ ] 라벨 12pt 700 weight
- [ ] 용달기사/인수자 서명 박스 80×35mm
- [ ] 서명 박스 안 placeholder "(서명 대기 — Slice C)" 표시 (Slice A)
- [ ] A4 portrait 273mm 안에 모든 섹션 (잘리지 않음)
- [ ] 인쇄 미리보기 (Ctrl+P) 1장 1전표 (라인 10건 이하)

---

## 5. 컴포넌트 의존성 그래프

```
SlipDetailPage
  ├── usePageTitle('출고전표 상세', slip.slipNo)  → AppHeader
  ├── <ProgressBar currentStatus={slip.status} branchReason={...} history={...} />  [신규]
  ├── (헤더 정보 카드)
  ├── <LineRow ... onSpecificationChange={...} />  × N  [10-col 갱신]
  └── (결재 정보 카드 — dispatcher/inspector 표시)

SlipFormPage (new mode)
  ├── usePageTitle('새 출고전표')
  └── <LineRow ... onSpecificationChange={...} />  × N  [10-col 갱신]

DispatchPrintPage
  ├── usePageTitle('출고전표 작업지시서', slip.slipNo)
  └── <DispatchView slip={...} dispatcher={...} inspector={...} ownerDepartment={...} ownerFullName={...} />  [갱신]

AppLayout
  └── <AppHeader title={pageTitle.title} meta={pageTitle.meta} user={...} onLogout={...} />  [갱신]
```

---

## 6. BE 가 인용해야 할 spec (참고)

본 디자인 spec 이 요구하는 BE 변경:

1. **`SlipStatus.INSPECTING`** enum 추가 + 전이 규칙 (`PROCESSING → INSPECTING → COMPLETED`)
2. **`Slip.dispatcherUserId / dispatcherSignedAt`** 필드 추가 (ACCEPTED 트랜지션 시 자동 set)
3. **`Slip.inspectorUserId / inspectorSignedAt`** 필드 추가 (INSPECTING 트랜지션 시 자동 set)
4. **`SlipLine.specification`** 필드 추가 (varchar 50, nullable)
5. **`SlipDetailResponse.dispatcher / inspector`** 응답 필드 추가 (user-service lookup 후 fullName 포함)
6. **`SlipDetailResponse.lines[].specification`** 응답 필드 추가
7. **신규 endpoint**: `POST /api/slips/{id}/inspect` (검수 트랜지션 호출)

상세 BE spec 은 BE 팀의 Plan 단계에서 확정.
