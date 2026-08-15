// @vitest-environment jsdom
import React, { act, useState } from 'react'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CollaborativeSlipInput } from '../components/collab/CollaborativeSlipInput'
import { formatEditableAmountInput, parseEditableAmountForServer } from './editableAmountInput'

function RenderedAmountInput({ label, initialValue, onServerValue }: {
  label: string
  initialValue: string
  onServerValue: (value: string) => void
}) {
  const [value, setValue] = useState(initialValue)
  return <CollaborativeSlipInput
    provider={null}
    fieldPath={label}
    value={value}
    onValueChange={(nextValue) => { onServerValue(nextValue); setValue(nextValue) }}
    formatValue={formatEditableAmountInput}
    parseFormattedValue={parseEditableAmountForServer}
    aria-label={label}
    inputMode="decimal"
  />
}

describe('#1222 actual rendered amount-input coverage', () => {
  it('renders estimate unit price and derived amounts with commas', () => {
    render(<>
      <RenderedAmountInput label="estimate-unit-price" initialValue="123456" onServerValue={() => undefined} />
      <RenderedAmountInput label="estimate-supply-amount" initialValue="112232" onServerValue={() => undefined} />
      <RenderedAmountInput label="estimate-vat" initialValue="11224" onServerValue={() => undefined} />
    </>)
    expect((screen.getByLabelText('estimate-unit-price') as HTMLInputElement).value).toBe('123,456')
    expect((screen.getByLabelText('estimate-supply-amount') as HTMLInputElement).value).toBe('112,232')
    expect((screen.getByLabelText('estimate-vat') as HTMLInputElement).value).toBe('11,224')
  })

  it('renders cash-receipt DRAFT amount and preserves raw server values', () => {
    const serverValues: string[] = []
    render(<RenderedAmountInput label="cash-receipt-amount" initialValue="123456" onServerValue={(value) => serverValues.push(value)} />)
    const input = screen.getByLabelText('cash-receipt-amount') as HTMLInputElement
    expect(input.value).toBe('123,456')
    fireEvent.change(input, { target: { value: '-123456.75', selectionStart: 11 } })
    expect(input.value).toBe('-123,456.75')
    expect(serverValues.at(-1)).toBe('-123456.75')
    fireEvent.change(input, { target: { value: '1234567890123', selectionStart: 13 } })
    expect(input.value).toBe('1,234,567,890,123')
    expect(serverValues.at(-1)).toBe('1234567890123')
  })

  it('keeps the caret position when editing in the middle', async () => {
    render(<RenderedAmountInput label="cash-receipt-middle-edit" initialValue="123456" onServerValue={() => undefined} />)
    const input = screen.getByLabelText('cash-receipt-middle-edit') as HTMLInputElement
    fireEvent.change(input, { target: { value: '123956', selectionStart: 4 } })
    await act(async () => { await new Promise<void>((resolve) => requestAnimationFrame(() => resolve())) })
    expect(input.value).toBe('123,956')
    expect(input.selectionStart).toBe(5)
    expect(input.selectionEnd).toBe(5)
  })
})
