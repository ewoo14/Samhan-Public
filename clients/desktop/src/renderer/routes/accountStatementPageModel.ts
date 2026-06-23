import type {
  AccountStatementAccountSection,
  AccountStatementBalanceDirection,
  AccountStatementLine,
  AccountStatementTotal,
} from '../api/accounting'

export type AccountStatementAmountValue = string | number | null | undefined

export interface AccountStatementTableRow extends AccountStatementLine {
  rowKey: string
}

export interface AccountStatementTotalItem {
  label: string
  value: AccountStatementAmountValue
  direction: AccountStatementBalanceDirection
}

export function amountNumber(raw: AccountStatementAmountValue): number {
  if (raw == null || raw === '') return 0
  const parsed = typeof raw === 'number' ? raw : Number.parseFloat(raw)
  return Number.isFinite(parsed) ? parsed : 0
}

export function isNegativeAmount(raw: AccountStatementAmountValue): boolean {
  return amountNumber(raw) < 0
}

/** KRW 정수/소수 string -> "5,000" 형식. 0은 eCount 표처럼 dash로 표시한다. */
export function fmtAmount(raw: AccountStatementAmountValue): string {
  const value = amountNumber(raw)
  if (value === 0) return '—'
  const rounded = Math.round(Math.abs(value))
  const text = rounded.toLocaleString('ko-KR')
  return value < 0 ? `-${text}` : text
}

export function buildAccountStatementRows(
  section: AccountStatementAccountSection,
): AccountStatementTableRow[] {
  return section.lines.map((line, index) => ({
    ...line,
    rowKey: `${section.accountCode}:${index}`,
  }))
}

export function bizNoDigits(line: Pick<AccountStatementLine, 'bizNo'>): string {
  return line.bizNo?.replace(/-/g, '').trim() ?? ''
}

export function partnerLabel(line: Pick<AccountStatementLine, 'partnerName'>): string {
  const name = line.partnerName?.trim()
  return name || ''
}

export function accountStatementTotalItems(
  total: AccountStatementTotal | null | undefined,
): AccountStatementTotalItem[] {
  const items: AccountStatementTotalItem[] = []
  if (total?.receivableTotal) {
    items.push({
      label: '채권 합계',
      value: total.receivableTotal.balance,
      direction: 'DEBIT',
    })
  }
  if (total?.payableTotal) {
    items.push({
      label: '채무 합계',
      value: total.payableTotal.balance,
      direction: 'CREDIT',
    })
  }
  return items
}
