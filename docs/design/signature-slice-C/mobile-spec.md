# Mobile Spec — Signature Slice C (Slice B 확장)

본 문서는 Slice B `mobile-spec.md` 의 **확장** — 기존 정책 (자체 mini bundle, ≤12KB total, UUID 미노출, 410 GONE 등) 모두 그대로 유지하며 신규 라우트 / 신규 mini bundle (`signature.js`) / canvas 캡처 spec 만 추가합니다.

> **변경 가드**: Slice B 의 §1 (디바이스 매트릭스), §3 (410 GONE), §5 (접근성/성능/SEO), §6 (검증 체크리스트) 는 **변경 없음 — 본 슬라이스는 §2 / §4 만 확장**.

---

## 1. 신규 라우트

| route | 인증 | 토큰 | 설명 |
| --- | --- | --- | --- |
| `/d/{token}/s/{slipNo}` | NO AUTH | batchToken (base64url) | 서명 페이지 (Slice C 신규) |
| `/share/{shareToken}` | NO AUTH | shareToken (base64url) | 인수자 view (Slice C 신규) |

기존 Slice B `/d/{token}` 배치 페이지는 **변경 없음**. [상세보기 →] href 만 placeholder 에서 실제 라우트로 활성화.

### 1.1 URL spec

| 컴포넌트 | spec |
| --- | --- |
| `{token}` | base64url, 32~64자, batch token (배송일 +1일 만료) |
| `{slipNo}` | URL-encode `2026/05/05-1` → `2026%2F05%2F05-1` (실제 데이터 형식). 또는 BE 가 `2026-05-05-1` slug 형식으로 변환 권장 |
| `{shareToken}` | base64url, 32~64자, +30일 만료 |

> **slipNo URL 형식 확정 권장 (BE agent 인계)**: `/` 슬래시는 URL path 안에서 escape 필수 → BE 가 응답 시 `slipPublicSlug = "2026-05-05-1"` 형식으로 변환해 응답. 모바일 페이지는 그대로 사용.

---

## 2. API contract

### 2.1 POST `/public/batches/{token}/slips/{slipNo}/signature`

**Request**
```json
{
  "signerName": "김인수",
  "signaturePngBase64": "data:image/png;base64,iVBORw0KGgo...",
  "clientHash": "a3f2b1c9d4e5f6a7b8c9d0e1f2a3b4c5..."
}
```

**Response 200**
```json
{
  "signedAt": "2026-05-05T14:32:18Z",
  "shareToken": "Xy7kP2mQrN4vL8wAbCdEfGhIjKlMnOp",
  "shareTokenExpiresAt": "2026-06-04T14:32:18Z",
  "signatureHash": "a3f2b1c9d4e5f6a7b8c9d0e1f2a3b4c5..."
}
```

**Response 400** — clientHash mismatch (BE 재계산 결과 불일치)
**Response 410** — token 만료
**Response 404** — slip 없음 또는 token 무효 (동일 응답 — 정보 노출 X)

### 2.2 GET `/public/signatures/{shareToken}`

**Response 200**
```json
{
  "slip": {
    "slipNo": "2026-05-05-1",
    "partnerName": "한국전력",
    "deliveryAddress": "서울시 강남구...",
    "deliveryDate": "2026-05-05",
    "lines": [
      { "itemName": "모터 220V", "quantity": 2, "uom": "EA" }
    ],
    "totalAmount": 1250000
  },
  "signature": {
    "signerName": "김인수",
    "signedAt": "2026-05-05T14:32:18Z",
    "signaturePngBase64": "data:image/png;base64,...",
    "signatureHashShort": "a3f2b1c9"
  },
  "shareTokenExpiresAt": "2026-06-04T14:32:18Z"
}
```

UUID (`slip.id`, `batch.id`, `signature.id`) 절대 미포함.

---

## 3. signature.js mini bundle

### 3.1 budget

| 항목 | 사이즈 (gzip) |
| --- | --- |
| `signature.js` total | ≤6KB |
| `mobile.css` 추가분 (canvas 클래스) | ≤0.5KB |

### 3.2 dynamic import 정책

`/d/{token}/s/{slipNo}` 진입 시점에만 `<script src="signature.js" defer>` 또는 dynamic `import('signature.js')` 로딩. `/d/{token}` 배치 리스트 페이지는 로딩 X (bundle 격리).

### 3.3 의존성

- vanilla JS only (signature_pad lib 미사용)
- Web Crypto API (`crypto.subtle.digest`) — iOS 14+ / Android Chrome 90+ 지원
- `navigator.share` (옵셔널 — 미지원 시 clipboard fallback)

### 3.4 canvas 사이즈 분기

```javascript
const wrap = document.querySelector('.m-sig-canvas-wrap');
const isWide = window.innerWidth >= 375;
const w = isWide ? 400 : 320;
const h = 200;
const ratio = window.devicePixelRatio || 1;
canvas.width = w * ratio;
canvas.height = h * ratio;
canvas.style.width = w + 'px';
canvas.style.height = h + 'px';
ctx.scale(ratio, ratio);
```

### 3.5 touch 이벤트 — passive: false

```javascript
canvas.addEventListener('touchstart', onTouchStart, { passive: false });
canvas.addEventListener('touchmove',  onTouchMove,  { passive: false });
canvas.addEventListener('touchend',   onTouchEnd,   { passive: false });

function onTouchStart(e) {
  e.preventDefault();   // 페이지 스크롤 차단 (canvas 영역 안에서만)
  // ...
}
```

`{ passive: false }` 명시 의무 — Chrome 90+ 는 default passive 라 `preventDefault()` 무효. 본 spec 의무 사항.

### 3.6 PNG → base64 변환

```javascript
const dataURL = canvas.toDataURL('image/png');
// "data:image/png;base64,iVBORw0KGgoAAAANSUhEUg..."
```

PNG 사이즈 주의: 320×200 빈 캔버스 ≈ 1KB, 일반 서명 ≈ 5~15KB. BE bytea ≤50KB 제한 안에 들어옴.

### 3.7 SHA-256 클라이언트 해시 (Web Crypto API)

```javascript
async function sha256Hex(dataURL) {
  const base64 = dataURL.split(',')[1];
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  const hashBuf = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(hashBuf))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}
```

BE 가 동일 알고리즘으로 재계산 → mismatch 시 400 반환. 무결성 검증 1차 수단.

---

## 4. UX 패턴

### 4.1 [서명 완료] disabled 조건

```javascript
function updateSubmitState() {
  const nameOk = nameInput.value.trim().length > 0;
  const sigOk = !pad.isEmpty();
  submitBtn.disabled = !(nameOk && sigOk);
}

nameInput.addEventListener('input', updateSubmitState);
pad.onChange = updateSubmitState;   // SignaturePad 콜백
```

### 4.2 전송 중 lock

```javascript
async function submitSignature() {
  submitBtn.disabled = true;
  submitBtn.textContent = '전송 중...';
  canvas.style.pointerEvents = 'none';
  canvas.style.opacity = '0.6';

  try {
    const dataURL = pad.toDataURL();
    const hash = await sha256Hex(dataURL);
    const res = await fetch(`/public/batches/${token}/slips/${slipNo}/signature`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        signerName: nameInput.value.trim(),
        signaturePngBase64: dataURL,
        clientHash: hash
      })
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const json = await res.json();
    location.href = `/share/${json.shareToken}?from=signed`;
  } catch (e) {
    submitBtn.disabled = false;
    submitBtn.textContent = '서명 완료';
    canvas.style.pointerEvents = '';
    canvas.style.opacity = '';
    showToast('전송 실패. 다시 시도해주세요.');
  }
}
```

### 4.3 Web Share API + clipboard fallback

```javascript
async function share(shareToken, slipNo) {
  const url = `${location.origin}/share/${shareToken}`;
  const shareData = {
    title: '출고 인수증',
    text: `[삼한물류] 출고전표 ${slipNo} — 서명 완료`,
    url
  };

  if (navigator.share) {
    try {
      await navigator.share(shareData);
    } catch (e) {
      if (e.name !== 'AbortError') {
        copyToClipboard(`${shareData.text}\n${url}`);
        showToast('링크가 복사되었습니다');
      }
    }
  } else {
    copyToClipboard(`${shareData.text}\n${url}`);
    showToast('링크가 복사되었습니다');
  }
}
```

---

## 5. UUID 미노출 검증 표

| 화면 | UUID 노출 위치 | 검증 |
| --- | --- | --- |
| 서명 페이지 | URL path = `/d/{token}/s/{slipNo}` (UUID X) | DOM `data-*` 검색 0건 |
| 완료 페이지 | URL = `/share/{shareToken}?from=signed` (UUID X) | 동일 |
| 인수자 view | URL = `/share/{shareToken}` (UUID X) | 동일 |
| 응답 JSON | `slip.id` / `signature.id` 모두 미포함 | API spec 검증 |

PR QA 검증: Edge DevTools → Console → `document.body.outerHTML.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g)` → null 또는 빈 배열.

---

## 6. CSP 호환성

기존 Slice B CSP 정책 유지: `default-src 'self'; img-src 'self' data:; script-src 'self'`.

| 기능 | CSP 호환 |
| --- | --- |
| Web Crypto (`crypto.subtle.digest`) | OK — `script-src` 영향 없음 (브라우저 내장) |
| Canvas `toDataURL('image/png')` | OK — `img-src 'self' data:` 안에 data URI 허용 |
| `<img src="data:image/png...">` (인수자 view) | OK — 동일 |
| `navigator.share` | OK — 브라우저 내장 |
| clipboard `navigator.clipboard.writeText` | OK — 동일 |

CSP 변경 **없음**.

---

## 7. 검증 체크리스트 (QA 인계 — Slice B 체크리스트 + 신규)

### 7.1 디바이스 검증 (신규)

- [ ] iPhone SE 1세대 (320×568) 서명 캡처 + 전송 정상
- [ ] iPhone 13 (390×844) 서명 + Web Share 시트 정상
- [ ] Galaxy S22 (360×780) Chrome 서명 + Web Share 정상
- [ ] iPad mini (768×1024) Safari — canvas 400×200 정상, max-width 480px center
- [ ] desktop Chrome — Web Share API 미지원 → clipboard fallback + 토스트

### 7.2 기능 검증 (신규)

- [ ] 빈 canvas 에서 [서명 완료] disabled
- [ ] 인수자명 빈 상태에서 [서명 완료] disabled
- [ ] [다시 서명] 누르면 canvas clear + [서명 완료] disabled 복귀
- [ ] 전송 중 canvas 인터랙션 차단 (opacity 0.6)
- [ ] 전송 성공 후 `/share/{shareToken}` 자동 이동
- [ ] 전송 실패 시 토스트 + canvas 보존
- [ ] 410 응답 시 410 GONE 페이지 (Slice B 재사용)
- [ ] hash mismatch (400) 시 토스트 + canvas clear
- [ ] 인수자 view PNG 정상 표시 + [PNG 다운로드] 정상
- [ ] DOM inspector UUID 0건

### 7.3 성능 검증 (신규)

- [ ] `signature.js` ≤6KB gzip 검증
- [ ] `mobile.css` 추가분 ≤0.5KB gzip 검증
- [ ] 서명 페이지 First Contentful Paint < 1.5s (3G throttling)
- [ ] PNG 평균 사이즈 ≤30KB (BE bytea ≤50KB 안전 마진)

### 7.4 보안 검증 (신규)

- [ ] CSP 정책 변경 없이 정상 동작
- [ ] Web Crypto API SHA-256 해시 64자 hex 검증
- [ ] BE 재계산 hash mismatch 시 400 반환 검증
- [ ] shareToken +30일 만료 후 410 GONE 검증
