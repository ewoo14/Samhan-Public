import { describe, expect, it } from 'vitest'
import type { FundsFlowComparisonResponse } from '../api/accounting'
import { buildAccountRows, buildRows, lineMap } from './fundsFlowComparisonPageModel'

describe('fundsFlowComparisonPageModel', () => {
  it('상대계정 코드를 map key 로 변환한다', () => {
    const map = lineMap([
      { counterAccountCode: '130', counterAccountName: '미수금', amount: '20.00' },
      { counterAccountCode: '110', counterAccountName: '외상매출금', amount: '10.00' },
    ])

    expect(map.get('110')?.amount).toBe('10.00')
    expect(map.get('130')?.counterAccountName).toBe('미수금')
  })

  it('당기와 직전기간 상대계정 합집합을 코드순으로 만들고 결측 금액을 0으로 채운다', () => {
    const rows = buildAccountRows(
      'increase',
      [
        { counterAccountCode: '130', counterAccountName: '미수금', amount: '30.00' },
        { counterAccountCode: '110', counterAccountName: '외상매출금', amount: '10.00' },
      ],
      [
        { counterAccountCode: '120', counterAccountName: '받을어음', amount: '20.00' },
        { counterAccountCode: '130', counterAccountName: '미수금', amount: '40.00' },
      ],
    )

    expect(rows.map((row) => row.key)).toEqual(['increase:110', 'increase:120', 'increase:130'])
    expect(rows.map((row) => row.accountName)).toEqual(['외상매출금', '받을어음', '미수금'])
    expect(rows.map((row) => [row.current, row.prior])).toEqual([
      ['10.00', '0'],
      ['0', '20.00'],
      ['30.00', '40.00'],
    ])
  })

  it('기초, 증가/감소 라인, 소계, 기말 행을 구성한다', () => {
    const rows = buildRows(response)

    expect(rows.map((row) => row.key)).toEqual([
      'opening',
      'increase-section',
      'increase:110',
      'increase:120',
      'increase-subtotal',
      'decrease-section',
      'decrease:801',
      'decrease:802',
      'decrease-subtotal',
      'closing',
    ])
    expect(rows.find((row) => row.key === 'opening')).toMatchObject({
      kind: 'balance',
      label: '기초잔액',
      current: '1000.00',
      prior: '900.00',
    })
    expect(rows.find((row) => row.key === 'increase-subtotal')).toMatchObject({
      kind: 'subtotal',
      label: '증가 소계',
      current: '300.00',
      prior: '100.00',
    })
    expect(rows.find((row) => row.key === 'decrease-subtotal')).toMatchObject({
      kind: 'subtotal',
      label: '감소 소계',
      current: '50.00',
      prior: '40.00',
    })
    expect(rows.find((row) => row.key === 'closing')).toMatchObject({
      kind: 'balance',
      label: '기말잔액',
      current: '1250.00',
      prior: '960.00',
    })
  })

  it('응답이 없으면 빈 행을 반환한다', () => {
    expect(buildRows(undefined)).toEqual([])
  })
})

const response: FundsFlowComparisonResponse = {
  current: {
    fromDate: '2026-06-10',
    toDate: '2026-06-12',
    openingBalance: '1000.00',
    increases: [
      { counterAccountCode: '120', counterAccountName: '받을어음', amount: '200.00' },
      { counterAccountCode: '110', counterAccountName: '외상매출금', amount: '100.00' },
    ],
    increaseSubtotal: '300.00',
    decreases: [
      { counterAccountCode: '801', counterAccountName: '급여', amount: '50.00' },
    ],
    decreaseSubtotal: '50.00',
    closingBalance: '1250.00',
    reconciled: true,
  },
  prior: {
    fromDate: '2026-06-07',
    toDate: '2026-06-09',
    openingBalance: '900.00',
    increases: [
      { counterAccountCode: '110', counterAccountName: '외상매출금', amount: '100.00' },
    ],
    increaseSubtotal: '100.00',
    decreases: [
      { counterAccountCode: '802', counterAccountName: '지급수수료', amount: '40.00' },
    ],
    decreaseSubtotal: '40.00',
    closingBalance: '960.00',
    reconciled: true,
  },
  generatedAt: '2026-06-12T00:00:00',
}
