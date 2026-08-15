# 배차 계열 및 나머지 GAS 업무 규칙 패리티 정찰

- 정찰일: 2026-08-16
- 정찰자: Codex
- 환경: `cwd C:/dev/Samhan-Public (main, 읽기 전용)`
- 안전 조건: 정적 소스만 읽었다. 제품 코드·GAS·공유 DB·컨테이너·Git에는 쓰지 않았다.

## 결론 요약

`docs/dev-reports/2026-08-15-gas-programs-coverage-survey.md`의 `✅ 대체완료` 17개에서 일마감 1개와 `2026-08-15-remaining-gas-rule-parity.md`가 완결 조사한 회계 4개를 뺐다. 주문서웹·종합견적서는 17개 목록 밖이고, 데이터 원천 보고서의 단톡방·발송금지·DC·지역표 규칙은 적용 프로그램 전체를 조사한 것이 아니므로 중복 규칙만 제외했다. 가입고처리·에어디자이너·제이시스템 OCR도 대상 목록 밖이며 지시대로 제외했다.

남은 프로그램은 **12개**다.

1. DPS 입고기록 비교
2. 품목별 DPS 입고내역 비교
3. 가배차분류리스트
4. 지방가배차분류리스트
5. 미배차리스트
6. 배차안내문자
7. 운송사-실배차내역 비교
8. 거래처 업데이트 프로그램
9. 내일자 전표 이미지 생성
10. 전표정리리스트
11. 입출고 내역
12. 입출고 분석

도달 가능한 값 결정 함수와 분기를 프로그램별로 전수 읽고 연속 분기를 업무 규칙으로 합친 결과, 대조 규칙은 **60개**다. 판정은 **동일 8 · 다름 37 · 없음 13 · 확인 불가 2**다.

이 판정은 어느 코드가 옳다는 뜻이 아니다. 레거시 코드가 남아 있다는 사실만으로 현재 유효한 정책이라고 확정하지 않았으며, 아래 `업무 확인`이 최종 게이트다.

## 🚩 먼저 볼 차이 — 금액·수량·창고·상태

| 축 | 프로그램·규칙 | 정확한 차이 | 판정 |
|---|---|---|---|
| 금액 | DPS 입고기록 D-03 | 레거시는 같은 `납품번호+정제모델` 안에서 수량+합계, 수량, 합계 순으로 1:1 후보를 소비한다. 현행은 `productCode+partnerCode+date` 수량합만 비교하여 합계 금액을 대조하지 않는다. | 다름 |
| 금액 | 거래처 업데이트 P-04 | 레거시는 홈/상업 DC와 품목군별 정액 할인·단위처리를 문자열로 병합한다. 현행 import에는 이 가격정책 필드가 없다. | 없음 |
| 금액 | 전표정리 C-01·C-04 | 레거시는 금액을 `parseInt`로 원 미만 절사하고 8필드 snapshot의 일부로 수정 여부를 가른다. 현행은 VAT 포함 decimal 합계를 계산하지만 발송 snapshot·수정 상태기는 없다. | 다름/없음 |
| 금액 | 입출고 A-01 | 레거시 분석은 수량만 다룬다. 현행은 공급가액으로 매입·매출·단위이익·이익률까지 산출하므로 같은 이름의 결과 범위가 넓다. | 다름 |
| 수량 | DPS 입고기록 D-03 | 레거시는 양쪽 개별 행을 1:1 소비한다. 현행은 DPS bucket 수량과 출고 수량을 합산 비교하므로 중복 행 배치가 달라도 합계가 같으면 결과가 달라질 수 있다. | 다름 |
| 수량 | 품목별 DPS B-01~03 | 레거시는 사실상 `MM-DD+전표번호`로 수동 배차 history와 vendor 행을 대조한다. 현행은 검수 단계별 품목수량 pivot이며 `diffFromDps=0`이다. | 다름 |
| 수량 | 내일자 전표 N-03 | 레거시는 `parseInt` 수량의 그룹합이 정확히 0이면 전표 이미지 전체를 제외한다. 현행은 다음날 활성 전표를 그대로 반환하며 이 상쇄 gate가 없다. | 다름 |
| 창고 | 가배차 G-03·G-04 | 상일/초월 mode gate 자체는 같지만, 레거시는 일부 mode에서 야적을 먼저 보존한다. 현행은 공통 제외를 먼저 적용하고 창고 provenance 불명 행도 제외한다. | 동일/다름 |
| 창고 | 내일자 전표 N-01 | 레거시는 `삼성창고 (초월 무갑)`, `상일물류`만 허용한다. 현행은 다음날 활성 OUTBOUND 전체를 조회하며 창고 gate가 없다. | 다름 |
| 상태 | 미배차 U-02~05 | 레거시의 보류·해당없음·야적미배차·지방미배차·배차완료·중복배차·폐기전표 상태가 현행 미배차 응답에 없다. | 없음 |
| 상태 | 운송사 비교 R-03 | TRUE/FALSE_LEFT/FALSE_RIGHT 이름은 같지만 현행 `putIfAbsent`는 중복 key를 버려 레거시의 1:1 소비 결과와 다르다. | 다름 |
| 상태 | 전표정리 C-03 | 레거시 `unsent/sent/discard/restored/mod1/mod2/mod3` 상태기는 수동 snapshot 비교다. 현행은 전표 자체 status와 정합성 flag만 제공한다. | 없음 |

## 판정 기준

- **동일**: 확인한 입력·조건·계산 순서·결과가 소스상 같다.
- **다름**: 대응 기능은 있으나 입력, key, 조건, 계산, 출력 중 하나 이상이 다르다.
- **없음**: 관련 현행 화면·controller·service·DTO를 확인했으나 대응 규칙이 없다.
- **확인 불가**: 정적 코드만으로 동일 데이터의 결과 동등성을 확정할 수 없다.

## 1. 배차 계열

### 1.1 가배차분류리스트 — 7개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| G-01 | `case '1': out = sangil_chowol_except_region(ecountData, day); break;` / `case '8': out = sangil_with_region(ecountData, day); break;` (`tools/legacy-gas/가배차분류리스트/Code.js:598-607`) | 8개 실행 mode 중 하나를 선택한다. | `if (mode != null) { slips = slips.stream().filter(slip -> matchesMode(slip, mode)).toList(); }` (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/PreClassifyService.java:80-87`) | 동일 | 필요. 8 mode를 계속 운영할지 확인. |
| G-02 | `targetData = ecountData.filter(function(e) {` / `if (minV !== null && n < minV) return false;` / `if (maxV !== null && n > maxV) return false;` (`tools/legacy-gas/가배차분류리스트/Index.html:624-632`) | 업로드 자료를 전표번호 전체 또는 숫자 범위로 제한한다. | `getOutboundSlips(from, to)`와 날짜 범위만 사용 (`PreClassifyService.java:78-86`) | 다름 | 필요. 전표번호 범위 실행이 아직 필요한지 확인. |
| G-03 | `if (String(r['출고창고'] \|\| '').indexOf('초월')<0) { skip_warehouse_filter(raw); return; }` / `if (String(r['출고창고'] \|\| '').indexOf('상일')<0) { skip_warehouse_filter(raw); return; }` (`Code.js:419,432`) | mode별 상일·초월 창고 gate. | `"CHOWOL".equals(slip.warehouseBusinessType())` / `"SANGIL".equals(slip.warehouseBusinessType())` (`PreClassifyService.java:147-156`) | 동일 | 필요. business type이 레거시 창고명과 같은 정본인지 확인. |
| G-04 | `var y = extract_yajek_item(raw, r); if (y) { recs_y.push(y); return; }`가 제외 검사보다 먼저다 (`Code.js:403-406,416-419,429-432`) | mode 1~3·6~8은 야적을 공통 제외보다 먼저 보존한다. | `if (commonExcluded) return false;` 뒤에 `if (stack) return true;` (`PreClassifyService.java:121-143`) | 다름 | 필요. 야적+회수/자가 등의 우선순위 결정. |
| G-05 | `if (pre.indexOf('회수')>-1 \|\| pre.indexOf('회차')>-1) { counters.skip++; counters.returns++; return ['', true]; }` / `if (/경동.*[\/:]/.test(o)) { counters.skip++; counters.kyungdong++; return ['', true]; }` (`Code.js:315-337`) | 주소 앞 10자 및 carrier/지방 표식으로 제외한다. | 같은 10자 키워드와 `(?:경동\|로젠)[^/\|:]*[/\|:]`, `deliveryTag`를 사용 (`PreClassifyService.java:49-50,119-143`) | 다름 | 필요. 주소 표식과 구조화 tag 중 어느 것이 권위인지 확인. |
| G-06 | `var three = String(addr).split(/\s+/).slice(0,3).join(' ');` 뒤 region priority를 순회 (`Code.js:276-311`) | 주소 앞 3토큰에서 광역·시군 우선순위를 찾는다. | 공백 제거한 전체 주소에서 광역 prefix→keyword→group prefix 순 (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionClassifier.java:49-103`) | 다름 | 필요. 동일 주소 표본으로 오분류 허용 기준 확인. |
| G-07 | `pushGrouped(df_s, '상일상차');` / `pushGrouped(df_c, '초월상차');` / `{'분류항목': '<미분류>'}` / `{'분류항목': '<기존 야적>'}` (`Code.js:521-579`) | 상일→초월→미분류→야적 section과 5개 표시값을 만든다. | 응답은 `slipNo, partnerCode, partnerName, address, regionGroup, planned`이며 입력순 map (`PreClassifyService.java:95-114`) | 다름 | 필요. 창고 section·금액·특이사항 표시 필요 여부 확인. |

### 1.2 지방가배차분류리스트 — 4개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| L-01 | `if (raw.indexOf('지방') === 0 \|\| raw.indexOf('지방/') > -1)` (`tools/legacy-gas/지방가배차분류리스트/Code.js:276-280`) | 주소의 지방 표식 행만 대상. | `if (!"REGION".equals(slip.deliveryTag())) { continue; }` (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionalService.java:79-84`) | 확인 불가 | 필요. REGION tag 생성 결과가 원문 조건과 같은지 실데이터 확인. |
| L-02 | `var cleanAddr = raw.replace(/^지방\s*[\/:]\s*/, '').trim();` (`Code.js:279-280`) | 선두 지방 표식을 지운 주소를 표시한다. | `slip.address()` 원문을 entry에 넣음 (`RegionalService.java:84-94`) | 다름 | 필요. 표식 포함 주소 표시 허용 여부. |
| L-03 | `'주소': cleanAddr,` / `'업체명': cust,` / `'전표번호': vid,` / `'특이사항': spec,` / `'창고': wh,` / `'품목': itemVal,` / `'날짜': dateVal,` / `'금액': amt \|\| ''` (`Code.js:308-315`) | 8필드 결과. | `slipNo, partnerCode, partnerName, address, sido` (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/dto/RegionalDispatchResponse.java:39-45`) | 다름 | 필요. 금액·품목·창고 누락을 허용할지 확인. |
| L-04 | `if (dateA !== dateB) return dateB.localeCompare(dateA);` / `return vidB.localeCompare(vidA);` (`Code.js:320-327`) | 날짜, 전표번호 내림차순. | `LinkedHashMap`에 조회 입력순으로 누적 (`RegionalService.java:73-97`) | 다름 | 필요. 작업 순서를 무엇으로 볼지 확인. |

### 1.3 미배차리스트 — 6개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| U-01 | `assignedCounts[num] = (assignedCounts[num] \|\| 0) + 1;` (`tools/legacy-gas/미배차리스트/Index.html:815-820`) | 두 수동 배차 목록의 괄호 끝 번호를 전표번호와 대조한다. | vehicle stop의 `parsedPartnerCode`와 전표 `partnerCode`를 대조 (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/UnassignedService.java:60-93`) | 다름 | 필요. 미배차 판정 key를 전표번호/거래처 중 확정. |
| U-02 | `if (/입금/.test(r)) return '보류';` / `if (/배차\s*x/i.test(o) \|\| /배차\s*x/i.test(r)) return '해당없음';` (`Index.html:761-775`) | 입금·배차X·반품·창고·회수·택배 표식으로 초기 상태를 세분화. | 미할당이면 단일 entries 목록에 추가 (`UnassignedService.java:65-75`) | 없음 | 필요. 보류·해당없음 상태가 현행에도 필요한지 확인. |
| U-03 | `return '야적미배차';` / `return '지방미배차';` (`Index.html:769-770`) | 야적·지방 미배차를 별도 목록으로 분리. | 단일 미배차 목록 (`UnassignedService.java:65-77`) | 없음 | 필요. 별도 운영 queue 필요 여부. |
| U-04 | `if (assignedCounts[slipNumber] > 1) { isDuplicate = true; }` (`Index.html:876-883`) | 같은 전표번호가 수동 목록에 2회 이상이면 중복배차. | repository 결과를 `Set<String>`으로 축약 (`UnassignedService.java:82-94`) | 없음 | 필요. 중복배차 탐지가 아직 필요한지 확인. |
| U-05 | `if (!ecountNumbers.has(num)) { let discarded = { isDiscarded: true, num: num, date: '', client: '', addr: '', amt: '', prod: '', rmk: '', wh: '', mgr: '' };` (`Index.html:844-851`) | 배차 목록에는 있으나 현재 전표에는 없는 번호를 폐기전표로 표시. | 현재 전표에서 미배차만 찾으며 역방향 비교 없음 | 없음 | 필요. 배차 후 전표 삭제 감시 필요 여부. |
| U-06 | ``return `-${addrPart}(${clientPart}-${slipNumber})${rem}`;`` (`Index.html:788`) | 복사용 포맷과 일정 이상·긴급도를 결과 표시로 만든다. | 응답 entry는 전표번호·거래처·주소뿐 (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/UnassignedService.java:67-73`) | 없음 | 필요. 현행 배차 보드가 대체하는지 업무 확인. |

### 1.4 배차안내문자 — 7개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| S-01 | `var original_text = normalizeStr(cleanValue(df_source[i]['배차요청내역']));` / `var match_row = lookupEcount(dispatch_number, rowDateKey);` (`tools/legacy-gas/배차안내문자/Code.js:208-231`) | 수동 배차 원문과 이카운트 전표를 매칭. | 지정일 OUTBOUND를 자동 조회하고 운전자 입력을 slipNo/업체명으로 보정 (`services/notification-service/src/main/java/com/samhanair/logis/notification/service/DispatchBatchPreviewService.java:80-118,219-265`) | 다름 | 필요. 자동조회와 수동 배차 원문 중 권위 확인. |
| S-02 | `let key = roomKey ? 'R_' + roomKey : (phoneKey ? 'P_' + phoneKey : 'N_' + ai);` (`tools/legacy-gas/배차안내문자/Index.html:1154-1165`) | 단톡방 우선, 없으면 인수자번호, 둘 다 없으면 전표별 그룹. | `R_`, `P_`, `N_` 우선순위 동일 (`services/notification-service/src/main/java/com/samhanair/logis/notification/service/DispatchMessageGroupComposer.java:79-87`) | 동일 | 필요. 단톡방/전화번호 우선순위 유지 확인. |
| S-03 | `driver_phone + ' / ' + truncated_display` (`tools/legacy-gas/배차안내문자/Code.js:376-379`) | 기사번호 + 주소 앞 3토큰. | `return safeText(slip.driverPhone()) + " / " + shortenedAddress;` (`DispatchBatchPreviewService.java:198-212`) | 동일 | 필요. 기사번호 없는 오류문구 포함 확인. |
| S-04 | `dayOrder.sort((a, b) => Number(a) - Number(b));`와 고정 머리말 (`Index.html:1170-1186`) | 하차일 오름차순 section, 빈 줄 1개, 고정 머리말. | `TreeMap`과 `HEADER + "\n\n" + String.join("\n\n", sections)` (`DispatchMessageGroupComposer.java:53-75`) | 동일 | 필요. 문구 자체의 현재 유효성 확인. |
| S-05 | `if (isAccountingRoom_(room)) return;` (`tools/legacy-gas/배차안내문자/Code.js:195-201`) | 회계방 매핑 제외. | 매핑된 모든 `chatRoomName`을 group에 추가 (`DispatchBatchPreviewService.java:107-132`) | 없음 | 필요. 회계방 제외 유지 여부. |
| S-06 | `'발송멘트': '발송금지 업체입니다.'` (`Code.js:272-285`) | 발송금지면 오류문구. | 조회 성공 시 같지만 조회 예외는 `return false` fail-open (`DispatchBatchPreviewService.java:121-132,164-172`) | 다름 | 필요. 차단 조회 장애 시 fail-open/closed 결정. |
| S-07 | `e.clipboardData.setData('text/plain', textLines.join('\n'));` (`Index.html:880-914`) | 화면 선택 결과를 외부 채널에 복사. | 선택 행을 `거래처명, 전표번호, 코멘트, 단톡방` TSV로 만듦 (`clients/desktop/src/renderer/routes/dispatchSmsClipboard.ts:32-39`) | 다름 | 필요. 레거시 셀 범위 복사와 고정 4열 복사 중 확정. |

### 1.5 운송사-실배차내역 비교 — 4개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| R-01 | `let normL = item.date.length >= 10 ? item.date.substring(5, 10) : item.date;` / `let m = inner.match(/(?:^\|[^\d])(\d{1,3})\s*$/);` (`tools/legacy-gas/운송사-실배차내역 비교/Index.html:403-437`) | 수동 배차 history가 왼쪽 정본. | 자체 dispatch/vehicle stop을 기간 조회해 평탄화 (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/DispatchReconcileService.java:92-118,124-151`) | 다름 | 필요. 비교 왼쪽 정본 결정. |
| R-02 | `let dateIdx = headers.findIndex(h => String(h).includes('접수시간')); let nameIdx = headers.findIndex(h => String(h).includes('업체명'));` (`Index.html:341-348`); `let parts = namesStr.split('/');` (`Index.html:445-450`) | vendor 한 셀의 여러 전표를 분리. | `VendorExcelParser`가 별도 운송사 파일 schema를 파싱 (`services/arologis-service/src/main/java/com/samhanair/logis/arologis/parser/VendorExcelParser.java`) | 다름 | 필요. 실제 운송사 양식별 adapter 확인. |
| R-03 | `!usedL[i] && l.slip === r.slip && l.normDate === r.normDate` (`Index.html:491-503`) | 날짜+전표번호 1:1 소비 후 TRUE/FALSE_LEFT/FALSE_RIGHT. | 같은 key와 3상태지만 양쪽을 `putIfAbsent`로 축약 (`DispatchReconcileService.java:169-227,247-248`) | 다름 | 필요. 중복 key 처리 정책. |
| R-04 | `return d1.localeCompare(d2);` (`Index.html:506-510`) | 결과 날짜 오름차순. | dispatch map 순회 후 vendor-only를 붙임 (`DispatchReconcileService.java:186-237`) | 다름 | 필요. 불일치 검토 순서 확인. |

## 2. 나머지 프로그램

### 2.1 DPS 입고기록 비교 — 4개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| D-01 | `data.findIndex(r => r && r.includes('품명 및 규격') && r.includes('적요'))` / `data.findIndex(r => r && r.includes('납품일자') && r.includes('모델') && r.includes('납품번호'))` (`tools/legacy-gas/DPS 입고기록 비교/Index.html:339-355`) | 양쪽 Excel을 모두 업로드해야 실행. | DPS Excel 1개 + 내부 출고전표 조회 (`services/inventory-service/src/main/java/com/samhanair/logis/inventory/service/DpsCompareService.java:76-109`) | 다름 | 필요. 양쪽 원본 중 내부 전표를 정본으로 볼지 확인. |
| D-02 | `split('[')[0].split('(')[0].split('.')[0].replace(/\s+/g, '')` (`Index.html:381-384`) | 모델의 대괄호·괄호·점 뒤와 공백 제거. | `productCode.trim()`을 사용 (`services/inventory-service/src/main/java/com/samhanair/logis/inventory/service/DpsExcelParser.java:82-96`) | 다름 | 필요. 모델명 정규화 폐기 여부. |
| D-03 | `let mIdx = rg.findIndex((r, i) => !usedR[i] && l._qty === r._qty && l._sum === r._sum);` / `if (mIdx === -1) mIdx = rg.findIndex((r, i) => !usedR[i] && l._qty === r._qty);` (`Index.html:439-461`) | 정확히 수량과 합계가 모두 같을 때만 TRUE. | `productCode\|partnerCode\|date` bucket 수량합 비교 (`DpsCompareService.java:171-229`) | 다름 | 필요. 금액 대조와 1:1 소비 유지 여부. |
| D-04 | `if (d1 !== d2) return d1.localeCompare(d2);` / `return c1.localeCompare(c2);` (`Index.html:465-477`) | 결과 정렬. | mismatch 생성 순서는 내부 slip, 남은 DPS 순이며 명시 정렬 없음 (`DpsCompareService.java:184-229`) | 다름 | 필요. 검수 순서 확인. |

### 2.2 품목별 DPS 입고내역 비교 — 3개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| B-01 | `for (let s = 1; s < wb.SheetNames.length; s++) {` / `for (let i = 4; i < rData.length; i++) {` / `if (cleanStr(row[14]) === '정상입고') {` (`tools/legacy-gas/품목별 DPS 입고내역 비교/Index.html:349-371`) | vendor 정상입고 자료를 읽는다. | 내부 `inbound_inspections`를 PENDING/COMPLETED/CANCELED로 집계 (`services/inventory-service/src/main/java/com/samhanair/logis/inventory/repository/InboundInspectionLineRepository.java:48-71`) | 다름 | 필요. 프로그램 명칭과 실제 비교 목적 확정. |
| B-02 | `let mIdx = leftData.findIndex((l, i) => !usedL[i] && l.slip === r.slip && l.normDate === r.normDate);` (`Index.html:509-529`) | 결과는 TRUE/FALSE_LEFT/FALSE_RIGHT. | 품목별 pending/completed/qc/return/total pivot (`services/inventory-service/src/main/java/com/samhanair/logis/inventory/web/dto/DpsByProductRow.java:39-54`) | 다름 | 필요. 배차 대조와 품목 검수 pivot을 분리할지 확인. |
| B-03 | `google.script.run.autoSaveToNotion(JSON.stringify(matchedResults), currentUserEmail, currentUserName, '자동저장 (' + todayStr + ')');` (`Index.html:532-544`) | 비교 결과 snapshot 저장. | history는 있으나 `diffFromDps`가 항상 `0` (`DpsByProductRow.java:45-53`) | 다름 | 필요. DPS 차이값을 0으로 보이는 것이 허용되는지 확인. |

### 2.3 거래처 업데이트 프로그램 — 5개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| P-01 | `upd !== 'TRUE' && upd !== '마스터' && upd !== 'MASTER'`면 제외 (`tools/legacy-gas/거래처 업데이트 프로그램/Code.js:384-417`) | 사용자리스트 flag와 공유시트 링크로 배포 대상 결정. | 중앙 partner DB import만 수행 (`services/partner-service/src/main/java/com/samhanair/logis/partner/service/EcountPartnerImporter.java:137-268`) | 없음 | 필요. 담당자별 Google Sheet 배포 폐기 여부. |
| P-02 | `ws = ss.getSheetByName("거래처");` / `ws.clear();` / `range.setValues(valuesMatrix);` (`Code.js:421-450`) | 대상 시트를 전면 교체. | partnerCode 기준 upsert, 삭제행 복원, 기존 status 보존 (`EcountPartnerImporter.java:740-789`) | 다름 | 필요. 파일에 사라진 거래처 처리 정책. |
| P-03 | `rawCode.replace(/^0+/, '')`로 Notion dictionary 조회 (`Code.js:630-637`) | 선행 0을 제거한 거래처코드도 동일 key. | `return new Classification(Kind.NORMAL, rawCode);` (`EcountPartnerImporter.java:523-532`) | 다름 | 필요. 선행 0이 business code 일부인지 확인. |
| P-04 | `if (homeDcNum != null) dcParts.push("홈" + Math.round(homeDcNum * 100) + "%");` / `if (commDcNum != null) dcParts.push("상업" + Math.round(commDcNum * 100) + "%");` / `if (unitSel && unitSel.name) segments.push(String(unitSel.name).trim());` (`Code.js:785-786,818`) | 가격정책 문자열을 싱글 할인/특이사항에 병합. | import 16 header에 할인/DC/단위처리 필드 없음 (`EcountPartnerImporter.java:83-87`) | 없음 | 필요. 가격정책 정본 위치 확정. |
| P-05 | `row[specialIdx] = [notionGeneral, excelSpecial, notionSpecial].filter(Boolean).join(" / ");` (`Code.js:641-654`) | 기존 Excel 고유 메모를 보존하며 Notion segment를 교체. | note를 import 원문으로 `partner.updateNote(note)` (`EcountPartnerImporter.java:723-771`) | 없음 | 필요. 메모 병합인지 원천 덮어쓰기인지 확인. |

### 2.4 내일자 전표 이미지 생성 — 5개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| N-01 | `const allowedWarehouses = ['삼성창고 (초월 무갑)', '상일물류'];` / `if(!allowedWarehouses.includes(warehouse)) return;` (`tools/legacy-gas/내일자 전표 이미지 생성/Index.html:437-479`) | 두 창고만 대상. | 입력일+1의 활성 전표 전체 조회 (`services/slip-service/src/main/java/com/samhanair/logis/slip/service/NextDaySlipImageService.java:63-71`) | 다름 | 필요. 창고 gate 유지 여부. |
| N-02 | `const jsException = String(row['거래처코드'] \|\| '').trim() === '8428102605'; const isForbidden = forbiddenData.includes(customer) \|\| jsException;` (`Index.html:473-477`) | 전표는 만들되 발송제한 문구. | partnerCode/legacyNameKey block set을 boolean으로 반환, 하드코딩 예외·회계방 제거 없음 (`NextDaySlipImageService.java:80-109`) | 다름 | 필요. 예외 코드와 회계방 정책 확인. |
| N-03 | `qty: parseInt(row['수량']) \|\| 0`; `if (totalQty === 0) continue;` (`Index.html:483-507`) | 정수 절사 수량합 0인 전표 제외. | 응답은 slip 단위이며 line 수량·상쇄 gate가 없음 (`NextDaySlipImageResponse.java:41-51`) | 다름 | 필요. 반품 상쇄 전표 표시 여부. |
| N-04 | `if (L.getDay() === 6 && !first.note.includes('일요일')) {` / `if (/야적.*\//.test(addr)) {` / `else if (/지방.*\//.test(addr)) {` (`Index.html:525-580`) | 주소·특이사항으로 하차 예정일 문구 결정. | targetDate는 호출일+1이고 별도 문구/캘린더 없음 (`NextDaySlipImageService.java:63-68`) | 다름 | 필요. 기준일과 주말 캘린더 확정. |
| N-05 | `if (unshippedModels.some(model => r.item.includes(model))) {` / `if (isRain) {` / `} else if (isSnow) {` (`Index.html:582-615`) | 품목·기상 안내문 추가. | 응답 DTO에 미출/기상/안내문 필드 없음 (`NextDaySlipImageResponse.java:41-51`) | 없음 | 필요. 이 안내가 아직 업무 필수인지 확인. |

### 2.5 전표정리리스트 — 4개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| C-01 | `parseInt(rawAmount).toLocaleString()`과 8필드 `join('\|\|')` (`tools/legacy-gas/전표정리리스트/Index.html:404-428`) | 금액 원 미만 절사 후 snapshot key 생성. | VAT 포함 `BigDecimal totalAmount` 계산 (`services/slip-service/src/main/java/com/samhanair/logis/slip/service/SlipCleanupService.java:112-139`) | 다름 | 필요. 표시 절사와 수정 비교 정밀도 확인. |
| C-02 | `if ((hasYard \|\| hasJibang) && !hasSangHa) {` / `else if (mVal !== null && actualDay !== null && actualDay !== mVal) {` (`Index.html:564-615`) | 일정 이상 상태 표시. | 4 flag는 partnerCodeMissing/amountZero/linesMissing/regionMissing (`services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipCleanupResponse.java:11-18`) | 없음 | 필요. 일정 이상 검사가 다른 화면으로 대체됐는지 확인. |
| C-03 | `let currentState = 'unsent';` / `currentState = 'discard';` / `currentState = 'sent';` / `currentState = 'mod1';` / `currentState = 'mod2';` / `currentState = 'mod3';` (`Index.html:728,761,764,773-775`) | base/over/modHistory로 발송·폐기·복원·수정차수 판정. | 전표 status별 count와 4 정합성 flag (`SlipCleanupService.java:75-106`) | 없음 | 필요. 발송 증거를 수동 snapshot/실제 event 중 결정. |
| C-04 | `pMap.set(key, { base: curStr, over: null, status: 'completed' });` (`Index.html:996-1016`) | 사용자가 완료를 수동 각인하고 저장. | cleanup history는 보고서 payload 저장이지 전표별 완료 상태기가 아님 | 없음 | 필요. 수동 완료 workflow 필요 여부. |

### 2.6 입출고 내역 — 5개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| I-01 | `DriveApp.getFilesByName('이카운트입출고내역.xlsx')` (`tools/legacy-gas/입출고 내역/code.js:14-17`) | Drive Excel 1개가 원천. | 활성 confirmed/delivered/completed 내부 전표 (`services/slip-service/src/main/java/com/samhanair/logis/slip/service/InOutAnalysisService.java:35-60,79-83`) | 다름 | 필요. 내부 전표 완전성 확인. |
| I-02 | `var cleanName = rawName.replace(/[\(\[\{].*?[\)\]\}]/g, '').trim();` / `if (cleanName && !hasUsage && !isKoreanOnly && !startsWithL) {` (`code.js:53-61`) | 표시명 기반 모델 제외. | `line.getModelName()` 그대로 key (`InOutAnalysisService.java:42-46`) | 다름 | 필요. 제외 문자열이 폐기 가능한지 확인. |
| I-03 | `keyDate = rawDate.replace('/', '').substring(0, 6)` 후 입고·출고 합산 (`code.js:63-70`) | 모델×월 수량 합계. | `YearMonth.from(date)`에 inbound/outbound 수량 누적 (`InOutAnalysisService.java:129-133`) | 동일 | 필요. 대상 상태만 확인. |
| I-04 | `document.getElementById('startDate').value = '2025-01';` / `document.getElementById('endDate').value = '2025-12';` / `else if (diffMonths >= 12) {` (`tools/legacy-gas/입출고 내역/index.html:234-278`) | 고정 기본기간과 12개월 제한. | 사용자가 from/to를 주며 서버 제한 없음 (`clients/desktop/src/renderer/routes/warehouse/InOutAnalysisPage.tsx:27-35`) | 다름 | 필요. 분석 최대기간 결정. |
| I-05 | `const exists = modelNames.some(m => m.toUpperCase() === val);` / `label: '입고수량'` / `label: '출고수량'` (`index.html:378-454`) | 단일 모델 월 차트. | 복수 chip 필터와 전체 모델 월점·표 (`InOutAnalysisPage.tsx:31-39,53-83`) | 다름 | 필요. 단일 모델/복수 분류 UX 중 확정. |

### 2.7 입출고 분석 — 6개

| ID | ① 레거시 원문(파일:라인) | ② 레거시 규칙 | ③ 현행 구현(파일:라인) | ④ 판정 | ⑤ 업무 확인 |
|---|---|---|---|---|---|
| A-01 | `result.push({` / `model: cleanName,` / `category: category,` / `year: year,` / `month: month,` / `quantity: quantity` (`tools/legacy-gas/입출고 분석/Code.js:221-227`) | 금액·이익은 계산하지 않음. | 공급가액으로 매입·매출·단위이익·이익률 산출 (`InOutAnalysisService.java:47-57,107-125`) | 다름 | 필요. 이익률 정의가 회계상 맞는지 확인. |
| A-02 | `if (prefix === 'AJ') {` / `category = '홈멀티';` / `} else if (prefix === 'AM') {` / `category = '상업멀티';` / `} else if (['AC', 'AP', 'AR', 'AF'].indexOf(prefix) !== -1) {` / `category = '싱글중대형';` (`Code.js:203-210`) | 모델문자열로 분류·제외. | product category와 이름으로 chip 계산 (`clients/desktop/src/renderer/routes/warehouse/inoutAnalysisModel.ts:168-177`) | 확인 불가 | 필요. 동일 실모델 분류 결과 대조. |
| A-03 | `item.year === 2025` / `item.year === 2026` (`tools/legacy-gas/입출고 분석/Index.html:350-354`) | 두 고정 연도를 전년/당년으로 사용. | 자료에 존재하는 연도 중 최근 두 개 선택 (`inoutAnalysisModel.ts:86-102`) | 다름 | 필요. rolling 연도 정책 확인. |
| A-04 | `rate = totalThis / totalLast`; 미래월 `Math.round(monthlyLast[m] * rate)` (`Index.html:376-382`) | 동기 배율로 전년 미래월을 곱해 예측. | `forecastRate`와 `Math.round(previous * forecastRate)` (`inoutAnalysisModel.ts:117-123`) | 동일 | 필요. 단순 배율 예측을 계속 쓸지 확인. |
| A-05 | `var sorted = Object.keys(outCounts).sort(function(a, b) { return outCounts[b] - outCounts[a]; });` / `var topRank = sorted.slice(0, 3);` / `var bottomRank = sorted.slice(-3).reverse();` (`Index.html:388-393`) | Top/Bottom 3. | 출고량 0을 먼저 제외하고 같은 slice (`inoutAnalysisModel.ts:144-149`) | 다름 | 필요. 0출고 모델을 Bottom에 포함할지 확인. |
| A-06 | `if (stock <= 0) recs.push({ text: top + ' 발주 권장', subtext: '출고량 대비 잔여 재고가 부족합니다.' });` / `if (rate > 1.1) recs.push({ text: '전반적 수요 상승', subtext: '작년 동기 대비 판매량이 증가하고 있습니다.' });` (`Index.html:395-403`) | 추천·알림. | 같은 조건과 `특이사항 없음` fallback (`inoutAnalysisModel.ts:150-164`) | 동일 | 필요. 누적 입고-출고를 재고로 볼 수 있는지 확인. |

## 전수 범위와 한계

- 12개 레거시 디렉터리의 생존 업무 함수와 그 호출 분기를 읽었다. 저장소 압축·Notion pagination·셀 선택·canvas 좌표처럼 대상/값/상태를 바꾸지 않는 기술 구현은 규칙 수에서 제외했다.
- `2026-08-15-data-source-rule-parity.md`의 단톡방·발송금지·지역표 규칙은 중복 집계하지 않았지만, 해당 프로그램의 결과를 바꾸는 사용 방식은 이 보고서에서 다시 대조했다.
- 공유 DB와 운영 파일은 읽지 않았다. 따라서 REGION tag와 레거시 지방 표식의 실데이터 동등성, 모델문자열 분류와 상품 category의 전 행 동등성은 `확인 불가`다.
- 원문 인용은 모두 현재 저장소의 `tools/legacy-gas`에서 그대로 옮겼다. 시크릿 값은 인용하지 않았다.

## 개발책임자 업무 확인 우선순위

1. DPS 금액 포함 1:1 대조를 유지할지, 현행 수량합 대조를 정본으로 할지.
2. 가배차에서 야적 보존과 회수·자가 등 공통 제외의 우선순위, 상일/초월 provenance 불명 행 처리.
3. 미배차 7상태와 전표정리 7상태를 현행에서 폐기한 것이 맞는지.
4. 내일자 전표의 허용창고 2개, 코드 `8428102605`, 회계방, 토요일 지방 +2일 정책의 현재 유효성.
5. 거래처 DC·정액할인·단위처리의 정본과 담당자별 Sheet 배포의 존폐.
6. 입출고 예측의 단순 전년 배율, 누적 입고-출고를 재고로 보는 추천, 새 이익률 산식의 업무 명칭.
