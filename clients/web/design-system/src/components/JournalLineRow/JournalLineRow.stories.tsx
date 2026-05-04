import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { JournalLineRow, type JournalLineDraft } from './JournalLineRow'
import type { Account } from '../AccountCodeSelect/AccountCodeSelect'

const SAMPLE_ACCOUNTS: Account[] = [
  { code: '1010', name: '현금', category: '100' },
  { code: '1020', name: '보통예금', category: '100' },
  { code: '4010', name: '제품매출', category: '400' },
  { code: '8010', name: '급여', category: '800' },
  { code: '8110', name: '지급수수료', category: '800' },
]

const meta: Meta<typeof JournalLineRow> = {
  title: 'Components/JournalLineRow',
  component: JournalLineRow,
}
export default meta

type Story = StoryObj<typeof JournalLineRow>

/** 빈 라인 — 모든 셀 비어있음. */
export const Empty: Story = {
  render: () => {
    const [line, setLine] = useState<JournalLineDraft>({
      accountCode: '',
      debit: 0,
      credit: 0,
      partnerName: '',
      note: '',
    })
    return (
      <div style={{ width: 900, padding: 12 }}>
        <JournalLineRow
          index={1}
          line={line}
          accounts={SAMPLE_ACCOUNTS}
          onChange={(p) => setLine((prev) => ({ ...prev, ...p }))}
          onRemove={() => alert('remove')}
        />
      </div>
    )
  },
}

/** 차변 입력 — 보통예금 / 1,000,000원. */
export const DebitFilled: Story = {
  render: () => {
    const [line, setLine] = useState<JournalLineDraft>({
      accountCode: '1020',
      debit: 1_000_000,
      credit: 0,
      partnerName: '',
      note: '5월 매출 대금 입금',
    })
    return (
      <div style={{ width: 900, padding: 12 }}>
        <JournalLineRow
          index={1}
          line={line}
          accounts={SAMPLE_ACCOUNTS}
          onChange={(p) => setLine((prev) => ({ ...prev, ...p }))}
          onRemove={() => {}}
        />
      </div>
    )
  },
}

/** 다중 라인 — 차변 1 + 대변 1. */
export const MultiLines: Story = {
  render: () => {
    const [lines, setLines] = useState<JournalLineDraft[]>([
      {
        accountCode: '1020',
        debit: 1_000_000,
        credit: 0,
        partnerName: '윌리',
        note: '5월 매출 입금',
      },
      {
        accountCode: '4010',
        debit: 0,
        credit: 1_000_000,
        partnerName: '윌리',
        note: '제품 매출',
      },
    ])
    return (
      <div style={{ width: 900, padding: 12 }}>
        {lines.map((line, i) => (
          <JournalLineRow
            key={i}
            index={i + 1}
            line={line}
            accounts={SAMPLE_ACCOUNTS}
            onChange={(p) =>
              setLines((prev) =>
                prev.map((l, idx) => (idx === i ? { ...l, ...p } : l)),
              )
            }
            onRemove={() =>
              setLines((prev) => prev.filter((_, idx) => idx !== i))
            }
          />
        ))}
      </div>
    )
  },
}

/** 비활성화 — POSTED 분개. */
export const Disabled: Story = {
  render: () => (
    <div style={{ width: 900, padding: 12 }}>
      <JournalLineRow
        index={1}
        line={{
          accountCode: '1020',
          debit: 1_000_000,
          credit: 0,
          partnerName: '윌리',
          note: '5월 매출 입금',
        }}
        accounts={SAMPLE_ACCOUNTS}
        onChange={() => {}}
        onRemove={() => {}}
        disabled
      />
    </div>
  ),
}
