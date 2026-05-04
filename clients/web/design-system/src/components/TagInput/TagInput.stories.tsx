import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { TagInput } from './TagInput'

const meta: Meta<typeof TagInput> = {
  title: 'Components/TagInput',
  component: TagInput,
}
export default meta

type Story = StoryObj<typeof TagInput>

/** 빈 상태 */
export const Empty: Story = {
  render: () => {
    const [value, setValue] = useState<Record<string, string>>({})
    return (
      <div style={{ maxWidth: 520 }}>
        <TagInput value={value} onChange={setValue} />
        <pre style={{ marginTop: 12, fontSize: 12 }}>
          {JSON.stringify(value, null, 2)}
        </pre>
      </div>
    )
  },
}

/** HVAC 스펙 사전 입력 */
export const Prefilled: Story = {
  render: () => {
    const [value, setValue] = useState<Record<string, string>>({
      냉방능력: '18평형',
      전압: '220V',
      시리즈: 'WindFree',
    })
    return (
      <div style={{ maxWidth: 520 }}>
        <TagInput value={value} onChange={setValue} />
        <pre style={{ marginTop: 12, fontSize: 12 }}>
          {JSON.stringify(value, null, 2)}
        </pre>
      </div>
    )
  },
}

/** disabled — 추가/삭제 모두 비활성 */
export const Disabled: Story = {
  render: () => {
    const [value, setValue] = useState<Record<string, string>>({
      냉방능력: '18평형',
      전압: '220V',
    })
    return (
      <div style={{ maxWidth: 520 }}>
        <TagInput value={value} onChange={setValue} disabled />
      </div>
    )
  },
}
