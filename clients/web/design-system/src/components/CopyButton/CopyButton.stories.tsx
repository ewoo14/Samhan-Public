import type { Meta, StoryObj } from '@storybook/react'
import { CopyButton } from './CopyButton'

const meta: Meta<typeof CopyButton> = {
  title: 'Components/CopyButton',
  component: CopyButton,
  args: {
    text: 'https://sign.samhan-air.com/b/abcd1234',
    label: '복사',
  },
}
export default meta

type Story = StoryObj<typeof CopyButton>

/** 기본 — 라벨 "복사", 클릭 시 토스트 3초. */
export const Default: Story = {}

/** 커스텀 라벨. */
export const CustomLabel: Story = {
  args: { label: '링크 복사' },
}

/** 비활성화. */
export const Disabled: Story = {
  args: { disabled: true },
}

/** 짧은 토스트 (1초). */
export const ShortToast: Story = {
  args: { toastDurationMs: 1000 },
}

/** onCopy 콜백. */
export const WithCallback: Story = {
  args: {
    onCopy: (text) => {
      console.log('[CopyButton] copied:', text)
    },
  },
}
