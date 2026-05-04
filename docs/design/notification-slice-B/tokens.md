# 디자인 토큰 — Notification Slice B 신규 추가

본 문서는 1차 (`sales-form-polish-slice/tokens.md`) + 2차 (`sales-polish-2-slice/tokens.md`) 토큰을 **그대로 유지** + Slice B 한정 신규 alias 만 추가합니다. 기존 화면 visual regression X.

> FE agent 는 본 spec 을 `clients/web/design-system/src/tokens/tokens.css` 끝에 append 하며, 기존 토큰은 손대지 않습니다.

---

## 1. 신규 토큰 그룹 (3 그룹)

### 1.1 Phone Input (`<PhoneInput>` 신규 컴포넌트)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice B: PhoneInput — 휴대폰 번호 입력
   * 자동 하이픈 + 한국 패턴 검증 (010-XXXX-XXXX)
   * ──────────────────────────────────────────── */

  /* 테두리 */
  --phone-input-border:        var(--line-default);     /* #E1E5EA — 기본 테두리 */
  --phone-input-border-hover:  var(--line-hover);       /* #C9D1D9 — hover */
  --phone-input-border-focus:  var(--line-focus);       /* #3B82F6 — 포커스 (파란 외곽선) */
  --phone-input-border-error:  var(--state-danger);     /* #EF4444 — 패턴 에러 */

  /* 배경 */
  --phone-input-bg:            var(--surface-card);     /* #FFFFFF — 입력 배경 */
  --phone-input-bg-disabled:   var(--surface-subtle);   /* #F4F6F8 — disabled */
  --phone-input-bg-error:      #FEF2F2;                 /* 에러 배경 (subtle red) */

  /* 텍스트 */
  --phone-input-color:         var(--ink-primary);      /* #1A1F2E */
  --phone-input-color-placeholder: var(--ink-tertiary); /* #8A95A4 */
  --phone-input-color-disabled:    var(--ink-tertiary);
  --phone-input-color-error:       var(--state-danger); /* #EF4444 — 에러 메시지 */

  /* 폰트 — 숫자 가독성 우선 */
  --phone-input-font:          var(--font-input);       /* 14px */
  --phone-input-font-feature:  "tnum" 1, "lnum" 1;     /* tabular-nums */

  /* 크기 */
  --phone-input-h:             40px;                    /* 표준 input 높이 */
  --phone-input-pad-x:         12px;
  --phone-input-radius:        var(--radius-input);     /* 4px */

  /* 에러 메시지 */
  --phone-input-error-msg-size: 12px;
  --phone-input-error-msg-gap:  4px;                    /* input 아래 메시지 간격 */
}
```

### 1.2 Copy Button (`<CopyButton>` 신규 컴포넌트)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice B: CopyButton — 클립보드 복사 버튼
   * LinkDispatchListPage [복사] / BatchDetailModal [복사]
   * ──────────────────────────────────────────── */

  /* 배경 */
  --copy-button-bg:            transparent;             /* 기본 투명 (link-style) */
  --copy-button-bg-hover:      var(--action-brand-subtle); /* #DBEAFE — hover */
  --copy-button-bg-active:     #BFDBFE;                 /* active */
  --copy-button-bg-success:    var(--state-success-bg); /* #D1FAE5 — 복사 성공 200ms flash */

  /* 테두리 — 외곽선 없음 (link-style) */
  --copy-button-border:        none;
  --copy-button-border-hover:  none;

  /* 텍스트 / 아이콘 */
  --copy-button-color:         var(--action-brand);     /* #1E40AF — 파란 액션 컬러 */
  --copy-button-color-hover:   var(--action-brand-hover); /* #1D4ED8 */
  --copy-button-color-success: var(--state-success);    /* #10B981 — 200ms flash */

  /* 아이콘 (📋 SVG) */
  --copy-button-icon-size:     16px;
  --copy-button-icon-stroke:   1.5px;
  --copy-button-icon-color:    currentColor;            /* 텍스트 색 상속 */

  /* 크기 */
  --copy-button-h:             32px;                    /* 표 셀 안 컴팩트 */
  --copy-button-pad-x:         8px;
  --copy-button-gap:           4px;                     /* 아이콘 ↔ 라벨 */
  --copy-button-radius:        var(--radius-button);    /* 4px */
  --copy-button-font:          13px;                    /* sm */
  --copy-button-font-weight:   var(--font-weight-medium); /* 500 */

  /* 토스트 (Toast 컴포넌트 토큰 재사용 — 별도 정의 없음) */
}
```

### 1.3 Batch List Row (`<LinkDispatchListPage>` 표 행 색상 분기)

```css
:root {
  /* ────────────────────────────────────────────
   * Slice B: BatchListRow — SMS 발송 상태 시각화
   * sent (smsSentAt != null) 행은 옅은 파랑으로 구분
   * ──────────────────────────────────────────── */

  /* 미발송 행 (default) */
  --batch-list-row-unsent-bg:        var(--surface-card);    /* #FFFFFF */
  --batch-list-row-unsent-bg-hover:  var(--surface-hover);   /* #F4F6F8 */
  --batch-list-row-unsent-color:     var(--ink-primary);     /* #1A1F2E */

  /* 발송완료 행 — 옅은 파랑 (성공 상태 시각화, 단 success-bg #D1FAE5 는 너무 강함) */
  --batch-list-row-sent-bg:          #F0F9FF;                /* sky-50 — 옅은 파랑 */
  --batch-list-row-sent-bg-hover:    #E0F2FE;                /* sky-100 */
  --batch-list-row-sent-color:       var(--ink-primary);     /* #1A1F2E (불변) */
  --batch-list-row-sent-icon:        var(--state-success);   /* #10B981 — ☑ 아이콘 */

  /* 만료 임박 행 (D-Day, plan §3.2 tokenExpiresAt = batchDate+1 → 배송일 당일까지 노출) */
  --batch-list-row-expiring-bg:      #FFFBEB;                /* amber-50 — D-1 옅은 노랑 */
  --batch-list-row-expiring-color:   var(--state-warning);   /* #F59E0B */

  /* 만료된 행 (rare — 자동 그룹 후 며칠 방치) */
  --batch-list-row-expired-bg:       var(--surface-subtle);  /* #F4F6F8 — 회색 */
  --batch-list-row-expired-color:    var(--ink-tertiary);    /* #8A95A4 — 흐릿하게 */

  /* 행 높이 / 구분선 — 기존 토큰 재사용 */
  --batch-list-row-h:                var(--row-h);           /* 40px */
  --batch-list-row-divider:          var(--line-default);    /* #E1E5EA */
}
```

---

## 2. BatchStatusCell 부수 토큰 (1.3 의 일부 — 별도 그룹화 X)

```css
:root {
  /* ────────────────────────────────────────────
   * BatchStatusCell — sent/unsent 분기 셀
   * (batch-list-row-* 토큰을 셀 단위로 적용)
   * ──────────────────────────────────────────── */

  /* unsent: [SMS 발송] 버튼 (PrimaryButton compact) */
  --batch-status-send-btn-h:         28px;                   /* 표 셀 안 컴팩트 */
  --batch-status-send-btn-pad-x:     12px;
  --batch-status-send-btn-bg:        var(--action-brand);    /* #1E40AF */
  --batch-status-send-btn-bg-hover:  var(--action-brand-hover); /* #1D4ED8 */
  --batch-status-send-btn-color:     var(--ink-on-primary);  /* #FFFFFF */
  --batch-status-send-btn-radius:    var(--radius-button);   /* 4px */

  /* sent: ☑ + HH:mm + [재발송] */
  --batch-status-sent-icon-size:     16px;
  --batch-status-sent-icon-color:    var(--state-success);   /* #10B981 */
  --batch-status-sent-time-color:    var(--ink-secondary);   /* #5C6773 */
  --batch-status-resend-link-color:  var(--ink-tertiary);    /* #8A95A4 — 절제된 회색 */
  --batch-status-resend-link-hover:  var(--action-brand);    /* #1E40AF — hover 시 강조 */
  --batch-status-resend-link-size:   12px;

  /* gap */
  --batch-status-cell-gap:           8px;                    /* 아이콘 ↔ 시각 ↔ 링크 */
}
```

---

## 3. 인쇄 토큰 (DispatchView 변경 없음)

본 Slice B 는 **인쇄 토큰 신규 추가 없음**. Slice A 의 `--print-approval-*` / `--print-signature-*` 토큰 그대로 재사용.

driverName 자동 표시는 셀 안 텍스트 주입만 — **CSS 무변경**.

---

## 4. 모바일 공개 페이지 — 자체 mini bundle 색상 (하드코딩)

`mobile-spec.md` §1.3 의 자체 CSS bundle 은 디자인 시스템 의존이 없으므로 다음 hex 를 **하드코딩** 합니다 (1차 토큰과 동기화):

```css
/* mobile.css — 공개 모바일 페이지 (별도 mini bundle) */
:root {
  --m-bg:           #FAFBFC;              /* surface-app */
  --m-card:         #FFFFFF;              /* surface-card */
  --m-border:       #E1E5EA;              /* line-default */
  --m-ink-1:        #1A1F2E;              /* ink-primary */
  --m-ink-2:        #5C6773;              /* ink-secondary */
  --m-ink-3:        #8A95A4;              /* ink-tertiary */
  --m-brand:        #1E40AF;              /* action-brand */
  --m-brand-bg:     #DBEAFE;              /* action-brand-subtle */
  --m-success:      #10B981;              /* state-success */
  --m-danger:       #EF4444;              /* state-danger */
  --m-warning:      #F59E0B;              /* state-warning */

  /* 모바일 spacing */
  --m-pad-page:     16px;
  --m-pad-card:     16px;
  --m-card-gap:     12px;

  /* radius */
  --m-radius-card:  8px;
  --m-radius-btn:   4px;

  /* font */
  --m-font-base:    16px;                 /* 모바일 본문 — 데스크톱 14px 보다 ↑ */
  --m-font-sm:      14px;
  --m-font-xs:      12px;
  --m-font-h-card:  18px;
  --m-font-h-page:  20px;

  /* line-height */
  --m-lh-tight:     1.3;
  --m-lh-normal:    1.5;

  /* shadow — soft elevation */
  --m-elev-card:    0 1px 3px rgba(0, 0, 0, 0.06);

  /* min-tap-target — Apple HIG 44pt / Material 48dp */
  --m-tap-min:      44px;
}
```

> 1차 토큰 변경 시 본 파일도 동기 업데이트 필요. FE agent 는 PR 시 양쪽 동기 확인.

---

## 5. 적용 매핑

| 토큰 그룹 | 컴포넌트 | 화면 |
| --- | --- | --- |
| `--phone-input-*` | `<PhoneInput>` | SlipFormPage 헤더 driverPhone 필드 |
| `--copy-button-*` | `<CopyButton>` | LinkDispatchListPage 표 / BatchDetailModal |
| `--batch-list-row-*` | `<LinkDispatchListPage>` 표 행 | LinkDispatchListPage |
| `--batch-status-*` | `<BatchStatusCell>` | LinkDispatchListPage 표 SMS 컬럼 |
| `--m-*` | mobile.css | 공개 모바일 페이지 (`/d/<token>`) |

---

## 6. dark theme (Phase 2 후속)

Phase 1 (현재) 은 light theme only. 본 슬라이스 토큰은 모두 light 기준. Phase 2 dark theme 추가 시 `--batch-list-row-sent-bg` 등은 `#0F2939` 계열로 invert 필요 — 후속 슬라이스에서 처리.
