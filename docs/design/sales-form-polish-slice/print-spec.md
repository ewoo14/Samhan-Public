# Print Spec — A4 양식 (DispatchView 세로 / InvoiceView 가로)

본 문서는 인쇄 양식의 정확한 spec — 페이지 크기, 여백, 폰트, 표 grid, 서명 박스 치수 — 을 정의합니다. FE 구현 시 본 spec 의 mm 단위를 그대로 CSS 에 사용해야 합니다.

---

## 1. 공통 print 환경

### 1.1 @page 규칙

```css
@page {
  margin: 12mm;
}

@page :first {
  /* 첫 페이지만 별도 여백이 필요할 경우 */
}
```

### 1.2 @media print

```css
@media print {
  body { background: white; margin: 0; padding: 0; }
  .no-print { display: none !important; }

  /* 색상 강제 출력 (배경/border 흑백 출력 안전) */
  * {
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }

  /* 표 페이지 분할 방지 (행 단위) */
  tr { page-break-inside: avoid; }
  thead { display: table-header-group; }
  tfoot { display: table-footer-group; }
}
```

### 1.3 페이지 크기 표

| 양식           | 방향   | 크기 (mm)    | 본문 영역 (mm) | 여백 |
| -------------- | ------ | ------------ | -------------- | ---- |
| DispatchView   | portrait  | 210 × 297 | 186 × 273      | 12mm |
| InvoiceView    | landscape | 297 × 210 | 273 × 186      | 12mm |

---

## 2. DispatchView (작업지시서) — 세로 A4

### 2.1 @page

```css
@page dispatch {
  size: A4 portrait;
  margin: 12mm;
}

.dispatch-page {
  page: dispatch;
  width: 186mm;
  /* 297mm - 24mm = 273mm 까지 자유 사용 (1장 권장) */
}
```

### 2.2 폰트 / 색상

```css
.dispatch-page {
  font-family: 'Pretendard', 'Noto Sans KR', sans-serif;
  font-size: 11pt;
  line-height: 1.4;
  color: #000;
  background: #FFF;
}

.dispatch-page h1 { font-size: 14pt; font-weight: 700; }
.dispatch-page h2 { font-size: 12pt; font-weight: 600; }
.dispatch-page small { font-size: 9pt; color: #333; }
```

### 2.3 헤더 grid

```css
.dispatch-header {
  display: grid;
  grid-template-columns: 1fr 80mm;  /* 좌(브랜드/날짜) - 우(담당박스) */
  align-items: start;
  gap: 6mm;
  margin-bottom: 6mm;
}

.dispatch-brand .logo { font-size: 14pt; font-weight: 700; letter-spacing: 0.05em; }
.dispatch-brand .partner-name { font-size: 12pt; margin-top: 2mm; }
.dispatch-brand .slip-no {
  margin-top: 4mm;
  border: 1px solid #000;
  padding: 2mm 4mm;
  display: inline-block;
  font-size: 11pt;
}
```

### 2.4 담당 박스 grid (이미지 2 매치)

```
┌───────────┬─────────┐
│ 담당부서   │ 담당자   │   row 1 (12mm)
├───────────┼─────────┤
│ 출고인    │ 검수인   │   row 2 (12mm)
├───────────┴─────────┤
│ 결재 (full row)     │   row 3 (18mm)
│       *             │
└─────────────────────┘
total: 80mm × 42mm
```

```css
.dispatch-roles {
  display: grid;
  grid-template-columns: 40mm 40mm;
  border: 1px solid #000;
}
.dispatch-role-box {
  border: 1px solid #000;
  padding: 2mm 3mm;
  min-height: 12mm;
  display: flex;
  flex-direction: column;
}
.dispatch-role-box.full {
  grid-column: 1 / -1;
  min-height: 18mm;
}
.dispatch-role-label {
  font-size: 9pt;
  color: #555;
  margin-bottom: 1mm;
}
.dispatch-role-value {
  font-size: 11pt;
  font-weight: 500;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
```

### 2.5 라인 표 spec

```css
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
  vertical-align: middle;
}

.dispatch-table th {
  background: #F0F0F0;
  font-weight: 600;
  text-align: center;
}

/* 컬럼 폭 */
.dispatch-table .col-month  { width: 8mm;  text-align: center; }
.dispatch-table .col-day    { width: 8mm;  text-align: center; }
.dispatch-table .col-product{ /* flex */ }
.dispatch-table .col-spec   { width: 24mm; text-align: center; }
.dispatch-table .col-qty    { width: 18mm; text-align: right; font-variant-numeric: tabular-nums; }

/* 모델명 + 품목명 2줄 셀 */
.dispatch-table .product-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}
.dispatch-table .product-cell .model-name {
  font-weight: 600;
  font-size: 10pt;
}
.dispatch-table .product-cell .product-name {
  color: #333;
  font-size: 9pt;
  margin-top: 0.5mm;
}

.dispatch-table tfoot td {
  font-weight: 700;
  background: #FAFAFA;
}
```

### 2.6 배송지 / 연락처 / 특이사항

```css
.dispatch-section {
  margin-top: 4mm;
  border: 1px solid #000;
  padding: 3mm;
}
.dispatch-section .label {
  font-size: 9pt;
  color: #555;
  margin-bottom: 1mm;
}
.dispatch-section .content {
  font-size: 10pt;
  line-height: 1.5;
}
```

### 2.7 서명 박스 (필수 spec)

```css
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
  text-align: center;
  background: #F0F0F0;
}
.dispatch-sign-area {
  width: 60mm;
  height: 40mm;
  /* 빈 영역 — 사용자가 종이에 직접 서명 */
}
```

### 2.8 안내 문구

```css
.dispatch-notice {
  margin-top: 4mm;
  text-align: center;
  font-size: 11pt;
  color: #000;
}
.dispatch-warning {
  margin-top: 2mm;
  text-align: center;
  font-size: 10pt;
  color: #C00;
  font-weight: 500;
}
```

---

## 3. InvoiceView (거래명세서) — 가로 A4 (참고)

### 3.1 @page

```css
@page invoice {
  size: A4 landscape;
  margin: 12mm;
}

.invoice-page {
  page: invoice;
  width: 273mm;
  font-size: 10pt;
}
```

### 3.2 본 슬라이스 영향도

InvoiceView 는 본 슬라이스 변경 대상 **아님**. 기존 가로 양식 그대로 유지. DispatchView 만 가로 → 세로 정정.

(이유: 거래명세서는 회계 표준상 가로 + 다단 컬럼 — A4 가로 적합. 작업지시서는 단일 표 + 서명 + 메모 — A4 세로 적합.)

---

## 4. 폰트 fallback

### 4.1 우선순위

```css
font-family:
  'Pretendard',          /* 1순위 — 한글 최적화 */
  'Pretendard Variable', /* variable font */
  'Noto Sans KR',        /* 2순위 — 시스템 fallback */
  -apple-system,
  BlinkMacSystemFont,
  'Segoe UI',
  Roboto,
  sans-serif;
```

### 4.2 인쇄 시 폰트 embedding

브라우저 인쇄에서 폰트는 자동 임베드. 단, Pretendard 미설치 머신에서는 Noto Sans KR fallback 으로 표시되므로 글자 폭 약간 차이 가능.

→ 프로덕션에서는 `@font-face` 로 Pretendard CDN 임베드 권장:
```css
@font-face {
  font-family: 'Pretendard';
  font-weight: 400;
  src: url('https://cdn.jsdelivr.net/gh/orioncactus/pretendard/dist/web/static/woff2/Pretendard-Regular.woff2') format('woff2');
}
```

---

## 5. 색상 정책 (인쇄)

### 5.1 흑백 출력 안전

- 모든 border `#000`
- 텍스트 `#000` (주황/빨강 절제 — 경고 문구만 `#C00`)
- thead 배경 `#F0F0F0` (회색 — 흑백 인쇄 시 옅은 회색)
- 컬러 절제: 사진/로고 외 컬러 잉크 사용 X

### 5.2 컬러 인쇄 가능 시

- SAMSUNG 로고 영역: 향후 PNG/SVG 로고 삽입 가능
- 경고 문구 `#C00` 빨강

---

## 6. 페이지 분할

### 6.1 라인 표

```css
.dispatch-table tbody tr {
  page-break-inside: avoid;
  break-inside: avoid;
}
.dispatch-table thead {
  display: table-header-group;  /* 매 페이지 thead 반복 */
}
.dispatch-table tfoot {
  display: table-footer-group;  /* 마지막 페이지에만 tfoot */
}
```

### 6.2 서명 박스

서명 박스 1쌍 (60mm × 40mm × 2 + gap 8mm = 128mm 폭) 은 페이지 하단 고정. 표가 길어 페이지가 넘치면 서명은 마지막 페이지에 자동 위치.

```css
.dispatch-signatures {
  page-break-inside: avoid;
  break-inside: avoid;
}
```

---

## 7. 검증 (QA)

- [ ] 인쇄 미리보기 (Chromium `Ctrl+P`) 에서 A4 portrait 자동 선택
- [ ] 여백 12mm 정확
- [ ] 1장 1전표 (라인 10건 이하 시)
- [ ] 라인 20건 시 페이지 2장, thead 반복, 서명 마지막 페이지
- [ ] 서명 박스 정확히 60mm × 40mm (자/모눈종이로 검증)
- [ ] 폰트 Pretendard fallback Noto Sans KR (Pretendard 없는 머신)
- [ ] PDF 저장 시 동일 layout (Chrome → PDF로 저장)
- [ ] 흑백 인쇄에서 thead 회색 잘 표시 (`print-color-adjust: exact`)
