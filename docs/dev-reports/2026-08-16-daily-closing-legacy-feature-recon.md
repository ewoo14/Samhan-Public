```text
cwd   C:/dev/Samhan-Public   (main, 읽기 전용)
```

# 일마감 레거시 기능 답습 4건 기획 정찰

- 정찰일: 2026-08-16
- 정찰 범위: 저장소 정적 소스만 조회
- 레거시 정본: `tools/legacy-gas/일마감 프로그램/Index.html`
- 현행 정본: `clients/desktop/src/renderer/routes/DailyClosingPage.tsx`, `clients/desktop/src/renderer/api/closingApi.ts`, `services/slip-service`
- 안전 조건: 제품 코드·DB·컨테이너·Git 상태를 변경하지 않았다. 이 보고서만 작성했다.

## 🚩 금액에 닿는 결론부터

### M-1. GAS 화면식과 현행 영속식은 수량 2 이상에서 공급가액·부가세가 달라질 수 있다

GAS 편집 직후 원문은 **VAT 포함 단가 1개를 먼저 분리**한다.

`tools/legacy-gas/일마감 프로그램/Index.html:1233-1237`

```js
let unit = rowData['단가(VAT포함)'];
rowData['합계'] = unit * qty;
rowData['공급가액'] = Math.round(unit / 1.1);
rowData['부가세'] = unit - rowData['공급가액'];
rowData['총계'] = unit * qty;
```

현행 화면도 같은 per-unit 식이다.

`clients/desktop/src/renderer/routes/DailyClosingPage.tsx:404-410`

```ts
const supply = Math.round(next.unit / 1.1)
return {
  ...next,
  supply,
  vat: next.unit - supply,
  total: next.unit,
}
```

하지만 저장 시 도메인은 **VAT 포함 단가×수량을 먼저 원 단위 반올림한 뒤 라인 합계를 분리**한다.

`services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SlipLine.java:533-545`

```java
/** 일마감 금액 전용 경로에서 VAT 포함 단가와 권위 금액을 함께 갱신한다. */
public void changeUnitPriceWithVat(BigDecimal newUnitPriceWithVat) {
    validateUnitPrice(newUnitPriceWithVat);
    BigDecimal lineInclVat = newUnitPriceWithVat.multiply(BigDecimal.valueOf(this.quantity))
            .setScale(0, RoundingMode.HALF_UP);
    VatAmountCalculator.Split vatSplit = VatAmountCalculator.splitVatInclusive(
            lineInclVat, RoundingMode.HALF_UP);
    this.lineTotal = vatSplit.supplyAmount();
    this.supplyAmount = vatSplit.supplyAmount();
    this.vatAmount = vatSplit.vatAmount();
    this.unitPrice = this.supplyAmount.divide(BigDecimal.valueOf(this.quantity), 2,
            RoundingMode.HALF_UP);
    this.unitPriceWithVat = newUnitPriceWithVat.setScale(2, RoundingMode.HALF_UP);
```

예를 들어 VAT 포함 단가가 100원이고 수량이 2라면 GAS/현행 편집 화면은 공급가액 91원·부가세 9원을 행에 표시하지만, 현행 저장 도메인은 라인 합계 200원을 분리한다. 새로고침 전후 숫자가 달라질 수 있으므로 **어느 식이 업무 정본인지 결정 전 구현하면 안 된다.**

### M-2. 출고가 편집 동작에 기존 결정문과 GAS·현행 코드가 충돌한다

- GAS와 현행 화면 코드: 출고가를 바꾸면 **단가는 그대로**, 할인율만 다시 계산한다.
- `docs/decisions/2026-08-15-daily-closing-legacy-parity.md:360`: 출고가를 바꾸면 “할인율·단가가 다시 계산”된다고 확정되어 있다.
- 현행 저장: 출고가와 할인율은 계산 근거/감사값이고, 실제 영속되는 원본 금액은 VAT 포함 단가뿐이다(`DailyClosingAmountUpdateService.java:52,88-95`).

따라서 출고가 편집 시 단가를 유지할지, 다른 규칙으로 단가까지 바꿀지는 업무 재확인이 필요하다.

### M-3. 금액 관련 기존 확정 규칙과의 대조

| 확정 규칙 | 4개 기능과의 관계 | 판정 |
|---|---|---|
| 선결제 할인은 견적 총액을 깎지 않고 표기만 한다 | 일마감 `recalcRow`는 단가·출고가·할인율만 계산하며 선결제 입력을 참조하지 않는다 | 직접 충돌 없음. 선결제 표기를 단가 변경으로 변환하면 안 됨 |
| 구제품 할인율 0.5, 카드 수수료 3% | 4개 조작의 계산식에는 두 설정이 등장하지 않는다 | 직접 충돌 없음. 이 편집기로 해당 설정을 재계산하거나 덮어쓰면 안 됨 |
| 금지된 DC 명칭은 사용하지 않는다 | 레거시 4개 조작과 현행 대응 코드에는 그 명칭이 계산축으로 등장하지 않는다 | 새 UI·필터명에도 도입하지 않음 |
| 운임·절삭은 제외 대상이 아니다 | 현행 조회·편집 가능 판정은 품목명으로 제외하지 않는다. 레거시의 운임·절삭 `확인=true` 판정은 금액 편집 제외 규칙이 아니다 | 현행 방향 유지 |

---

## ① 다중선택

### 레거시 원문

선택 범위와 방식 — 일반/Shift 직사각형/Ctrl·Cmd 토글/마우스 드래그. 이동 셀과 소계·합계행은 제외한다.

`tools/legacy-gas/일마감 프로그램/Index.html:364-411`

```js
function setupExcelEvents() {
  document.addEventListener('mousedown', (e) => {
    let td = e.target.closest('td');
    if (!td || !td.closest('.custom-table')) {
      document.querySelectorAll('.selected').forEach(el => el.classList.remove('selected'));
      calcSelectionSum();
      return;
    }
    if (td && td.dataset.col === '이동') return;
    
    if (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'SELECT' || document.activeElement.tagName === 'TEXTAREA') {
       if (td && td.contains(document.activeElement)) return;
    }
    
    currentTable = td.closest('table');
    if (td.classList.contains('total-row') || td.parentElement.classList.contains('total-row') || td.parentElement.classList.contains('subtotal-row')) return;

    let targetR = parseInt(td.dataset.logRow);
    let targetC = parseInt(td.dataset.logCol);
    if(isNaN(targetR) || isNaN(targetC)) return;

    if (e.shiftKey && startR !== -1 && startC !== -1) {
      let minR = Math.min(startR, targetR), maxR = Math.max(startR, targetR);
      let minC = Math.min(startC, targetC), maxC = Math.max(startC, targetC);
      
      document.querySelectorAll('.selected').forEach(el => el.classList.remove('selected'));
      currentTable.querySelectorAll('td[data-log-row]').forEach(cell => {
         let cr = parseInt(cell.dataset.logRow);
         let cc = parseInt(cell.dataset.logCol);
         let crs = parseInt(cell.dataset.logRowSpan);
         let ccs = parseInt(cell.dataset.logColSpan);
         if (!(cc > maxC || (cc + ccs - 1) < minC || cr > maxR || (cr + crs - 1) < minR)) {
             cell.classList.add('selected');
         }
      });
    } else if (e.ctrlKey || e.metaKey) {
      isSelecting = true;
      startR = targetR;
      startC = targetC;
      td.classList.toggle('selected');
    } else {
      isSelecting = true;
      startR = targetR;
      startC = targetC;
      document.querySelectorAll('.selected').forEach(el => el.classList.remove('selected'));
      td.classList.add('selected');
    }
    calcSelectionSum();
  });
```

고른 뒤 숫자 합계를 보고, TSV로 복사한다.

`tools/legacy-gas/일마감 프로그램/Index.html:344-360,446-479`

```js
function calcSelectionSum() {
  let sum = 0;
  document.querySelectorAll('.selected').forEach(td => {
    if (td.style.display === 'none') return;
    let val = '';
    let input = td.querySelector('input');
    if (input) val = input.value;
    else val = td.innerText;
    
    let num = Number(val.replace(/,/g, ''));
    if (!isNaN(num)) sum += num;
  });
  
  let activeTabId = tabsInfo.find(t => document.getElementById(t.id).classList.contains('active'))?.dataKey;
  if (activeTabId) {
    let displayEl = document.getElementById('sum_display_' + activeTabId);
    if (displayEl) displayEl.innerText = '합계: ' + sum.toLocaleString();
  }
}

document.addEventListener('copy', (e) => {
  if (document.activeElement && (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'SELECT' || document.activeElement.tagName === 'TEXTAREA')) return;
  let selectedCells = Array.from(document.querySelectorAll('.selected'));
  if (selectedCells.length === 0) return;
  e.preventDefault();
  
  let rows = [...new Set(selectedCells.map(td => parseInt(td.dataset.logRow)))].sort((a,b)=>a-b);
  let cols = [...new Set(selectedCells.map(td => parseInt(td.dataset.logCol)))].sort((a,b)=>a-b);
  let textLines = [];
  
  for (let r of rows) {
    let rowData = [];
    for (let c of cols) {
      let cell = selectedCells.find(td => parseInt(td.dataset.logRow) === r && parseInt(td.dataset.logCol) === c);
      if (cell) {
         let input = cell.querySelector('input');
         let select = cell.querySelector('select');
         let textarea = cell.querySelector('textarea');
         if (select) rowData.push(select.value);
         else if (input) rowData.push(input.value);
         else if (textarea) rowData.push(textarea.value);
         else rowData.push(cell.innerText.replace(/\n$/, ''));
      } else {
         let spanParent = selectedCells.find(td => {
             let cr = parseInt(td.dataset.logRow), cc = parseInt(td.dataset.logCol);
             let crs = parseInt(td.dataset.logRowSpan), ccs = parseInt(td.dataset.logColSpan);
             return r >= cr && r < cr + crs && c >= cc && c < cc + ccs;
         });
         if(spanParent) rowData.push(''); 
      }
    }
    if(rowData.length > 0) textLines.push(rowData.join('\t'));
  }
  e.clipboardData.setData('text/plain', textLines.join('\n'));
});
```

또한 단일값을 여러 선택 셀에 붙여넣거나, 선택 시작점부터 TSV 직사각형을 붙여넣는다. 일반 탭에서 실제로 바뀌는 열은 `단가(VAT포함)·출고가·할인율·확인·회계반영일자`다(`Index.html:482-634`).

### 현행 위치

- `clients/desktop/src/renderer/routes/DailyClosingPage.tsx:748-860`: 일반 `<table>`과 행 렌더만 있고 셀 선택 상태·좌표·copy/paste 핸들러·선택 합계가 없다.
- `DailyClosingPage.tsx:764-778`: 헤더는 텍스트만 렌더한다.
- `DailyClosingPage.tsx:435-447`: 행 식별 키는 이미 `lineId` 우선이라 선택 상태의 안정 키로 재사용할 수 있다. 식별자는 화면에 노출하지 않는다.

### 격차

**없음 → 신규 필요.** 셀 좌표/선택 상태, rowspan 교차 판정, Shift·Ctrl/Cmd·drag 선택, 선택 합계, TSV 복사가 필요하다. 붙여넣기까지 답습한다면 읽기 전용 열 차단, 금액식 적용, dirty 행 표시, 전표 전체 라인 저장 계약과 원자성/부분실패 처리가 추가로 필요하다. 이후 “선택 여러 전표 전표 생성”에도 쓸 수 있도록 셀 선택과 전표 선택의 의미를 분리해야 한다.

### 🚩 업무 확인

1. 요청된 “다중선택 및 복사”를 넘어 GAS의 **다중 붙여넣기**까지 허용할지.
2. 셀 범위가 여러 전표의 일부만 걸칠 때 후속 전표 생성은 “걸친 전표 전체 선택”인지, 별도 행/전표 선택 UI인지.

---

## ② 정렬 / 필터

### 레거시 원문

상태 축은 `결과(main)·선발행(pre)·합산(sum)` 탭별이고, 열별 필터·통합검색·단일 정렬 열을 각각 가진다.

`tools/legacy-gas/일마감 프로그램/Index.html:216-220`

```js
let storeData = { main: [], pre: [], sum: [] };
let filtersMap = { main: {}, pre: {}, sum: {} };
let filteredViews = { main: [], pre: [], sum: [] };
let globalSearchMap = { main: '', pre: '', sum: '' };
let sortState = { main: null, pre: null, sum: null };
```

모든 17열 헤더에 같은 팝업을 달고 탭별 통합검색을 둔다.

`tools/legacy-gas/일마감 프로그램/Index.html:792-817`

```js
let theadHtml = HEADERS.map(h => `
  <th>
    <div class="th-content">
      <span>${h}</span>
      <button id="btn-filter-${tab.dataKey}-${h.replace(/[^a-zA-Z가-힣]/g, '')}" data-col="${h}" onclick="openPopup('${h}', '${tab.dataKey}', event)">▼</button>
    </div>
  </th>
`).join('');

// ...

<input type="text" placeholder="🔍 통합검색" style="padding: 8px 15px; width: 100%; max-width: 400px; border-radius: 20px; border: 1px solid #cbd5e0; text-align: center; outline: none;" oninput="applyGlobalSearch('${tab.dataKey}', this.value)">
```

필터 기준은 열별 `정확히 일치(확인 TRUE/FALSE)·비어있음·비어있지 않음·포함·미포함`, 그리고 17열 전체의 대소문자 무시 통합검색이다.

`tools/legacy-gas/일마감 프로그램/Index.html:1025-1053`

```js
let filteredData = dataList.filter(d => {
  for (let col in activeFilters) {
    let f = activeFilters[col];
    let val = d[col];
    
    if (f.type === 'exact') {
       if (val !== f.text) return false;
    } else if (f.type === 'empty') {
       if (String(val || '').trim() !== '') return false;
    } else if (f.type === 'not_empty') {
       if (String(val || '').trim() === '') return false;
    } else {
       let strVal = String(val || '');
       if (f.type === 'include' && !strVal.includes(f.text)) return false;
       if (f.type === 'exclude' && strVal.includes(f.text)) return false;
    }
  }
  
  if (globalText) {
    let match = false;
    for (let col of HEADERS) {
      if (String(d[col] || '').toLowerCase().includes(globalText)) {
        match = true; break;
      }
    }
    if (!match) return false;
  }
  return true;
});
```

정렬 기준은 선택한 한 열이다. `번호`는 괄호와 비숫자를 제거한 숫자, 숫자로 읽히는 다른 값은 숫자, 나머지는 문자열로 오름/내림차순 정렬한다.

`tools/legacy-gas/일마감 프로그램/Index.html:1427-1476`

```js
function toggleSort(dir) {
  let sState = sortState[curFilterTab];
  if (sState && sState.col === curFilterCol && sState.dir === dir) {
    sortState[curFilterTab] = null;
    restoreOriginalSort(curFilterTab);
  } else {
    sortState[curFilterTab] = { col: curFilterCol, dir: dir };
    executeSort(curFilterTab, curFilterCol, dir);
  }
  renderTable(tabsInfo.find(t => t.dataKey === curFilterTab).tbodyId, curFilterTab);
  updateFilterUI();
  closePopup();
}

function executeSort(tabKey, col, dir) {
  let arr = storeData[tabKey];
  arr.sort((a, b) => {
    let v1 = String(a[col] || '');
    let v2 = String(b[col] || '');

    if (col === '번호') {
      let n1 = Number(v1.replace(/\(.*?\)/g, '').replace(/[^0-9]/g, '')) || 0;
      let n2 = Number(v2.replace(/\(.*?\)/g, '').replace(/[^0-9]/g, '')) || 0;
      return dir === 'asc' ? n1 - n2 : n2 - n1;
    }

    let n1 = Number(v1.replace(/,/g, ''));
    let n2 = Number(v2.replace(/,/g, ''));
    if (v1 !== '' && v2 !== '' && !isNaN(n1) && !isNaN(n2)) {
      v1 = n1; 
      v2 = n2;
    }
    if (v1 < v2) return dir === 'asc' ? -1 : 1;
    if (v1 > v2) return dir === 'asc' ? 1 : -1;
    return 0;
  });
}

function restoreOriginalSort(tabKey) {
  let arr = storeData[tabKey];
  arr.sort((a, b) => {
    let n1 = Number(String(a['번호'] || '').replace(/\(.*?\)/g, '').replace(/[^0-9]/g, '')) || 0;
    let n2 = Number(String(b['번호'] || '').replace(/\(.*?\)/g, '').replace(/[^0-9]/g, '')) || 0;
    if (n1 !== n2) return n1 - n2;
    let riA = (a._ri !== undefined) ? a._ri : 0;
    let riB = (b._ri !== undefined) ? b._ri : 0;
    return riA - riB;
  });
}
```

상태는 같은 브라우저 실행 중 탭별로 유지되지만, 새 처리 실행에서 전부 초기화된다.

`tools/legacy-gas/일마감 프로그램/Index.html:944-949`

```js
// 상태
filtersMap = { main: {}, pre: {}, sum: {} };
globalSearchMap = { main: '', pre: '', sum: '' };
sortState = { main: null, pre: null, sum: null };
undoStack = []; redoStack = [];
renderAll();
```

필터/정렬 상태는 `saveToNotion` 저장 payload에도 포함되지 않는다(`Index.html:1545-1551`). 즉 영구 저장 대상이 아니다.

### 현행 위치

- `DailyClosingPage.tsx:654-659`: 원본행 표의 유일한 행 필터는 탭에 따른 `accountingPostedAt` 유무다.
- `DailyClosingPage.tsx:748-860`: 통합검색·열 필터·정렬 상태 없이 조회 순서 그대로 렌더한다.
- 페이지 상단에는 날짜와 탭 필터가 있으나(`DailyClosingPage.tsx:1290-1305,1323-1365`), 레거시의 17열 표 필터/정렬과 다른 축이다.

### 격차

**없음 → 신규 필요.** 17열별 타입을 정의하고, 탭별 열 필터·통합검색·단일 열 정렬·초기화·활성 표시를 만들어야 한다. 정렬/필터 뒤에도 `lineId` 기반 draft·확장행·rowspan·소계·다중선택이 같은 원본행을 가리키도록 고쳐야 한다. 합계/소계가 전체 원천과 필터 결과 중 무엇을 집계하는지도 명시해야 한다. GAS는 필터 결과만 다시 집계한다(`Index.html:1055-1083`).

### 🚩 업무 확인

3. 상태 수명을 GAS처럼 “탭별·현재 화면 세션만”으로 할지, 사용자별 마지막 필터를 재진입 때 복원할지. 또한 날짜를 바꿀 때 초기화할지 유지할지 정해야 한다.

---

## ③ 금액 편집

### 레거시 원문

결과·선발행·합산 세 탭이 같은 17열 렌더러를 쓰며, 단가(VAT포함)·출고가·할인율은 모두 입력칸이다. 수량·공급가액·부가세·합계·총계는 표시 전용이다.

`tools/legacy-gas/일마감 프로그램/Index.html:1103-1147`

```js
HEADERS.forEach((col, cIdx) => {
  let isMergeCol = MERGE_COLS.includes(col);
  
  if (isMergeCol) {
    if (rowSpans[i]) {
      let rs = rowSpans[i];
      let modClass = '';
      if (col === '번호' && (tabKey === 'main' || tabKey === 'pre')) {
          let k = d['일자'] + '_' + d['번호'];
          let cnt = invoiceModCounts[k] || 0;
          if (cnt === 1) modClass = ' mod-1';
          else if (cnt === 2) modClass = ' mod-2';
          else if (cnt >= 3) modClass = ' mod-3';
      }
      if (col === '회계반영일자') {
        let dVal = String(d[col] || '').trim();
        let errClass = (dVal && !/^\d{4}[\/\-]\d{1,2}[\/\-]\d{1,2}$/.test(dVal)) ? 'bg-err-date' : '';
        html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="${rs}" data-log-col-span="1" rowspan="${rs}" data-col="회계반영일자" class="${errClass}" style="vertical-align:middle; text-align:center; cursor:pointer;" title="더블클릭하여 수정">${d[col] || ''}</td>`;
      } else {
        html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="${rs}" data-log-col-span="1" rowspan="${rs}" data-col="${col}" class="${modClass}" style="vertical-align:middle; text-align:center;">${d[col] || ''}</td>`;
      }
    }
  } else {
    if (col === '단가(VAT포함)') {
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="단가(VAT포함)"><input type="text" class="edit-input" value="${formatNum(d[col])}" oninput="formatInput(this)" onchange="recalcRow(this, '${tbodyId}', 'unit')"></td>`;
    } else if (col === '확인') {
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="확인"><select class="edit-select" onchange="updateVal(this, '${tbodyId}', '${col}')">
                <option value="TRUE" ${d[col] ? 'selected' : ''}>TRUE</option>
                <option value="FALSE" ${!d[col] ? 'selected' : ''}>FALSE</option>
              </select></td>`;
    } else if (col === '수량') {
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="수량" style="text-align:center;">${formatNum(d[col])}</td>`;
    } else if (col === '출고가') {
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="출고가"><input type="text" class="edit-input" value="${formatNum(d[col])}" oninput="formatInput(this)" onchange="recalcRow(this, '${tbodyId}', 'price')"></td>`;
    } else if (['공급가액','부가세','합계','총계'].includes(col)) {
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="${col}" style="text-align:right;">${formatNum(d[col])}</td>`;
    } else if (col === '할인율') {
      let rate = Number(d[col] || 0);
      let roundedRate = Math.round(rate * 100);
      html += `<td data-log-row="${i}" data-log-col="${cIdx}" data-log-row-span="1" data-log-col-span="1" data-col="할인율" class="${getDcClass(roundedRate)}">
        <div style="display:inline-flex; align-items:center; justify-content:center;">
          <input type="text" class="edit-rate" value="${roundedRate}" onchange="recalcRow(this, '${tbodyId}', 'rate')">
          <span style="margin-left:2px;">%</span>
        </div>
      </td>`;
    }
```

레거시 데이터에는 전표 상태 필드가 없고, 위 입력에는 `disabled` 조건도 없다. 따라서 업로드 후 세 탭에 렌더된 모든 행에서 세 필드를 편집할 수 있다. 편집은 브라우저 `storeData`를 바꾸며, “내역저장”은 `main·pre` 전체 스냅샷을 별도 저장한다(`Index.html:1539-1567`). 원본 이카운트 행을 수정하지 않는다.

### 현행 위치

- `DailyClosingPage.tsx:358-415,450-535`: 세 필드 입력과 계산 전용 열이 이미 있다.
- `DailyClosingPage.tsx:413-421`: `amountEditable=false` 또는 회계반영일자가 있으면 세 입력을 비활성화한다.
- `DailyClosingPage.tsx:570-635`: dirty 행이 속한 전표의 **모든 라인**을 묶어 저장하고 부분 실패를 행 오류로 남긴다.
- `DailyClosingQueryService.java:21-43`: `CONFIRMED·DELIVERED·COMPLETED` 출고전표만 조회한다.
- `DailyClosingAmountUpdateController.java:27-41`: 저장 endpoint는 `sales.slip.edit`의 `UPDATE` 권한을 요구한다.
- `DailyClosingAmountUpdateService.java:42-43,58-86`: 같은 세 상태, 출고전표, 마감일 미잠금, 회계전표 없음, 라인 수/순서 동일을 요구한다.
- `DailyClosingAmountUpdateService.java:52,87-95`: 실제 전표에는 VAT 포함 단가만 저장하며 출고가·할인율은 감사 계산 근거로만 남긴다.

### 격차

**핵심 UI와 저장 경로는 이미 있음. 보정 필요.**

1. 기존 결정은 “회계전표가 아직 생성되지 않은 CONFIRMED”만 허용한다고 했지만 현행은 `DELIVERED·COMPLETED`도 허용한다.
2. 레거시는 원본을 고치지 않지만 현행은 개발책임자 후속 결정에 따라 출고전표 원본 단가를 고친다. 이는 의도된 비답습이다.
3. 출고가·할인율은 직접 편집되지만 영속 원본은 아니므로, 새로고침 후 출고가는 가격 이력값으로 돌아오고 할인율은 다시 파생된다. 화면에서 “세 값 모두 저장”처럼 보이면 계약과 어긋난다.
4. M-1의 per-unit/line-total VAT 분리 차이를 먼저 해소해야 한다.
5. 금액을 바꾸는 다중 붙여넣기는 현재의 `sales.slip.edit:UPDATE` 권한·감사·낙관적 잠금 경계를 반드시 통과해야 한다.

### 🚩 업무 확인

4. 편집 상태를 기존 결정문대로 `CONFIRMED`만 허용할지, 현행처럼 `DELIVERED·COMPLETED`까지 허용할지.

---

## ④ 양방향 할인율 동기화

### 레거시 원문 — 원본/파생과 계산식

GAS에는 단일한 “항상 원본인 필드”가 없다. **사용자가 방금 고친 필드가 입력**, 나머지가 아래 규칙의 파생값이다.

- 단가 변경: 출고가 유지 → 할인율 파생
- 할인율 변경: 출고가 유지 → 단가를 `Math.round`로 원 단위 반올림
- 출고가 변경: 단가 유지 → 할인율 파생
- 그 뒤 합계/총계는 단가×수량, 공급가액은 단가÷1.1 반올림, 부가세는 단가−공급가액
- 화면 할인율은 `Math.round(rate×100)` 정수 %로 다시 표시

`tools/legacy-gas/일마감 프로그램/Index.html:1203-1249`

```js
function recalcRow(el, tbodyId, changedField) {
  saveState();
  let tr = el.closest('tr');
  let idx = tr.getAttribute('data-idx');
  let tabKey = tabsInfo.find(t => t.tbodyId === tbodyId).dataKey;
  let rowData = storeData[tabKey][idx];
  
  let price = Number(rowData['출고가']) || 0;
  let qty = Number(rowData['수량']) || 0;
  
  if (changedField === 'unit') {
    let unit = Number(el.value.replace(/,/g, '')) || 0;
    el.value = unit.toLocaleString();
    rowData['단가(VAT포함)'] = unit;
    rowData['할인율'] = price ? (1 - (unit / price)) : 0;
  } else if (changedField === 'rate') {
    let rVal = Number(el.value.replace(/[^0-9.-]/g, '')) || 0;
    el.value = rVal;
    let rate = rVal / 100;
    rowData['할인율'] = rate;
    rowData['단가(VAT포함)'] = Math.round(price * (1 - rate));
  } else if (changedField === 'price') {
    let newPrice = Number(el.value.replace(/,/g, '')) || 0;
    el.value = newPrice.toLocaleString();
    rowData['출고가'] = newPrice;
    price = newPrice;
    let unit = Number(rowData['단가(VAT포함)']) || 0;
    rowData['할인율'] = price ? (1 - (unit / price)) : 0;
  }
  
  let unit = rowData['단가(VAT포함)'];
  rowData['합계'] = unit * qty;
  rowData['공급가액'] = Math.round(unit / 1.1);
  rowData['부가세'] = unit - rowData['공급가액'];
  rowData['총계'] = unit * qty;

  tr.querySelector('[data-col="단가(VAT포함)"] input').value = unit.toLocaleString();
  tr.querySelector('[data-col="합계"]').innerText = formatNum(rowData['합계']);
  tr.querySelector('[data-col="공급가액"]').innerText = formatNum(rowData['공급가액']);
  tr.querySelector('[data-col="부가세"]').innerText = formatNum(rowData['부가세']);
  tr.querySelector('[data-col="총계"]').innerText = formatNum(rowData['총계']);
  
  let rateCell = tr.querySelector('[data-col="할인율"]');
  let roundedRate = Math.round(rowData['할인율'] * 100);
  rateCell.className = getDcClass(roundedRate);
  let rateInput = rateCell.querySelector('input');
  if(rateInput) rateInput.value = roundedRate;
```

### 현행 위치

- `DailyClosingPage.tsx:386-410`: 세 방향 화면 계산은 GAS와 같다.
- `DailyClosingPage.tsx:504-514`: 현재는 `onChange`마다 계산해 표시하므로 blur 시점인 GAS `onchange`보다 더 즉시 반영된다.
- `DailyClosingPage.tsx:584-592`: 저장 payload의 할인율은 화면의 표시율을 그대로 보내지 않고 `1 - unit/price`로 다시 계산한다.
- `DailyClosingAmountUpdateService.java:109-119`: 서버는 `unit/releasePrice`를 소수점 8자리 `HALF_UP`으로 나눈 할인율과 payload 차이가 `0.0001` 이내인지 검증한다.
- `SlipLine.java:533-545`: 저장 후 권위 원본은 VAT 포함 단가이며, 공급가액·부가세는 라인 합계 기준으로 다시 파생한다.

### 격차

**화면 양방향식은 이미 있음. 계약·반올림 보정 필요.**

1. 출고가 변경 시 현행/GAS는 단가를 유지하지만 기존 결정 13 문구는 단가도 재계산한다고 되어 있다.
2. 화면은 할인율을 정수로 표시하고 단가를 정수 반올림하지만, 서버는 8자리 할인율을 검증하고 VAT 포함 단가는 scale 2로 저장한다. 허용 소수 자릿수와 반올림 단계가 하나의 계약으로 고정돼 있지 않다.
3. 저장 payload가 사용자가 입력한 `values.rate`가 아니라 unit/price에서 다시 계산한 값이므로, 정수 % 입력 뒤 단가 반올림으로 역산된 비율과 사용자가 본 비율은 달라질 수 있다. 감사로그에 어느 값을 남길지도 고정해야 한다.
4. M-1의 공급가액·부가세 분리축이 화면과 영속층에서 다르다.

### 🚩 업무 확인

5. 출고가 변경 시 단가 유지(GAS/현행 코드)와 단가 재계산(기존 결정문) 중 어느 것이 맞는지.
6. 할인율 입력/표시/저장 정밀도와 반올림 순서를 확정해야 한다. 최소한 다음을 한 문장으로 고정해야 한다: “정수 % 입력 → 단가 원 단위 반올림 → 라인 VAT 합계 분리”인지, 다른 순서인지.

---

## 🚩 업무 판단 필요 — 총 6건

1. 다중선택 복사를 넘어 다중 붙여넣기까지 허용할지.
2. 셀 범위 선택과 여러 전표 후속 작업 선택의 의미를 어떻게 분리할지.
3. 정렬/필터 상태를 화면 세션·탭에만 둘지, 날짜 전환/재진입에도 유지할지.
4. 금액 편집 상태를 `CONFIRMED`만으로 할지 `DELIVERED·COMPLETED`까지로 할지.
5. 출고가 변경 시 단가를 유지할지 다시 계산할지.
6. 할인율·단가·VAT의 정밀도, 반올림 순서, per-unit 대 line-total 분리 중 무엇을 정본으로 할지.

이미 확정되어 다시 묻지 않을 것: 세 직접 편집 열, 계산 전용 열, 회계전표 존재 시 수정 금지, 출고전표 원본 단가 저장과 감사로그, 선결제 표기 원칙, 구제품/카드 설정값, 금지 DC 명칭 미사용, 운임·절삭 비제외.

## 구현 순서 제안 — 의존 관계 기준

1. **금액 계약 확정**: 업무 판단 4~6을 먼저 닫고, 원본/파생 필드·반올림·수량축·저장 후 재조회 불변식을 문서와 테스트 계약으로 고정한다.
2. **단일 행 금액 경로 보정**: 화면 계산, payload, 서버 검증, `SlipLine` 저장, 감사로그가 같은 계약을 쓰게 한다. 수량 1·2 이상과 경계 반올림 값을 검증한다.
3. **정렬/필터 도입**: 안정 행 키를 유지한 visible view를 먼저 만든다. 소계/전체합계·확장행·dirty 상태가 필터/정렬 후에도 같은 행을 가리키게 한다.
4. **셀 다중선택·복사 도입**: 3단계의 visible order 위에서 rowspan 좌표, Shift/Ctrl·Cmd/drag, 선택 합계, TSV 복사를 만든다.
5. **다중 붙여넣기 또는 선택 후속 작업**: 업무 판단 1~2가 허용한 범위만 추가한다. 금액 붙여넣기는 2단계의 단일 계산/저장 함수를 재사용하고 전표별 전체 라인·권한·감사·부분실패 경계를 유지한다.

일정 추정은 하지 않는다.
