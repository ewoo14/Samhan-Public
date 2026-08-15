// @vitest-environment jsdom
import React, { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { EditableAmountInput } from './EditableAmountInput'

function Harness() {
  const [value, setValue] = useState('1234567')
  return <EditableAmountInput aria-label="금액" value={value} onValueChange={setValue} />
}

describe('EditableAmountInput keyboard step', () => {
  it('increments and decrements a formatted amount with ArrowUp/ArrowDown', () => {
    render(<Harness />)
    const input = screen.getByLabelText('금액')

    expect((input as HTMLInputElement).value).toBe('1,234,567')
    fireEvent.keyDown(input, { key: 'ArrowUp' })
    expect((input as HTMLInputElement).value).toBe('1,234,568')
    fireEvent.keyDown(input, { key: 'ArrowDown' })
    expect((input as HTMLInputElement).value).toBe('1,234,567')
  })
})
