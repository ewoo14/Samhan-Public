/**
 * `<EstimateLineRow>` 단위 테스트 — Vitest + @testing-library/react 도입 시 활성화.
 *
 * 본 design-system 패키지는 현재 vitest devDep 미설치 상태이며, 본 파일은 도입 시
 * 곧바로 사용 가능한 spec 을 보존하기 위해 주석 처리. (typecheck/lint 영향 없음.)
 *
 * 도입 후 활성화 절차:
 * 1. `npm i -D vitest @testing-library/react @testing-library/jest-dom jsdom`
 * 2. vitest.config.ts 추가 (jsdom 환경)
 * 3. 본 파일의 주석 블럭 제거 + import 활성화
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2
 */

/*
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
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
    expect(screen.getByText('AC180RXADKG')).toBeInTheDocument()
    expect(screen.getByText('시스템 에어컨')).toBeInTheDocument()
    expect(screen.getByText('5,400,000')).toBeInTheDocument()
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
    // 음수는 차단되어 0 으로 정규화됨
    expect(onQtyChange).toHaveBeenLastCalledWith(0)
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
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.getByRole('button', { name: /라인 1 삭제/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /라인 1 스펙/ })).toBeDisabled()
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
    expect(screen.getByText('-')).toBeInTheDocument()
  })
})
*/

export {}
