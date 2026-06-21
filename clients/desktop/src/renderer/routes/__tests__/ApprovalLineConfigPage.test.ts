import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, test } from 'vitest'
import { ApprovalRoleRow } from '../ApprovalLineConfigPage'
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
})
