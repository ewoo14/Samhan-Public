import type { Meta, StoryObj } from '@storybook/react'
import { Label } from './Label'

const meta: Meta<typeof Label> = {
  title: 'Components/Label',
  component: Label,
  args: { children: '담당자명', htmlFor: 'demo-input' },
  argTypes: {
    required: { control: 'boolean' },
    size: { control: 'select', options: ['sm', 'md'] },
  },
}
export default meta

type Story = StoryObj<typeof Label>

export const Default: Story = {}
export const Required: Story = { args: { required: true } }
export const Small: Story = { args: { size: 'sm' } }
