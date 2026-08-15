import { describe, expect, it } from 'vitest'
import {
  formatEditableAmountInput,
  parseEditableAmountForServer,
} from './editableAmountInput'

describe('editable amount input', () => {
  it('displays thousands separators while keeping the server value unformatted', () => {
    const result = formatEditableAmountInput('1234567', 7)

    expect(result.displayValue).toBe('1,234,567')
    expect(result.selectionStart).toBe(9)
    expect(parseEditableAmountForServer(result.displayValue)).toBe('1234567')
  })

  it('keeps a negative decimal amount and cursor position stable', () => {
    const result = formatEditableAmountInput('-1234567.50', 11)

    expect(result.displayValue).toBe('-1,234,567.50')
    expect(result.selectionStart).toBe(13)
    expect(parseEditableAmountForServer(result.displayValue)).toBe('-1234567.50')
  })
})
