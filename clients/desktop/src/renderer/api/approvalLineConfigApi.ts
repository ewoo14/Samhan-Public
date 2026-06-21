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
