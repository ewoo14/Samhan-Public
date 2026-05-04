# Mobile Spec — 공개 모바일 페이지 (`sign.samhan-air.com/d/<token>`)

본 문서는 **공개 모바일 페이지** (인증 없음, 토큰 검증 만) 의 UX / 디바이스 / 컴포넌트 / Slice C 통합 지점 spec 입니다.

> **중요**: 본 페이지는 `@samhan/design-system` 의존 **없음**. 자체 mini bundle (`mobile.css` + `mobile.js`) 로 빌드. 사이즈 ≤ 12KB total.

---

## 1. 디바이스 / 환경 매트릭스

### 1.1 지원 디바이스

| OS | 브라우저 | 최소 버전 | 비고 |
| --- | --- | --- | --- |
| iOS | Safari | 14+ | iPhone 8 이상 (2023~ 현역) |
| iOS | Chrome | 90+ | webview 도 OK |
| Android | Chrome | 90+ | Galaxy S10 이상 |
| Android | Samsung Internet | 14+ | 한국 사용자 비중 ↑ |
| desktop | (any) | - | viewport ≥ 768px 시 폭 제한 (max-width 480px center) |

### 1.2 viewport 매트릭스

| viewport (px) | 기기 예 | 적용 |
| --- | --- | --- |
| 320 | iPhone SE 1세대 | 최소 지원 (좌우 padding 12px) |
| 360 | Galaxy S 세로 | 일반 |
| 375 | iPhone SE 2/3, iPhone 13 mini | 일반 |
| 390 | iPhone 13/14/15 Pro | 일반 |
| 414 | iPhone 13/14 Plus | 일반 |
| 480~768 | tablet vertical | 본문 max-width 480px center |
| ≥ 768 | tablet/desktop | max-width 480px center + 좌우 회색 배경 |

### 1.3 mini bundle 사이즈 budget

| 파일 | 최대 크기 (gzip 후) |
| --- | --- |
| `mobile.html` (entry) | ≤ 4 KB |
| `mobile.css` | ≤ 4 KB |
| `mobile.js` (vanilla, fetch + render) | ≤ 4 KB |
| **total** | **≤ 12 KB** |

> Slice C 에서 서명 캡처 추가 시 `+ signature.js` (canvas 기반) ≤ 8KB 추가 — Slice C 진입 시 dynamic import.

---

## 2. 화면 — 정상 흐름

### 2.1 배치 카드 + 슬립 N건 리스트

```
┌─ 320~414px viewport ─────────────────┐
│                                       │
│ [상단 brand bar — 40px]               │
│  ▒▒▒ 삼한물류 ▒▒▒                     │
│                                       │
│ [본문 — padding 16px]                 │
│  ┌───────────────────────────────┐   │
│  │ 오늘 배송               (h-card│   │
│  │ 김기사 · 3건             18px)│   │
│  │ 2026/05/05 (목)               │   │
│  └───────────────────────────────┘   │
│  ↕ 12px gap                           │
│  슬립 3건                  (sm 14px) │
│  ↕ 8px                                │
│  ┌───────────────────────────────┐   │
│  │ 한국전력          (base 16px) │   │
│  │ 서울시 강남구 테헤란로 123    │   │
│  │ ...                            │   │
│  │                                │   │
│  │ 슬립번호: 2026/05/05-1  (sm)  │   │
│  │ 합계: 1,250,000 원       (sm) │   │
│  │                                │   │
│  │              [상세보기 →]     │   │
│  └───────────────────────────────┘   │
│  ↕ 12px gap                           │
│  ┌───────────────────────────────┐   │
│  │ 삼성전자                       │   │
│  │ ...                            │   │
│  └───────────────────────────────┘   │
│  ↕ 12px gap                           │
│  ┌───────────────────────────────┐   │
│  │ LG화학                         │   │
│  │ ...                            │   │
│  └───────────────────────────────┘   │
│                                       │
│  ─── divider ───                      │
│  토큰 만료: 2026/05/06 23:59 (xs 12px)│
│  문의: 02-XXXX-XXXX                   │
│                                       │
└───────────────────────────────────────┘
```

### 2.2 layout / spacing

```css
/* mobile.css 발췌 */
body {
  background: var(--m-bg);
  color: var(--m-ink-1);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Pretendard", "Noto Sans KR", Roboto, sans-serif;
  font-size: var(--m-font-base);
  line-height: var(--m-lh-normal);
  margin: 0;
  padding: 0;
}

.m-brand-bar {
  background: var(--m-brand);
  color: #FFFFFF;
  height: 40px;
  display: flex;
  align-items: center;
  padding: 0 var(--m-pad-page);
  font-size: var(--m-font-sm);
  font-weight: 600;
  letter-spacing: 0.5px;
}

.m-page {
  max-width: 480px;
  margin: 0 auto;
  padding: var(--m-pad-page);
}

.m-batch-card,
.m-slip-card {
  background: var(--m-card);
  border: 1px solid var(--m-border);
  border-radius: var(--m-radius-card);
  padding: var(--m-pad-card);
  box-shadow: var(--m-elev-card);
  margin-bottom: var(--m-card-gap);
}

.m-batch-card-title {
  font-size: var(--m-font-h-card);
  font-weight: 600;
  margin: 0 0 4px 0;
}

.m-batch-card-meta {
  font-size: var(--m-font-sm);
  color: var(--m-ink-2);
  margin: 0;
}

.m-slip-card-partner {
  font-size: var(--m-font-base);
  font-weight: 600;
  margin: 0 0 4px 0;
}

.m-slip-card-address {
  font-size: var(--m-font-sm);
  color: var(--m-ink-2);
  margin: 0 0 12px 0;
  line-height: var(--m-lh-tight);
}

.m-slip-card-meta {
  font-size: var(--m-font-sm);
  color: var(--m-ink-3);
  margin: 0 0 4px 0;
  font-feature-settings: "tnum" 1, "lnum" 1;
}

.m-slip-card-amount {
  font-size: var(--m-font-sm);
  color: var(--m-ink-1);
  font-weight: 600;
  margin: 0 0 12px 0;
  font-feature-settings: "tnum" 1, "lnum" 1;
}

.m-detail-btn {
  display: inline-block;
  min-height: var(--m-tap-min);   /* 44px tap target */
  line-height: var(--m-tap-min);
  padding: 0 16px;
  background: var(--m-brand-bg);
  color: var(--m-brand);
  border: 1px solid var(--m-brand);
  border-radius: var(--m-radius-btn);
  font-size: var(--m-font-sm);
  font-weight: 600;
  text-decoration: none;
  text-align: center;
  float: right;        /* 우측 정렬 */
}

.m-footer {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--m-border);
  font-size: var(--m-font-xs);
  color: var(--m-ink-3);
  text-align: center;
}

.m-footer a {
  color: var(--m-brand);
  text-decoration: none;
}
```

### 2.3 데이터 매핑 (UUID 노출 X)

| BE 응답 필드 | 화면 표시 | UUID 여부 |
| --- | --- | --- |
| `batch.driverName` | "김기사" | X |
| `batch.batchDate` | "2026/05/05 (목)" | X |
| `batch.slipCount` | "3건" | X |
| `batch.tokenExpiresAt` | "2026/05/06 23:59" | X |
| `slips[].slipNo` | "2026/05/05-1" | X (비즈니스 식별자) |
| `slips[].partnerName` | "한국전력" | X |
| `slips[].deliveryAddress` | "서울시 강남구 테헤란로 123..." | X |
| `slips[].totalAmount` | "1,250,000 원" | X |
| `slips[].id` (UUID) | (DOM data-* 안 절대 미노출 — link 만 토큰 사용) | **숨김** |
| `batch.id` (UUID) | (DOM 미노출) | **숨김** |
| `batch.batchToken` (base64url) | URL path 만 — 화면 텍스트 미표시 | (URL 내부) |

[상세보기] 링크 href: `/d/{batchToken}/s/{slipPublicSlug}` — 슬립 UUID 대신 batchToken + 슬립 순번 (1, 2, 3...) 또는 BE 가 slipNo (`2026-05-05-1`) URL-encode. **UUID 절대 미노출**.

> BE 와 협의: `/public/batches/{token}/slips/{slipNo}` 형식으로 routing — slipNo 는 비즈니스 식별자.

---

## 3. 화면 — 만료 토큰 (410 GONE)

### 3.1 layout

```
┌─ 320~414px viewport ─────────────────┐
│ [상단 brand bar 40px]                 │
│  ▒▒▒ 삼한물류 ▒▒▒                     │
│                                       │
│ [본문 — center, vertical padding 64px]│
│                                       │
│         ⓘ  (48px)                     │
│                                       │
│   링크가 만료되었습니다.   (h 18px)   │
│                                       │
│   배송일이 지나 더 이상   (base 16px) │
│   접근할 수 없습니다.                  │
│                                       │
│   문의가 필요한 경우                   │
│   관리자에게 연락해 주세요.            │
│                                       │
│   ┌─────────────────────────┐         │
│   │  📞 02-XXXX-XXXX         │         │
│   └─────────────────────────┘         │  ← <a href="tel:..."> tap target 44px
│                                       │
└───────────────────────────────────────┘
```

### 3.2 CSS

```css
.m-error-page {
  text-align: center;
  padding: 64px var(--m-pad-page);
}

.m-error-icon {
  font-size: 48px;
  color: var(--m-ink-3);
  margin-bottom: 24px;
}

.m-error-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--m-ink-1);
  margin: 0 0 16px 0;
}

.m-error-body {
  font-size: var(--m-font-base);
  color: var(--m-ink-2);
  line-height: var(--m-lh-normal);
  margin: 0 0 32px 0;
}

.m-error-call {
  display: inline-block;
  min-height: var(--m-tap-min);
  line-height: var(--m-tap-min);
  padding: 0 24px;
  background: var(--m-brand);
  color: #FFFFFF;
  border-radius: var(--m-radius-btn);
  font-size: var(--m-font-base);
  font-weight: 600;
  text-decoration: none;
}
```

### 3.3 보안

- 잘못된 토큰 (404) 도 동일 410 GONE 페이지 사용 → 토큰 존재 여부 정보 노출 X
- robots noindex (`<meta name="robots" content="noindex, nofollow">`) — 검색 엔진 색인 방지

---

## 4. Slice C 후속 통합 지점

본 Slice B 는 **read-only 스켈레톤** 만. Slice C 에서 다음을 활성화합니다.

### 4.1 [상세보기 →] 클릭 시 (Slice C)

| step | 동작 |
| --- | --- |
| 1 | 슬립 카드 [상세보기 →] 탭 |
| 2 | `/d/{batchToken}/s/{slipNo}` 페이지 진입 (Slice C 신규 라우트) |
| 3 | 슬립 단건 상세 + 라인 N건 read-only |
| 4 | 하단 `<canvas>` 서명 캡처 영역 (signature_pad lib 또는 vanilla canvas) |
| 5 | [서명 완료] 버튼 → POST `/public/batches/{token}/slips/{slipNo}/signature` body `{ signatureBase64, recipientName }` |
| 6 | BE 가 PNG 저장 + slip.status = DELIVERED 자동 전이 |
| 7 | [인수자에게 공유] 버튼 활성화 |

### 4.2 [인수자에게 공유] (Slice C — Web Share API)

```javascript
// Slice C 후속
async function shareToRecipient(slipPdfUrl, slipNo) {
  const text = `[삼한물류] 출고전표 ${slipNo} — 서명 완료`;
  const url = slipPdfUrl;

  if (navigator.share) {
    try {
      await navigator.share({ title: '출고전표', text, url });
    } catch (e) {
      if (e.name !== 'AbortError') {
        // 폴백
        copyToClipboard(`${text}\n${url}`);
        showToast('링크가 복사되었습니다');
      }
    }
  } else {
    // Web Share API 미지원 (desktop / 일부 브라우저)
    copyToClipboard(`${text}\n${url}`);
    showToast('링크가 복사되었습니다');
  }
}
```

| 환경 | 동작 |
| --- | --- |
| iOS Safari 14+ | Web Share API 정상 → 카톡/메시지/AirDrop 등 시스템 공유 시트 |
| Android Chrome 90+ | Web Share API → 카톡/SMS/지메일 등 |
| Web Share API 미지원 | clipboard 복사 + 토스트 |

### 4.3 본 Slice B 의 placeholder

Slice B 에서는 [상세보기 →] 링크가 다음 placeholder 페이지로 이동:

```
┌────────────────────────────────────┐
│ ▒▒▒ 삼한물류 ▒▒▒                   │
├────────────────────────────────────┤
│                                    │
│  슬립 상세                         │
│                                    │
│  거래처: 한국전력                  │
│  슬립번호: 2026/05/05-1            │
│  합계: 1,250,000 원                │
│                                    │
│  라인:                             │
│  - 모터 220V (2EA)                 │
│  - 펌프 4HP (1EA)                  │
│  - ...                             │
│                                    │
│  ─────────────────────────────    │
│                                    │
│  서명 캡처는 다음 단계 (Slice C)   │  ← 안내 문구
│  에서 활성화됩니다.                │
│                                    │
│  [목록으로]                        │
│                                    │
└────────────────────────────────────┘
```

---

## 5. 접근성 / 성능

### 5.1 접근성

- `<html lang="ko">` 명시
- `<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">`
- 모든 tap target ≥ 44px (`--m-tap-min`)
- 색상 대비 WCAG AA 준수 (`--m-ink-2` #5C6773 on #FFFFFF — 4.5:1 ↑)
- `<a href="tel:...">` / `<a href="mailto:...">` 정확한 link role
- `:focus-visible` 키보드 포커스 outline (외장 키보드 사용자)

### 5.2 성능

- mini bundle 12KB total → 3G 환경 < 1초 로드
- inline critical CSS (`<style>` in HTML head) — render blocking 최소화
- 이미지 없음 (icon 은 SVG inline 또는 unicode)
- font-family system fallback — 외부 font 다운로드 없음
- `Cache-Control: public, max-age=300` (5분 캐시)

### 5.3 SEO / 보안

- `<meta name="robots" content="noindex, nofollow">` — 토큰 URL 검색 엔진 색인 방지
- `Strict-Transport-Security: max-age=31536000` HTTPS 강제
- `X-Frame-Options: DENY` — clickjacking 방지
- `Content-Security-Policy: default-src 'self'; img-src 'self' data:; script-src 'self'` — XSS 방지

---

## 6. 검증 체크리스트 (QA / DevOps)

### 디바이스 검증

- [ ] iPhone SE 1세대 (320×568) — horizontal scroll 없음
- [ ] iPhone 13 (390×844) — 정상 렌더
- [ ] Galaxy S22 (360×780) Chrome — 정상 렌더
- [ ] iPad mini (768×1024) Safari — max-width 480px center, 좌우 회색
- [ ] desktop Chrome — max-width 480px center

### 기능 검증

- [ ] 정상 토큰 → 배치 카드 + 슬립 N건 표시
- [ ] 만료 토큰 → 410 GONE 화면 표시
- [ ] 잘못된 토큰 → 동일 410 GONE (토큰 존재 노출 X)
- [ ] tap-to-call `tel:` link 정상 동작 (iOS / Android)
- [ ] [상세보기 →] 클릭 → placeholder 페이지 이동 (Slice B)
- [ ] DOM inspector 로 UUID 노출 0건 검증

### 성능 검증

- [ ] mini bundle 12KB total ↓ 검증 (gzip 후)
- [ ] Lighthouse mobile score ≥ 90
- [ ] First Contentful Paint < 1.5s (3G throttling)
- [ ] Largest Contentful Paint < 2.5s

### 보안 검증

- [ ] HTTPS 강제 (HTTP 시 redirect)
- [ ] CSP 헤더 적용
- [ ] noindex 메타 적용
- [ ] iframe 차단 (X-Frame-Options)
