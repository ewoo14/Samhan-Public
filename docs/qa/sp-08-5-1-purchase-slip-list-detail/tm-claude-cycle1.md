## Claude 5-agent 사이클 1 통합 리뷰 (head `0c607e6d`)

> tech-manager agent 통합 — BE / FE / Designer / QA (후공정) / DevOps.

### 결함 종합 표 (HIGH → P1 → Major → P2 → Minor → Nit)

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | QA | **HIGH (보안)** | `SlipQueryController.listForQuery` | `/slips/query` INBOUND 권한 가드 누락 — PurchaseQueryPage 실제 호출 endpoint, INVENTORY 우회 가능. IT testListInboundForbiddenForInventory 는 `/slips?type=INBOUND` 만 커버 |
| 2 | FE | **Blocker** | `InboundInspectionDialog.tsx:210` + `PurchaseQueryPage.tsx:143` | `completeMutation.onSuccess invalidateQueries({ queryKey: ['slips', 'list', 'INBOUND'] })` vs 실제 `['slips', 'query', 'INBOUND', ...]` — 검수 완료 후 목록 미갱신 |
| 3 | BE | **P1 (UUID)** | `SlipDetailResponse:35,41,43,58` | `id`/`partnerId`/`sourceWarehouseId`/`destinationWarehouseId`/`deliveryBatchId` UUID 직노출 — `feedback_uuid_no_user_visibility` 위반 |
| 4 | BE | P1 | `SlipController.guardInboundPurchaseRead:472-481` | SALES/ACCOUNTANT INBOUND 차단 정책 미문서 — dev-report §7 명시 또는 IT 추가 |
| 5 | BE | P1 | `SlipDetailResponse.inspectionStatusOf:121-128` | `inspectionStatus` String literal → `InspectionStatusBadge` enum 분리 |
| 6 | Designer | Major | PNG 01 상태 컬럼 | `SAVED/CONFIRMED/DRAFT` 영문 enum 직노출 → `slipStatusLabel` 한국어 매핑 (저장/확인/임시저장) |
| 7 | Designer | Major | PNG 02 검수 Badge | `READY/NOT_READY` 영문 직노출 → `INSPECTION_STATUS_LABEL` 한국어 매핑 (검수 가능/검수 대기) |
| 8 | BE | P2 | `SlipController.getOne` UUID path | `/{id}` UUID 직접 접근 — slipNo 기반 alias 또는 후속 |
| 9 | BE / QA | P2 / O1 | IT `SAVED → READY` 미커버 | `testGetDetailReadyWhenSaved` 추가 |
| 10 | BE | P2 | IT `seqNo` 정렬 미검증 | 동일 날짜 2건 fixture + 역순 검증 |
| 11 | Designer | Minor | PNG 01~04 서브타이틀 | raw API (`type=INBOUND`) + dev memo (`R2: lines + ...`, `InboundInspectionDialog 진입`, `R1/R2 표면`) 노출 → 제거 또는 사용자 문구 |
| 12 | FE | Non-blocker | `PurchaseQueryPage` DataGrid `useMemo` deps | `warehouses` 매 렌더 새 배열 — 성능 |
| 13 | FE | Non-blocker | `<input type="date">` raw | design-system Input 미사용 |
| 14 | FE | Non-blocker | `SlipQueryRow` 타입 | `inspectionStatus` 필드 누락 — 후속 상세 화면 영향 |
| 15 | Designer | Nit | `InboundInspectionDialog DiffBadge` | `--color-warning-100`/`--color-danger-100` 비표준 → `--state-warning-bg`/`--state-danger-bg` |
| 16 | BE | Nit | IT `doesNotContain("SP0851-기간밖")` | jsonPath 단언 권장 |
| 17 | DevOps | Low | `guardInboundPurchaseRead` null type | DEBUG log 권장 |
| 18 | DevOps | Info | IT inspectionStatus 단언 근거 | 주석 문서화 권장 |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (P1 3건) |
| FE | **Blocker FE-C1-1 사이클 1 1c fix 필수** |
| Designer | 사이클 2 필요 (Major 2 + Minor 4) |
| QA | **HIGH 보안 D1 사이클 1 1c fix 필수** |
| DevOps | APPROVE (CI 20/20 pass) |

### TM 결정 (사이클 1 1c Claude fix 책임)

- **종합**: HIGH 보안 (QA D1) + Blocker (FE-C1-1) + P1 UUID (BE-2) — 3건 최우선 fix. P1/Major 추가 + Minor/Nit 일괄 처리.
- **Claude fix 후보 (1c 단계)**:
  1. **QA D1 HIGH**: `SlipQueryController.listForQuery` INBOUND 가드 추가 (slipType=INBOUND 시 WAREHOUSE/MANAGER/MASTER 만, INVENTORY 403) + IT testListPurchaseQueryForbiddenForInventory
  2. **FE-C1-1 Blocker**: `InboundInspectionDialog.completeMutation.onSuccess` queryKey `['slips', 'query', 'INBOUND']` 통일 + Playwright T5 정합. 또는 `onSuccess` prop 경유 `slipsQuery.refetch()` 위임
  3. **BE P1-2 UUID**: `SlipDetailResponse` `id`/`partnerId`/`sourceWarehouseId`/`destinationWarehouseId`/`deliveryBatchId` 제거 또는 internal-only DTO 분리 (PublicSlipDetailResponse vs InternalSlipDetailResponse)
  4. **BE P1-1**: dev-report §7 SALES/ACCOUNTANT 차단 정책 명시 + IT testListInboundForbiddenForSales 추가
  5. **BE P1-3**: `InspectionReadyStatus` enum 분리 (`READY`/`NOT_READY`) + Jackson serialization
  6. **Designer Major D-01/02**: `slipStatusLabel` (저장/확인/임시저장) + `INSPECTION_STATUS_LABEL` (검수 가능/검수 대기) — PurchaseQueryPage + 상세 + PNG 재생성
  7. **Designer Minor D-03~06**: PNG 4장 서브타이틀 제거 또는 사용자 친화 문구 + 재생성
  8. **Designer Nit D-07**: DiffBadge `--state-warning-bg`/`--state-danger-bg` 통일
  9. **BE P2 + QA O1**: `testGetDetailReadyWhenSaved` 신규 + `testListInboundOrderBySeqNo` 정렬 검증
- **Codex 2a review 대기**: Claude fix 후 push → Codex 5-agent cross-check
- **2c Codex fix**: Codex 신규 + Claude valid 미처리

**tech-manager — 2026-05-17**
