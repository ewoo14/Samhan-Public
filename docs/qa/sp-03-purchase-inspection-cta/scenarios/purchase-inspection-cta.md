# SP-03 구매관리 입고 검수 CTA QA 시나리오

## 목적

Samhan Public 데스크톱의 정식 구매관리 화면(`/purchases`, alias `/purchases/query`)에서 입고전표를 찾은 사용자가 같은 행의 **[검수]** 버튼으로 `InboundInspectionDialog`에 즉시 진입할 수 있는지 검증한다.

## 현재 계약 기준

| 영역 | 계약 |
|---|---|
| 진입 화면 | `/purchases`, `/purchases/query` 모두 `PurchaseQueryPage` |
| 검수 CTA 권한 | `WAREHOUSE / MANAGER / MASTER` |
| CTA 노출 상태 | `SAVED / CONFIRMED` 입고전표 행 |
| CTA 미노출 상태 | `DRAFT / SENT / ACCEPTED / PROCESSING / INSPECTING / COMPLETED / REJECTED / CANCELED` |
| Dialog | 기존 `InboundInspectionDialog` 재사용 |
| 저장/완료 후 갱신 | Dialog 성공 후 구매관리 query 직접 refetch |
| 사용자 노출 식별자 | 구매번호(`YYYY/MM/DD-{순번}`), 거래처명, 사업자번호, 창고명. 내부 UUID 노출 금지 |

## 위험 가설

| ID | 위험 | 검증 목표 |
|---|---|---|
| SP03-R1 | `/purchases` 통합 후 legacy `SlipListPage`에만 검수 CTA가 남아 있다. | `PurchaseQueryPage` 자체에서 `InboundInspectionDialog`를 import/render 하는지 확인한다. |
| SP03-R2 | 버튼 클릭이 행 선택으로 전파된다. | CTA `onClick`에서 `stopPropagation()`을 호출하고 Dialog를 연다. |
| SP03-R3 | FE 타입에 `status`가 없어 상태별 CTA 조건이 깨진다. | `SlipQueryRow.status` 타입과 mock `/slips/query` status fixture를 확인한다. |
| SP03-R4 | 권한이 메뉴와 API 사이에서 갈린다. | `canInspectInbound()`를 `WAREHOUSE / MANAGER / MASTER`로 고정하고 AppLayout/PurchaseQueryPage가 같은 helper를 쓴다. |
| SP03-R5 | Gateway 경유 검수 API가 404가 된다. | inventory-service controller가 `/inventory/inbound-inspections/**`와 직접 `/api/v1/inventory/inbound-inspections/**`를 모두 수신한다. |
| SP03-R6 | 전표번호 표준이 `-IN1` 또는 zero padding으로 회귀한다. | mock/문서/캡처는 `YYYY/MM/DD-N` 형식만 사용한다. |
| SP03-R7 | 내부 UUID가 화면이나 캡처에 노출된다. | 버튼 test id는 구매번호 기반 public id를 쓰고, 캡처/문서에 UUID regex가 없어야 한다. |

## 시나리오 매트릭스

| ID | 역할 | 진입 | 절차 | 핵심 assertion | 산출 캡처 |
|---|---|---|---|---|---|
| SP03-01 | WAREHOUSE | `/purchases` | 구매관리 목록을 연다. | `SAVED / CONFIRMED` 행에만 **[검수]** 버튼, `COMPLETED` 행은 `—`. | `01-warehouse-purchase-inspect-cta.png` |
| SP03-02 | WAREHOUSE | `/purchases` | `2026/05/10-1` 행 **[검수]** 클릭. | `InboundInspectionDialog`가 열리고 전표번호/거래처/검수 저장/검수 완료가 보인다. | `02-warehouse-inspection-dialog.png` |
| SP03-03 | MANAGER | `/purchases` | 구매관리 목록을 연다. | **[검수]**와 **신규 입고전표**가 함께 보인다. | `03-manager-purchase-dual-cta.png` |
| SP03-04 | MASTER | `/purchases` | 구매관리 목록을 연다. | MASTER도 WAREHOUSE와 같은 검수 CTA를 본다. | `04-master-purchase-inspect-cta.png` |
| SP03-05 | INVENTORY | `/purchases` | 구매관리 목록을 연다. | 검수 컬럼/버튼과 `입고 검수` 메뉴가 보이지 않는다. | `05-inventory-no-inspect-cta.png` |
| SP03-06 | QA | 전체 | 문서/캡처/코드 contract를 확인한다. | UUID regex 0건, 업무번호는 서비스/메뉴/업무 타입별 독립 순번이며 재고이동은 날짜별 마지막 순번 이후로 채번. | `06-business-number-uuid-hidden-matrix.png` |

## Playwright spec

정적 contract spec 위치:

```text
clients/desktop/playwright/purchase-inspection-cta/purchase-inspection-cta.spec.ts
```

실행:

```powershell
cd clients\desktop
npx playwright test playwright/purchase-inspection-cta/purchase-inspection-cta.spec.ts --reporter=line
```

## Backend gate

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:inventory-service:test --tests "com.samhanair.logis.inventory.it.InboundInspectionControllerIT" --no-daemon --rerun-tasks
.\gradlew.bat :services:slip-service:test --tests "com.samhanair.logis.slip.it.SlipQueryRedesignSpecIT" --tests "com.samhanair.logis.slip.it.SlipQueryRedesignIT" --no-daemon --rerun-tasks
```

## PASS 기준

- 정식 구매관리 화면에서 검수 가능한 입고전표 행에 **[검수]** 버튼이 보인다.
- 버튼 클릭은 행 선택과 분리되어 `InboundInspectionDialog`를 연다.
- `WAREHOUSE / MANAGER / MASTER`만 CTA를 보고, `INVENTORY / SALES / ACCOUNTANT`는 보지 않는다.
- Gateway 경유 검수 API가 404로 빠지지 않는다.
- 전표번호는 `YYYY/MM/DD-N` 형식을 사용하고 서비스/메뉴/업무 타입별 중복을 허용한다.
- 화면/캡처에는 내부 UUID가 표시되지 않는다.
