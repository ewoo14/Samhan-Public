import type { Meta, StoryObj } from '@storybook/react'
import { TagChip } from './TagChip'

const meta: Meta<typeof TagChip> = {
  title: 'Components/TagChip',
  component: TagChip,
  args: {
    label: '전압',
    value: '220V',
  },
}
export default meta

type Story = StoryObj<typeof TagChip>

export const ReadOnly: Story = {
  args: { label: '전압', value: '220V' },
}

export const Removable: Story = {
  args: {
    label: '냉방능력',
    value: '18평형',
    onRemove: () => alert('removed'),
  },
}

export const LongValue: Story = {
  args: {
    label: '시리즈',
    value: 'WindFree Premium 4-way Cassette System (긴 모델명 말줄임 처리)',
    onRemove: () => alert('removed'),
  },
}

export const HvacSpecGroup: Story = {
  name: 'HVAC 스펙 묶음 예시',
  render: () => (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <TagChip label="냉방능력" value="18평형" />
      <TagChip label="전압" value="220V" />
      <TagChip label="시리즈" value="WindFree" />
      <TagChip label="실외기" value="포함" onRemove={() => undefined} />
    </div>
  ),
}
