import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { PriceField } from './PriceField'

const meta: Meta<typeof PriceField> = {
  title: 'Components/PriceField',
  component: PriceField,
}
export default meta

type Story = StoryObj<typeof PriceField>

export const Empty: Story = {
  render: () => {
    const [value, setValue] = useState('')
    return (
      <div style={{ maxWidth: 320 }}>
        <PriceField value={value} onChange={setValue} />
        <pre style={{ marginTop: 12, fontSize: 12 }}>value = "{value}"</pre>
      </div>
    )
  },
}

export const WithValue: Story = {
  render: () => {
    const [value, setValue] = useState('1234000')
    return (
      <div style={{ maxWidth: 320 }}>
        <PriceField value={value} onChange={setValue} />
        <pre style={{ marginTop: 12, fontSize: 12 }}>value = "{value}"</pre>
      </div>
    )
  },
}

export const Error: Story = {
  render: () => {
    const [value, setValue] = useState('0')
    return (
      <div style={{ maxWidth: 320 }}>
        <PriceField
          value={value}
          onChange={setValue}
          error="0 이상이어야 합니다"
        />
      </div>
    )
  },
}

export const Disabled: Story = {
  render: () => (
    <div style={{ maxWidth: 320 }}>
      <PriceField value="9990000" onChange={() => undefined} disabled />
    </div>
  ),
}

export const UsdCurrency: Story = {
  name: 'USD ($) 통화',
  render: () => {
    const [value, setValue] = useState('2599.99')
    return (
      <div style={{ maxWidth: 320 }}>
        <PriceField value={value} onChange={setValue} currency="USD" />
        <pre style={{ marginTop: 12, fontSize: 12 }}>value = "{value}"</pre>
      </div>
    )
  },
}

export const CurrencyShowcase: Story = {
  name: '여러 통화 비교',
  render: () => (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: '120px 1fr',
        gap: 12,
        maxWidth: 420,
        alignItems: 'center',
      }}
    >
      <span>KRW</span>
      <PriceField value="1234567" onChange={() => undefined} currency="KRW" />
      <span>USD</span>
      <PriceField value="2599.99" onChange={() => undefined} currency="USD" />
      <span>EUR</span>
      <PriceField value="999.50" onChange={() => undefined} currency="EUR" />
      <span>JPY</span>
      <PriceField value="125000" onChange={() => undefined} currency="JPY" />
    </div>
  ),
}
