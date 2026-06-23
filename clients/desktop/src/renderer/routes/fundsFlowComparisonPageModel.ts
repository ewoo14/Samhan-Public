import type {
  FundsFlowComparisonResponse,
  FundsFlowCounterAccountLine,
} from '../api/accounting'

export type FlowRowKind = 'balance' | 'section' | 'line' | 'subtotal'

export interface FlowTableRow {
  key: string
  kind: FlowRowKind
  label: string
  accountName: string
  current: string
  prior: string
}

export function lineMap(lines: FundsFlowCounterAccountLine[]): Map<string, FundsFlowCounterAccountLine> {
  return new Map(lines.map((line) => [line.counterAccountCode, line]))
}

export function buildAccountRows(
  section: 'increase' | 'decrease',
  currentLines: FundsFlowCounterAccountLine[],
  priorLines: FundsFlowCounterAccountLine[],
): FlowTableRow[] {
  const current = lineMap(currentLines)
  const prior = lineMap(priorLines)
  const codes = Array.from(new Set([...current.keys(), ...prior.keys()])).sort()
  return codes.map((code) => {
    const currentLine = current.get(code)
    const priorLine = prior.get(code)
    const name = currentLine?.counterAccountName ?? priorLine?.counterAccountName ?? code
    return {
      key: `${section}:${code}`,
      kind: 'line',
      label: section === 'increase' ? '증가' : '감소',
      accountName: name,
      current: currentLine?.amount ?? '0',
      prior: priorLine?.amount ?? '0',
    }
  })
}

export function buildRows(data: FundsFlowComparisonResponse | undefined): FlowTableRow[] {
  if (!data) return []
  return [
    {
      key: 'opening',
      kind: 'balance',
      label: '기초잔액',
      accountName: '',
      current: data.current.openingBalance,
      prior: data.prior.openingBalance,
    },
    {
      key: 'increase-section',
      kind: 'section',
      label: '증가',
      accountName: '',
      current: '',
      prior: '',
    },
    ...buildAccountRows('increase', data.current.increases, data.prior.increases),
    {
      key: 'increase-subtotal',
      kind: 'subtotal',
      label: '증가 소계',
      accountName: '',
      current: data.current.increaseSubtotal,
      prior: data.prior.increaseSubtotal,
    },
    {
      key: 'decrease-section',
      kind: 'section',
      label: '감소',
      accountName: '',
      current: '',
      prior: '',
    },
    ...buildAccountRows('decrease', data.current.decreases, data.prior.decreases),
    {
      key: 'decrease-subtotal',
      kind: 'subtotal',
      label: '감소 소계',
      accountName: '',
      current: data.current.decreaseSubtotal,
      prior: data.prior.decreaseSubtotal,
    },
    {
      key: 'closing',
      kind: 'balance',
      label: '기말잔액',
      accountName: '',
      current: data.current.closingBalance,
      prior: data.prior.closingBalance,
    },
  ]
}
