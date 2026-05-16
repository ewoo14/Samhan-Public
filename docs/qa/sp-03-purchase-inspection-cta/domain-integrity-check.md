# SP-03 구매관리 입고 검수 CTA 도메인 정합성 체크

## 목적

SP-03은 frontend CTA 복구처럼 보이지만, 클릭 직후 inventory-service 검수 API와 gateway route까지 이어지는 업무 흐름이다. 따라서 UI 버튼, FE 타입, mock fixture, gateway/controller path, 권한, 업무번호 표준을 함께 확인한다.

## 정적 계약 확인

```powershell
rg -n "InboundInspectionDialog|INSPECTABLE_STATUSES|canInspectInbound|purchase-query-inspect|slipId=\\{inspectionSlipId\\}" clients/desktop/src/renderer/routes/purchase-query/PurchaseQueryPage.tsx
```

기대:

- `InboundInspectionDialog` import/render 존재.
- `INSPECTABLE_STATUSES = ['SAVED', 'CONFIRMED']`.
- 버튼 test id는 `row.slipNo` 기반 public id.
- `onSuccess`에서 `slipsQuery.refetch()` 호출.

```powershell
rg -n "status: SlipStatus|canInspectInbound|WAREHOUSE|MANAGER|MASTER|INVENTORY" clients/desktop/src/renderer/api/slip.ts clients/desktop/src/renderer/stores/session.ts
```

기대:

- `SlipQueryRow.status` 타입 존재.
- `canInspectInbound()`은 `WAREHOUSE / MANAGER / MASTER`만 허용.
- `INVENTORY`는 입고 검수 CTA helper에 포함되지 않는다.

## Gateway/API path 정합성

```powershell
rg -n "Path=/api/v1/inventory|StripPrefix=2|RequestMapping\\(\\{\\\"/inventory/inbound-inspections\\\"" services/api-gateway/src/main/resources/application.yml services/inventory-service/src/main/java/com/samhanair/logis/inventory/web/InboundInspectionController.java
```

기대:

- Gateway는 `/api/v1/inventory/**`에서 `api/v1`만 제거한다.
- inventory-service controller는 gateway 도착 경로 `/inventory/inbound-inspections/**`를 수신한다.
- 기존 MockMvc/직접 호출 호환용 `/api/v1/inventory/inbound-inspections/**`도 유지한다.

## Docker/Testcontainers gate

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:inventory-service:test --tests "com.samhanair.logis.inventory.it.InboundInspectionControllerIT" --no-daemon --rerun-tasks
```

기대:

- `WAREHOUSE` GET/inspect/complete/list 200.
- `SALES` 403.
- `INVENTORY` 403.
- Gateway stripped path `/inventory/inbound-inspections/{slipId}` 200.
- 결과는 `failures=0 / errors=0 / skipped=0`.

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:slip-service:test --tests "com.samhanair.logis.slip.it.SlipQueryRedesignSpecIT" --tests "com.samhanair.logis.slip.it.SlipQueryRedesignIT" --no-daemon --rerun-tasks
```

기대:

- `/slips/query?slipType=INBOUND` 응답에 `id/status/slipNo`가 포함된다.
- slipNo는 `^\d{4}/\d{2}/\d{2}-\d+$` 형식이다.
- 판매/구매 같은 날짜 같은 순번은 업무 타입으로 구분된다.

## UUID 비노출 검색 가드

```powershell
rg -n "slipId|lineId|inspectionId|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}" docs/qa/sp-03-purchase-inspection-cta/screenshots
```

기대:

- 캡처 파일명/텍스트에는 내부 UUID 패턴과 내부 id key가 없다.
- `slipId`, `lineId`, `inspectionId`는 API 타입/요청 param/정적 contract source에서만 쓰고 화면 표시 문자열에는 넣지 않는다.

## 업무번호 범위형 표준

재고이동 이동번호도 같은 공개 표시번호 표준을 따른다. 이동전표 메뉴 자체가 업무 타입 구분자이므로 `T-`/`TR-` prefix는 사용하지 않는다. 신규 채번은 해당 날짜의 마지막 numeric suffix + 1을 사용한다.

```powershell
rg -n "transferNo: 'T-|transferNo: 'TR-|String prefix = \"TR-\"|TR-YYYYMMDD" clients/desktop/src/renderer/api/mock.ts services/inventory-service/src/main/java services/inventory-service/src/test
```

기대:

- 검색 결과 0건.
- mock 이동번호는 `2026/05/04-1` 형식.
- `StockTransferService.nextTransferNo()`는 `yyyy/MM/dd-` prefix + 일자별 마지막 seq 이후 번호를 사용.
- 같은 날짜에 `2026/05/02-7`이 남아 있으면 다음 번호는 row count 기준 `2026/05/02-2`가 아니라 `2026/05/02-8`이어야 한다.
- Flyway V10이 기존 `T-YYYY/MM/DD-N`, `TR-YYYYMMDD-NNN` 값을 정규화.

```sql
-- 판매/구매 전표번호는 slip_type 별로 독립 unique 여야 한다.
select slip_no, count(distinct slip_type) as type_count
from slips
where is_deleted = false
group by slip_no
having count(distinct slip_type) > 1;

-- 기대: 0 rows 일 필요 없음.
-- 같은 slip_no 가 여러 slip_type 에 존재해도 정상이며, 화면/업무 구분은 slip_type + slip_no 조합이다.
```

```sql
-- 같은 업무 타입 안의 활성 전표번호 중복은 금지.
select slip_type, slip_no, count(*) as active_count
from slips
where is_deleted = false
group by slip_type, slip_no
having count(*) > 1;

-- 기대: 0 rows.
```

## PASS 기준

- UI/FE 타입/mock fixture가 상태별 검수 CTA를 일관되게 표현한다.
- Gateway 경유와 service 직접 호출 path가 모두 검수 controller에 도달한다.
- inventory-service 검수 권한은 `WAREHOUSE / MANAGER / MASTER`로 고정된다.
- 업무번호는 `YYYY/MM/DD-N`, 서비스/메뉴/업무 타입별 독립 순번이며 내부 UUID는 화면에 노출되지 않는다.
