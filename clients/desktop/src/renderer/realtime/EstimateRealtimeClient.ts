/**
 * 견적서 SSE realtime client — PR-H4c FE-A.
 *
 * <p>BE endpoint: {@code GET /api/v1/estimates/{estimateId}/realtime}
 *
 * <p>견적서는 slip-service 의 estimate sub-domain 으로 노출 — slip-service 의
 * SlipRealtimeBroker 와는 별개의 path 사용. 본 client 는 estimate 전용.
 *
 * <p>이벤트:
 * <ul>
 *   <li>{@code estimate:edit} — 견적 본문 수정</li>
 *   <li>{@code estimate:edit-request:created/decided} — 수정 요청 라이프사이클</li>
 * </ul>
 */
import { createRealtimeClient } from './createRealtimeClient'

export const EstimateRealtimeClient = createRealtimeClient({
  name: 'EstimateRealtimeClient',
  endpointPath: (id) => `/api/v1/estimates/${encodeURIComponent(id)}/realtime`,
})
