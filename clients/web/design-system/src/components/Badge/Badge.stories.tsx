import type { Meta, StoryObj } from '@storybook/react'
import { Badge } from './Badge'

const meta: Meta<typeof Badge> = {
  title: 'Components/Badge',
  component: Badge,
  args: {
    children: '판매중',
    variant: 'neutral',
  },
  argTypes: {
    variant: {
      control: 'select',
      options: ['brand', 'neutral', 'success', 'warning', 'danger'],
    },
  },
}
export default meta

type Story = StoryObj<typeof Badge>

export const Brand: Story = { args: { variant: 'brand', children: '신제품' } }
export const Neutral: Story = { args: { variant: 'neutral', children: '단종' } }
export const Success: Story = { args: { variant: 'success', children: '판매중' } }
export const Warning: Story = { args: { variant: 'warning', children: '재고부족' } }
export const Danger: Story = { args: { variant: 'danger', children: '판매중지' } }

export const WithIcon: Story = {
  args: {
    variant: 'success',
    children: '판매중',
    icon: (
      <svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true">
        <circle cx="6" cy="6" r="4" fill="currentColor" />
      </svg>
    ),
  },
}

export const ProductStatusShowcase: Story = {
  name: 'ProductStatus 활용 예시',
  render: () => (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      <Badge variant="success">ACTIVE</Badge>
      <Badge variant="brand">NEW</Badge>
      <Badge variant="warning">LOW_STOCK</Badge>
      <Badge variant="neutral">DISCONTINUED</Badge>
      <Badge variant="danger">RECALL</Badge>
    </div>
  ),
}
