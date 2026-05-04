# 디자인 토큰 — Slice A 신규 추가

본 문서는 1차 슬라이스 (`sales-form-polish-slice/tokens.md`) 의 토큰을 **그대로 유지** + Slice A 한정 신규 alias 만 추가합니다. 기존 화면에 visual regression 발생 X.

> FE agent 는 본 spec 을 `clients/web/design-system/src/tokens/tokens.css` 끝에 append 하며, 기존 토큰은 손대지 않습니다.

---

## 1. 신규 토큰 그룹 (3 그룹)

### 1.1 Progress Bar 토큰 (`<ProgressBar>` 신규 컴포넌트)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice A: 전표 진행 단계 ProgressBar
   * ──────────────────────────────────────────── */

  /* 트랙 / 채움 */
  --progress-track-bg:        var(--surface-subtle);    /* #F4F6F8 회색 트랙 */
  --progress-fill:            var(--action-brand);      /* #1E40AF 파란 채움 (정상 진행) */
  --progress-fill-rejected:   var(--state-danger);      /* #EF4444 빨강 (분기) */
  --progress-fill-canceled:   var(--ink-tertiary);      /* #8A95A4 회색 (분기) */

  /* 단계 노드 */
  --progress-step-size:       32px;                     /* 원형 노드 지름 */
  --progress-step-border:     2px;                      /* 노드 외곽선 */
  --progress-step-bg-done:    var(--action-brand);      /* 완료 노드 배경 (파랑 채움) */
  --progress-step-bg-current: var(--surface-card);      /* 현재 노드 배경 (흰색 + 파란 외곽선) */
  --progress-step-bg-todo:    var(--surface-card);      /* 미진행 노드 배경 (흰색 + 회색 외곽선) */
  --progress-step-border-done:    var(--action-brand);
  --progress-step-border-current: var(--action-brand);
  --progress-step-border-todo:    var(--line-default);

  /* 연결선 */
  --progress-line-width:      2px;                      /* 단계 사이 연결선 두께 */
  --progress-line-color-done: var(--action-brand);
  --progress-line-color-todo: var(--line-default);

  /* 라벨 */
  --progress-label-size:      12px;                     /* 단계 라벨 폰트 */
  --progress-label-current-weight: 600;                 /* 현재 단계 bold */
  --progress-label-color-done:    var(--ink-primary);
  --progress-label-color-current: var(--action-brand);
  --progress-label-color-todo:    var(--ink-tertiary);

  /* spacing */
  --progress-step-gap:        4px;                      /* 노드와 라벨 사이 */
  --progress-section-pad:     24px;                     /* SlipDetailPage 안 ProgressBar 카드 padding */
}
```

### 1.2 Page Header 토큰 (AppLayout 갱신)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice A: AppHeader 동적 화면명
   * ──────────────────────────────────────────── */

  --page-header-h:            56px;                     /* 헤더 고정 높이 */
  --page-header-bg:           var(--surface-card);      /* 흰 배경 */
  --page-header-border:       1px solid var(--line-default);  /* 하단 선 */
  --page-header-pad-x:        24px;                     /* 좌우 padding */

  --page-title-size:          20px;                     /* 화면명 폰트 (h2) */
  --page-title-weight:        600;
  --page-title-color:         var(--ink-primary);

  --page-title-meta-size:     14px;                     /* slipNo bracket 폰트 (예: [2026/05/04-1]) */
  --page-title-meta-color:    var(--ink-secondary);
  --page-title-meta-gap:      8px;                      /* 화면명과 bracket 사이 */
}
```

### 1.3 Print 토큰 (작업지시서 A4 portrait 정정)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice A: DispatchView 인쇄 spec 정정
   * (1차 슬라이스 11pt → 14pt 본문 / 결재란 1×5 / 서명 80×35mm)
   * ──────────────────────────────────────────── */

  /* 본문 폰트 */
  --print-text-base:          14pt;     /* 본문 (배송지/연락처/특이사항) — 사용자 피드백 #6 */
  --print-text-sm:            11pt;     /* 라인 표 / 메타 */
  --print-text-xs:            9pt;      /* 결재란 시각 small */
  --print-text-md:            12pt;     /* 결재란 라벨 + bold label */
  --print-text-lg:            18pt;     /* 일련번호 강조 */

  /* 결재란 (1×5 horizontal) — 사용자 피드백 #7 */
  --print-approval-w:         38mm;     /* 칸 폭 (5칸 × 38mm = 190mm < 본문 186mm 보다 살짝 넘침 → 36mm 권장) */
  --print-approval-w-actual:  36mm;     /* 실제 사용 폭 (186mm / 5 = 37.2mm 균등) */
  --print-approval-h:         22mm;     /* 칸 높이 */
  --print-approval-label-h:   5mm;      /* 라벨 영역 높이 */
  --print-approval-value-h:   17mm;     /* 값 영역 높이 (이름 + 시각 들어감) */

  /* 용달기사/인수자 서명 — 사용자 피드백 #8 */
  --print-signature-w:        80mm;     /* 가로 폭 */
  --print-signature-h:        35mm;     /* 세로 높이 */
  --print-signature-gap:      6mm;      /* 두 박스 사이 gap */

  /* A4 portrait 본문 영역 재배치 */
  --print-page-w:             210mm;
  --print-page-h:             297mm;
  --print-page-margin:        12mm;
  --print-content-w:          186mm;     /* 210 - 12*2 */
  --print-content-h:          273mm;     /* 297 - 12*2 */

  /* 섹션별 높이 budget (273mm 안에 모두) */
  --print-budget-header:      35mm;     /* SAMSUNG/거래처/일련번호 + 결재란 1×5 */
  --print-budget-table:       80mm;     /* 라인 표 (10건 기준, 초과 시 page break) */
  --print-budget-address:     50mm;     /* 배송지 + 연락처 + 특이사항 + 출발전 안내 */
  --print-budget-signatures:  70mm;     /* 용달기사 + 인수자 서명 80×35mm 가로 */
  --print-budget-gap:         8mm;      /* 섹션 간 margin (2 × 4mm) */
  /* 합계: 35 + 80 + 50 + 70 + 8 + 30 footer = 273 ✓ */

  /* 색상 (인쇄) */
  --print-line-color:         #000;     /* border + text */
  --print-thead-bg:           #F0F0F0;  /* 라인 표 thead */
  --print-approval-label-bg:  #F0F0F0;  /* 결재란 라벨 영역 (위쪽 5mm) */
}
```

---

## 2. 1차 슬라이스 토큰과의 관계

| 영역             | 1차 슬라이스 토큰              | Slice A 신규                        | 관계                          |
| ---------------- | ------------------------------ | ----------------------------------- | ----------------------------- |
| Surface          | `--surface-app/card/subtle`    | (재사용)                            | ProgressBar 트랙에 `--surface-subtle` |
| Brand            | `--action-brand`               | (재사용)                            | ProgressBar fill / 현재 단계 |
| State            | `--state-danger/success`       | (재사용)                            | ProgressBar REJECTED 분기     |
| Text             | `--ink-primary/secondary/tertiary` | (재사용)                       | 라벨 색상                     |
| Border           | `--line-default/focus`         | (재사용)                            | ProgressBar 노드 외곽선       |
| Page Header      | (없음)                         | `--page-header-h/bg/...`            | 신규 그룹                     |
| Progress         | (없음)                         | `--progress-*`                      | 신규 그룹                     |
| Print            | (간접 정의 print-spec.md 안)   | `--print-text-base/sm/lg/...`       | CSS 변수로 격상 (변경 용이)   |

---

## 3. 적용 우선순위 (FE)

1. **신규 alias 추가** (`tokens.css` 끝에 append, 기존 안 건드림)
2. **AppLayout 갱신** — `--page-header-*` 토큰 적용
3. **`<ProgressBar>` 신규 구현** — `--progress-*` 토큰 적용
4. **`<DispatchView>` 갱신** — `--print-*` 토큰 적용 (결재란 1×5 / 서명 80×35 / 본문 14pt)
5. **`<LineRow>` 갱신** — 규격 컬럼 추가 (별도 신규 토큰 X, 기존 spacing 재사용)

---

## 4. dark mode 정책

본 Slice A 도 1차 슬라이스 정책 계승 — light theme 만 신규 토큰 추가. dark mode 는 Slice C 이후 별도 디자인 검토.

(이유: ProgressBar 의 분기 색상 — REJECTED `#EF4444` / CANCELED `#8A95A4` — 은 dark mode 대비 시 별도 채도 조정 필요.)

---

## 5. 검증 체크리스트

- [ ] `tokens.css` 변경 시 1차 슬라이스 화면 (SlipFormPage / StockBalanceModal / DispatchView) visual regression 없음
- [ ] ProgressBar 노드 정확히 32px 지름
- [ ] ProgressBar 현재 단계 라벨 600 weight + 파란색
- [ ] AppHeader 정확히 56px 높이 + 하단 1px 선
- [ ] AppHeader 화면명 20px 600 weight, slipNo bracket 14px secondary
- [ ] DispatchView 결재란 5칸 균등 폭 (~37mm)
- [ ] DispatchView 본문 14pt (배송지/연락처/특이사항)
- [ ] DispatchView 용달기사/인수자 서명 박스 80mm × 35mm 정확
- [ ] DispatchView 인쇄 시 273mm 본문 안에 모든 섹션 (잘리지 않음)
