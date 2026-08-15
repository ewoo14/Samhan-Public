export interface FormattedEditableAmount {
  displayValue: string
  selectionStart: number
}

function sanitizeAmount(raw: string): string {
  const sign = raw.trimStart().startsWith('-') ? '-' : ''
  const withoutSign = raw.replace(/-/g, '')
  const [integerPart = '', ...decimalParts] = withoutSign.split('.')
  const digits = integerPart.replace(/\D/g, '')
  const decimal = decimalParts.join('').replace(/\D/g, '')
  return `${sign}${digits}${decimalParts.length > 0 ? `.${decimal}` : ''}`
}

function groupedInteger(integerPart: string): string {
  if (!integerPart) return ''
  return integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function nonCommaLength(value: string): number {
  return value.replace(/,/g, '').length
}

export function formatEditableAmountInput(raw: string, selectionStart: number | null): FormattedEditableAmount {
  const beforeCursor = raw.slice(0, Math.max(0, selectionStart ?? raw.length))
  const sanitized = sanitizeAmount(raw)
  const sign = sanitized.startsWith('-') ? '-' : ''
  const unsigned = sanitized.replace(/^-/, '')
  const [integerPart = '', decimalPart] = unsigned.split('.')
  const displayValue = `${sign}${groupedInteger(integerPart)}${decimalPart === undefined ? '' : `.${decimalPart}`}`
  const semanticCursor = nonCommaLength(sanitizeAmount(beforeCursor))
  let cursor = 0
  let seen = 0
  while (cursor < displayValue.length && seen < semanticCursor) {
    if (displayValue[cursor] !== ',') seen += 1
    cursor += 1
  }
  return { displayValue, selectionStart: cursor }
}

export function parseEditableAmountForServer(displayValue: string): string {
  return sanitizeAmount(displayValue).replace(/,/g, '')
}
