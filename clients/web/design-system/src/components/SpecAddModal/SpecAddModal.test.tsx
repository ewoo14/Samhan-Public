/**
 * `<SpecAddModal>` 단위 테스트 — Vitest 도입 시 활성화 (EstimateLineRow.test.tsx 참조).
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DOMAIN-EXTENSIONS §4 D15
 */

/*
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { SpecAddModal, type SpecKeyTemplate } from './SpecAddModal'

const tpl: SpecKeyTemplate[] = [
  { specKey: '냉방성능', defaultUnit: 'kW', displayOrder: 1, isRecommended: true },
  { specKey: '전원선', defaultUnit: 'mm²', displayOrder: 2, isRecommended: true },
]

describe('SpecAddModal', () => {
  it('이미 등록된 키의 chip 은 disabled', () => {
    render(
      <SpecAddModal
        open
        onClose={() => {}}
        category="HOME_MULTI"
        recommended={tpl}
        existingKeys={['냉방성능']}
        onAdd={() => {}}
      />,
    )
    const chip = screen.getByRole('button', { name: /냉방성능/ })
    expect(chip).toBeDisabled()
  })

  it('chip 선택 시 키 input 자동 입력 + defaultUnit 전파', () => {
    render(
      <SpecAddModal
        open
        onClose={() => {}}
        category="HOME_MULTI"
        recommended={tpl}
        existingKeys={[]}
        onAdd={() => {}}
      />,
    )
    fireEvent.click(screen.getByRole('button', { name: /냉방성능/ }))
    const keyInput = screen.getByLabelText(/스펙 키/) as HTMLInputElement
    expect(keyInput.value).toBe('냉방성능')
    const unitInput = screen.getByLabelText(/단위/) as HTMLInputElement
    expect(unitInput.value).toBe('kW')
  })

  it('자유 입력으로 중복 키 입력 시 추가 버튼 disabled + 에러 메시지', () => {
    render(
      <SpecAddModal
        open
        onClose={() => {}}
        category="HOME_MULTI"
        recommended={tpl}
        existingKeys={['냉방성능']}
        onAdd={() => {}}
      />,
    )
    const keyInput = screen.getByLabelText(/스펙 키/) as HTMLInputElement
    fireEvent.change(keyInput, { target: { value: '냉방성능' } })
    expect(screen.getByText('이미 등록된 키입니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '추가' })).toBeDisabled()
  })

  it('정상 입력 시 onAdd 호출 후 onClose', () => {
    const onAdd = vi.fn()
    const onClose = vi.fn()
    render(
      <SpecAddModal
        open
        onClose={onClose}
        category="HOME_MULTI"
        recommended={tpl}
        existingKeys={[]}
        onAdd={onAdd}
      />,
    )
    fireEvent.change(screen.getByLabelText(/스펙 키/), { target: { value: '신규키' } })
    fireEvent.change(screen.getByLabelText(/값/), { target: { value: '값1' } })
    fireEvent.change(screen.getByLabelText(/단위/), { target: { value: '단위1' } })
    fireEvent.click(screen.getByRole('button', { name: '추가' }))
    expect(onAdd).toHaveBeenCalledWith('신규키', '값1', '단위1')
    expect(onClose).toHaveBeenCalled()
  })
})
*/

export {}
