import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const routesRoot = join(process.cwd(), 'src/renderer/routes')
const moneySignals = /sellingPrice|purchasePrice|releasePrice|deliveryPrice|creditLimit|fixedAllocationAmount|unitPrice|supplyAmount|vatAmount|lineTotal|금액|판매가|매입가|출고가|배송가|신용한도|고정금액|단가/i
const nonMoneySignals = /discountRate|basicDiscount|paymentTermDays|할인율|quantity|multiplier|allocationWeight|수량|비중|반올림 단위|연도|year/i
const rawNumericInput = /type=["']number["']/gi
const amountFormatter = /EditableAmountInput|formatEditableAmountInput|CollaborativeSlipInput|PriceField/

function routeSources(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) return routeSources(path)
    if (!entry.name.endsWith('.tsx') || entry.name.includes('.test.')) return []
    return [path]
  })
}

function discoverUncoveredMoneyScreens(sources: Array<{ path: string; content: string }>) {
  return sources
    .filter(({ content }) => {
      return [...content.matchAll(rawNumericInput)].some((match) => {
        const start = Math.max(0, (match.index ?? 0) - 180)
        const context = content.slice(start, (match.index ?? 0) + 120)
        return moneySignals.test(context) && !nonMoneySignals.test(context) && !amountFormatter.test(context)
      })
    })
    .map(({ path }) => path)
}

describe('#1222 automatic amount-input screen inventory', () => {
  it('discovers raw numeric money-input screens from renderer sources without a manual route list', () => {
    const sources = routeSources(routesRoot).map((path) => ({ path, content: readFileSync(path, 'utf8') }))
    expect(discoverUncoveredMoneyScreens(sources)).toEqual([])
  })

  it('rejects a newly created money screen when it is not wired to an amount formatter', () => {
    const hypotheticalNewScreen = {
      path: 'src/renderer/routes/NewMoneyScreen.tsx',
      content: '<Input label="금액" type="number" value={amount} onChange={onChange} />',
    }
    expect(discoverUncoveredMoneyScreens([hypotheticalNewScreen])).toEqual([
      'src/renderer/routes/NewMoneyScreen.tsx',
    ])
  })
})
