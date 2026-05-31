## frontend-engineer 사이클 1 리뷰 (head `1248cdc1`)

### 결함 표

| # | 중요도 | 위치 | 내용 |
|---|---|---|---|
| F-1 | Major | `SlipDetailPage.tsx:1785` | 수정 modal 라벨 `지급예정일` 이 상세 뷰(L1049) `입금예정일` 과 불일치. 동일 `paymentDueDate` 두 곳 표기 통일 필요. |
| F-2 | Major | `SlipDetailPage.tsx:1656` | 수정 modal `onClose` 가 `() => setPurchaseEditOpen(false)` 만 — `isPending` 중 Esc/백드롭으로 닫혀 낙관적 업데이트 유실 가능. SP-08-4-2 `invalidateOpen` 패턴처럼 `isPending` 가드 추가. |
| F-3 | Major | `SlipDetailPage.tsx:1677` | `updatedAt: slip.updatedAt` PUT body — `slip` 은 `detailQuery.data` 스냅샷. `handlePurchaseConflictReload` 후 form state 갱신되어도 `slip.updatedAt` 은 reload 전 값. 별도 state `purchaseUpdatedAt` 동기화 필요. |
| F-4 | Minor | `SlipDetailPage.tsx:1702-1717` | 409 conflict banner "최신 내용 불러오기" 버튼 노출 조건이 `purchaseConflictMessage.includes('최신 내용')` 문자열 매칭 의존. 메시지 변경 시 버튼 누락. boolean state `purchaseIsConflict` 분리 안전. |
| F-5 | Minor | `SlipDetailPage.tsx:1725` | 수정 modal 라인 테이블에 라인 추가/삭제 버튼 부재. `SlipUpdateRequest.lines` 전체 교체이므로 modal 안에서 add/remove UX 필요. SP-08-4-2 주문서 수정 modal 도 `×` 행 제공. |
| F-6 | Minor | `SlipDetailPage.tsx:527-529` | `canDirectEditPurchase` 에 status 조건 없음 — CONFIRMED/INSPECTING 등 모든 단계에서 "수정" 버튼 노출. BE service 에서 거부하지만 UX 관점 종료 단계 숨김/비활성 권고. |

### 긍정 사항

- `syncPurchaseFormFromData` + `useCallback` 의존성 SP-08-4-2 패턴 정확 재현. `purchaseEditOpen` 가드로 form 덮어쓰기 방지
- `purchaseReloadSuccessTimerRef` 클린업 `useEffect` 별도 — SP-08-4-2 대비 개선
- `updatePurchaseSlip` PUT `encodeURIComponent(id)` + `ApiEnvelope<SlipDetail>` 언래핑
- UUID 비공개: `구매번호` 셀 `slip.slipNo` 만 표시
- `purchaseUpdateMutation.onSuccess` `setQueryData` 즉시 패치 + 3 cache invalidate (`slipAuditLogs`/`slips`/`slips query INBOUND`)

### 종합

F-1 (라벨 불일치) + F-3 (updatedAt 스냅샷) 머지 전 수정. F-2 (modal 닫힘 가드) 사용자 실수 데이터 유실 — 사이클 1 내 수정. F-4/5/6 Minor (F-5 라인 add/remove 완결성 권고).

**frontend-engineer agent — 2026-05-18**
