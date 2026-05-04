import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { PhoneInput, KOREAN_MOBILE_PHONE_PATTERN } from './PhoneInput'

const meta: Meta<typeof PhoneInput> = {
  title: 'Components/PhoneInput',
  component: PhoneInput,
  args: {
    label: '기사 연락처',
    name: 'driverPhone',
  },
  argTypes: {
    required: { control: 'boolean' },
    disabled: { control: 'boolean' },
  },
}
export default meta

type Story = StoryObj<typeof PhoneInput>

/** 기본 — 빈 값 + placeholder. */
export const Default: Story = {
  render: (args) => {
    const [value, setValue] = useState('')
    return <PhoneInput {...args} value={value} onChange={setValue} />
  },
}

/** 정상 입력 (자동 하이픈). */
export const WithValue: Story = {
  render: (args) => {
    const [value, setValue] = useState('010-1234-5678')
    return <PhoneInput {...args} value={value} onChange={setValue} />
  },
}

/** 헬퍼 텍스트 + 필수 표시. */
export const RequiredWithHelper: Story = {
  render: (args) => {
    const [value, setValue] = useState('')
    return (
      <PhoneInput
        {...args}
        required
        helperText="010 또는 011/016/017/018/019 로 시작하는 11자리 휴대폰 번호"
        value={value}
        onChange={setValue}
      />
    )
  },
}

/** 에러 (외부 검증 후 메시지 표시). */
export const WithError: Story = {
  render: (args) => {
    const [value, setValue] = useState('010-12-34')
    const isValid = KOREAN_MOBILE_PHONE_PATTERN.test(value)
    return (
      <PhoneInput
        {...args}
        value={value}
        onChange={setValue}
        error={isValid ? undefined : '올바른 휴대폰 번호 형식이 아닙니다'}
      />
    )
  },
}

/** 비활성화. */
export const Disabled: Story = {
  args: { disabled: true },
  render: (args) => (
    <PhoneInput {...args} value="010-9876-5432" onChange={() => undefined} />
  ),
}
