## Codex 5-agent 사이클 1 2a 통합 리뷰 (head `0b6633a7`)

> Codex 5 agent (BE/FE/Designer/QA/DevOps) cross-check. Read-only 정적 검토.

### Claude fix 정합 검증 (사이클 1 1c)

- **D1 INBOUND 권한 가드**: `slipType=INBOUND` / `type=INBOUND` 명시 요청에 대해서는 적용 확인. `INVENTORY`, `SALES`, `ACCOUNTANT` 403 IT 추가됨.
- **FE-C1-1**: `InboundInspectionDialog` invalidate key `['slips', 'query', 'INBOUND']` 통일 → `PurchaseQueryPage` prefix invalidate 정합.
- **BE P1-2 UUID**: `SlipDetailResponse` top-level UUID 제거 + `InspectionReadyStatus` enum 전환 코드/IT 반영.
- **신규 IT**: `testGetDetailReadyWhenSaved`, `testListInboundOrderBySeqNo`, Playwright T5, PNG 4장 산출물 존재 확인.
- 단, 아래 신규 P1 3건 때문에 "0 P0/P1" 상태는 아님.

### Codex 자체 신규 발견 (사이클 1)

**P1 — INBOUND 조회 권한 우회 가능**

`/slips` 와 `/slips/query` 모두 요청 필터가 `INBOUND` 일 때만 가드. `slipType` / `type` 을 생략하면 `guardInboundPurchaseRead(null, role)` 이 즉시 return 하므로, `SALES` / `ACCOUNTANT` / `INVENTORY` 가 전체 목록 호출 시 INBOUND row 받을 수 있음.

- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/SlipController.java:113-117, :473`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/SlipQueryController.java:142-144, :152`

수정 방향: 비허용 role 이면 INBOUND 포함 "전체 조회" 차단 또는 role 별 INBOUND 제외 필터 강제. `/slips/query` + `/slips` 모두 누락형 요청 IT 필요.

**P1 — `SlipDetailResponse` UUID 제거가 기존 desktop detail 계약 깨뜨림**

BE 가 detail 응답에서 `id`, `partnerId`, `sourceWarehouseId`, `destinationWarehouseId`, `deliveryBatchId` 제거. 그러나 FE `SlipDetail extends SlipSummary` 가 여전히 해당 필드 필수 계약. `duplicateSlip()` 은 제거된 필드로 새 전표 body 생성, 성공 후 `created.id` 로 이동. print view 도 warehouse id 기반 lookup 의존.

- `clients/desktop/src/renderer/api/slip.ts:78, :350-355`
- `clients/desktop/src/renderer/routes/SlipDetailPage.tsx:361`
- 영향: 복사/인쇄/detail 내부 동작 런타임 `undefined` 깨짐 가능

수정 방향: 공개용 구매 상세 DTO 별도 분리 또는 internal API 계약상 필요한 UUID 응답 유지 + 화면 노출만 금지.

**P1 — 실제 구매 목록 endpoint `/slips/query` 정렬 누락**

`/slips` 는 `slipDate DESC, seqNo DESC` 정렬 있으나, FE 구매 목록이 쓰는 `/slips/query` 는 `PageRequest.of(page, size)` 만. 현재 `testListInboundOrderBySeqNo` 는 `/slips` 만 검증.

- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/SlipQueryController.java:143`
- `clients/desktop/src/renderer/routes/purchase-query/PurchaseQueryPage.tsx` 는 `/slips/query` 사용

### Codex P2

- `CONFIRMED` 라벨 `확인` — 기존 lifecycle 용어 `확정` 과 불일치
- dev-report 검증 표 테스트 수 IT case 수와 드리프트
- 일부 QA mock screenshot subtitle/문구 잔여 정리 필요

### 종합

P0 0건. **P1 3건 잔존** — 양쪽 0 P0/P1 미도달. 사이클 2 필요.

핵심: INBOUND 권한 우회 (보안 추가 노출), UUID DTO 제거 부작용 (FE 계약 깨짐 — CI 14+ IT FAIL 도 동일 원인), `/slips/query` 정렬 누락.

**Codex 5-agent TM — 2026-05-17**
