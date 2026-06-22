import { describe, expect, test } from 'vitest'
import type { SlipDetail } from '../../api/slip'
import type { ApprovalLineStructure } from '../../api/approvalLineConfigApi'
import { fallbackRoles, roleSignerName } from '../approvalRoleCells'

describe('approvalRoleCells', () => {
  test('roleSignerName maps creator and inbound/outbound action keys to flat full name fields', () => {
    expect(roleSignerName(
      { ownerFullName: '홍길동' } as SlipDetail,
      { stepType: 'CREATOR', actionKey: null } as ApprovalLineStructure,
      'INBOUND',
    )).toBe('홍길동')

    expect(roleSignerName(
      { acceptedByFullName: '김입고' } as SlipDetail,
      { stepType: 'GROUP', actionKey: 'INBOUND_RECEIVE' } as ApprovalLineStructure,
      'INBOUND',
    )).toBe('김입고')

    expect(roleSignerName(
      { inspectorFullName: '이검수' } as SlipDetail,
      { stepType: 'GROUP', actionKey: 'INBOUND_INSPECT' } as ApprovalLineStructure,
      'INBOUND',
    )).toBe('이검수')

    expect(roleSignerName(
      { dispatcherFullName: '박출고' } as SlipDetail,
      { stepType: 'GROUP', actionKey: 'OUTBOUND_DISPATCH' } as ApprovalLineStructure,
      'OUTBOUND',
    )).toBe('박출고')

    expect(roleSignerName(
      {} as SlipDetail,
      { stepType: 'GROUP', actionKey: null } as ApprovalLineStructure,
      'INBOUND',
    )).toBeNull()
  })

  test('fallbackRoles returns slipType-specific default labels', () => {
    expect(fallbackRoles('INBOUND').map((role) => role.label))
      .toEqual(['작성자', '입고자', '검수자'])
    expect(fallbackRoles('OUTBOUND').map((role) => role.label))
      .toEqual(['작성자', '출고자', '검수자'])
  })
})
