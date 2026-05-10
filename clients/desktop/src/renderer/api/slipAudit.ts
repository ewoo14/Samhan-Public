/**
 * 전표 audit log + revert API 클라이언트 — PR-H2 FE-1.
 *
 * <p>BE endpoint:
 * <ul>
 *   <li>{@code GET  /api/v1/slips/{slipId}/audit-logs}            — 변경 이력 목록 (revisionNo 내림차순)</li>
 *   <li>{@code POST /api/v1/slips/{slipId}/revert/{revisionNo}}   — 특정 revision 으로 복원</li>
 * </ul>
 *
 * <p>UUID 비공개 가드: 응답의 {@code actorId} 는 색상 hash 입력 전용. 화면 텍스트
 * 노출 금지. 사용자 노출은 {@code actorName} (풀네임) 만 사용한다.
 */
import { apiClient, type ApiEnvelope } from './client'

/**
 * BE {@code SlipAuditLogResponse} 와 1:1.
 *
 * 한 audit log 행 = 한 필드 변경 한 건. 동일 revisionNo 내에 여러 필드가 변경되면
 * 여러 행으로 응답되며, 호출자가 field 별로 group 하여 AuditOverlay 에 전달한다.
 */
export interface SlipAuditLogEntry {
  /** revision 번호 (1, 2, 3, ... — 큰 수록 최근). */
  revisionNo: number
  /** 변경된 필드명 — "memo", "shippingAddress", "lines[2].quantity" 등. */
  field: string
  /** 변경 이전 값 (null/undefined = 신규 추가). */
  beforeValue: string | null
  /** 변경 이후 값 — 호출자가 currentValue 와 비교 가능. */
  afterValue: string | null
  /** 변경자 UUID — 색상 hash 입력 전용 (화면 텍스트 노출 금지). */
  actorId: string
  /** 변경자 풀네임 — 화면 표시. */
  actorName: string
  /** 변경 시각 ISO-8601. */
  changedAt: string
}

/** revert 응답 — 갱신된 SlipDetail revisionNo + 메시지 (BE 가 본체는 별도 GET 으로 fetch 권장). */
export interface SlipRevertResponse {
  /** 신규 revision 번호 (revert 결과로 +1 된 값). */
  newRevisionNo: number
  /** 사용자 안내 메시지. */
  message: string
}

/**
 * 전표 변경 이력 목록 조회 (revisionNo 내림차순).
 *
 * @param slipId 전표 UUID (path 전용 — 화면 노출 X)
 */
export async function listAuditLogs(slipId: string): Promise<SlipAuditLogEntry[]> {
  const res = await apiClient.get<ApiEnvelope<SlipAuditLogEntry[]>>(
    `/api/v1/slips/${encodeURIComponent(slipId)}/audit-logs`,
  )
  return res.data.data
}

/**
 * 특정 revision 으로 복원 — 200 응답 시 신규 revision 발급.
 *
 * @param slipId      전표 UUID
 * @param revisionNo  복원 대상 revision 번호 (목록의 revisionNo)
 */
export async function revertToRevision(
  slipId: string,
  revisionNo: number,
): Promise<SlipRevertResponse> {
  const res = await apiClient.post<ApiEnvelope<SlipRevertResponse>>(
    `/api/v1/slips/${encodeURIComponent(slipId)}/revert/${revisionNo}`,
  )
  return res.data.data
}
