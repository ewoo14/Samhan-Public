/**
 * `<ProductSpecList>` 단위 테스트 — Vitest 도입 시 활성화 (EstimateLineRow.test.tsx 참조).
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DOMAIN-EXTENSIONS §4 D18
 */

/*
import { describe, it, expect } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import { ProductSpecList, type ProductSpec } from './ProductSpecList'

const specs: ProductSpec[] = [
  { specKey: '냉방성능', specValue: '5.6', unit: 'kW', displayOrder: 3 },
  { specKey: '전원', specValue: '220V', displayOrder: 1 },
  { specKey: '배관경', specValue: 'Φ12.7', displayOrder: 2 },
]

describe('ProductSpecList', () => {
  it('mode=screen — ProductSpec.displayOrder 순서 (전원 → 배관경 → 냉방성능)', () => {
    render(<ProductSpecList specs={specs} mode="screen" layout="table" />)
    const rows = screen.getAllByRole('row')
    expect(within(rows[0]!).getByRole('rowheader')).toHaveTextContent('전원')
    expect(within(rows[1]!).getByRole('rowheader')).toHaveTextContent('배관경')
    expect(within(rows[2]!).getByRole('rowheader')).toHaveTextContent('냉방성능')
  })

  it('mode=print + templateOrder — SpecKeyTemplate 순서 (냉방성능 → 전원 → 배관경)', () => {
    const tplOrder = { 냉방성능: 1, 전원: 2, 배관경: 3 }
    render(
      <ProductSpecList
        specs={specs}
        mode="print"
        templateOrder={tplOrder}
        layout="table"
      />,
    )
    const rows = screen.getAllByRole('row')
    expect(within(rows[0]!).getByRole('rowheader')).toHaveTextContent('냉방성능')
    expect(within(rows[1]!).getByRole('rowheader')).toHaveTextContent('전원')
    expect(within(rows[2]!).getByRole('rowheader')).toHaveTextContent('배관경')
  })

  it('unit 자동 합성 — "5.6" + "kW" → "5.6 kW"', () => {
    render(<ProductSpecList specs={specs.slice(0, 1)} layout="table" />)
    expect(screen.getByText(/5\.6\s*kW/)).toBeInTheDocument()
  })

  it('빈 목록 — emptyMessage 표시', () => {
    render(<ProductSpecList specs={[]} emptyMessage="데이터 없음" />)
    expect(screen.getByText('데이터 없음')).toBeInTheDocument()
  })
})
*/

export {}
