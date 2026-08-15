import { describe, expect, it } from 'vitest'
import {
  adjustEditableAmountByArrow,
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

  it.each([
    ['123456', '123,456'],
    ['112232', '112,232'],
    ['11224', '11,224'],
  ])('formats the amount visible to users (%s)', (raw, expected) => {
    expect(formatEditableAmountInput(raw, raw.length).displayValue).toBe(expected)
  })

  it('keeps ArrowUp and ArrowDown amount increments available after formatting', () => {
    expect(adjustEditableAmountByArrow('1000', 'up')).toBe('1001')
    expect(adjustEditableAmountByArrow('1,001', 'down')).toBe('1000')
    expect(parseEditableAmountForServer(adjustEditableAmountByArrow('1,000', 'up'))).toBe('1001')
  })

  it('does not move the semantic cursor to the end when editing in the middle', () => {
    const result = formatEditableAmountInput('123456', 3)

    expect(result.displayValue).toBe('123,456')
    expect(result.selectionStart).toBe(3)
  })
})
