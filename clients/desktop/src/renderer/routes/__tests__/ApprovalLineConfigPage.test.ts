import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test, vi } from 'vitest'
import {
  ApprovalRoleRow,
  notifyApprovalRoleGroupChange,
  notifyApprovalRoleRequiredChange,
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
})
