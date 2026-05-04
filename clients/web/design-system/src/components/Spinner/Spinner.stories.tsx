import type { Meta, StoryObj } from '@storybook/react'
import { Spinner } from './Spinner'

const meta: Meta<typeof Spinner> = {
  title: 'Components/Spinner',
  component: Spinner,
  args: { size: 'md' },
  argTypes: {
    size: { control: 'select', options: ['xs', 'sm', 'md', 'lg'] },
    tone: { control: 'text' },
  },
}
export default meta

type Story = StoryObj<typeof Spinner>

export const Default: Story = {}
export const Brand: Story = { args: { tone: 'var(--color-brand-500)' } }
export const Danger: Story = { args: { tone: 'var(--color-danger)' } }

export const AllSizes: Story = {
  render: () => (
    <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
      <Spinner size="xs" />
      <Spinner size="sm" />
      <Spinner size="md" />
      <Spinner size="lg" />
    </div>
  ),
}
