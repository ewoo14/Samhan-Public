// @vitest-environment jsdom
import React from 'react'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { LineRow } from '@samhan/design-system'
import '../styles/global.css'

const longProductName = '초장문 품목명 1024px 전표에서 잘리지 않아야 하는 제품 설명'
const longModelName = 'MODEL-LONG-2026-08-15-IDENTIFIER-0000000001'

describe('#1222 1024px slip edit columns', () => {
  it('keeps identifier and amount text intact inside a horizontally scrollable table', () => {
    render(
      <div className="sfp-line-table" style={{ width: '1024px', overflowX: 'auto', overflowY: 'hidden' }} data-testid="slip-form-line-table">
        <LineRow
          lineNumber={1}
          line={{
            id: 'line-1',
            productId: 'product-1',
            modelName: longModelName,
            productName: longProductName,
            specification: '220V',
            quantity: '1',
            unitPrice: '1234567',
            supplyAmount: '1122334',
            vatAmount: '112233',
            lineTotal: '1234567',
            lookupError: null,
            lookupLoading: false,
          }}
          selected={false}
          onSelect={() => undefined}
          onModelNameChange={() => undefined}
          onModelNameBlur={() => undefined}
          onSpecificationChange={() => undefined}
          onQuantityChange={() => undefined}
          onUnitPriceChange={() => undefined}
          onDelete={() => undefined}
          dragHandleProps={{}}
          vatInclusive
          modelCell={<span>{longModelName}</span>}
        />
      </div>,
    )

    const table = screen.getByTestId('slip-form-line-table')
    expect(table.textContent).toContain(longModelName)
    expect(table.textContent).toContain(longProductName)
    expect(screen.getByDisplayValue('1,234,567')).toBeTruthy()
    expect(table.style.overflowX).toBe('auto')
    const globalCss = readFileSync(resolve(process.cwd(), 'src/renderer/styles/global.css'), 'utf8')
    expect(globalCss).toMatch(/\.sfp-line-table \[class\*='lineRow'\][\s\S]*?min-width:\s*1320px/)
  })
})
