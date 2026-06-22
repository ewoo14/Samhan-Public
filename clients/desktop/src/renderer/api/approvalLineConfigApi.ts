import { apiClient, type ApiEnvelope } from './client'

export type StepType = 'CREATOR' | 'GROUP' | 'USER'

export interface ApprovalLineRole {
  id: string
  sequence: number
  label: string
  stepType: StepType
  approvers: ApprovalLineApprover[]
  required: boolean
  enforced: boolean
  seedManaged: boolean
}

export interface ApprovalLineStructure {
  sequence: number
  label: string
  stepType: StepType
  actionKey: string | null
}

export interface ApprovalLineGroupOption {
  id: string
  name: string
}

export interface ApprovalLineApprover {
  id: string
  type: 'GROUP' | 'USER'
  refId: string
  displayName: string
}

export interface ApprovalLineUserOption {
  id: string
  displayName: string
}

/** 결재라인 설정 대상 전표 종류. */
export const DOC_TYPES: { value: string; label: string }[] = [
  { value: 'SLIP_OUTBOUND', label: '판매전표' },
  { value: 'SLIP_INBOUND', label: '입고전표' },
  { value: 'PARTNER_ORDER', label: '주문' },
]

export async function fetchApprovalLineRoles(documentType: string): Promise<ApprovalLineRole[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineRole[]>>(
    `/auth/admin/approval-line-configs?documentType=${encodeURIComponent(documentType)}`,
  )
  return res.data.data ?? []
}

export async function fetchApprovalLineStructure(documentType: string): Promise<ApprovalLineStructure[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineStructure[]>>(
    `/auth/approval-line-configs/${encodeURIComponent(documentType)}/structure`,
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
  payload: { required: boolean },
): Promise<ApprovalLineRole> {
  const res = await apiClient.put<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(id)}`,
    payload,
  )
  return res.data.data
}

export async function addApprovalLineStep(
  documentType: string,
  label: string,
): Promise<ApprovalLineRole> {
  const res = await apiClient.post<ApiEnvelope<ApprovalLineRole>>(
    '/auth/admin/approval-line-configs',
    { documentType, label },
  )
  return res.data.data
}

export async function deleteApprovalLineStep(id: string): Promise<void> {
  await apiClient.delete<ApiEnvelope<null>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(id)}`,
  )
}

export async function searchApprovalLineUsers(q: string, limit = 20): Promise<ApprovalLineUserOption[]> {
  const res = await apiClient.get<ApiEnvelope<ApprovalLineUserOption[]>>(
    `/auth/admin/approval-line-configs/users?q=${encodeURIComponent(q)}&limit=${encodeURIComponent(String(limit))}`,
  )
  return res.data.data ?? []
}

export async function addApprovalLineApprover(
  roleId: string,
  type: 'GROUP' | 'USER',
  refId: string,
): Promise<ApprovalLineRole> {
  const res = await apiClient.post<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(roleId)}/approvers`,
    { type, refId },
  )
  return res.data.data
}

export async function removeApprovalLineApprover(
  roleId: string,
  approverId: string,
): Promise<ApprovalLineRole> {
  const res = await apiClient.delete<ApiEnvelope<ApprovalLineRole>>(
    `/auth/admin/approval-line-configs/${encodeURIComponent(roleId)}/approvers/${encodeURIComponent(approverId)}`,
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
