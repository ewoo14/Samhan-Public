# SP-03 구매관리 입고 검수 CTA 복구 — dev-report

작성: 2026-05-16 | 브랜치: `codex/sp-03-purchase-inspection-cta`

---

## 1. 배경

Samhan Public 매뉴얼은 입고 검수 진입 경로를 두 가지로 안내한다. 하나는 `창고 운영 > 입고 검수` 전용 목록이고, 다른 하나는 `구매관리`에서 `SAVED / CONFIRMED` 입고전표 행의 **[검수]** 버튼을 누르는 흐름이다.

그러나 `/purchases`가 `PurchaseQueryPage`로 통합된 뒤 legacy `/purchases/slips`의 `SlipListPage(INBOUND)`에 있던 검수 CTA와 `InboundInspectionDialog` 연결이 새 구매관리 화면으로 이식되지 않았다. 결과적으로 사용자는 구매번호를 확인한 같은 문맥에서 검수 수량/불량 사유 입력으로 넘어갈 수 없었다.

---

## 2. 결정

| 항목 | 결정 |
|---|---|
| 검수 CTA 위치 | `PurchaseQueryPage` 기본 표와 Excel형 DataGrid 양쪽에 `검수` 액션을 제공한다. |
| 상태 조건 | `SAVED / CONFIRMED` 입고전표 행만 버튼을 노출한다. 나머지 상태는 `—`로 표시한다. |
| 권한 | `WAREHOUSE / MANAGER / MASTER`만 검수 컬럼과 버튼을 본다. `INVENTORY`는 현 inventory-service 권한 계약에 맞춰 제외한다. |
| Dialog | 기존 `InboundInspectionDialog`를 재사용하고, 저장/완료 성공 후 구매관리 query를 직접 refetch한다. |
| API path | Gateway `StripPrefix=2` 후 도착하는 `/inventory/inbound-inspections/**`와 기존 직접 호출 `/api/v1/inventory/inbound-inspections/**`를 모두 수신한다. |
| 공개 식별자 | 화면/test id/캡처에는 구매번호(`YYYY/MM/DD-N`)만 표시한다. 내부 `row.id`/UUID는 Dialog path param으로만 사용한다. |
| 업무번호 범위 | 판매전표 `YYYY/MM/DD-1`과 구매전표 `YYYY/MM/DD-1`은 서로 다른 서비스/메뉴/업무 타입이므로 중복 가능하다. |
| 관리형 메뉴명 | 판매/구매/재고이동/창고/견적서/주문서처럼 생성·상세·수정/처리 흐름을 포함하는 화면은 `…관리` 라벨을 쓴다. `주문서 승인`, `거래처 DC 설정`은 업무명이 이미 구체적이므로 유지한다. |
| 재고이동 이동번호 | `T-`/`TR-` prefix 없이 `YYYY/MM/DD-N`을 사용한다. 신규 채번은 해당 날짜의 마지막 순번 + 1을 사용하고, 기존 `T-YYYY/MM/DD-N`, `TR-YYYYMMDD-NNN` 데이터는 Flyway V10으로 정규화한다. |

---

## 3. 변경 요약

### Desktop

- `PurchaseQueryPage.tsx`
  - `InboundInspectionDialog` 연결 추가.
  - `canInspectInbound(role)` 권한으로 검수 컬럼/버튼 노출.
  - `SAVED / CONFIRMED` 행만 **[검수]** CTA 표시.
  - `data-testid`는 `slipNo`를 sanitize한 public id 사용.
  - Dialog 성공 시 `slipsQuery.refetch()`로 통합 구매관리 목록 갱신.
- `session.ts`
  - `canInspectInbound()` helper 추가.
- `AppLayout.tsx`
  - `입고 검수` 메뉴도 같은 helper를 사용해 권한 drift 방지.
  - 최상단/영업 메뉴 라벨을 `판매관리`, `구매관리`, `재고이동 관리`, `창고 관리`, `견적서 관리`, `주문서 관리`로 정리.
- `slip.ts`
  - `SlipQueryRow.status` 추가. Backend `SlipResponse`는 이미 status를 내려주고 있었다.
- `mock.ts`
  - `/slips/query` 판매/구매 mock row에 `status` 추가.
  - 구매번호 mock을 `2026/05/10-1` 형식으로 정리.
  - 입고 검수 상세 mock과 구매관리 mock의 `slipNo` 연결을 맞춤.
  - 재고이동 mock 이동번호의 `T-` prefix를 제거.

### Backend

- `InboundInspectionController`
  - `@RequestMapping({"/inventory/inbound-inspections", "/api/v1/inventory/inbound-inspections"})`로 gateway 경유와 직접 호출을 모두 수신.
  - 권한은 기존 `WAREHOUSE / MANAGER / MASTER` 유지.
- `InboundInspectionService`
  - Javadoc의 검수 가능 상태 설명을 실제 코드(`SAVED / CONFIRMED / COMPLETED / PROCESSING / INSPECTING`)와 정렬하되, 구매관리 CTA는 발견성 기준으로 `SAVED / CONFIRMED`만 노출한다고 명시.
- `InboundInspectionControllerIT`
  - `INVENTORY` 403 계약 테스트 추가.
  - Gateway stripped path `/inventory/inbound-inspections/{slipId}` 200 테스트 추가.
  - 샘플 전표번호를 `YYYY/MM/DD-N` 형식으로 정리.
- `StockTransferService`
  - 신규 이동번호 채번을 `YYYY/MM/DD-N`으로 변경.
  - 같은 날짜 prefix 의 row count 가 아니라 numeric suffix 최댓값 + 1을 사용해 `2026/05/02-7` 이후 `2026/05/02-8`로 이어지게 했다.
- `V10__normalize_stock_transfer_numbers.sql`
  - 기존 `T-YYYY/MM/DD-N`, `TR-YYYYMMDD-NNN` 값을 prefix 없는 표준 형식으로 변환.

### 문서/QA

- `docs/manual/02-창고/01-입고-처리.md`
- `docs/manual/02-창고/06-구매조회.md`
- `docs/qa/sp-03-purchase-inspection-cta/**`
- `docs/team-reviews/sp-03/team-1-tm-integration-review.md`
- `migration/decisions/DECISIONS.md`

---

## 4. 검증 대상

```powershell
cd clients\web\design-system
npm run build
```

```powershell
cd clients\desktop
npm run typecheck
npm run lint
npm run build
npx playwright test playwright/purchase-inspection-cta/purchase-inspection-cta.spec.ts --reporter=line
```

```powershell
.\scripts\generate-sp-03-purchase-inspection-cta-screenshots.ps1
Get-ChildItem docs\qa\sp-03-purchase-inspection-cta\screenshots -Filter *.png
```

```powershell
$env:DOCKER_HOST='tcp://localhost:2375'
.\gradlew.bat :services:inventory-service:test --tests "com.samhanair.logis.inventory.service.StockTransferServiceTest" --tests "com.samhanair.logis.inventory.it.InboundInspectionControllerIT" --tests "com.samhanair.logis.inventory.it.StockTransferControllerIT" --no-daemon --rerun-tasks
.\gradlew.bat :services:slip-service:test --tests "com.samhanair.logis.slip.it.SlipQueryRedesignSpecIT" --tests "com.samhanair.logis.slip.it.SlipQueryRedesignIT" --no-daemon --rerun-tasks
```

최종 PR gate는 `failures=0 / errors=0 / skipped=0`만 PASS로 인정한다.
