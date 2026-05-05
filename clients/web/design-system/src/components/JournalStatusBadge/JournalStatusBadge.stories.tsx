import type { Meta, StoryObj } from '@storybook/react'
import { JournalStatusBadge, type JournalStatus } from './JournalStatusBadge'

const meta: Meta<typeof JournalStatusBadge> = {
  title: 'Components/JournalStatusBadge',
  component: JournalStatusBadge,
  args: {
    status: 'DRAFT',
  },
  argTypes: {
    status: {
      control: 'select',
      options: ['DRAFT', 'POSTED', 'REVERSED'] satisfies JournalStatus[],
    },
  },
}
export default meta

type Story = StoryObj<typeof JournalStatusBadge>

/**
 * 3종 상태 전체 — 색상 그룹 비교.
 */
export const AllStatuses: Story = {
  name: '3종 상태 전체',
  render: () => (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <JournalStatusBadge status="DRAFT" />
      <JournalStatusBadge status="POSTED" />
      <JournalStatusBadge status="REVERSED" />
    </div>
  ),
}

/** DRAFT — 임시저장. 회색. 편집 자유, 마감 미반영. */
export const Draft: Story = { args: { status: 'DRAFT' } }

/** POSTED — 확정. 녹색 + bold. 원장 반영, 시산표 합산. */
export const Posted: Story = { args: { status: 'POSTED' } }

/** REVERSED — 역분개. 회색 + 취소선. 대응 분개로 무효화됨. */
export const Reversed: Story = { args: { status: 'REVERSED' } }
