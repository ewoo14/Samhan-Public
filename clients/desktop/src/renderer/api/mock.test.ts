import { describe, expect, it } from 'vitest'
import type { AxiosRequestConfig } from 'axios'
import { getMockResponse } from './mock'
import type { MonthlyIncomeStatementResponse } from './accounting'

type MockEnvelope<T> = {
  success: boolean
  data: T
}

type MockRole = {
  id: string
  documentType?: string
  sequence: number
  label: string
  stepType: 'CREATOR' | 'GROUP' | 'USER'
}

function mockRequest(config: AxiosRequestConfig): unknown {
  return getMockResponse(config)
}

function amount(raw: string | number): number {
  return typeof raw === 'number' ? raw : Number(raw)
}

describe('mock approval-line-config contract', () => {
  it('GROUPWARE 기본 결재자 resolve 는 USER 결재자만 sequence 순으로 반환한다', () => {
    const resolved = mockRequest({
      method: 'GET',
      url: '/auth/approval-line-configs/GROUPWARE_EXPENSE_REPORT/default-approvers',
    }) as MockEnvelope<Array<{ sequence: number; label: string; userId: string; displayName: string }>>

    expect(resolved.data).toEqual([
      { sequence: 1, label: '검토자', userId: 'user-002', displayName: '이정훈' },
      { sequence: 2, label: '승인자', userId: 'user-005', displayName: '홍지수' },
    ])
  })

  it('미설정 GROUPWARE 기본 결재자는 빈 배열을 반환한다', () => {
    const resolved = mockRequest({
      method: 'GET',
      url: '/auth/approval-line-configs/GROUPWARE_LEAVE_REQUEST/default-approvers',
    }) as MockEnvelope<unknown[]>

    expect(resolved.data).toEqual([])
  })

  it('GROUPWARE 문서의 sequence 0 GROUP 단계는 삭제할 수 있다', () => {
    const documentType = `GROUPWARE_TEST_${Date.now()}`
    const created = mockRequest({
      method: 'POST',
      url: '/auth/admin/approval-line-configs',
      data: { documentType, label: '검토자' },
    }) as MockEnvelope<MockRole>

    expect(created.data).toMatchObject({
      sequence: 0,
      label: '검토자',
      stepType: 'GROUP',
    })

    const deleted = mockRequest({
      method: 'DELETE',
      url: `/auth/admin/approval-line-configs/${encodeURIComponent(created.data.id)}`,
    }) as MockEnvelope<null>

    expect(deleted.success).toBe(true)
    expect(deleted.data).toBeNull()
  })

  it('전표 CREATOR 단계 삭제는 계속 거부한다', () => {
    const deleted = mockRequest({
      method: 'DELETE',
      url: '/auth/admin/approval-line-configs/mock-approval-line-slip-outbound-creator',
    }) as { __mockStatus: number; body: MockEnvelope<null> & { code: string; message: string } }

    expect(deleted.__mockStatus).toBe(400)
    expect(deleted.body.code).toBe('INVALID_INPUT')
    expect(deleted.body.message).toContain('작성자 역할은 삭제할 수 없습니다')
  })
})

describe('mock monthly income statement contract', () => {
  it('실 BE 월별손익분석 행 순서와 영업외손익 소계 자기정합을 유지한다', () => {
    const resolved = mockRequest({
      method: 'GET',
      url: '/accounting/reports/income-statement/monthly',
      params: { year: 2027 },
    }) as MockEnvelope<MonthlyIncomeStatementResponse>

    const rows = resolved.data.rows

    expect(rows.map((row) => row.accountName)).toEqual([
      '상품매출',
      '매출액 합계',
      '상품매출원가',
      '매출원가 합계',
      '매출총이익',
      '직원급여(판)',
      '판매비와관리비 합계',
      '영업이익',
      '이자비용',
      '영업외손익 합계',
      '법인세차감전순이익',
      '법인세비용',
      '당기순이익',
    ])

    const nonOperatingAccounts = rows.filter((row) => row.section === 'NON_OPERATING' && row.rowKind === 'ACCOUNT')
    const nonOperatingSubtotal = rows.find((row) => row.accountName === '영업외손익 합계')

    expect(nonOperatingSubtotal).toMatchObject({
      rowKind: 'SUBTOTAL',
      section: 'NON_OPERATING',
      accountCode: null,
      category: null,
      sortOrder: 9899,
    })

    const expectedMonthly = resolved.data.months.map((_, index) =>
      nonOperatingAccounts.reduce((sum, row) => sum + amount(row.monthlyAmounts[index] ?? 0), 0),
    )

    expect(nonOperatingSubtotal?.monthlyAmounts.map(amount)).toEqual(expectedMonthly)
    expect(amount(nonOperatingSubtotal?.annualTotal ?? 0)).toBe(
      expectedMonthly.reduce((sum, value) => sum + value, 0),
    )
    expect(amount(nonOperatingSubtotal?.priorYearTotal ?? 0)).toBe(
      nonOperatingAccounts.reduce((sum, row) => sum + amount(row.priorYearTotal), 0),
    )
    expect(amount(nonOperatingSubtotal?.difference ?? 0)).toBe(
      amount(nonOperatingSubtotal?.annualTotal ?? 0) - amount(nonOperatingSubtotal?.priorYearTotal ?? 0),
    )
  })
})
