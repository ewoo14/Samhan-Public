# Component Spec — Signature Slice C

본 문서는 Slice C 의 신규 컴포넌트 spec 입니다.

> **중요**: 모바일 컴포넌트 (`SignaturePad` / 모바일 `SignatureViewer`) 는 **mini bundle 자체완결 — 디자인 시스템 신규 X**. 데스크톱 `SignatureCard` 만 디자인 시스템에 추가.

---

## 1. `SignaturePad` (vanilla, mini bundle 전용)

### 1.1 개요

`/d/{token}/s/{slipNo}` 서명 페이지에서 인수자 서명을 Canvas 로 캡처합니다. signature_pad lib **미사용** (의존성 zero), 자체 vanilla JS 구현. touch + mouse 통합 이벤트.

### 1.2 인터페이스 (vanilla — class)

```javascript
/**
 * SignaturePad — Canvas 서명 캡처 (vanilla, 의존성 zero)
 *
 * @example
 *   const pad = new SignaturePad(document.querySelector('#sig-canvas'), {
 *     penColor: '#000000',
 *     penWidth: 2.5,
 *     onChange: (isEmpty) => { ... }
 *   });
 *   pad.clear();
 *   pad.isEmpty();        // true / false
 *   pad.toDataURL();      // "data:image/png;base64,..."
 */
class SignaturePad {
  constructor(canvasEl, options = {}) {
    this.canvas = canvasEl;
    this.ctx = canvasEl.getContext('2d');
    this.penColor = options.penColor || '#000000';
    this.penWidth = options.penWidth || 2.5;
    this.onChange = options.onChange || (() => {});
    this._empty = true;
    this._drawing = false;
    this._bind();
  }

  /** Canvas 비우기 + onChange(true) */
  clear() { /* ... */ }

  /** 비어있는지 판단 (점/획 검출) */
  isEmpty() { return this._empty; }

  /** PNG base64 dataURL 반환 */
  toDataURL() { return this.canvas.toDataURL('image/png'); }

  /** 내부 — touch + mouse 통합 이벤트 바인딩 */
  _bind() { /* ... */ }
}
```

### 1.3 옵션

| key | 타입 | 기본 | 설명 |
| --- | --- | --- | --- |
| `penColor` | string | `#000000` | 펜 색상 (`--signature-pen-color`) |
| `penWidth` | number | `2.5` | 펜 두께 px |
| `onChange` | `(isEmpty) => void` | noop | isEmpty 변경 시 호출 (버튼 disabled 토글용) |

### 1.4 이벤트 매트릭스

| 디바이스 | 이벤트 | 처리 |
| --- | --- | --- |
| iOS Safari (touch) | `touchstart` / `touchmove` / `touchend` | `passive: false` + `preventDefault()` (스크롤 차단) |
| Android Chrome (touch) | 동일 | 동일 |
| desktop (mouse) | `mousedown` / `mousemove` / `mouseup` | 동일 |
| stylus (Apple Pencil) | `touchstart` 로 흡수 (force 무시) | 펜 두께 일정 |

### 1.5 좌표 변환

`canvas.getBoundingClientRect()` 로 화면 좌표 → canvas 좌표 변환. devicePixelRatio (`window.devicePixelRatio`) 고려해 retina 화면에서 stroke 흐림 방지:

```javascript
// 초기화 시
const ratio = window.devicePixelRatio || 1;
canvas.width = displayWidth * ratio;
canvas.height = displayHeight * ratio;
canvas.style.width = displayWidth + 'px';
canvas.style.height = displayHeight + 'px';
ctx.scale(ratio, ratio);
```

### 1.6 isEmpty 판정 알고리즘

```javascript
isEmpty() {
  // pixel 데이터 검사 (모든 alpha 채널 0 = 빈 캔버스)
  const data = this.ctx.getImageData(0, 0, this.canvas.width, this.canvas.height).data;
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] !== 0) return false;
  }
  return true;
}
```

### 1.7 placeholder (빈 상태 안내)

CSS pseudo `::before` 로 처리 (Canvas 안에 직접 그리지 않음 — `isEmpty()` 정확도 보장):

```css
.m-sig-canvas-wrap {
  position: relative;
  width: var(--signature-canvas-w-narrow);
  height: var(--signature-canvas-h);
  border: 2px dashed var(--signature-canvas-border);
  border-radius: var(--signature-canvas-radius);
  padding: var(--signature-canvas-padding);
  background: var(--signature-canvas-bg);
}
.m-sig-canvas-wrap.is-empty::before {
  content: var(--signature-placeholder-text);
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--signature-placeholder-color);
  font-size: var(--signature-placeholder-size);
  pointer-events: none;
}
.m-sig-canvas-wrap:not(.is-empty) {
  border-style: solid;
  border-color: var(--signature-canvas-border-active);
}
```

`onChange(isEmpty)` 콜백에서 wrap 의 `is-empty` 클래스 토글.

### 1.8 SHA-256 클라이언트 해시 (Web Crypto API)

```javascript
async function sha256Hex(base64) {
  const binary = atob(base64.split(',')[1]);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const hashBuf = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(hashBuf))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}
```

POST body 에 `signaturePngBase64` + `clientHash` 동봉 → BE 가 재계산 검증.

### 1.9 사이즈 budget (gzip 후)

| 항목 | LOC | gzip |
| --- | --- | --- |
| `SignaturePad` 클래스 | ~120 | ~2.5KB |
| `sha256Hex` + base64 helper | ~30 | ~0.8KB |
| 페이지 진입 / 전송 / 라우팅 | ~80 | ~2.0KB |
| **total `signature.js`** | **~230** | **~5.3KB** |

`signature.js` 6KB budget 내. dynamic import 로 서명 페이지 진입 시만 로드.

---

## 2. `SignatureViewer` (모바일 + 데스크톱 양쪽 사용)

### 2.1 개요

서명된 PNG 와 메타 정보를 read-only 로 표시합니다.

| 사용처 | 컨테이너 | 구현 |
| --- | --- | --- |
| 모바일 `/share/{shareToken}` | mini bundle | vanilla `<img>` + `<dl>` 메타, mobile.css 클래스 |
| 모바일 서명 완료 페이지 | mini bundle | ✓ 아이콘 + 메타 + [공유] 버튼 |
| 데스크톱 SlipDetailPage | 디자인 시스템 | `<SignatureCard>` React 컴포넌트 |
| 데스크톱 DispatchView 인쇄 | 디자인 시스템 | `<PrintSignatureCell>` (인쇄 전용 CSS) |

### 2.2 모바일 mini bundle 마크업 (vanilla HTML)

```html
<section class="m-sig-viewer">
  <div class="m-sig-viewer-img-wrap">
    <img class="m-sig-viewer-img"
         src="data:image/png;base64,..."
         alt="인수자 서명" />
  </div>
  <dl class="m-sig-viewer-meta">
    <dt>서명자</dt><dd>김인수</dd>
    <dt>서명시각</dt><dd>2026/05/05 14:32</dd>
    <dt>검증코드</dt><dd class="m-sig-viewer-hash">a3f2b1c9</dd>
  </dl>
</section>
```

### 2.3 데스크톱 `<SignatureCard>` Props (React)

```typescript
interface SignatureCardProps {
  /** 서명 정보 — null 이면 "아직 서명되지 않았습니다" 표시 */
  signature: {
    signerName: string;
    signedAt: string;             // ISO 8601
    signaturePngBase64: string;   // "data:image/png;base64,..."
    signatureHash: string;        // 64자 hex
    signatureChannel: 'MOBILE_CANVAS' | 'PAPER_SCAN';
    shareToken: string | null;
    shareTokenExpiresAt: string | null;
  } | null;

  /** MASTER 권한 시 [무효화] 버튼 표시 */
  canInvalidate: boolean;

  /** 무효화 콜백 — confirm dialog 후 reason 과 함께 호출 */
  onInvalidate?: (reason: string) => Promise<void>;

  /** 공유 링크 복사 콜백 */
  onCopyShareLink?: () => void;
}
```

### 2.4 데스크톱 시각 spec

| 영역 | spec |
| --- | --- |
| 카드 컨테이너 | `--slip-signature-card-*` |
| PNG `<img>` | `width: 150px; height: 80px; object-fit: contain; border: 1px solid var(--slip-signature-img-border)` |
| 메타 | `<dl>` 레이블 14px ink-3, 값 14px ink-1 |
| hash | mono 12px, ink-2, 줄바꿈 32자 단위 |
| [무효화] 버튼 | outline danger, 32px, MASTER only |
| 빈 상태 | "아직 서명되지 않았습니다." ink-3 14px center |

### 2.5 무효화 confirm dialog (데스크톱)

`<ConfirmDialog>` 디자인 시스템 컴포넌트 재사용 + 다음 props:

```typescript
{
  title: "서명 무효화",
  body: <>다음 서명을 무효화합니다. ... <textarea minLength={10} maxLength={500} /></>,
  confirmLabel: "⚠ 무효화",
  confirmVariant: "danger",
  cancelLabel: "취소",
  confirmDisabled: reason.length < 10
}
```

---

## 3. 디자인 시스템 신규 컴포넌트 (1개만)

| 컴포넌트 | path | 비고 |
| --- | --- | --- |
| `<SignatureCard>` | `clients/web/design-system/src/components/SignatureCard/` | SlipDetailPage 전용. 모바일은 mini bundle 자체구현 |

모바일 `SignaturePad` / `SignatureViewer` 는 **mini bundle 자체완결** — 디자인 시스템 export X.

---

## 4. 접근성

### 4.1 SignaturePad
- `<canvas role="img" aria-label="서명 입력 영역">` (스크린리더 인지)
- 키보드 사용자 fallback: 본 슬라이스 미지원 (다음 슬라이스 — 외장 키보드 + tab 진입 시 안내)
- focus outline `:focus-visible` 적용 (외장 키보드 사용자)

### 4.2 SignatureViewer
- `<img alt="인수자 서명">` (alt 의무)
- `<dl>` 시멘틱 — 스크린리더 메타 정보 정상 읽기
- 데스크톱 [무효화] 버튼: `aria-label="서명 무효화 (MASTER 권한)"`

### 4.3 색상 대비
- 펜 #000 on #FFF = 21:1 (WCAG AAA)
- 메타 ink-2 #5C6773 on #FFF = 7.5:1 (AA 통과)
- placeholder ink-3 #8A95A4 on #FFF = 3.5:1 (AA Large 통과 — 14px 이상)

---

## 5. 테스트 우선순위 (QA 인계)

| 우선순위 | 시나리오 |
| --- | --- |
| P0 | iPhone SE 1세대 (320×568) 서명 캡처 + 전송 정상 |
| P0 | 빈 canvas 에서 [서명 완료] disabled 검증 |
| P0 | 인수자명 빈 상태에서 [서명 완료] disabled 검증 |
| P0 | UUID DOM inspector 0건 검증 (4 mock 모두) |
| P1 | Galaxy S22 Chrome 서명 + Web Share API 시트 노출 |
| P1 | desktop Safari → Web Share API 미지원 시 clipboard fallback + 토스트 |
| P1 | DispatchView 인쇄 미리보기 PNG 셀 안에 fit |
| P2 | 데스크톱 MANAGER 권한 시 [무효화] 버튼 미노출 검증 |
| P2 | MASTER 무효화 confirm reason <10자 시 disabled |
