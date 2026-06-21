import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient } from '@tanstack/react-query'
import { describe, expect, test, vi } from 'vitest'
import {
  ApprovalRoleRow,
  approvalLineRolesQueryKey,
  computeApprovalRoleReorder,
  notifyApprovalRoleGroupChange,
  notifyApprovalRoleLabelChange,
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

// ── 샘플 역할 픽스처 ──
const roleCreator: ApprovalLineRole = {
  id: 'r0',
  sequence: 0,
  label: '작성자',
  stepType: 'CREATOR',
  approverGroupId: null,
  approverGroupName: null,
  required: true,
}

const roleDispatcher: ApprovalLineRole = {
  id: 'r1',
  sequence: 1,
  label: '출고인',
  stepType: 'GROUP',
  approverGroupId: null,
  approverGroupName: null,
  required: true,
}

const roleInspector: ApprovalLineRole = {
  id: 'r2',
  sequence: 2,
  label: '검수인',
  stepType: 'GROUP',
  approverGroupId: null,
  approverGroupName: null,
  required: true,
}

describe('notifyApprovalRoleLabelChange (Task 3)', () => {
  test('정상 라벨 변경 시 onRename 을 호출한다', () => {
    const onRename = vi.fn()
    notifyApprovalRoleLabelChange('출고담당', roleDispatcher, onRename)
    expect(onRename).toHaveBeenCalledWith('출고담당')
  })

  test('blank 입력은 onRename 을 호출하지 않는다', () => {
    const onRename = vi.fn()
    notifyApprovalRoleLabelChange('', roleDispatcher, onRename)
    notifyApprovalRoleLabelChange('   ', roleDispatcher, onRename)
    expect(onRename).not.toHaveBeenCalled()
  })

  test('동일 값 입력은 onRename 을 호출하지 않는다', () => {
    const onRename = vi.fn()
    notifyApprovalRoleLabelChange('출고인', roleDispatcher, onRename)
    expect(onRename).not.toHaveBeenCalled()
  })

  test('CREATOR 역할은 onRename 을 호출하지 않는다', () => {
    const onRename = vi.fn()
    notifyApprovalRoleLabelChange('새이름', roleCreator, onRename)
    expect(onRename).not.toHaveBeenCalled()
  })

  test('앞뒤 공백을 trim 하여 호출한다', () => {
    const onRename = vi.fn()
    notifyApprovalRoleLabelChange('  출고담당  ', roleDispatcher, onRename)
    expect(onRename).toHaveBeenCalledWith('출고담당')
  })
})

describe('computeApprovalRoleReorder (Task 4)', () => {
  const roles = [roleCreator, roleDispatcher, roleInspector]

  test('비-CREATOR 드롭 시 작성자는 항상 index 0', () => {
    const result = computeApprovalRoleReorder(roles, 'r1', 'r2')
    expect(result[0]).toBe('r0') // CREATOR 고정
  })

  test('출고인(r1) → 검수인(r2) 위치로 드래그 시 순서 [r0, r2, r1]', () => {
    const result = computeApprovalRoleReorder(roles, 'r1', 'r2')
    expect(result).toEqual(['r0', 'r2', 'r1'])
  })

  test('검수인(r2) → 출고인(r1) 위치로 드래그 시 순서 [r0, r2, r1]', () => {
    // r2 를 r1 위치(앞)로 드래그 → r2, r1 순
    const result = computeApprovalRoleReorder(roles, 'r2', 'r1')
    expect(result).toEqual(['r0', 'r2', 'r1'])
  })

  test('CREATOR 가 active 이면 현재 순서 그대로 반환한다', () => {
    const result = computeApprovalRoleReorder(roles, 'r0', 'r1')
    expect(result).toEqual(['r0', 'r1', 'r2'])
  })

  test('CREATOR 가 over 이면 현재 순서 그대로 반환한다 (작성자 위로 드롭 불가)', () => {
    const result = computeApprovalRoleReorder(roles, 'r1', 'r0')
    expect(result).toEqual(['r0', 'r1', 'r2'])
  })
})
