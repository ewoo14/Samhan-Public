import type { Meta, StoryObj } from '@storybook/react'
import { FormField } from './FormField'
import { Input } from '../Input/Input'

const meta: Meta<typeof FormField> = {
  title: 'Components/FormField',
  component: FormField,
}
export default meta

type Story = StoryObj<typeof FormField>

export const WithInput: Story = {
  render: () => (
    <FormField
      label="배송지 주소"
      hint="도/시까지만 입력해도 됩니다."
      required
      render={({ id, ariaDescribedBy, invalid, required }) => (
        <input
          id={id}
          aria-describedby={ariaDescribedBy}
          aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          required={required}
          placeholder="예: 경기도 성남시"
          style={{
            height: 36,
            padding: '0 12px',
            border: '1px solid var(--color-border)',
            borderRadius: 4,
            font: 'inherit',
          }}
        />
      )}
    />
  ),
}

export const WithDsInput: Story = {
  render: () => (
    <FormField
      label="고객사명"
      required
      render={({ id, ariaDescribedBy, invalid, required }) => (
        <Input
          id={id}
          aria-describedby={ariaDescribedBy}
          aria-invalid={invalid || undefined}
          required={required}
          placeholder="고객사를 입력하세요"
        />
      )}
    />
  ),
}

export const WithError: Story = {
  render: () => (
    <FormField
      label="이메일"
      error="올바른 이메일 형식이 아닙니다."
      required
      render={({ id, ariaDescribedBy, invalid, required }) => (
        <Input
          id={id}
          aria-describedby={ariaDescribedBy}
          aria-invalid={invalid || undefined}
          required={required}
          defaultValue="not-an-email"
        />
      )}
    />
  ),
}
