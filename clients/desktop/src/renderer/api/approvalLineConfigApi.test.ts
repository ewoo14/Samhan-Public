import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import {
  fetchApprovalLineGroups,
  fetchApprovalLineRoles,
  addApprovalLineApprover,
  DOC_TYPES,
  removeApprovalLineApprover,
  renameApprovalLineRole,
  reorderApprovalLineRoles,
  searchApprovalLineUsers,
  updateApprovalLineRole,
} from './approvalLineConfigApi'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('approvalLineConfigApi contract', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.post).mockReset()
    vi.mocked(apiClient.put).mockReset()
    vi.mocked(apiClient.delete).mockReset()
  })

  it('DOC_TYPES 에 입고전표와 주문 옵션을 포함한다', () => {
    expect(DOC_TYPES).toContainEqual({ value: 'SLIP_INBOUND', label: '입고전표' })
    expect(DOC_TYPES).toContainEqual({ value: 'PARTNER_ORDER', label: '주문' })
  })

  it('GET /approval-line-configs 에 documentType query 를 전송한다', async () => {
    const rows = [
      {
        id: 'r1',
        sequence: 1,
        label: '출고인',
        stepType: 'GROUP',
        approvers: [],
        required: true,
      },
    ]
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: rows } })

    await expect(fetchApprovalLineRoles('SLIP_OUTBOUND')).resolves.toBe(rows)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs?documentType=SLIP_OUTBOUND',
    )
  })

  it('PUT /approval-line-configs/{id} 에 필수 payload 만 전송한다', async () => {
    const row = {
      id: 'role/1',
      sequence: 1,
      label: '출고인',
      stepType: 'GROUP',
      approvers: [],
      required: false,
    }
    const payload = { required: false }
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: row } })

    await expect(updateApprovalLineRole('role/1', payload)).resolves.toBe(row)

    expect(apiClient.put).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/role%2F1',
      payload,
    )
  })

  it('GET /approval-line-configs/users 에 q/limit query 를 전송한다', async () => {
    const users = [{ id: 'u1', displayName: '홍길동 (물류팀)' }]
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: users } })

    await expect(searchApprovalLineUsers('홍 길', 10)).resolves.toBe(users)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/users?q=%ED%99%8D%20%EA%B8%B8&limit=10',
    )
  })

  it('POST /approval-line-configs/{roleId}/approvers 에 type/refId body 를 전송한다', async () => {
    const row = {
      id: 'role/1',
      sequence: 1,
      label: '출고인',
      stepType: 'GROUP',
      approvers: [{ id: 'a1', type: 'GROUP', refId: 'g1', displayName: '창고원' }],
      required: true,
    }
    vi.mocked(apiClient.post).mockResolvedValueOnce({ data: { data: row } })

    await expect(addApprovalLineApprover('role/1', 'GROUP', 'g1')).resolves.toBe(row)

    expect(apiClient.post).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/role%2F1/approvers',
      { type: 'GROUP', refId: 'g1' },
    )
  })

  it('DELETE /approval-line-configs/{roleId}/approvers/{approverId} 로 결재자를 제거한다', async () => {
    const row = {
      id: 'role/1',
      sequence: 1,
      label: '출고인',
      stepType: 'GROUP',
      approvers: [],
      required: true,
    }
    vi.mocked(apiClient.delete).mockResolvedValueOnce({ data: { data: row } })

    await expect(removeApprovalLineApprover('role/1', 'approver/1')).resolves.toBe(row)

    expect(apiClient.delete).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/role%2F1/approvers/approver%2F1',
    )
  })

  it('GET /approval-line-configs/groups 로 picker 권한그룹 목록을 조회한다', async () => {
    const groups = [{ id: 'g1', name: '창고원' }]
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: groups } })

    await expect(fetchApprovalLineGroups()).resolves.toBe(groups)

    expect(apiClient.get).toHaveBeenCalledWith('/auth/admin/approval-line-configs/groups')
  })

  it('PUT /approval-line-configs/{id}/label 에 라벨 payload 를 전송한다', async () => {
    const row = {
      id: 'r-out',
      sequence: 1,
      label: '출고담당',
      stepType: 'GROUP',
      approvers: [],
      required: true,
    }
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: row } })

    await expect(renameApprovalLineRole('r-out', '출고담당')).resolves.toBe(row)

    expect(apiClient.put).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/r-out/label',
      { label: '출고담당' },
    )
  })

  it('PUT /approval-line-configs/reorder?documentType= 에 orderedIds body 를 전송한다', async () => {
    const rows = [
      { id: 'r0', sequence: 0, label: '작성자', stepType: 'CREATOR', approvers: [], required: true },
      { id: 'r2', sequence: 1, label: '검수인', stepType: 'GROUP',   approvers: [], required: true },
      { id: 'r1', sequence: 2, label: '출고인', stepType: 'GROUP',   approvers: [], required: true },
    ]
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: rows } })

    const result = await reorderApprovalLineRoles('SLIP_OUTBOUND', ['r0', 'r2', 'r1'])
    expect(result).toBe(rows)

    expect(apiClient.put).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/reorder?documentType=SLIP_OUTBOUND',
      { orderedIds: ['r0', 'r2', 'r1'] },
    )
  })
})
