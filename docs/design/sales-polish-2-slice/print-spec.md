# Print Spec — A4 작업지시서 (Slice A 정정)

본 문서는 1차 슬라이스 (`sales-form-polish-slice/print-spec.md`) 의 DispatchView 인쇄 spec 을 갱신합니다. 사용자 피드백 #3, #5, #6, #7, #8, #9 모두 반영.

> 1차의 InvoiceView (가로 A4) spec 은 Slice A 변경 대상 아님. DispatchView 만 정정.

---

## 1. 갱신 사항 요약 (1차 → Slice A)

| 영역             | 1차 슬라이스                          | Slice A                                       | 사용자 피드백 |
| ---------------- | ------------------------------------- | --------------------------------------------- | ------------- |
| 본문 폰트        | 11pt                                  | **14pt** (배송지/연락처/특이사항)             | #6            |
| 결재란 layout    | 2×3 grid (5칸, 80×42mm)               | **1×5 horizontal (5칸, 186×22mm)**            | #7            |
| 결재란 칸 폭     | 40mm                                  | **36mm** (5칸 균등 — 186/5 = 37.2)            | #7            |
| 결재란 칸 높이   | 12~18mm                               | **22mm** (라벨 5mm + 값 17mm)                 | #7            |
| 출고인/검수인    | 빈 칸 (수기 입력)                     | **자동 채움** (이름 12pt + 시각 9pt)          | #9            |
| 라인 표 컬럼     | 6 + 마지막 빈 열 (월/일/모델/품목 2줄/규격/수량/빈) | **7 (월/일/모델/품/규격/수량)** — 빈 열 제거 | #3, #5        |
| 모델명/품목명    | 같은 셀 안 2줄 (model 위, product 아래) | **별도 컬럼 좌우 분리**                       | #3            |
| 서명 박스        | 60mm × 40mm                           | **80mm × 35mm** (가로 ↑, 세로 ↓)              | #8            |
| 서명 박스 gap    | 8mm                                   | **6mm**                                       | #8            |
| A4 budget        | 헤더 42mm + 표 가변 + 서명 40mm        | **헤더 35mm + 표 80mm + 주소 50mm + 서명 70mm + footer 30mm = 265mm < 273mm** | #8 |

---

## 2. 공통 print 환경 (1차 계승)

### 2.1 @page

```css
@page dispatch {
  size: A4 portrait;
  margin: var(--print-page-margin);  /* 12mm */
}

.dispatch-page {
  page: dispatch;
  width: var(--print-content-w);     /* 186mm */
  /* min-height: var(--print-content-h);  273mm — 인쇄 시 자동 */
}
```

### 2.2 @media print (1차 계승)

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

  /* 서명 박스 페이지 분할 방지 */
  .dispatch-signatures {
    page-break-inside: avoid;
    break-inside: avoid;
  }
}
```

### 2.3 페이지 크기 표

| 양식           | 방향   | 크기 (mm) | 본문 영역 (mm) | 여백 |
| -------------- | ------ | --------- | -------------- | ---- |
| DispatchView   | portrait  | 210 × 297 | 186 × 273      | 12mm |

---

## 3. DispatchView (작업지시서) — Slice A spec

### 3.1 폰트 / 색상 (갱신)

```css
.dispatch-page {
  font-family: 'Pretendard', 'Pretendard Variable', 'Noto Sans KR', sans-serif;
  font-size: var(--print-text-sm);   /* 11pt — 라인 표 / 메타 기본 */
  line-height: 1.4;
  color: #000;
  background: #FFF;
}

.dispatch-page h1 { font-size: var(--print-text-base); font-weight: 700; }  /* 14pt */
.dispatch-page h2 { font-size: var(--print-text-md);   font-weight: 600; }  /* 12pt */
.dispatch-page small { font-size: var(--print-text-xs); color: #555; }       /* 9pt */
```

### 3.2 헤더 grid (1차 계승, 결재란만 변경)

```css
.dispatch-header {
  display: grid;
  grid-template-rows: auto auto;  /* 1행: 브랜드 / 2행: 결재란 (full width) */
  align-items: start;
  gap: 4mm;
  margin-bottom: 4mm;
  height: var(--print-budget-header);  /* 35mm */
}

.dispatch-brand .logo         { font-size: var(--print-text-base); font-weight: 700; letter-spacing: 0.05em; }
.dispatch-brand .partner-name { font-size: var(--print-text-md); margin-top: 1mm; }
.dispatch-brand .slip-no {
  margin-top: 2mm;
  border: 1px solid #000;
  padding: 2mm 4mm;
  display: inline-block;
  font-size: var(--print-text-md);
}
```

### 3.3 결재란 1×5 horizontal (사용자 피드백 #7)

```css
.dispatch-roles {
  display: grid;
  grid-template-columns: repeat(5, 1fr);  /* 5칸 균등 */
  width: var(--print-content-w);          /* 186mm — 각 칸 ~37mm */
  height: var(--print-approval-h);        /* 22mm */
  border: 1px solid #000;
  border-collapse: collapse;
}

.dispatch-role-cell {
  border-right: 1px solid #000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dispatch-role-cell:last-child {
  border-right: none;
}

.dispatch-role-label {
  height: var(--print-approval-label-h);  /* 5mm */
  background: var(--print-approval-label-bg);  /* #F0F0F0 */
  border-bottom: 1px solid #000;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--print-text-md);  /* 12pt */
  font-weight: 600;
}

.dispatch-role-value {
  height: var(--print-approval-value-h);  /* 17mm */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1mm 2mm;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.dispatch-role-value .name {
  font-size: var(--print-text-md);   /* 12pt */
  font-weight: 600;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dispatch-role-value .time {
  font-size: var(--print-text-xs);   /* 9pt */
  color: #555;
  margin-top: 1mm;
}
```

### 3.4 결재란 출고인/검수인 자동 채움 (사용자 피드백 #9)

```jsx
<div className="dispatch-roles">
  <div className="dispatch-role-cell">
    <div className="dispatch-role-label">담당부서</div>
    <div className="dispatch-role-value">
      <span className="name">{ownerDepartment || ''}</span>
    </div>
  </div>
  <div className="dispatch-role-cell">
    <div className="dispatch-role-label">담당자</div>
    <div className="dispatch-role-value">
      <span className="name">{ownerFullName || ''}</span>
    </div>
  </div>
  <div className="dispatch-role-cell">
    <div className="dispatch-role-label">출고인</div>
    <div className="dispatch-role-value">
      <span className="name">{dispatcher?.fullName || ''}</span>
      {dispatcher && <span className="time">{formatTime(dispatcher.signedAt)}</span>}
    </div>
  </div>
  <div className="dispatch-role-cell">
    <div className="dispatch-role-label">검수인</div>
    <div className="dispatch-role-value">
      <span className="name">{inspector?.fullName || ''}</span>
      {inspector && <span className="time">{formatTime(inspector.signedAt)}</span>}
    </div>
  </div>
  <div className="dispatch-role-cell">
    <div className="dispatch-role-label">결재</div>
    <div className="dispatch-role-value">
      <span className="name">*</span>
    </div>
  </div>
</div>
```

`formatTime("2026-05-04T14:32:18+09:00")` → `"14:32"` (HH:mm).

### 3.5 라인 표 spec (사용자 피드백 #3, #5)

```css
.dispatch-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 4mm;
  max-height: var(--print-budget-table);  /* 80mm — 초과 시 page break */
  font-size: var(--print-text-sm);        /* 11pt */
}

.dispatch-table th,
.dispatch-table td {
  border: 1px solid #000;
  padding: 2mm 3mm;
  vertical-align: middle;
}

.dispatch-table th {
  background: var(--print-thead-bg);  /* #F0F0F0 */
  font-weight: 600;
  text-align: center;
  font-size: var(--print-text-sm);    /* 11pt */
}

/* 7 컬럼 (월 / 일 / 모델명 / 품목명 / 규격 / 수량) — 마지막 빈 열 X */
.dispatch-table .col-month   { width: 8mm;  text-align: center; }
.dispatch-table .col-day     { width: 8mm;  text-align: center; }
.dispatch-table .col-model   { /* flex 1 */ text-align: left; font-weight: 600; }
.dispatch-table .col-product { /* flex 1 */ text-align: left; }
.dispatch-table .col-spec    { width: 18mm; text-align: center; }
.dispatch-table .col-qty     { width: 14mm; text-align: right; font-variant-numeric: tabular-nums; }

/* 모델명/품목명 셀 (1차의 2줄 셀 X) */
.dispatch-table .model-cell {
  font-weight: 600;
  font-size: var(--print-text-sm);
}
.dispatch-table .product-cell {
  font-size: var(--print-text-sm);
  color: #333;
}

.dispatch-table tfoot td {
  font-weight: 700;
  background: #FAFAFA;
  text-align: right;
}

.dispatch-table tfoot td.qty {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
```

#### 3.5.1 라인 표 thead/tbody (JSX)

```jsx
<table className="dispatch-table">
  <thead>
    <tr>
      <th className="col-month">월</th>
      <th className="col-day">일</th>
      <th className="col-model">모델명</th>
      <th className="col-product">품목명</th>
      <th className="col-spec">규격</th>
      <th className="col-qty">수량</th>
    </tr>
  </thead>
  <tbody>
    {slip.lines.map((line, idx) => (
      <tr key={idx}>
        <td className="col-month">{getMonth(slip.slipDate)}</td>
        <td className="col-day">{getDay(slip.slipDate)}</td>
        <td className="col-model model-cell">{line.modelName}</td>
        <td className="col-product product-cell">{line.productName}</td>
        <td className="col-spec">{line.specification || '-'}</td>
        <td className="col-qty">{line.quantity}</td>
      </tr>
    ))}
  </tbody>
  <tfoot>
    <tr>
      <td colSpan={5}>합계</td>
      <td className="col-qty">{totalQty}</td>
    </tr>
  </tfoot>
</table>
```

### 3.6 배송지 / 연락처 / 특이사항 14pt (사용자 피드백 #6)

```css
.dispatch-section {
  margin-top: 4mm;
  border: 1px solid #000;
  padding: 4mm;
  height: var(--print-budget-address);  /* 50mm */
  display: flex;
  flex-direction: column;
  gap: 2mm;
}

.dispatch-section p {
  display: flex;
  align-items: baseline;
  gap: 4mm;
  margin: 0;
}

.dispatch-section .label {
  font-size: var(--print-text-md);   /* 12pt */
  font-weight: 700;
  color: #000;
  flex-shrink: 0;
  min-width: 16mm;
}

.dispatch-section .content {
  font-size: var(--print-text-base); /* 14pt — 사용자 피드백 #6 */
  color: #000;
  line-height: 1.5;
}

.dispatch-section .depart-notice {
  margin-top: auto;  /* 박스 하단 고정 */
  text-align: center;
  font-size: var(--print-text-md);
  font-weight: 600;
  color: #000;
}
```

### 3.7 서명 박스 80×35mm (사용자 피드백 #8)

```css
.dispatch-signatures {
  margin-top: 4mm;
  display: grid;
  grid-template-columns: repeat(2, var(--print-signature-w));  /* 80mm × 2 */
  gap: var(--print-signature-gap);  /* 6mm */
  justify-content: center;          /* 중앙 정렬 (남는 폭 좌우 균등) */
  height: var(--print-signature-h); /* 35mm */
}

.dispatch-sign-box {
  border: 1px solid #000;
  display: flex;
  flex-direction: column;
  width: var(--print-signature-w);  /* 80mm */
  height: var(--print-signature-h); /* 35mm */
}

.dispatch-sign-label {
  padding: 2mm 3mm;
  border-bottom: 1px solid #000;
  font-size: var(--print-text-md);  /* 12pt */
  font-weight: 600;
  text-align: center;
  background: var(--print-thead-bg);  /* #F0F0F0 */
  flex-shrink: 0;
}

.dispatch-sign-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 빈 영역 — Slice A 는 placeholder, Slice B 는 모바일 서명 PNG 삽입 */
}

.dispatch-sign-area .placeholder {
  font-size: var(--print-text-xs);  /* 9pt */
  color: #999;
  font-style: italic;
}
```

### 3.8 footer (회사 정보 — Slice A 신규 명시)

```css
.dispatch-footer {
  margin-top: 4mm;
  text-align: center;
  font-size: var(--print-text-xs);  /* 9pt */
  color: #555;
  border-top: 1px solid #000;
  padding-top: 2mm;
  height: 30mm;  /* var(--print-budget-footer 신규 추가 가능) */
}
```

> 1차 슬라이스에서 footer 영역이 명시되지 않았음. Slice A 에서 30mm 명시.

---

## 4. A4 portrait 273mm budget 검산

```
12mm (margin top)
  ┌─────────────────────────────────────┐
  │ 헤더 (브랜드 + 결재란 1×5)           │ 35mm   ← --print-budget-header
  ├─────────────────────────────────────┤
  │ (gap 4mm)                            │ 4mm
  ├─────────────────────────────────────┤
  │ 라인 표                              │ 80mm   ← --print-budget-table
  ├─────────────────────────────────────┤
  │ (gap 4mm)                            │ 4mm
  ├─────────────────────────────────────┤
  │ 배송지 / 연락처 / 특이사항 / 출발 안내 │ 50mm   ← --print-budget-address
  ├─────────────────────────────────────┤
  │ (gap 4mm)                            │ 4mm
  ├─────────────────────────────────────┤
  │ 용달기사 + 인수자 서명 (80×35 × 2)   │ 35mm   ← --print-budget-signatures (실제 35mm)
  ├─────────────────────────────────────┤
  │ (gap 4mm)                            │ 4mm
  ├─────────────────────────────────────┤
  │ footer (회사 정보)                   │ 30mm   ← --print-budget-footer
  └─────────────────────────────────────┘
12mm (margin bottom)

본문 합계: 35 + 4 + 80 + 4 + 50 + 4 + 35 + 4 + 30 = 246mm
페이지 본문 영역: 273mm
여유: 27mm — 라인 표가 80mm 초과 시 자동으로 27mm 더 확장 가능 (105mm 까지)

✓ 모든 섹션이 A4 portrait 안에 들어옴 (사용자 피드백 #8 해결)
```

---

## 5. 라인 다수 시 페이지 분할 (1차 계승)

### 5.1 라인 10건 이하 — 1장 1전표 (권장)

위 §4 budget 그대로.

### 5.2 라인 20건 (2장)

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

- 1장: 헤더 + 라인 1~12 + (뒤 thead 반복 표시)
- 2장: 라인 13~20 + tfoot + 배송지/연락처/특이사항 + 서명 + footer

### 5.3 헤더 (결재란) 1장만 표시

```css
.dispatch-header {
  /* page-break-after: avoid; */  /* 헤더가 1장에만 — 자연스러움 */
}
```

→ 결재란은 매 페이지 반복 X (1장에만). 2장째는 라인 표 thead 만 반복.

### 5.4 서명 마지막 페이지 고정

```css
.dispatch-signatures {
  page-break-before: avoid;  /* 배송지 영역 직후 */
  page-break-inside: avoid;
}
```

배송지/연락처/특이사항 + 서명 + footer 는 마지막 페이지 1장에 묶임 (page-break-inside: avoid 로 그룹).

```jsx
<div className="dispatch-bottom-group">
  <section className="dispatch-section">...</section>
  <div className="dispatch-signatures">...</div>
  <footer className="dispatch-footer">...</footer>
</div>

/* CSS */
.dispatch-bottom-group {
  page-break-inside: avoid;
  break-inside: avoid;
}
```

> Designer 결정: bottom group wrapper 추가. 1차 슬라이스에는 없었음.

---

## 6. 폰트 fallback (1차 계승)

```css
font-family:
  'Pretendard',
  'Pretendard Variable',
  'Noto Sans KR',
  -apple-system,
  BlinkMacSystemFont,
  'Segoe UI',
  Roboto,
  sans-serif;
```

Pretendard 미설치 머신은 `@font-face` 로 CDN 임베드 (1차 spec 그대로).

---

## 7. 색상 정책 (인쇄 — 1차 계승)

### 7.1 흑백 출력 안전

- 모든 border `#000`
- 텍스트 `#000`
- thead 배경 `#F0F0F0`
- 결재란 라벨 영역 배경 `#F0F0F0`
- 컬러 절제 (사진/로고 외)

### 7.2 컬러 인쇄 가능 시

- SAMSUNG 로고 영역: PNG/SVG 컬러 삽입 가능
- 경고 문구 `#C00` 빨강 (출발전 주의 등)

---

## 8. 검증 (QA)

### 8.1 인쇄 미리보기 (Chromium Ctrl+P)

- [ ] A4 portrait 자동 선택
- [ ] 여백 12mm 정확
- [ ] 1장 1전표 (라인 10건 이하)
- [ ] 라인 20건 시 2장 (thead 반복, 결재란 1장만, 서명/footer 마지막 장 묶음)

### 8.2 결재란

- [ ] 5칸 균등 폭 (~37mm)
- [ ] 라벨 영역 5mm + 회색 배경 #F0F0F0
- [ ] 값 영역 17mm
- [ ] 출고인 셀: dispatcher.fullName (12pt 600) + signedAt HH:mm (9pt secondary)
- [ ] 검수인 셀: inspector.fullName + signedAt HH:mm
- [ ] 미도달 단계 (예: ACCEPTED 미도달) — 출고인 셀 빈 값
- [ ] 이름 6자 초과 시 ellipsis (`오병승순한...`)
- [ ] 라벨 12pt + 값 영역 12pt + 시각 9pt 정확

### 8.3 라인 표

- [ ] 7 컬럼 thead (월/일/모델명/품목명/규격/수량) — 마지막 빈 열 X
- [ ] 모델명/품목명 한 행 좌우 (2줄 셀 X)
- [ ] 모델명 600 weight, 품목명 normal weight
- [ ] 규격 빈 값일 때 `-` 표시
- [ ] tfoot "합계" + 총 수량 우측 정렬 + tabular-nums

### 8.4 배송지 / 연락처 / 특이사항

- [ ] 본문 14pt (사용자 피드백 #6)
- [ ] 라벨 12pt 700 weight
- [ ] 출발 전 안내 문구 박스 하단 고정

### 8.5 서명 박스

- [ ] 정확히 80mm × 35mm × 2 (자/모눈종이로 검증)
- [ ] gap 6mm
- [ ] Slice A: placeholder "(서명 대기 — Slice C)" 9pt italic 회색 표시
- [ ] page-break-inside: avoid (서명 잘리지 않음)

### 8.6 폰트

- [ ] Pretendard 우선 + Noto Sans KR fallback
- [ ] 본문 14pt 이상 가독성 확인 (사용자 피드백 #6)
- [ ] 한글 + 영문 모두 정상 표시 (모델명 영숫자)

### 8.7 흑백 인쇄

- [ ] thead `#F0F0F0` 회색 잘 표시 (`print-color-adjust: exact`)
- [ ] 결재란 라벨 회색 잘 표시
- [ ] border `#000` 모두 표시

### 8.8 PDF 저장

- [ ] Chrome → "PDF로 저장" 동일 layout
- [ ] 출고인/검수인 자동 채움 PDF 에서도 정상 표시
- [ ] 라인 다수 시 PDF 도 페이지 분할 정상

---

## 9. Slice B/C 후속 적용 노트

### 9.1 Slice B — 모바일 서명

- 본 spec 의 `.dispatch-sign-area` 안에 `<img src="signature.png" />` 자동 삽입
- placeholder 텍스트 → 모바일 서명 완료 시 사라짐, 미완료 시 "(모바일 서명 미완료)" 표시
- 서명 박스 80×35mm 그대로 — 박스 안 PNG 자동 fit (`object-fit: contain; max-width: 100%; max-height: 100%`)

### 9.2 Slice C — e-Sign 첨부 PDF

- 본 spec 의 layout 그대로 PDF 변환 (puppeteer 또는 wkhtmltopdf)
- 출고인/검수인 자동 채움 + 모바일 서명 PNG 모두 PDF 에 포함
- 카톡 발송 첨부파일로 사용

### 9.3 Slice C — 카톡 deeplink

- DispatchView 화면에는 "카톡 서명 요청" 버튼 추가 (기존 인쇄 버튼 옆)
- 화면에만 표시, 인쇄/PDF 에는 표시 X (`.no-print`)

본 Slice A 의 print spec 은 후속 변경 최소 (mm/pt 단위 그대로).
