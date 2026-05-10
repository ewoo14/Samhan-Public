/**
 * 전표 코멘트 API 클라이언트 — PR-H1 FE-1.
 *
 * <p>BE endpoint:
 * <ul>
 *   <li>{@code GET  /api/v1/slips/{slipId}/comments?limit=N} — 최근 코멘트 목록</li>
 *   <li>{@code POST /api/v1/slips/{slipId}/comments}         — 신규 코멘트 등록</li>
 * </ul>
 *
 * <p>UUID 비공개 가드: 응답의 {@code id}/{@code authorId} 는 비-노출 식별자 — 사용자 표시는
 * {@code authorName} + {@code body} + {@code createdAt} 만 사용한다 (memory feedback_uuid_no_user_visibility).
 */
import { apiClient, type ApiEnvelope } from './client'

/** BE {@code SlipCommentResponse} 와 1:1. */
export interface SlipComment {
  /** UUID — data-testid 키 전용. 화면 텍스트 노출 금지. */
  id: string
  /** 작성자 UUID — 화면 노출 금지. */
  authorId: string
  /** 작성자 이름 — 화면 노출 (UUID 비공개 가드의 비즈니스 식별자). */
  authorName: string
  /** 코멘트 본문 (≤ 1000자, plain text). */
  body: string
  /** 작성 시각 ISO-8601. */
  createdAt: string
}

/** POST body — BE {@code SlipCommentCreateRequest} 와 1:1. */
export interface SlipCommentCreateRequest {
  body: string
}

/**
 * 최근 코멘트 목록 조회 — 기본 20건.
 *
 * @param slipId 전표 UUID (path 전용, 화면 노출 X)
 * @param limit  최대 반환 개수 (기본 20)
 */
export async function listSlipComments(
  slipId: string,
  limit = 20,
): Promise<SlipComment[]> {
  const res = await apiClient.get<ApiEnvelope<SlipComment[]>>(
    `/api/v1/slips/${encodeURIComponent(slipId)}/comments`,
    { params: { limit } },
  )
  return res.data.data
}

/**
 * 코멘트 등록 — 200 OK 시 신규 코멘트 응답.
 *
 * @param slipId 전표 UUID
 * @param body   코멘트 본문 (1~1000자)
 */
export async function addSlipComment(
  slipId: string,
  body: SlipCommentCreateRequest,
): Promise<SlipComment> {
  const res = await apiClient.post<ApiEnvelope<SlipComment>>(
    `/api/v1/slips/${encodeURIComponent(slipId)}/comments`,
    body,
  )
  return res.data.data
}
