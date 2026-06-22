import { describe, expect, it, vi } from 'vitest'
import {
  addApproverOption,
  loadDefaultApproverOptions,
  mapDefaultApproversToApproverOptions,
  removeApproverAt,
  shouldApplyDefaultApproverPrefill,
} from './GroupwareApprovalCreatePage'
import type { ApprovalLineDefaultApprover } from '../api/approvalLineConfigApi'
import type { ApproverOption } from '../api/groupwareApprovalApprover'

describe('GroupwareApprovalCreatePage default approver prefill', () => {
  it('기본 결재자를 sequence 순서의 ApproverOption 으로 매핑한다', () => {
    const defaults: ApprovalLineDefaultApprover[] = [
      { sequence: 2, label: '승인자', userId: 'user-008', displayName: '김관리' },
      { sequence: 1, label: '검토자', userId: 'user-002', displayName: '김회계' },
    ]

    expect(mapDefaultApproversToApproverOptions(defaults)).toEqual([
      { userId: 'user-002', name: '김회계', department: null },
      { userId: 'user-008', name: '김관리', department: null },
    ])
  })

  it('템플릿 code 로 GROUPWARE 문서종류를 조회하고 프리필한다', async () => {
    const fetcher = vi.fn<Parameters<typeof loadDefaultApproverOptions>[1]>()
      .mockResolvedValueOnce([
        { sequence: 1, label: '검토자', userId: 'user-002', displayName: '김회계' },
      ])

    await expect(loadDefaultApproverOptions('EXPENSE_REPORT', fetcher)).resolves.toEqual([
      { userId: 'user-002', name: '김회계', department: null },
    ])

    expect(fetcher).toHaveBeenCalledWith('GROUPWARE_EXPENSE_REPORT')
  })

  it('템플릿 미선택 또는 조회 실패 시 빈 결재선으로 교체한다', async () => {
    const fetcher = vi.fn<Parameters<typeof loadDefaultApproverOptions>[1]>()
      .mockRejectedValueOnce(new Error('auth unavailable'))

    await expect(loadDefaultApproverOptions('', fetcher)).resolves.toEqual([])
    expect(fetcher).not.toHaveBeenCalled()

    await expect(loadDefaultApproverOptions('LEAVE_REQUEST', fetcher)).resolves.toEqual([])
    expect(fetcher).toHaveBeenCalledWith('GROUPWARE_LEAVE_REQUEST')
  })

  it('프리필 후 생성자 add/remove override 는 기존 순서를 보존한다', () => {
    const prefilled: ApproverOption[] = [
      { userId: 'user-002', name: '김회계', department: null },
      { userId: 'user-008', name: '김관리', department: null },
    ]
    const extra = { userId: 'user-005', name: '박창고', department: '물류팀' }

    const added = addApproverOption(prefilled, extra)
    expect(added).toEqual([...prefilled, extra])
    expect(addApproverOption(added, extra)).toBe(added)
    expect(removeApproverAt(added, 1)).toEqual([
      { userId: 'user-002', name: '김회계', department: null },
      extra,
    ])
  })

  it('프리필 응답이 늦게 도착하면 사용자 override 를 덮어쓰지 않는다', () => {
    expect(shouldApplyDefaultApproverPrefill(3, 3, false)).toBe(true)
    expect(shouldApplyDefaultApproverPrefill(3, 4, false)).toBe(false)
    expect(shouldApplyDefaultApproverPrefill(3, 3, true)).toBe(false)
  })
})
