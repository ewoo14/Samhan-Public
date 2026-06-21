import { apiClient, type ApiEnvelope } from './client'

export type StepType = 'CREATOR' | 'GROUP' | 'USER'

export interface ApprovalLineRole {
  id: string
  sequence: number
  label: string
  stepType: StepType
  approverGroupId: string | null
  approverGroupName: string | null
  required: boolean
}

export interface ApprovalLineGroupOption {
  id: string
  name: string
}

/** 결재라인 설정 대상 전표 종류(A2-1=출고만 seed). */
export const DOC_TYPES: { value: string; label: string }[] = [
  { value: 'SLIP_OUTBOUND', label: '출고전표' },
]

export async function fetchApprovalLineRoles(documentType: string): Promise<ApprovalLineRole[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineRole[]>>(
    `/auth/admin/approval-line-configs?documentType=${encodeURIComponent(documentType)}`,
  )
  return res.data.data ?? []
}

export async function fetchApprovalLineGroups(): Promise<ApprovalLineGroupOption[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineGroupOption[]>>(
    '/auth/admin/approval-line-configs/groups',
  )
  return res.data.data ?? []
}

export async function updateApprovalLineRole(
  id: string,
  payload: { approverGroupId: string | null; required: boolean },
): Promise<ApprovalLineRole> {
  const res = await apiClient.put<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(id)}`,
    payload,
  )
  return res.data.data
}

/**
 * 결재라인 역할 라벨 인라인 편집 — PUT /auth/admin/approval-line-configs/{id}/label.
 * CREATOR 역할은 BE 에서 거부(400). blank 입력은 FE 에서 사전 차단.
 */
export async function renameApprovalLineRole(
  id: string,
  label: string,
): Promise<ApprovalLineRole> {
  const res = await apiClient.put<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(id)}/label`,
    { label },
  )
  return res.data.data
}

/**
 * 결재라인 역할 순서 변경 — PUT /auth/admin/approval-line-configs/reorder?documentType=.
 * orderedIds[0] 는 CREATOR 강제(BE 검증). 비-CREATOR 행만 재배치 대상.
 */
export async function reorderApprovalLineRoles(
  documentType: string,
  orderedIds: string[],
): Promise<ApprovalLineRole[]> {
  const res = await apiClient.put<ApiEnvelope<ApprovalLineRole[]>>(
    `/auth/admin/approval-line-configs/reorder?documentType=${encodeURIComponent(documentType)}`,
    { orderedIds },
  )
  return res.data.data ?? []
}
