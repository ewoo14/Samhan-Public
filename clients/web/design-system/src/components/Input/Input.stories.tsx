import type { Meta, StoryObj } from '@storybook/react'
import { Input } from './Input'

const meta: Meta<typeof Input> = {
  title: 'Components/Input',
  component: Input,
  args: {
    label: '담당자명',
    placeholder: '이름을 입력하세요',
  },
  argTypes: {
    inputSize: { control: 'select', options: ['sm', 'md', 'lg'] },
    required: { control: 'boolean' },
    disabled: { control: 'boolean' },
  },
}
export default meta

type Story = StoryObj<typeof Input>

export const Default: Story = {}

export const WithHint: Story = {
  args: { hint: '예: 홍길동' },
}

export const Required: Story = {
  args: { required: true, hint: '필수 입력 항목입니다.' },
}

export const WithError: Story = {
  args: { error: '담당자명을 입력해 주세요.', value: '' },
}

export const Disabled: Story = {
  args: { disabled: true, value: '편집 불가' },
}

export const Small: Story = { args: { inputSize: 'sm' } }
export const Large: Story = { args: { inputSize: 'lg' } }

export const NoLabel: Story = {
  args: { label: undefined, placeholder: '검색…' },
}
