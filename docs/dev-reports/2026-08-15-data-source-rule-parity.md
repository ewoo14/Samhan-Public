# 데이터 원천 4종 업무 규칙 패리티 정찰

- 정찰일: 2026-08-15
- 정찰자: CODEX SOL
- 질문: **“단톡방·발송금지·거래처 DC·지역별 분류표의 값은 레거시와 같은 규칙으로 쓰이며, Notion 변경을 계속 따라가는가?”**
- 레거시 범위: `tools/legacy-gas/배차안내문자`, `내일자 전표 이미지 생성`, `거래처별 일괄 거래명세서 생성`, `거래처별 원장생성 프로그램`, `거래처 발송 주문서`, `종합견적서`, `일마감 프로그램`, `가배차분류리스트`
- 현행 범위: `notification-service`, `partner-service`, `dc-config-service`, `arologis-service`, `slip-service`, `accounting-service`와 해당 desktop/web 호출부
- 안전 조건: 정적 소스와 읽기 전용 Git 이력만 대조했다. PM 실측 숫자를 다시 재지 않았고, GAS·공유 DB·서비스에 쓰지 않았으며 Git 쓰기·배포·프로세스 제어를 하지 않았다.

## 결론 요약

**값의 개수와 규칙 패리티는 별개이며, 네 원천 모두 “Notion을 계속 따라가는 구조”가 아니다.** 규칙 17개를 대조한 결과는 **동일 1 · 다름 14 · 없음 1 · 확인 불가 1**이다.

가장 큰 차이는 원천 권위다. 레거시는 실행 때마다 Notion을 조회한다(DC만 10분 cache). 현행은 Notion export CSV를 관리자가 import한 뒤 각 서비스 DB를 정본으로 삼는다. 예약 동기화나 Notion runtime client는 확인되지 않았다. 따라서 Notion/CSV 변경, 화면 수정, 재import가 서로 자동 합쳐지지 않는다.

PM 실측은 그대로 전제로 사용했다.

| 원천 | PM 실측 | 규칙상 의미 |
|---|---:|---|
| 지역분류 | CSV 20 = DB 20, 값 완전 일치 | 현재 값은 같지만 주소 판정 알고리즘과 적용 화면은 다르다. |
| 단톡방 | CSV 112 = DB 112, `partner_code` 106 연결 | 방 이름 값은 들어왔지만 매칭 키·1:N·회계방 처리·장애 처리가 다르다. |
| 발송금지 | CSV 6, DB 0 | 테이블·화면·조회 코드는 있으나 현재 운영 차단값은 한 건도 없다. |
| DC | CSV 210 = DB 210, drift 9 | 이름 4건은 재import로도 기존 Partner 이름을 고치지 않으며, 단위처리 3건은 최초 importer의 select 파싱 누락 경로가 확인됐다. 비율 2건의 변경 주체는 확인 불가다. |

특히 발송금지는 “코드가 있다”와 “지금 차단된다”가 다르다. 현재 DB 0건이므로 DB 기반 차단은 전부 false이며, 배차문자 화면도 실제 발송 endpoint가 아니라 preview·clipboard 경로만 확인됐다. 내일자 이미지는 레거시가 금지 행을 남겨 `발송제한 업체입니다.`라고 표시하지만 현행은 행 자체를 제외한다.

이 문서는 코드 차이를 업무 정책의 정답으로 간주하지 않는다. 모든 항목의 `⑤ 업무 확인`이 최종 판단 게이트다.

## 판정 기준

- **동일**: 확인한 입력·조건·계산 순서·결과가 소스상 같다.
- **다름**: 같은 목적의 구현이 있으나 원천·매칭 키·조건·적용 시점·결과가 다르다.
- **없음**: 현행 사용자 경로에서 대응 규칙을 찾지 못했다.
- **확인 불가**: 코드로 가능한 원인은 좁혔지만 audit·운영 이력 없이는 실제 변경 주체나 현재 유효성을 확정할 수 없다.

---

## R-01. 네 원천의 권위와 변경 반영 시점

### ① 레거시 규칙

단톡방·발송금지는 `tools/legacy-gas/배차안내문자/Code.js:609-688`에서 실행마다 Notion을 전 페이지 조회한다.

```js
UrlFetchApp.fetch('https://api.notion.com/v1/databases/' + NOTION_DB_ID_CHAT + '/query', opts);
// ...
UrlFetchApp.fetch('https://api.notion.com/v1/databases/' + NOTION_DB_ID_BLOCK + '/query', opts);
```

지역표도 `tools/legacy-gas/가배차분류리스트/Code.js:210-251,583-594`에서 매 실행 조회하고, DC는 `tools/legacy-gas/거래처 발송 주문서/Code.js:2443-2463,2616-2619`에서 Notion을 조회하되 10분 cache를 둔다.

```js
var records = getRegionFromNotion();
// ...
cachePutJSON_(cacheKey, out, 60 * 10);
```

### ② 우리 구현

`tools/operational-validation/import-notion-csv.ps1:3-17,125-156,239-267`

```powershell
tools/legacy-gas/_notion-export/ 의 4 CSV 를 admin endpoint 4 회 POST 호출해
Samhan Public 각 service DB 로 이관한다. 이관 후 조회/수정/삭제는 Notion 이 아니라
Samhan Public DB CRUD 화면/API 에서 수행한다.
```

스크립트를 사람이 실행해 네 import endpoint를 호출하는 구조다. 관련 production 경로에서 `api.notion.com` 호출이나 예약 import를 찾지 못했다.

### ③ 판정

**다름** — 레거시는 Notion runtime read, 현행은 export CSV의 수동·일회성 import 후 DB runtime read다.

### ④ 사용자 차이

Notion 원본을 고쳐도 현행 화면·계산·차단은 바뀌지 않는다. 반대로 현행 화면에서 고친 값도 Notion으로 돌아가지 않는다. 재import하지 않는 동안 두 값은 독립적으로 drift한다.

### ⑤ 업무 확인

**🚨 업무 확인 필요 — 최우선.** 2026-08-15 현재 네 표의 업무 정본이 Notion인지 서비스 DB인지 확인해야 한다. 코드 주석의 “이관 후 DB CRUD”가 실제 운영 합의로 지금도 유효한지는 코드만으로 보장되지 않는다.

---

## R-02. 단톡방 매칭 키·다중 매핑·회계방 제외

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:195-202,307-311`

```js
var k = normalizeForMatch(name);
var room = cleanValue(chatData[name]);
if (isAccountingRoom_(room)) return;
if (!kakaoIndex[k]) kakaoIndex[k] = room;
```

정규화한 **사업자명** 하나를 방 하나에 매핑하고 `회계방`은 발송 방에서 제외한다.

### ② 우리 구현

`services/notification-service/src/main/java/com/samhanair/logis/notification/service/DispatchBatchPreviewService.java:92-131`

```java
List<PartnerChatRoomMapping> mappings =
        chatRoomMappingRepository.findAllByPartnerCode(partnerCode);
if (mappings.isEmpty() && slip.partnerName() != null && !slip.partnerName().isBlank()) {
    mappings = chatRoomMappingRepository.findAllByPartnerBusinessNameSnapshot(slip.partnerName());
}
for (PartnerChatRoomMapping mapping : mappings) { /* 각 방에 entry 추가 */ }
```

`partnerCode` 우선, 사업자명 snapshot exact fallback이며 한 거래처를 N개 방에 fan-out한다. 이 경로에는 `회계방` 제외 조건이 없다.

### ③ 판정

**다름** — 이름 정규화 1:1과 코드 우선 1:N이고, 회계방 제외도 다르다.

### ④ 사용자 차이

이름만 바뀐 거래처는 레거시와 현행의 성공 여부가 달라질 수 있고, 현행은 한 전표가 여러 방에 중복 노출되거나 `회계방`에도 노출될 수 있다. 106건 코드 연결은 이름 drift 내성을 높이지만 나머지 매핑과 회계방 규칙을 같게 만들지는 않는다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 한 거래처의 다중 방이 의도인지, `회계방`을 배차·내일자 발송에서 지금도 제외해야 하는지 확인해야 한다.

---

## R-03. 단톡방의 배차안내문자 그룹·정렬 사용

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:381-397,413-429`

```js
'단톡방': kakao_room,
// ...
var aHas = boolKey(a['단톡방']), bHas = boolKey(b['단톡방']);
if (aHas !== bHas) return aHas - bHas;
if (aKey !== bKey) return aKey < bKey ? -1 : 1;
```

방 있는 행을 먼저 놓고 방 이름, 전화번호, 거래처 순으로 정렬한다.

### ② 우리 구현

`services/notification-service/src/main/java/com/samhanair/logis/notification/service/DispatchBatchPreviewService.java:83-87,135-161`

```java
Map<String, List<PartnerEntry>> grouped = new LinkedHashMap<>();
grouped.computeIfAbsent(entry.chatRoomName(), ignored -> new ArrayList<>()).add(...);
```

방별 그룹은 만들지만 DB/전표 입력 순서의 `LinkedHashMap`이며 레거시 방 이름·전화번호 정렬 comparator는 없다. 미매핑은 별도 목록이다.

### ③ 판정

**다름** — 단톡방으로 묶는 목적은 같지만 표시 순서 규칙이 다르다.

### ④ 사용자 차이

같은 112개 매핑이어도 작업자가 보는 방·거래처 순서가 달라져 수동 복사·확인 순서가 달라진다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 레거시의 “방 있는 행 우선 → 방명 → 전화번호”가 현재도 작업 순서를 뜻하는지 확인해야 한다.

---

## R-04. 단톡방의 내일자 이미지·거래명세서·원장 사용

### ① 레거시 규칙

내일자 이미지는 `tools/legacy-gas/내일자 전표 이미지 생성/Index.html:617-655`에서 거래처명으로 방을 붙이고 회계방을 지운 뒤 방 있는 행부터 정렬한다. 거래명세서도 `tools/legacy-gas/거래처별 일괄 거래명세서 생성/Index.html:817-844`, 원장도 `tools/legacy-gas/거래처별 원장생성 프로그램/Index.html:747-777`에서 사업자명→방 매핑을 표시·정렬·저장 폴더 선택에 쓴다.

```js
let roomName = mappingData[first.customer] || '';
if (roomName.includes('회계방')) roomName = '';
processedList.sort((a, b) => /* 방 존재/방명/연락처 */);
```

### ② 우리 구현

`services/accounting-service/src/main/java/com/samhanair/logis/accounting/client/ChatRoomMappingClient.java:47-80`, `services/accounting-service/src/main/java/com/samhanair/logis/accounting/service/LedgerImageService.java:61-78`, `services/accounting-service/src/main/java/com/samhanair/logis/accounting/service/StatementBatchService.java:68-102`

```java
List<String> chatRooms = chatRoomMappingClient.findChatRoomNamesByPartnerCode(partnerCode);
// 외부 오류도 empty list
```

원장·명세서는 코드로 0~N개 방을 표시하고 notification 장애도 “방 없음”으로 처리한다. 내일자 역시 코드 우선 매핑을 쓴다. 회계방 제외와 레거시 정렬 계약은 이 client에 없다.

### ③ 판정

**다름** — 사용 목적은 남았지만 이름 1개/회계방 제외/정렬에서 코드 N개/fail-soft 표시로 바뀌었다.

### ④ 사용자 차이

notification 장애가 실제 미매핑처럼 보일 수 있고, 한 거래처에 여러 방이 보인다. 레거시에서 숨던 회계방이 문서 화면에 나타날 수 있다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 문서별로 다중 방을 모두 표시할지, 조회 장애와 실제 미매핑을 같은 빈값으로 보여도 되는지 확인해야 한다.

---

## R-05. 단톡방 화면 수정과 변경 효과

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:609-648`에는 조회만 있다. 값 수정은 Notion에서 하며 다음 실행부터 반영된다.

### ② 우리 구현

`services/notification-service/src/main/java/com/samhanair/logis/notification/controller/ChatRoomMappingAdminController.java:43-49`, `services/notification-service/src/main/java/com/samhanair/logis/notification/service/ChatRoomMappingService.java:82-110`

```java
// GET, POST, POST /import, DELETE 네 endpoint
public PartnerChatRoomMapping create(...)
public void delete(UUID id, String actor)
```

화면에서 등록·삭제·CSV import는 가능하지만 기존 행의 방명/거래처를 수정하는 PUT/PATCH는 없다. 바꾸려면 삭제 후 재등록하며 이후 DB 소비자는 새 값을 읽는다.

### ③ 판정

**다름** — Notion 직접 수정과 DB 등록·삭제이며 in-place 수정도 없다.

### ④ 사용자 차이

현행에서 바꾼 방은 배차·내일자·명세서·원장에 반영되지만 Notion에는 남지 않는다. Notion만 고치면 아무 변화가 없다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 단톡방 관리 주체와 수정 정본이 Notion에서 현행 화면으로 실제 이관됐는지 확인해야 한다.

---

## R-06. 발송금지 매칭 키와 현재 운영값

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:161-168,269-292`는 Notion 사업자명을 정규화해 매칭한다. 내일자 이미지는 `tools/legacy-gas/내일자 전표 이미지 생성/Index.html:473-477`에서 exact 이름에 코드 `8428102605` 예외를 추가한다.

```js
const jsException = String(row['거래처코드'] || '').trim() === '8428102605';
const isForbidden = forbiddenData.includes(customer) || jsException;
```

### ② 우리 구현

`services/partner-service/src/main/java/com/samhanair/logis/partner/service/PartnerBlockService.java:101-123`과 `services/partner-service/src/main/java/com/samhanair/logis/partner/service/PartnerBlockImportService.java:85-199`는 거래처코드 우선, 이름 alias fallback으로 DB의 활성 차단을 조회·이관한다. 그러나 PM 실측 DB는 0건이다.

### ③ 판정

**다름** — 매칭 키가 다르고, 무엇보다 현행 운영 차단 집합이 비어 있다.

### ④ 사용자 차이

레거시에서 6개 이름(및 내일자 hard-coded 1개)에 걸리던 거래처가 현행 DB 기반 경로에서는 차단되지 않는다.

### ⑤ 업무 확인

**🚨 업무 확인 필요 — 최우선.** CSV 6개가 지금도 유효한 발송금지인지와 hard-coded 거래처코드의 현재 유효성을 각각 확인해야 한다. 이 확인은 별도 적재 승인과 구분된다.

---

## R-07. 배차안내문자에서 발송금지가 막는 경로

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:272-292,431`

```js
'배송주소': '', '인수자 번호': '',
'발송멘트': '발송금지 업체입니다.', '기사번호': '',
// 결과 행은 남기고 오류 집합으로 분류
```

발송용 내용과 연락처를 비우고 금지 행을 오류로 남긴다.

### ② 우리 구현

`services/notification-service/src/main/java/com/samhanair/logis/notification/service/DispatchBatchPreviewService.java:121-146,164-172`는 `blocked` 표시를 붙이나 조회 실패는 false로 둔다. `clients/desktop/src/renderer/routes/DispatchSmsPage.tsx:233-250,476-504`는 금지 행 textarea를 disable하지만 선택·clipboard row 구성에는 `blocked` 배제가 없다. `services/notification-service/src/main/java/com/samhanair/logis/notification/controller/DispatchBatchAdminController.java:25-65`에는 preview endpoint만 있고 실제 send endpoint를 찾지 못했다.

### ③ 판정

**다름** — 현행은 preview 편집만 막고 clipboard에서 강제 제외하지 않으며, 조회 장애도 fail-open이다. DB 0건이라 현재는 그 표시조차 발생하지 않는다.

### ④ 사용자 차이

발송금지 거래처도 선택·복사 대상에 남을 수 있고, 차단 조회 장애를 정상으로 오인할 수 있다. “발송 직전 hard block”은 확인되지 않았다.

### ⑤ 업무 확인

**🚨 업무 확인 필요 — 최우선.** 현행 실제 발송이 이 화면 밖에서 이뤄지는지, 금지가 preview 경고인지 강제 발송 차단인지 확인해야 한다.

---

## R-08. 내일자 전표 이미지에서 발송금지가 막는 경로

### ① 레거시 규칙

`tools/legacy-gas/내일자 전표 이미지 생성/Index.html:627-661`

```js
processedList.push({ /* ... */ isForbidden: first.isForbidden });
item.msgText = item.isForbidden ? '발송제한 업체입니다.' : item.msgLines.join('\n');
```

금지 전표도 이미지·결과 행을 만들되 발송 문구를 제한 표시로 바꾼다.

### ② 우리 구현

`services/slip-service/src/main/java/com/samhanair/logis/slip/service/NextDaySlipImageService.java:80-95`가 blocked를 반환하고, `clients/desktop/src/renderer/api/nextDaySlipApi.ts:125-172`는 blocked row를 `continue`하여 그룹에서 제거한다. block client 오류는 빈 집합으로 처리한다.

### ③ 판정

**다름** — 레거시는 남겨 경고, 현행은 결과에서 완전 제외하며 장애 시에는 포함한다.

### ④ 사용자 차이

차단 데이터가 적재되면 현행에서는 금지 전표 자체를 확인할 수 없고, 지금처럼 DB 0건이면 모두 일반 전표처럼 보인다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 발송금지 전표를 작업 목록에 남겨 사유를 보여야 하는지, 아예 생성 대상에서 숨겨야 하는지 확인해야 한다.

---

## R-09. 발송금지 화면 수정과 변경 효과

### ① 레거시 규칙

`tools/legacy-gas/배차안내문자/Code.js:651-688`은 Notion 조회만 하므로 Notion 행 추가·삭제가 다음 실행의 차단 집합을 바꾼다.

### ② 우리 구현

`services/partner-service/src/main/java/com/samhanair/logis/partner/controller/PartnerBlockAdminController.java:34-48,57-129`와 `services/partner-service/src/main/java/com/samhanair/logis/partner/service/PartnerBlockService.java:35-99`는 목록·등록·CSV import·차단해제(soft delete)를 제공한다. 기존 행의 사유를 수정하는 endpoint는 없다. 변경 후 notification/slip 서비스가 DB를 조회하지만 실패 시 양쪽 모두 차단하지 않는다.

### ③ 판정

**다름** — 현행 화면 등록·해제는 즉시 DB 차단 집합을 바꾸나 Notion과 동기화하지 않고, 장애 시 fail-open이다.

### ④ 사용자 차이

현행 화면에서 등록하면 배차 preview·내일자 생성이 바뀌지만 Notion은 그대로다. 현재 0건 상태에서는 화면이 있어도 차단 효과가 없다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 누가 차단·해제를 승인하는지, 조회 장애 때 발송을 허용할지 중단할지 확인해야 한다.

---

## R-10. DC 기본율·품목 고정DC의 멀티 단가 적용

### ① 레거시 규칙

`tools/legacy-gas/거래처 발송 주문서/index.html:2452-2481,2575-2602`

```js
const fixedDc = parseFixedDc(r['고정DC']);
const useRate = (fixedDc ?? rate);
computed = Math.round(currentListPrice * (1 - useRate));
const finalVat = roundByConfig(computed);
```

품목 고정DC가 있으면 거래처 홈/상업멀티율보다 우선하고, 출고가에 `(1-rate)`를 곱힌 뒤 단위처리한다.

### ② 우리 구현

`services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/PriceCalculationService.java:54-84,103-127,183-198`

```java
BigDecimal appliedRate = pickCategoryRate(
        config, line.category(), line.fixedDiscountRate(),
        line.hasVariableDiscount(), applyNoMainEquipmentRule);
BigDecimal afterRate = listPrice.multiply(BigDecimal.ONE.subtract(appliedRate));
BigDecimal optionDc = sumOptionDc(config, line);
BigDecimal afterOption = afterRate.subtract(optionDc).max(BigDecimal.ZERO);
BigDecimal finalPrice = roundToUnit(afterOption, config);
```

정상적인 멀티 본체에서 품목 고정율 우선, 거래처 카테고리율 적용, 단위처리 순서가 같다.

### ③ 판정

**동일** — 이 범위의 기본 우선순위와 계산 순서는 같다. 단, 옵션·본체 없는 주문·현재 drift 값은 다음 규칙에서 별도 판정한다.

### ④ 사용자 차이

같은 출고가·고정DC·거래처율·반올림 설정이 들어오면 이 기본 경로의 단가는 같다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 품목 고정DC가 거래처율보다 항상 우선한다는 규칙이 현재도 유효한지 확인해야 한다.

---

## R-11. DC 옵션 6종의 적용 대상과 백분율/정액 판정

### ① 레거시 규칙

`tools/legacy-gas/거래처 발송 주문서/index.html:2532-2551`

```js
const calc = (val, rateAmt) => rateAmt < 1
  ? Math.round(val * (1 - rateAmt)) : Math.max(0, val - rateAmt);
if (flags.is360 && d360 > 0) v = calc(v, d360);
// 4way, stand, 1way, deluxe, grade1 순차 적용
```

값이 1 미만이면 비율, 그 이상이면 정액으로 보고 해당 모델 flag마다 순차 적용한다.

### ② 우리 구현

`services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/PriceCalculationService.java:166-180`

```java
if ("HOMEMULTI".equals(line.category()) || "COMMERCIAL_MULTI".equals(line.category())) {
    return BigDecimal.ZERO;
}
if (line.is360()) sum = sum.add(nz(config.getDiscount360Amount()));
// ... 합계를 한 번 차감
```

홈/상업멀티에는 옵션값을 적용하지 않고 OTHER에서 6종을 모두 **정액 합산**한다. 1 미만 비율 의미가 없다.

### ③ 판정

**다름** — 적용 카테고리와 비율/정액 판정, 순차 계산이 다르다.

### ④ 사용자 차이

같은 옵션값도 레거시는 퍼센트 복리처럼 적용될 수 있으나 현행은 원 단위 합산 차감이 되거나 멀티에서 무시된다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 6개 값의 단위가 지금도 “1 미만 비율/그 이상 원”인지, 어느 상품군에 적용하는지 확인해야 한다.

---

## R-12. DC 단위처리(반올림·올림·내림)의 적용과 drift 3건

### ① 레거시 규칙

`tools/legacy-gas/거래처 발송 주문서/Code.js:2588-2614`, `tools/legacy-gas/거래처 발송 주문서/index.html:1611-1628`

```js
if (/반올림/.test(unitSel)) roundMode = 'ROUND';
else if (/올림/.test(unitSel)) roundMode = 'CEIL';
// ...
if (unit > 0) return Math.ceil/Math.floor/Math.round(v / unit) * unit;
```

멀티 DC 계산 결과에 선택한 10/100/1000원 단위와 mode를 적용한다.

### ② 우리 구현

현재 importer는 `services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/DcConfigImportService.java:304-336`에서 9개 select를 올바르게 `enabled/roundTo/mode`로 변환하고, 계산은 `services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/PriceCalculationService.java:183-198`에서 `roundTo`와 `mode`를 적용한다. 그러나 최초 importer commit `b44ede31c`의 `services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/DcConfigImportService.java:155-176`은 다음과 같았다.

```java
boolean unitProc = parseYesNo(get(row, col, COL_UNIT_PROC));
cfg.changeUnitProcessingEnabled(unitProc);
```

`100원 반올림/올림`은 Yes가 아니므로 false가 되고 `roundTo/mode`는 저장하지 않았다. PM 실측 3건의 `enabled=false · round_to=NULL`과 정확히 같은 결과다. 자동 재import가 없어 과거 값이 남을 수 있다.

### ③ 판정

**다름** — 현재 코드는 산식을 옮겼지만 실제 3개 DB 행에는 계산에 필요한 `roundTo`가 없어 1원 HALF_UP만 적용된다. 화면의 enabled flag도 계산 함수가 읽지 않는다.

### ④ 사용자 차이

CSV가 `100원 반올림/올림`인 세 거래처의 최종 단가가 레거시와 최대 99원 단위로 달라질 수 있다.

### ⑤ 업무 확인

**🚨 업무 확인 필요 — 최우선.** 이 세 거래처의 단위처리가 현재도 유효한지 확인해야 한다. 과거 importer가 drift를 만든 코드 경로는 확인됐지만 운영자가 이후 의도적으로 끈 것인지까지는 코드로 단정할 수 없다.

---

## R-13. DC 화면 수정 가능 필드와 계산 반영

### ① 레거시 규칙

`tools/legacy-gas/거래처 발송 주문서/Code.js:2443-2463,2588-2619`에서 DC는 Notion에서 수정하며 최대 10분 cache 후 다시 읽는다.

### ② 우리 구현

`services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/dto/UpdatePartnerDcConfigRequest.java:16-27`와 `services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/DcConfigService.java:112-168`는 홈/상업율, 옵션, I형, `unitProcess` Yes/No, 비고를 PATCH한다. `services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/dto/PartnerDcConfigResponse.java:41-55`도 단위처리를 Yes/No만 노출한다.

```java
Boolean unitProc = parseYesNo(req.unitProcess());
dc.changeUnitProcessingEnabled(unitProc);
```

업체명·거래처코드·`unitRoundTo`·`unitRoundMode`는 화면에서 고칠 수 없다. 비율·옵션은 이후 DB 계산에 즉시 반영되지만 enabled만 바꿔서는 `PriceCalculationService`의 반올림이 바뀌지 않는다.

### ③ 판정

**다름** — DB 화면 편집은 가능하지만 레거시 select의 핵심 단위/mode를 편집하지 못하고, Yes/No 토글은 가격 계산의 gate가 아니다.

### ④ 사용자 차이

화면에서 “단위처리 Yes”로 바꿔도 `round_to=NULL`이면 금액은 그대로다. 홈 46→48%, 상업 공란→49% 같은 화면 수정은 곧바로 이후 계산을 바꾼다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 화면의 단위처리 Yes/No가 실제로 어떤 계산을 뜻해야 하는지, mode·단위를 누가 관리하는지 확인해야 한다.

---

## R-14. DC drift 9건의 발생 경로와 정확한 변경 주체

### ① 레거시 규칙

`tools/legacy-gas/거래처 발송 주문서/Code.js:2443-2463`은 Notion 값을 runtime에 읽으므로 별도 DB drift가 없다.

### ② 우리 구현

`services/dc-config-service/src/main/java/com/samhanair/logis/dcconfig/service/DcConfigImportService.java:155-184`는 기존 partner가 있으면 CSV `businessName`으로 이름을 갱신하지 않고, DC율·옵션·단위만 upsert한다.

```java
Partner partner = partnerRepository.findByPartnerCode(partnerCode)
        .orElseGet(() -> partnerRepository.save(Partner.create(
                partnerCode, partnerCode, businessName,
                null, null, null, null, null, "NOTION_DC_IMPORT")));
cfg.changeRates(homeRate, commRate);
```

따라서 업체명 4건은 재import해도 기존 Partner 이름이 유지된다. 단위 3건은 R-12의 과거 importer 경로가 설명한다. 홈율 1건·상업율 1건은 화면 PATCH 또는 CSV/Notion 변경 후 미재import 어느 쪽도 가능하다.

### ③ 판정

**확인 불가** — drift를 허용·지속시키는 코드와 단위처리의 역사적 원인은 확인했지만, 9행 각각의 마지막 변경 actor·시각·당시 원천 snapshot을 조회하지 않아 정확한 변경 주체는 확정할 수 없다.

### ④ 사용자 차이

“CSV 210 = DB 210”이어도 업체명·율·반올림은 독립적으로 달라지고, 재import가 모든 drift를 복구하지도 않는다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 업체명 4건과 비율 2건에서 CSV와 DB 중 어느 값이 현재 업무 정답인지 확인해야 한다. audit 없이 값 차이만 보고 한쪽을 덮어써서는 안 된다.

---

## R-15. 지역별 분류표의 주소 매칭 알고리즘

### ① 레거시 규칙

`tools/legacy-gas/가배차분류리스트/Code.js:276-311`

```js
var three = String(addr).split(/\s+/).slice(0,3).join(' ');
// 광역시 → 특별시 → 특별자치 → 도 순서
// 도 검색어가 여러 개면 first-three 안에서 가장 앞선 위치를 선택
return ['<미분류>',''];
```

주소 첫 3 token만 보고 행정구역 종류별 단계와 “가장 앞선 검색어 위치”로 그룹을 결정한다.

### ② 우리 구현

`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionClassifier.java:48-103`

```java
String normalized = address.replaceAll("\\s+", "");
for (Region region : regions) {
    if (normalized.startsWith(metroPrefix)) return region.getGroupName();
}
for (Region region : regions) {
    for (String keyword : region.keywordList()) {
        if (normalized.contains(keyword.replaceAll("\\s+", ""))) return region.getGroupName();
    }
}
```

전체 주소 공백을 제거하고 광역 접두, `sortOrder` 순 첫 keyword 포함, group명 접두 순으로 판정한다. 첫 3 token·행정구역 단계·최초 위치 비교가 없다.

### ③ 판정

**다름** — 표의 20개 값이 같아도 매칭 범위와 우선순위 알고리즘이 다르다.

### ④ 사용자 차이

검색어가 주소 뒤쪽에 있거나 여러 도 검색어가 섞인 주소, 광역시 접두만 있는 주소는 서로 다른 그룹으로 갈 수 있다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 레거시의 “첫 3 token”과 행정구역별 우선순위가 현재도 유효한 오분류 방지 규칙인지 확인해야 한다.

---

## R-16. 지역표가 가배차 8개 mode와 지역배차 화면에 미치는 범위

### ① 레거시 규칙

`tools/legacy-gas/가배차분류리스트/Code.js:583-606`은 매 실행 표로 `region_priority`·keyword를 만들고 8개 method 모두 같은 분류 함수를 사용한다.

```js
case '1': out = sangil_chowol_except_region(...); break;
// ...
case '8': out = sangil_with_region(...); break;
```

### ② 우리 구현

`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/PreClassifyService.java:64-156`의 가배차는 표 기반 `RegionClassifier`와 8개 mode filter를 사용한다. 그러나 `services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionalService.java:49-117`의 `/dispatches/regional`은 표를 호출하지 않고 hard-coded 17개 시·도 prefix와 `deliveryTag == REGION`으로 분류한다.

### ③ 판정

**없음** — 현행 지역배차 화면에는 “지역표가 그룹·우선순위를 결정”하는 대응 규칙이 없다. 가배차 화면에는 있으나 알고리즘은 R-15처럼 다르다.

### ④ 사용자 차이

지역표를 고쳐도 `/dispatches/regional` 결과는 바뀌지 않는다. 같은 주소가 가배차와 지역배차에서 다른 기준으로 분류될 수 있다.

### ⑤ 업무 확인

**🚨 업무 확인 필요 — 최우선.** “지역별 분류표”가 가배차에만 적용되는지 지역배차에도 적용되어야 하는지 확인해야 한다.

---

## R-17. 지역표 화면 수정·import와 기존 전표 반영

### ① 레거시 규칙

`tools/legacy-gas/가배차분류리스트/Code.js:210-251,583-594`에서 Notion 행의 생성순서가 우선순위이고, 그룹·검색어 수정은 다음 실행에 바로 쓰인다.

### ② 우리 구현

`services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionService.java:30-81`은 생성·검색어/정렬 수정·soft delete를 제공하되 그룹명은 수정하지 않는다. `services/arologis-service/src/main/java/com/samhanair/logis/arologis/service/RegionImportService.java:43-121`은 CSV 행 순서를 `sortOrder`로 upsert하며 CSV에서 빠진 기존 그룹을 삭제하지 않는다. `services/arologis-service/src/main/java/com/samhanair/logis/arologis/parser/KakaoDispatchParser.java:226-240`은 새 parse 때 표를 쓰지만, `services/slip-service/src/main/java/com/samhanair/logis/slip/service/NextDaySlipImageService.java:92-95`는 전표에 저장된 `classifiedRegionGroup` snapshot을 읽는다.

### ③ 판정

**다름** — 화면 수정은 다음 분류부터 반영되지만 기존 snapshot을 일괄 재분류하지 않고, 재import도 원천에서 삭제된 그룹을 제거하지 않는다.

### ④ 사용자 차이

검색어·순서를 고친 뒤에도 이미 분류된 전표와 새 전표가 다른 그룹을 보일 수 있다. CSV에서 행을 지우고 재import해도 DB의 옛 그룹이 남을 수 있다.

### ⑤ 업무 확인

**🚨 업무 확인 필요.** 지역표 변경을 과거 전표에 소급할지, CSV 삭제를 DB 삭제로 볼지, 그룹명 변경을 삭제+생성으로 처리해도 되는지 확인해야 한다.

---

## 정찰 범위와 미확인 영역

- 네 원천 모두 끝까지 훑었다. 단톡방은 배차안내문자·내일자 이미지·거래명세서·원장, 발송금지는 배차안내문자·내일자 이미지, DC는 발송 주문서·종합견적서·일마감 및 현행 공통 가격 계산, 지역표는 가배차·지역배차·내일자 snapshot 경로까지 추적했다.
- PM 실측 숫자는 재측정하지 않았다. 공유 DB를 조회하지 않았으므로 9개 drift 행의 audit actor·timestamp와 6개 발송금지 원문은 확인하지 않았다.
- runtime Notion 자동 동기화는 관련 production 소스와 import script에서 찾지 못했다. 저장소 밖 배치·외부 자동화가 별도로 있는지는 **확인 불가**다.
- 배차문자 화면에서 실제 외부 메시지 발송 API는 찾지 못했다. 따라서 “clipboard 이후 사람이 어디에서 보내는가”와 그 외부 단계의 차단은 **확인 불가**다.
- 코드에 남은 `1WAY`, `회계방`, hard-coded 거래처코드, 첫 3 token, fail-open이 2026-08-15에도 유효한 업무 정책이라는 보장은 없다. 각 규칙의 업무 확인이 필요하다.

## 업무 확인 우선순위 — 수정 제안 아님

1. 네 표의 현재 정본: Notion과 서비스 DB 중 어느 쪽인가.
2. 발송금지 6개와 hard-coded 1개의 현재 유효성, 금지 시 “경고 유지/목록 제외/발송 hard block” 중 어느 정책인가.
3. DC drift 9개에서 CSV/DB 정답, 특히 단위처리 3개의 mode·단위.
4. 지역표가 가배차만 지배하는지 `/dispatches/regional`도 지배하는지, 첫 3 token 규칙의 현재 유효성.
5. 단톡방 다중 매핑과 `회계방` 제외의 현재 정책.
