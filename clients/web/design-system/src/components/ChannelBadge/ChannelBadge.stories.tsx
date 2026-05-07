import type { Meta, StoryObj } from '@storybook/react'
import { ChannelBadge, type ChannelType, type ChannelBadgeSize } from './ChannelBadge'

const meta: Meta<typeof ChannelBadge> = {
  title: 'Components/ChannelBadge',
  component: ChannelBadge,
  args: {
    channel: 'PUSH',
    size: 'md',
  },
  argTypes: {
    channel: {
      control: 'select',
      options: ['PUSH', 'EMAIL', 'SMS'] satisfies ChannelType[],
    },
    size: {
      control: 'select',
      options: ['md', 'sm'] satisfies ChannelBadgeSize[],
    },
    label: { control: 'text' },
  },
}
export default meta

type Story = StoryObj<typeof ChannelBadge>

/**
 * 3 channel × 2 size = 6 variant grid.
 *
 * Designer D-W4-2 채택 — Storybook 시각 등재 의무.
 */
export const AllVariants: Story = {
  name: '3 채널 × 2 size (grid)',
  render: () => {
    const channels: ChannelType[] = ['PUSH', 'EMAIL', 'SMS']
    const sizes: ChannelBadgeSize[] = ['md', 'sm']
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {sizes.map((s) => (
          <div key={s}>
            <div style={{ fontSize: 12, color: '#6B7280', marginBottom: 6 }}>
              size = {s} ({s === 'md' ? '12px (기본)' : '11px (QA HTML 호환)'})
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {channels.map((c) => (
                <ChannelBadge key={`${c}-${s}`} channel={c} size={s} />
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  },
}

/** PUSH — Google Blue. FCM / APNs 푸시 알림. */
export const Push: Story = {
  args: { channel: 'PUSH' },
}

/** EMAIL — Google Red. AWS SES 이메일. */
export const Email: Story = {
  args: { channel: 'EMAIL' },
}

/** SMS — Google Green. Aligo / Solapi 단문. */
export const Sms: Story = {
  args: { channel: 'SMS' },
}

/** small (11px) — QA HTML matrix inline 호환. */
export const SmallSize: Story = {
  args: { channel: 'PUSH', size: 'sm' },
}

/** label override — 한국어 라벨 표시 예시. */
export const KoreanLabel: Story = {
  args: { channel: 'SMS', label: '문자' },
}
