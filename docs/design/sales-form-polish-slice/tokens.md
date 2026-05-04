# 디자인 토큰 — 갱신 spec

본 문서는 `clients/web/design-system/src/tokens/tokens.css` 의 갱신 spec 입니다. FE agent 가 본 spec 을 인용하여 토큰을 추가/덮어씁니다.

> **중요**: 본 슬라이스는 SlipFormPage / StockBalanceModal / DispatchView 만 신규 토큰 적용 (Q5=B). 기존 16 컴포넌트는 후속 슬라이스에서 점진 migration.

---

## 1. 신규 토큰 (추가)

기존 토큰과 호환을 깨지 않기 위해 **세만틱 alias 추가** 방식 사용. 기존 `--color-brand-500` 등은 그대로 유지하고, 본 슬라이스 화면은 신규 alias 를 사용합니다.

### 1.1 색상 — Notion / Linear 영감

```css
:root {
  /* ────────────────────────────────────────────
   * sales-form-polish 슬라이스: 모던 미니멀 색상
   * (기존 토큰과 별개의 alias — 점진 적용)
   * ──────────────────────────────────────────── */

  /* Surface — 배경 / 카드 / subtle */
  --surface-app:       #FAFBFC;  /* 앱 배경 (기존 --color-bg 보다 한 단계 옅음) */
  --surface-card:      #FFFFFF;  /* 카드 / 모달 */
  --surface-subtle:    #F4F6F8;  /* read-only field, 비활성 영역 */
  --surface-hover:     #F4F6F8;  /* 행 hover */
  --surface-selected:  #EFF6FF;  /* 행 선택 시 */

  /* Border */
  --line-default:      #E1E5EA;
  --line-hover:        #C9D1D9;
  --line-focus:        #3B82F6;
  --line-selected:     #3B82F6;

  /* Text */
  --ink-primary:       #1A1F2E;
  --ink-secondary:     #5C6773;
  --ink-tertiary:      #8A95A4;
  --ink-on-primary:    #FFFFFF;

  /* Brand (액션) */
  --action-brand:        #1E40AF;  /* primary button */
  --action-brand-hover:  #1D4ED8;  /* hover */
  --action-brand-active: #1E3A8A;  /* active/pressed */
  --action-brand-subtle: #DBEAFE;  /* selected chip */

  /* State */
  --state-success:     #10B981;
  --state-success-bg:  #D1FAE5;
  --state-danger:      #EF4444;
  --state-danger-bg:   #FEE2E2;
  --state-warning:     #F59E0B;
  --state-warning-bg:  #FEF3C7;
  --state-info:        #3B82F6;
  --state-info-bg:     #DBEAFE;
}
```

### 1.2 spacing — 4-base 일관 scale

기존 토큰과 동일하지만 본 슬라이스에서 의미적 alias 를 사용:

```css
:root {
  /* 기존: --space-1 ~ --space-20 (그대로 유지) */

  /* 의미적 alias (sales-form-polish) */
  --space-row-y:     8px;   /* 행 내부 vertical padding */
  --space-row-x:     12px;  /* 행 내부 horizontal padding */
  --space-card:      24px;  /* 카드 padding */
  --space-card-gap:  16px;  /* 카드 내 element gap */
  --space-section:   32px;  /* 섹션 간 gap */
}
```

### 1.3 typography — 한국어 우선

```css
:root {
  /* 폰트 family — 기존 유지 (Pretendard Variable + system fallback) */

  /* sales-form-polish 의미적 alias */
  --font-row:        14px;  /* table row */
  --font-row-num:    14px;  /* table 숫자 — tabular-nums */
  --font-label:      12px;  /* form label */
  --font-input:      14px;  /* form input */
  --font-card-title: 16px;  /* card 제목 */
  --font-page-title: 24px;  /* page 제목 */
  --font-modal-title:18px;  /* modal 제목 */

  /* 숫자 셀 의무 — tabular-nums */
  --font-feature-num: "tnum" 1, "lnum" 1;
}
```

### 1.4 radius — 4 / 8 / 12 만

```css
:root {
  /* 기존 유지 */

  /* sales-form-polish 의미적 alias */
  --radius-input:  4px;
  --radius-button: 4px;
  --radius-card:   8px;
  --radius-modal:  8px;
  --radius-chip:   4px;
}
```

### 1.5 shadow — soft elevation

```css
:root {
  /* 기존 유지 */

  /* sales-form-polish 의미적 alias */
  --elev-card:    0 1px 3px rgba(0, 0, 0, 0.04);
  --elev-popover: 0 4px 12px rgba(0, 0, 0, 0.08);
  --elev-modal:   0 8px 24px rgba(0, 0, 0, 0.12);
}
```

### 1.6 dimensions — 행 높이 / 컬럼 폭

```css
:root {
  --row-h:           40px;   /* table row */
  --row-h-thead:     44px;   /* table thead */
  --col-checkbox:    40px;
  --col-drag:        24px;
  --col-line-no:     24px;
  --col-qty:         80px;
  --col-price:       120px;
  --col-sum:         100px;
  --col-delete:      32px;

  --modal-max-w:     720px;
  --modal-max-h:     80vh;
  --overlay-bg:      rgba(0, 0, 0, 0.6);
}
```

### 1.7 motion — micro-interaction

```css
:root {
  /* 기존: --duration-fast 120ms, --duration-base 180ms */

  --motion-hover:    120ms ease-out;   /* row hover */
  --motion-focus:    120ms ease-out;   /* input focus */
  --motion-modal:    180ms ease-out;   /* modal open */
  --motion-drag:     200ms ease-out;   /* drag transform */
}
```

---

## 2. 기존 vs 신규 비교 표

### 2.1 색상

| 영역      | 기존 토큰                  | 기존 값   | 신규 토큰              | 신규 값   | 비교                             |
| --------- | -------------------------- | --------- | ---------------------- | --------- | -------------------------------- |
| 앱 배경   | `--color-bg`               | `#FFFFFF` | `--surface-app`        | `#FAFBFC` | 카드와 대비 강화 — 카드 띄움     |
| 카드      | `--color-bg`               | `#FFFFFF` | `--surface-card`       | `#FFFFFF` | 동일                             |
| Subtle    | `--color-bg-subtle`        | `#F7F8FA` | `--surface-subtle`     | `#F4F6F8` | 살짝 더 진함 (구분성 ↑)          |
| Selected  | (없음)                     | -         | `--surface-selected`   | `#EFF6FF` | 신규 — 선택 행 배경              |
| Border    | `--color-border`           | `#D6DCE3` | `--line-default`       | `#E1E5EA` | 옅게 (모던 미니멀)               |
| Text Pri  | `--color-text`             | `#0F1216` | `--ink-primary`        | `#1A1F2E` | 살짝 부드럽게 (눈 피로 ↓)        |
| Text Sec  | `--color-text-muted`       | `#4D5562` | `--ink-secondary`      | `#5C6773` | 동일 톤                          |
| Text Tri  | `--color-text-subtle`      | `#6B7280` | `--ink-tertiary`       | `#8A95A4` | 더 옅게 (placeholder 적합)       |
| Brand     | `--color-brand-500`        | `#2D77A8` | `--action-brand`       | `#1E40AF` | **bolder blue** (modern ERP)     |
| Hover     | `--color-brand-600`        | `#235F88` | `--action-brand-hover` | `#1D4ED8` | 동일 패밀리                      |
| Subtle    | `--color-brand-100`        | `#D7E8F4` | `--action-brand-subtle`| `#DBEAFE` | 동일 톤                          |
| Success   | `--color-success`          | `#2A9D8F` | `--state-success`      | `#10B981` | emerald — 더 fresh               |
| Danger    | `--color-danger`           | `#D6504A` | `--state-danger`       | `#EF4444` | 더 saturate (눈에 잘 띔)         |
| Warning   | `--color-warning`          | `#E9A53D` | `--state-warning`      | `#F59E0B` | amber                            |

### 2.2 spacing / radius / shadow

| 영역      | 기존              | 신규                      | 비교                |
| --------- | ----------------- | ------------------------- | ------------------- |
| 행 높이   | (정의 없음)       | `--row-h: 40px`           | dense ERP 표준      |
| 카드 pad  | `--space-5: 20px` | `--space-card: 24px`      | 더 여유 (모던)      |
| 카드 gap  | (인라인)          | `--space-card-gap: 16px`  | 일관성              |
| Input rad | `--radius-md: 4px`| `--radius-input: 4px`     | 동일                |
| Card rad  | `--radius-lg: 8px`| `--radius-card: 8px`      | 동일                |
| Card shad | `--shadow-sm`     | `--elev-card`             | 더 subtle (0.04 ↓)  |
| Modal shad| `--shadow-modal`  | `--elev-modal`            | 더 부드럽게         |

### 2.3 motion

| 영역      | 기존              | 신규               | 비교                     |
| --------- | ----------------- | ------------------ | ------------------------ |
| hover     | `--duration-fast: 120ms` | `--motion-hover: 120ms ease-out` | timing function 명시 |
| modal     | `--duration-base: 180ms` | `--motion-modal: 180ms ease-out` | 동일                |

---

## 3. 적용 우선순위 (FE)

1. **신규 alias 추가** (`tokens.css` 끝에 append, 기존 안 건드림)
2. **SlipFormPage** 의 스타일 신규 alias 사용 (`var(--surface-card)` 등)
3. **StockBalanceModal** 신규 컴포넌트, 처음부터 신규 alias 사용
4. **DispatchView** print spec 은 별도 (`@media print` 흑백)
5. **기존 16 컴포넌트** 는 손대지 않음 (후속 슬라이스)

---

## 4. dark mode 정책

본 슬라이스는 light theme 만 신규 토큰 추가. dark mode 는 후속 슬라이스에서 별도 디자인 검토 후 정의.

(이유: dark mode 테이블/모달 색상은 별도 양산 — 단순 색상 invert 로는 안 됨)

---

## 5. 검증 체크리스트

- [ ] `tokens.css` 변경 시 기존 컴포넌트 스토리북 visual regression 발생하지 않음
- [ ] SlipFormPage 행 높이 정확히 40px
- [ ] 모든 숫자 셀 `font-variant-numeric: tabular-nums` 적용
- [ ] 모달 max-width 720px, overlay rgba(0,0,0,0.6)
- [ ] hover 트랜지션 120ms ease-out
- [ ] focus 시 border 파란색 (`--line-focus`) + outline-offset 2px
