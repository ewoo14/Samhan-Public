import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import {
  fetchApprovalLineGroups,
  fetchApprovalLineRoles,
  updateApprovalLineRole,
} from './approvalLineConfigApi'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
  },
}))

describe('approvalLineConfigApi contract', () => {
  beforeEach(() => {
    vi.mocked(apiClient.get).mockReset()
    vi.mocked(apiClient.put).mockReset()
  })

  it('GET /approval-line-configs 에 documentType query 를 전송한다', async () => {
    const rows = [
      {
        id: 'r1',
        sequence: 1,
        label: '출고인',
        stepType: 'GROUP',
        approverGroupId: null,
        approverGroupName: null,
        required: true,
      },
    ]
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: rows } })

    await expect(fetchApprovalLineRoles('SLIP_OUTBOUND')).resolves.toBe(rows)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs?documentType=SLIP_OUTBOUND',
    )
  })

  it('PUT /approval-line-configs/{id} 에 그룹/필수 payload 를 전송한다', async () => {
    const row = {
      id: 'role/1',
      sequence: 1,
      label: '출고인',
      stepType: 'GROUP',
      approverGroupId: 'g1',
      approverGroupName: '창고원',
      required: false,
    }
    const payload = { approverGroupId: 'g1', required: false }
    vi.mocked(apiClient.put).mockResolvedValueOnce({ data: { data: row } })

    await expect(updateApprovalLineRole('role/1', payload)).resolves.toBe(row)

    expect(apiClient.put).toHaveBeenCalledWith(
      '/auth/admin/approval-line-configs/role%2F1',
      payload,
    )
  })

  it('GET /approval-line-configs/groups 로 picker 권한그룹 목록을 조회한다', async () => {
    const groups = [{ id: 'g1', name: '창고원' }]
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { data: groups } })

    await expect(fetchApprovalLineGroups()).resolves.toBe(groups)

    expect(apiClient.get).toHaveBeenCalledWith('/auth/admin/approval-line-configs/groups')
  })
})
