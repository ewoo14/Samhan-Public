import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { AccountCodeSelect, type Account } from './AccountCodeSelect'

/** 시연용 mock 계정 — 한국 일반기업회계기준 7 카테고리 샘플. */
const SAMPLE_ACCOUNTS: Account[] = [
  { code: '1010', name: '현금', category: '100' },
  { code: '1020', name: '보통예금', category: '100' },
  { code: '1110', name: '외상매출금', category: '100' },
  { code: '1500', name: '제품', category: '100' },
  { code: '2010', name: '외상매입금', category: '200' },
  { code: '2020', name: '예수금', category: '200' },
  { code: '2300', name: '단기차입금', category: '200' },
  { code: '3010', name: '자본금', category: '300' },
  { code: '4010', name: '제품매출', category: '400' },
  { code: '4020', name: '상품매출', category: '400' },
  { code: '5010', name: '제품매출원가', category: '500' },
  { code: '8010', name: '급여', category: '800' },
  { code: '8020', name: '복리후생비', category: '800' },
  { code: '8110', name: '지급수수료', category: '800' },
  { code: '8210', name: '광고선전비', category: '800' },
  { code: '8310', name: '여비교통비', category: '800' },
  { code: '8410', name: '소모품비', category: '800' },
  { code: '9010', name: '이자수익', category: '900' },
  { code: '9510', name: '이자비용', category: '900' },
]

const meta: Meta<typeof AccountCodeSelect> = {
  title: 'Components/AccountCodeSelect',
  component: AccountCodeSelect,
  args: {
    value: '',
    accounts: SAMPLE_ACCOUNTS,
  },
}
export default meta

type Story = StoryObj<typeof AccountCodeSelect>

/** 빈 상태 — 클릭 시 전체 후보 dropdown. */
export const Empty: Story = {
  render: () => {
    const [v, setV] = useState('')
    return (
      <div style={{ width: 320, padding: 8 }}>
        <AccountCodeSelect
          value={v}
          onChange={setV}
          accounts={SAMPLE_ACCOUNTS}
        />
        <div style={{ marginTop: 8, fontSize: 12, color: '#6B7280' }}>
          선택: {v || '(없음)'}
        </div>
      </div>
    )
  },
}

/** 카테고리 필터 — 800(판관비) 만 노출. */
export const FilteredByCategory: Story = {
  render: () => {
    const [v, setV] = useState('')
    return (
      <div style={{ width: 320, padding: 8 }}>
        <AccountCodeSelect
          value={v}
          onChange={setV}
          accounts={SAMPLE_ACCOUNTS}
          category="800"
          placeholder="판관비 계정 검색..."
        />
      </div>
    )
  },
}

/** 선택된 상태 — 코드 + 이름 결합 표시. */
export const Selected: Story = {
  render: () => {
    const [v, setV] = useState('1020')
    return (
      <div style={{ width: 320, padding: 8 }}>
        <AccountCodeSelect
          value={v}
          onChange={setV}
          accounts={SAMPLE_ACCOUNTS}
        />
      </div>
    )
  },
}

/** 비활성화 — POSTED 분개 셀. */
export const Disabled: Story = {
  render: () => (
    <div style={{ width: 320, padding: 8 }}>
      <AccountCodeSelect
        value="1020"
        onChange={() => {}}
        accounts={SAMPLE_ACCOUNTS}
        disabled
      />
    </div>
  ),
}

/** 에러 — 필수 항목 미선택. */
export const HasError: Story = {
  render: () => {
    const [v, setV] = useState('')
    return (
      <div style={{ width: 320, padding: 8 }}>
        <AccountCodeSelect
          value={v}
          onChange={setV}
          accounts={SAMPLE_ACCOUNTS}
          required
          error="필수 항목입니다"
        />
      </div>
    )
  },
}
