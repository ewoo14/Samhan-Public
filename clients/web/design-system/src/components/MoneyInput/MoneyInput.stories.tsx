import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { MoneyInput } from './MoneyInput'

const meta: Meta<typeof MoneyInput> = {
  title: 'Components/MoneyInput',
  component: MoneyInput,
  args: {
    value: 0,
    placeholder: '0',
  },
}
export default meta

type Story = StoryObj<typeof MoneyInput>

/** 빈 상태 — placeholder 표시. */
export const Empty: Story = {
  render: () => {
    const [v, setV] = useState(0)
    return (
      <div style={{ width: 200 }}>
        <MoneyInput value={v} onChange={setV} placeholder="0" />
      </div>
    )
  },
}

/** 값 입력 — 천단위 콤마 자동 포맷. */
export const WithValue: Story = {
  render: () => {
    const [v, setV] = useState(1_234_567)
    return (
      <div style={{ width: 200 }}>
        <MoneyInput value={v} onChange={setV} />
        <div style={{ marginTop: 8, fontSize: 12, color: '#6B7280' }}>
          payload: {v}
        </div>
      </div>
    )
  },
}

/** 비활성화 — 마감(POSTED) 셀. */
export const Disabled: Story = {
  render: () => (
    <div style={{ width: 200 }}>
      <MoneyInput value={5_000_000} onChange={() => {}} disabled />
    </div>
  ),
}

/** 에러 — 차변/대변 불일치. */
export const HasError: Story = {
  render: () => {
    const [v, setV] = useState(123)
    return (
      <div style={{ width: 220 }}>
        <MoneyInput value={v} onChange={setV} error="차변/대변 합계가 일치해야 합니다" />
      </div>
    )
  },
}

/** max 가드 — 999만 cap. */
export const MaxCap: Story = {
  render: () => {
    const [v, setV] = useState(0)
    return (
      <div style={{ width: 200 }}>
        <MoneyInput value={v} onChange={setV} max={9_999_999} />
        <div style={{ marginTop: 8, fontSize: 12, color: '#6B7280' }}>
          최대 9,999,999 (이상 입력 시 cap)
        </div>
      </div>
    )
  },
}
