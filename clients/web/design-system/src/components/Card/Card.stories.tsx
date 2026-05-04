import type { Meta, StoryObj } from '@storybook/react'
import { Card } from './Card'

const meta: Meta<typeof Card> = {
  title: 'Components/Card',
  component: Card,
  args: { padding: 4, shadow: 'sm', variant: 'elevated' },
  argTypes: {
    padding: { control: 'select', options: [0, 1, 2, 3, 4, 5, 6, 8, 10, 12] },
    shadow: { control: 'select', options: ['none', 'sm', 'md', 'lg', 'modal'] },
    variant: { control: 'select', options: ['elevated', 'outlined', 'plain'] },
  },
}
export default meta

type Story = StoryObj<typeof Card>

export const Default: Story = {
  args: {
    children: (
      <>
        <h3 style={{ margin: 0, fontSize: 18 }}>운송 의뢰 #2026-001</h3>
        <p style={{ margin: '8px 0 0 0', color: 'var(--color-text-muted)' }}>
          서울 → 부산 / 5톤 / 2026-05-05 출발
        </p>
      </>
    ),
  },
}

export const Outlined: Story = {
  args: { variant: 'outlined', children: '아웃라인 카드' },
}

export const HighShadow: Story = {
  args: { shadow: 'lg', padding: 6, children: '큰 그림자' },
}
