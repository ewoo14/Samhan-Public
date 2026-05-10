/**
 * 창고 도메인 SSE realtime client — PR-H4c FE-B.
 *
 * <p>BE endpoint:
 * <ul>
 *   <li>{@code GET /api/v1/slips/{id}/realtime}              — slip-service (PR-H4a)</li>
 *   <li>{@code GET /inventory/audits/{id}/realtime}          — inventory-service (PR-H4b BE-B)</li>
 * </ul>
 *
 * <p>이벤트:
 * <ul>
 *   <li>{@code inventory:edit} — InventoryAudit/StockBalance 본문 수정</li>
 *   <li>{@code inventory:edit-request:created/decided} — 수정 요청 라이프사이클</li>
 * </ul>
 *
 * <p>Slip 도메인은 기존 {@link ../realtime/SlipRealtimeClient.ts} 가 PR-H1 부터 사용 중 —
 * 본 module 은 inventory-service audit cluster 만 정의한다.
 */
import { createRealtimeClient } from './createRealtimeClient'

export const InventoryAuditRealtimeClient = createRealtimeClient({
  name: 'InventoryAuditRealtimeClient',
  endpointPath: (id) => `/inventory/audits/${encodeURIComponent(id)}/realtime`,
})
