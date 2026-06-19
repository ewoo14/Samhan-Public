import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { EstimateLineRow } from './EstimateLineRow'

describe('EstimateLineRow', () => {
  it('기본 렌더 — 모델명 / 품목명 / 소계 표시', () => {
    render(
      <EstimateLineRow
        lineNumber={1}
        model="AC180RXADKG"
        productName="시스템 에어컨"
        qty={2}
        releasePrice={2890000}
        deliveryPrice={2700000}
        lineAmount={5400000}
      />,
    )

    expect(screen.getByText('AC180RXADKG')).toBeTruthy()
    expect(screen.getByText('시스템 에어컨')).toBeTruthy()
    expect(screen.getByText('5,400,000')).toBeTruthy()
  })

  it('수량 input 변경 시 onQtyChange 호출 + 음수 차단', () => {
    const onQtyChange = vi.fn()
    render(
      <EstimateLineRow
        lineNumber={1}
        model="X"
        qty={1}
        releasePrice={1000}
        deliveryPrice={1000}
        lineAmount={1000}
        onQtyChange={onQtyChange}
      />,
    )
    const input = screen.getByLabelText(/라인 1 수량/) as HTMLInputElement

    fireEvent.change(input, { target: { value: '-5' } })
    expect(onQtyChange).toHaveBeenLastCalledWith(5)

    fireEvent.change(input, { target: { value: '12' } })
    expect(onQtyChange).toHaveBeenLastCalledWith(12)
  })

  it('readOnly 모드에서는 수량 input 미표시 + 액션 버튼 disabled', () => {
    render(
      <EstimateLineRow
        lineNumber={1}
        model="X"
        qty={3}
        releasePrice={1000}
        deliveryPrice={1000}
        lineAmount={3000}
        readOnly
        onDelete={vi.fn()}
        onSpecClick={vi.fn()}
        onQtyChange={vi.fn()}
      />,
    )

    expect(screen.queryByLabelText(/라인 1 수량/)).toBeNull()
    expect((screen.getByRole('button', { name: /라인 1 삭제/ }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: /라인 1 스펙 편집/ }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('할인율 0/undefined 시 "-" 표시', () => {
    render(
      <EstimateLineRow
        lineNumber={1}
        model="X"
        qty={1}
        releasePrice={1000}
        deliveryPrice={1000}
        lineAmount={1000}
      />,
    )

    expect(screen.getAllByText('-').length).toBeGreaterThan(0)
  })
})
