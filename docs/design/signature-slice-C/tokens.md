# 디자인 토큰 — Signature Slice C

본 문서는 Slice A/B 의 모든 토큰을 **그대로 유지** + Slice C 한정 신규 alias 만 추가합니다.

> **중요**: 본 슬라이스는 모바일 mini bundle (`mobile.css`) 자체완결 정책 유지 — 디자인 시스템 (`@samhan/design-system`) 신규 토큰 X. 단, 데스크톱 SlipDetailPage / DispatchView 인쇄 통합용 토큰만 디자인 시스템에 추가.

---

## 1. 신규 토큰 그룹 (3 그룹)

### 1.1 Signature Canvas (모바일 mini bundle 전용 — 하드코딩)

```css
/* mobile.css — 별도 mini bundle, 하드코딩 hex */
:root {
  /* ────────────────────────────────────────────
   * Slice C: SignaturePad — Canvas 서명 캡처
   * ──────────────────────────────────────────── */

  /* canvas 영역 테두리 / 배경 */
  --signature-canvas-border:        #C9D1D9;          /* line-hover — 점선 */
  --signature-canvas-border-active: #1E40AF;          /* m-brand — 펜 down 시 */
  --signature-canvas-bg:            #FFFFFF;          /* 순백 — 펜 자국 명료 */
  --signature-canvas-bg-disabled:   #F4F6F8;          /* 전송 중 */

  /* 펜 색상 */
  --signature-pen-color:            #000000;          /* 검은색 — 대비 ↑ */
  --signature-pen-width:            2.5px;            /* px (canvas 좌표) */
  --signature-pen-cap:              round;            /* lineCap */
  --signature-pen-join:             round;            /* lineJoin */

  /* placeholder (빈 상태 안내) */
  --signature-placeholder-color:    #8A95A4;          /* m-ink-3 */
  --signature-placeholder-size:     14px;             /* m-font-sm */
  --signature-placeholder-text:     "여기에 서명해주세요";  /* content */

  /* canvas 사이즈 */
  --signature-canvas-w-narrow:      320px;            /* iPhone SE 1세대 */
  --signature-canvas-w-wide:        400px;            /* iPhone 13+ */
  --signature-canvas-h:             200px;            /* 고정 */
  --signature-canvas-radius:        8px;              /* m-radius-card */
  --signature-canvas-padding:       4px;              /* 점선 안 여백 */

  /* 점선 두께 */
  --signature-canvas-dash:          4px;              /* dasharray */
}
```

### 1.2 Signature Meta (모바일 + 데스크톱 공통 — `<SignatureViewer>`)

**모바일 mini bundle (하드코딩)**
```css
/* mobile.css */
:root {
  /* ────────────────────────────────────────────
   * Slice C: SignatureViewer — 서명 메타 정보
   * 인수자 view + 서명 완료 페이지에서 사용
   * ──────────────────────────────────────────── */

  /* 메타 정보 (서명자명 / 시각) */
  --signature-meta-color:           #5C6773;          /* m-ink-2 */
  --signature-meta-size:            14px;             /* m-font-sm */
  --signature-meta-label-color:     #8A95A4;          /* m-ink-3 — "서명자:" 라벨 */

  /* 검증 코드 (hash 앞 8자) */
  --signature-meta-hash-color:      #8A95A4;          /* m-ink-3 */
  --signature-meta-hash-size:       12px;             /* m-font-xs */
  --signature-meta-hash-font:       "SF Mono", "Menlo", "Consolas", "Courier New", monospace;

  /* PNG <img> max sizing */
  --signature-img-max-w:            100%;
  --signature-img-max-h:            240px;            /* 모바일 view 자연 사이즈 */
  --signature-img-bg:               #FFFFFF;
  --signature-img-border:           #E1E5EA;          /* m-border */

  /* 완료 상태 ✓ 아이콘 */
  --signature-complete-icon-color:  #10B981;          /* m-success */
  --signature-complete-icon-size:   48px;
  --signature-complete-msg-color:   #1A1F2E;          /* m-ink-1 */
  --signature-complete-msg-size:    18px;             /* m-font-h-card */
}
```

**데스크톱 디자인 시스템 (`tokens.css` append)**
```css
:root {
  /* ────────────────────────────────────────────
   * Slice C: SignatureViewer — desktop 카드 (SlipDetailPage)
   * ──────────────────────────────────────────── */

  /* 카드 컨테이너 */
  --slip-signature-card-bg:        var(--surface-card);     /* #FFFFFF */
  --slip-signature-card-border:    var(--line-default);     /* #E1E5EA */
  --slip-signature-card-pad:       16px;
  --slip-signature-card-radius:    var(--radius-card);      /* 8px */

  /* 메타 정보 */
  --slip-signature-meta-label-color: var(--ink-tertiary);   /* #8A95A4 */
  --slip-signature-meta-value-color: var(--ink-primary);    /* #1A1F2E */
  --slip-signature-meta-size:        14px;                  /* sm */
  --slip-signature-hash-color:       var(--ink-secondary);  /* #5C6773 */
  --slip-signature-hash-size:        12px;
  --slip-signature-hash-font:        "SF Mono", "Menlo", "Consolas", monospace;

  /* PNG 미리보기 (desktop card) */
  --slip-signature-img-w:            150px;                  /* 고정 사이즈 */
  --slip-signature-img-h:            80px;
  --slip-signature-img-border:       var(--line-default);
  --slip-signature-img-bg:           #FFFFFF;

  /* 무효화 버튼 (MASTER only) */
  --slip-signature-invalidate-color: var(--state-danger);    /* #EF4444 */
  --slip-signature-invalidate-bg:    transparent;
  --slip-signature-invalidate-border: var(--state-danger);
  --slip-signature-invalidate-bg-hover: #FEF2F2;             /* 옅은 빨강 */
  --slip-signature-invalidate-h:     32px;                   /* 컴팩트 */
}
```

### 1.3 Print Signature (DispatchView 인쇄 — Slice A 토큰 재사용 + 신규 1개)

```css
@media print {
  :root {
    /* Slice A 의 --print-signature-* 토큰 재사용 + 신규 1개 */
    --print-signature-img-max-w:    100%;            /* 셀 폭 fit (grid 1fr × 5 균등) */
    --print-signature-img-max-h:    18mm;            /* 30mm 셀에서 메타 12mm 확보 */
    --print-signature-img-fit:      contain;         /* object-fit */

    /* 신규 — 메타 폰트 (Slice A 의 결재선 메타와 동일 토큰) */
    --print-signature-meta-color:   #1A1F2E;         /* ink-primary 흑 */
    --print-signature-meta-size:    8pt;             /* 결재선 안 작은 글씨 */
    --print-signature-meta-date-size: 7pt;
    --print-signature-meta-gap:     2pt;
  }
}
```

---

## 2. mobile mini bundle 동기화 hex 표

Slice B 토큰 (`mobile-spec.md` §1.4) 의 **모든 기존 토큰 변경 없음**. 본 슬라이스는 다음 신규 hex 만 추가:

| 신규 토큰 | hex | 디자인 시스템 매핑 |
| --- | --- | --- |
| `--signature-canvas-border` | #C9D1D9 | line-hover |
| `--signature-canvas-border-active` | #1E40AF | m-brand / action-brand |
| `--signature-canvas-bg` | #FFFFFF | surface-card |
| `--signature-canvas-bg-disabled` | #F4F6F8 | surface-subtle |
| `--signature-pen-color` | #000000 | (신규 — pure black, 서명 대비 우선) |
| `--signature-placeholder-color` | #8A95A4 | m-ink-3 / ink-tertiary |
| `--signature-meta-color` | #5C6773 | m-ink-2 / ink-secondary |
| `--signature-meta-hash-color` | #8A95A4 | m-ink-3 / ink-tertiary |
| `--signature-complete-icon-color` | #10B981 | m-success / state-success |
| `--signature-img-border` | #E1E5EA | m-border / line-default |

> **검증 의무 (FE agent)**: PR 시 `mobile.css` hex 와 `tokens.css` 의 alias 가 일대일 대응되는지 표로 첨부.

---

## 3. 적용 매핑

| 토큰 그룹 | 컴포넌트 | 화면 |
| --- | --- | --- |
| `--signature-canvas-*` | `SignaturePad` (mini bundle) | `/d/{token}/s/{slipNo}` 서명 페이지 |
| `--signature-meta-*` (mobile) | `SignatureViewer` (mini bundle) | `/share/{shareToken}` 인수자 view + 서명 완료 페이지 |
| `--slip-signature-*` (desktop) | `<SignatureCard>` (디자인 시스템) | SlipDetailPage |
| `--print-signature-img-*` + `--print-signature-meta-*` | `<DispatchView>` 인쇄 | DispatchView 인쇄 양식 |

---

## 4. dark theme (Phase 2 후속)

본 Slice C 는 light theme only. dark 시 `--signature-pen-color` 는 **여전히 검은색 유지** (인쇄 매체 호환성), 단 canvas 배경은 흰색 유지 — 서명은 인쇄 가독성 우선이므로 dark mode 에서도 흰 캔버스 + 검은 펜 정책 권장.

---

## 5. 변경 위험 평가

- **Slice A/B 토큰**: 변경 없음 (visual regression 0)
- **신규 토큰**: 모두 신규 prefix (`--signature-*`, `--slip-signature-*`, `--print-signature-img-*`) — 충돌 없음
- **mobile.css 사이즈 영향**: 신규 토큰 hex 약 +20 LOC = +0.3KB (gzip 후 ≤0.1KB) → 12KB budget 내
