### Codex FE 사이클 1 2a 리뷰 (head `0098c9e0`)

#### Claude 발견 평가

| 항목 | Codex 평가 |
|---|---|
| F-01/D-2 banner 통일 | valid + fix 정합 |
| F-03 isPending guard | valid + fix 정합 |
| D-1 422 alert→banner | valid + fix 정합 |
| D-3 inline→className | valid + fix 정합 |
| D-4 color 700→800 | valid + fix 정합 |

#### Codex 자체 신규 발견 (FE)

**P2**: `SlipDetailPage.tsx` 422 `purchaseDeleteInspectionAlert` state modal open/cancel/backdrop/ESC close 시 정리 OK. 그러나 **같은 modal 안 재시도 시 409/422 배너 상호 배타 정리 누락**:
- 422 후 재시도 → 409 응답 → `purchaseDeleteInspectionAlert` 남은 채 `purchaseDeleteConflict=true` → 두 배너 동시 노출
- 409 후 재시도 → 422 응답 → 동일 시나리오

**수정 권고**: 확인 onClick mutate 직전 양쪽 state reset 또는 onError 분기 상대방 정리.

#### 종합

**사이클 2 필요** (P2 FE 1건 — 배너 상호 배타 reset).
