import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient } from '@tanstack/react-query'
import { describe, expect, test, vi } from 'vitest'
import {
  ApprovalRoleRow,
  approvalLineRolesQueryKey,
  notifyApprovalRoleGroupChange,
  notifyApprovalRoleRequiredChange,
  optimisticallyUpdateApprovalLineRoles,
  restoreApprovalLineRolesSnapshot,
} from '../ApprovalLineConfigPage'
import type { ApprovalLineRole } from '../../api/approvalLineConfigApi'

describe('ApprovalRoleRow', () => {
  test('CREATOR 역할은 전표 작성자 자동 텍스트와 비활성 필수 체크박스를 렌더한다', () => {
    const role: ApprovalLineRole = {
      id: 'r0',
      sequence: 0,
      label: '작성자',
      stepType: 'CREATOR',
      approverGroupId: null,
      approverGroupName: null,
      required: true,
    }

    const html = renderToStaticMarkup(
      createElement(ApprovalRoleRow, {
        role,
        groups: [],
        saving: false,
        onSave: () => undefined,
      }),
    )

    expect(html).toContain('전표 작성자 자동')
    expect(html).toContain('type="checkbox"')
    expect(html).toContain('disabled=""')
  })

  test('GROUP 자동저장은 권한그룹 변경값과 현재 필수값을 onSave 로 전달한다', () => {
    const onSave = vi.fn()

    notifyApprovalRoleGroupChange(onSave, 'g1', true)
    notifyApprovalRoleGroupChange(onSave, '', false)

    expect(onSave).toHaveBeenNthCalledWith(1, 'g1', true)
    expect(onSave).toHaveBeenNthCalledWith(2, null, false)
  })

  test('GROUP 자동저장은 필수 변경값과 현재 권한그룹값을 onSave 로 전달한다', () => {
    const onSave = vi.fn()

    notifyApprovalRoleRequiredChange(onSave, 'g1', false)
    notifyApprovalRoleRequiredChange(onSave, '', true)

    expect(onSave).toHaveBeenNthCalledWith(1, 'g1', false)
    expect(onSave).toHaveBeenNthCalledWith(2, null, true)
  })

  test('자동저장 낙관적 업데이트 실패 시 이전 역할 스냅샷으로 롤백한다', () => {
    const queryClient = new QueryClient()
    const key = approvalLineRolesQueryKey('SLIP_OUTBOUND')
    const prev: ApprovalLineRole[] = [
      {
        id: 'r1',
        sequence: 1,
        label: '출고인',
        stepType: 'GROUP',
        approverGroupId: 'g0',
        approverGroupName: '기존그룹',
        required: true,
      },
    ]
    queryClient.setQueryData(key, prev)

    queryClient.setQueryData<ApprovalLineRole[]>(key, (current) =>
      optimisticallyUpdateApprovalLineRoles(current, {
        id: 'r1',
        approverGroupId: 'g1',
        required: false,
      }))

    expect(queryClient.getQueryData<ApprovalLineRole[]>(key)?.[0]).toMatchObject({
      approverGroupId: 'g1',
      required: false,
    })

    restoreApprovalLineRolesSnapshot(queryClient, key, prev)

    expect(queryClient.getQueryData<ApprovalLineRole[]>(key)?.[0]).toMatchObject({
      approverGroupId: 'g0',
      required: true,
    })
  })
})
