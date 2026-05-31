## Claude 5-agent 사이클 2 통합 리뷰 (head `58968fb6`)

> tech-manager 통합. 사용자 6회차 정책: PR 내 모든 문제 본 PR 사이클 안에서 해결.

### 사이클 1 결함 해소 표

| Agent | 해소 |
|---|---|
| BE | 6/6 (P1 3건 + Codex 2a P1 3건) |
| FE | 1/1 Blocker (FE-C1-1 queryKey) — Non-blocker 3건 잔존 |
| Designer | 5/5 (Major 2 + Minor 4 + Nit 1 + CONFIRMED 확정) |
| QA | 4/4 (D1 HIGH + O1 + 2c IT + dev-report) |
| DevOps | CI 24/24 회복 (4 FAIL → SUCCESS) |

### 사이클 2 신규 발견 종합 표

| # | 출처 | 우선순위 | 위치 | 내용 |
|---|---|---|---|---|
| 1 | QA | **HIGH** | `spec.ts:27-31` T1 | `.not.toContain('UUID id,'/...partnerId,'/sourceWarehouseId/destinationWarehouseId/deliveryBatchId)` 5개 단언이 `SlipDetailResponse.java:36,42,...` 실제 UUID 필드와 모순. dev-report §7 internal UUID 유지 정책 역행. **CI 에서 T1 FAIL 가능** |
| 2 | FE | P1 (NB-1) | `PurchaseQueryPage.tsx:153` | DataGrid `useMemo` deps `warehouses` 매 렌더 새 배열 참조 — `useMemo` 안정화 |
| 3 | FE | P2 (NB-2) | `PurchaseQueryPage.tsx:310-325` | 날짜 필터 native `<input type="date">` raw — design-system Input 교체 |
| 4 | FE | P2 (NB-3) | `slip.ts:435-459 SlipQueryRow` | `inspectionStatus` 필드 누락 (BE 응답 정합) |
| 5 | Designer | Minor (D-08) | `PurchaseQueryPage.tsx:337,492,790,793` | `--color-primary-*` 비표준 토큰 (`--color-brand-*` 표준) — 선택 상태 시각 오류 위험 |
| 6 | Designer | Nit (D-09) | `SlipDetailPage.tsx:836,491,515` | `현재 단계({slip.status})` 영문 enum 사용자 노출 + alert() 2곳 동일 |
| 7 | BE | P2-1 | `/slips/query` IT | ACCOUNTANT 403 IT 대칭 미커버 |
| 8 | BE | P2-2 | `inspectionStatusOf` | CONFIRMED → READY IT 부재 |
| 9 | BE | P2-3 | `/slips/query` IT | ACCOUNTANT null type 제외 IT 대칭 |
| 10 | DevOps | P2 | `restrictInboundWhenTypeOmitted` | controller 양쪽 중복 — util 추출 |
| 11 | DevOps | P3 | `SlipQueryPurchaseIT` X-User-Id | `UUID.randomUUID()` 15회 → `TEST_USER_ID` 상수 |
| 12 | QA | P2 (N2) | `InboundInspectionDialog.tsx:363,384,400,424` | `data-testid={inbound-inspection-line-${line.lineId}}` UUID raw — `feedback_uuid_no_user_visibility` 가드 위반 |
| 13 | QA | P3 (N3) | QA PNG | CONFIRMED 상태 검수 CTA PNG 누락 |
| 14 | FE | Nit (C2-1) | `InboundInspectionDialog.tsx:377-388,393-404` | 수량 native `<input type="number">` raw |

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | APPROVE (P2 3건 IT 대칭 — 6회차 PR 내 fix) |
| FE | **Non-blocker 3 + Nit 1 fix 필요** |
| Designer | Minor + Nit fix |
| QA | **HIGH N1 Playwright T1 정책 모순 fix 필수** |
| DevOps | APPROVE (P2/P3) |

### TM 결정 (6회차 — 사용자 명시 PR 내 모든 해결 + 자동 머지)

- **종합**: 사이클 1 결함 28건 전원 해소 (CI 24/24 회복). 사이클 2 신규 14건 — 모두 본 PR 사이클 안에서 fix.
- **Claude fix 후보 (1c 단계)** — 모든 14건 일괄:
  1. **QA N1 HIGH**: Playwright T1 L27-31 `.not.toContain('UUID ...')` 5개 단언 제거 (정책 §7 UUID internal 유지 정합)
  2. **FE-NB-1 P1**: `warehouses = useMemo(() => warehousesQuery.data ?? [], [warehousesQuery.data])` 안정화
  3. **FE-NB-2 P2**: 날짜 필터 raw input → design-system `Input type="date"` (또는 호환 컴포넌트)
  4. **FE-NB-3 P2**: `SlipQueryRow` 인터페이스 `inspectionStatus?: 'READY' | 'NOT_READY' | null` 추가
  5. **Designer D-08 Minor**: `--color-primary-*` → `--color-brand-*` (선택 카운트/배경/페이지네이션)
  6. **Designer D-09 Nit**: `현재 단계({SLIP_STATUS_LABEL[slip.status] ?? slip.status})` + alert() 2곳 동일 매핑
  7. **BE P2-1**: `testListPurchaseQueryForbiddenForAccountant` 추가
  8. **BE P2-2**: `testGetDetailReadyWhenConfirmed` 추가 (CONFIRMED → READY)
  9. **BE P2-3**: `testListAccountantSeesNoInboundWithoutSlipTypeFilter` (query 대칭)
  10. **DevOps P2**: `SlipPurchaseAccessGuard` util 추출 (controller 양쪽 사용)
  11. **DevOps P3**: `SlipQueryPurchaseIT` `TEST_USER_ID` 상수 (UUID.randomUUID 15회 제거)
  12. **QA N2 P2**: `InboundInspectionDialog` `data-testid` `toPublicTestId(line.modelCode || index)` 패턴 적용
  13. **QA N3 P3**: PNG 05 신규 — CONFIRMED 상태 검수 CTA 활성 화면 추가
  14. **FE C2-1 Nit**: 수량 input → design-system `Input type="number" size="sm"`
- **2a Codex review 대기**: Claude fix 후 push → Codex 5-agent cross-check
- **2c Codex fix**: Codex 신규 + Claude valid 미처리 보완
- **머지 조건**: 0 P0/P1 + CI green → PM 자동 머지 (사용자 6회차 정책)

**tech-manager — 2026-05-17**
