# Component Spec — Notification Slice B

본 문서는 Slice B 의 신규/변경 컴포넌트 spec 입니다. FE agent 는 본 spec 에 정확히 일치하는 props / states / visual 을 구현해야 합니다.

---

## 1. `<PhoneInput>` — 한국 휴대폰 번호 입력 (신규)

### 1.1 개요

SlipFormPage 헤더 `driverPhone` 필드 입력용. 자동 하이픈 (`010-XXXX-XXXX`) + 한국 휴대폰 패턴 검증. FormField 호환 (label / error 메시지 표시).

### 1.2 Props

```typescript
interface PhoneInputProps {
  /** 현재 값 — 하이픈 포함 형식 (010-1234-5678). */
  value: string;

  /** 변경 콜백 — 하이픈 포함 정규화된 값 전달. */
  onChange: (value: string) => void;

  /** 에러 메시지 (있으면 빨간 외곽선 + 메시지 표시). */
  error?: string;

  /** disabled 상태 (DRAFT/SAVED 외 단계 = read-only). */
  disabled?: boolean;

  /** placeholder (default: "010-1234-5678"). */
  placeholder?: string;

  /** 폼 제출 시 자동 trim + 검증. */
  onBlur?: () => void;

  /** id (label 연결용). */
  id?: string;

  /** name (form 제출 key). */
  name?: string;

  /** 자동완성 힌트 (default: "tel"). */
  autoComplete?: string;

  /** 필수 입력 (label 옆 * 표시는 FormField 가 처리). */
  required?: boolean;
}
```

### 1.3 자동 하이픈 알고리즘

```typescript
function formatPhone(raw: string): string {
  // 1. 숫자만 추출
  const digits = raw.replace(/\D/g, '').slice(0, 11);

  // 2. 010 / 011 / 016 / 017 / 018 / 019 패턴 분기
  if (digits.length < 4) return digits;
  if (digits.length < 8) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  // 11자: 010-1234-5678
  if (digits.length === 11) return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`;
  // 10자 (구식): 011-123-4567
  return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
}
```

### 1.4 검증 패턴

```typescript
const KOREAN_MOBILE_PATTERN = /^01[016789]-\d{3,4}-\d{4}$/;

function validate(value: string): string | null {
  if (!value) return null;  // 빈 값은 에러 X (required 는 FormField 가 처리)
  if (!KOREAN_MOBILE_PATTERN.test(value)) {
    return '올바른 휴대폰 번호를 입력해주세요. (예: 010-1234-5678)';
  }
  return null;
}
```

### 1.5 States / 시각

| state | 시각 |
| --- | --- |
| default | `--phone-input-bg` 흰색 + `--phone-input-border` 회색 외곽선 |
| hover | `--phone-input-border-hover` 진한 회색 외곽선 |
| focus | `--phone-input-border-focus` 파란 외곽선 + 2px ring |
| error | `--phone-input-border-error` 빨간 외곽선 + `--phone-input-bg-error` 옅은 빨강 + 아래 12px 빨간 메시지 |
| disabled | `--phone-input-bg-disabled` 회색 배경 + `--phone-input-color-disabled` 흐린 텍스트 + cursor: not-allowed |

### 1.6 키보드 / 접근성

- `inputMode="tel"` — 모바일 키패드 숫자 강제 (모바일 fallback 시)
- `pattern="[0-9-]*"` — HTML5 검증
- `aria-invalid={!!error}` / `aria-describedby={error ? errorId : undefined}`
- `maxLength={13}` — 010-1234-5678 (13자)
- Backspace 시 하이픈 자동 제거 (커서 위치 보정)
- Paste 시 자동 정규화

### 1.7 사용 예

```tsx
<FormField label="기사 연락처" required error={errors.driverPhone}>
  <PhoneInput
    id="driver-phone"
    name="driverPhone"
    value={driverPhone}
    onChange={setDriverPhone}
    onBlur={() => validatePhone(driverPhone)}
    error={errors.driverPhone}
    disabled={status !== 'DRAFT' && status !== 'SAVED'}
  />
</FormField>
```

---

## 2. `<CopyButton>` — 클립보드 복사 (신규)

### 2.1 개요

LinkDispatchListPage 표 [복사] 셀, BatchDetailModal 토큰 영역의 복사 버튼. `navigator.clipboard.writeText` + 토스트 "복사됨".

### 2.2 Props

```typescript
interface CopyButtonProps {
  /** 클립보드에 복사할 텍스트 (필수). */
  text: string;

  /** 버튼 라벨 (default: "복사"). icon-only 면 빈 문자열. */
  label?: string;

  /** 복사 성공 시 콜백 (analytics 등). */
  onCopy?: (text: string) => void;

  /** 복사 실패 시 콜백 (clipboard API 미지원 환경 등). */
  onError?: (error: Error) => void;

  /** 토스트 메시지 (default: "복사됨"). */
  toastMessage?: string;

  /** 아이콘만 표시 (label 숨김 — title 속성으로 접근성 보완). */
  iconOnly?: boolean;

  /** 컴팩트 모드 (height 28px — 표 셀 안). */
  compact?: boolean;

  /** disabled. */
  disabled?: boolean;
}
```

### 2.3 동작

```typescript
async function handleCopy() {
  try {
    if (!navigator.clipboard) {
      // 폴백 — execCommand (deprecated 이지만 fallback 용)
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      const ok = document.execCommand('copy');
      document.body.removeChild(textarea);
      if (!ok) throw new Error('execCommand copy failed');
    } else {
      await navigator.clipboard.writeText(text);
    }
    setSuccessFlash(true);  // 200ms 배경 flash
    showToast({ type: 'success', message: toastMessage ?? '복사됨', duration: 1500 });
    onCopy?.(text);
  } catch (e) {
    showToast({ type: 'danger', message: '복사 실패 — 다시 시도해주세요', duration: 3000 });
    onError?.(e as Error);
  }
}
```

### 2.4 시각

| state | 시각 |
| --- | --- |
| default | 투명 배경 + `--copy-button-color` 파란 텍스트/아이콘 |
| hover | `--copy-button-bg-hover` 옅은 파란 배경 |
| active | `--copy-button-bg-active` 진한 파란 배경 |
| success (200ms flash) | `--copy-button-bg-success` 옅은 초록 + `--copy-button-color-success` 초록 텍스트 |
| disabled | opacity 0.5 + cursor: not-allowed |

### 2.5 아이콘

```svg
<!-- 📋 clipboard outline icon -->
<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
  <rect x="3.5" y="2.5" width="9" height="11" rx="1"/>
  <path d="M5.5 1.5h5v2h-5z"/>
</svg>
```

성공 시 (200ms) 체크 아이콘 swap:

```svg
<svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2">
  <path d="M3 8l3.5 3.5L13 5"/>
</svg>
```

### 2.6 사용 예

```tsx
{/* LinkDispatchListPage 표 셀 */}
<CopyButton
  text={`https://sign.samhan-air.com/d/${batch.batchToken}`}
  label="복사"
  compact
/>

{/* BatchDetailModal — 토큰 옆 */}
<CopyButton
  text={`https://sign.samhan-air.com/d/${batch.batchToken}`}
  iconOnly
  toastMessage="배치 링크 복사됨"
/>
```

---

## 3. `<BatchStatusCell>` — SMS 발송 상태 셀 (신규)

### 3.1 개요

LinkDispatchListPage 표의 마지막 컬럼 (`SMS 발송완료`). 미발송 시 [SMS 발송] 버튼 / 발송완료 시 ☑ + HH:mm + [재발송] 링크.

### 3.2 Props

```typescript
interface BatchStatusCellProps {
  /** 발송 시각 (null 이면 unsent). ISO 8601. */
  smsSentAt: string | null;

  /** 마지막 발송 에러 메시지 (있으면 [SMS 발송] 옆 ⚠ 표시). */
  smsLastError?: string | null;

  /** 발송 버튼 클릭. */
  onSendClick: () => Promise<void>;

  /** 재발송 (sent 상태에서 재호출). */
  onResendClick: () => Promise<void>;

  /** 로딩 상태 (Promise pending). */
  loading?: boolean;

  /** 비활성화 (권한 없음 등). */
  disabled?: boolean;
}
```

### 3.3 분기 렌더

#### 3.3.1 unsent (smsSentAt === null)

```
┌────────────────────────────────────┐
│ ☐  [SMS 발송]   ⚠ (smsLastError)   │
└────────────────────────────────────┘
```

- `[SMS 발송]` PrimaryButton compact (height 28px, `--batch-status-send-btn-bg` 파랑)
- ⚠ 아이콘 — `smsLastError` 있을 때만, hover 시 tooltip "마지막 발송 실패: {error}"
- 클릭 → `onSendClick()` → loading spinner inline → 성공 시 sent state 자동 전환

#### 3.3.2 sent (smsSentAt !== null)

```
┌────────────────────────────────────┐
│ ☑ 14:32  [재발송]                   │
└────────────────────────────────────┘
```

- ☑ `--batch-status-sent-icon-color` 초록 체크 (16px)
- `14:32` `--batch-status-sent-time-color` 회색 텍스트 (HH:mm format, ISO 의 시간 부분만)
- `[재발송]` 텍스트 링크 (`--batch-status-resend-link-color` 회색, hover 시 파랑)
- 클릭 → confirm dialog "재발송하시겠습니까? 기사에게 동일 SMS 가 1건 더 발송됩니다." → 확인 시 `onResendClick()`

### 3.4 시각 / 색상

| state | 행 배경 | 셀 |
| --- | --- | --- |
| unsent | `--batch-list-row-unsent-bg` (#FFFFFF) | [SMS 발송] 파란 버튼 |
| sent | `--batch-list-row-sent-bg` (#F0F9FF 옅은 파랑) | ☑ + HH:mm + [재발송] |
| sent + error history | `--batch-list-row-sent-bg` | + ⚠ tooltip |
| expiring (D-Day) | `--batch-list-row-expiring-bg` (#FFFBEB) | unchanged 셀 |

### 3.5 키보드 / 접근성

- 버튼/링크는 모두 tab focusable
- `aria-label="SMS 발송 — 김기사"` (명확한 컨텍스트)
- confirm dialog 는 기존 `<ConfirmDialog>` 컴포넌트 재사용

### 3.6 사용 예

```tsx
<BatchStatusCell
  smsSentAt={batch.smsSentAt}
  smsLastError={batch.smsLastError}
  onSendClick={() => sendSms(batch.id)}
  onResendClick={() => sendSms(batch.id)}  // 재발송도 동일 endpoint
  loading={sendingBatchId === batch.id}
/>
```

---

## 4. 기존 컴포넌트 영향 (변경 없음)

| 컴포넌트 | 변경 |
| --- | --- |
| `<FormField>` | PhoneInput 호환 — error 메시지 표시 위임. **변경 없음** |
| `<Toast>` | CopyButton 호출 — 기존 API 그대로. **변경 없음** |
| `<ConfirmDialog>` | BatchStatusCell 재발송 confirm — 기존 API 그대로. **변경 없음** |
| `<DataTable>` | LinkDispatchListPage 표 — 기존 API 그대로 + 행 className 분기 (sent/unsent) 만 추가. props `getRowClassName?: (row) => string` 신규 추가 (기존 사용처는 무영향) |

`<DataTable>` 의 신규 prop `getRowClassName` 은 **기존 사용처 무영향** (옵션 prop, default 미지정 시 행 className 변동 없음).

---

## 5. 모바일 공개 페이지 — 컴포넌트 (자체 mini bundle)

`mobile.css` + `mobile.js` (≤ 12KB total) 에 다음 inline 컴포넌트만 사용 — 디자인 시스템 의존 없음:

| inline class | 역할 |
| --- | --- |
| `.m-brand-bar` | 상단 brand bar (`--m-brand` 배경, 40px) |
| `.m-batch-card` | 배치 헤더 카드 (driverName + 슬립수 + 배송일) |
| `.m-slip-card` | 슬립 카드 N건 (거래처 + 주소 + 슬립번호 + 합계 + [상세보기]) |
| `.m-detail-btn` | [상세보기 →] 우측 정렬 액션 (Slice C 활성) |
| `.m-footer` | 토큰 만료 + 문의 안내 |
| `.m-error-page` | 410 GONE 화면 (만료 안내 + tap-to-call) |

자세한 spec 은 `mobile-spec.md` §3.

---

## 6. 검증 체크리스트 (QA 인용)

### PhoneInput
- [ ] `010` 입력 → 자동 `010-` (3자 후 하이픈)
- [ ] `01012345678` 입력 → `010-1234-5678` (자동 하이픈 2개)
- [ ] `01112345678` 입력 → 011 도 정상 처리
- [ ] `02-1234-5678` 입력 → 패턴 에러 (휴대폰 아님)
- [ ] disabled 시 read-only 회색 배경
- [ ] paste `010 1234 5678` (공백 포함) → 자동 정규화
- [ ] backspace 시 하이픈 자동 제거 + 커서 위치 보정

### CopyButton
- [ ] 클릭 → 클립보드 복사 + 토스트 "복사됨" 1.5초
- [ ] 200ms success flash (배경 초록 → 원래 색)
- [ ] HTTPS 환경에서만 `navigator.clipboard` 사용 (HTTP 시 fallback execCommand)
- [ ] iOS Safari 14+ 동작 확인

### BatchStatusCell
- [ ] unsent → [SMS 발송] 버튼 표시
- [ ] sent → ☑ HH:mm [재발송] 표시
- [ ] [재발송] confirm dialog 후 호출
- [ ] smsLastError 있으면 ⚠ tooltip 표시
- [ ] loading 중 spinner 표시 + 버튼 disabled
