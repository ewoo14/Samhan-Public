import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { SpecAddModal, type SpecKeyTemplate } from './SpecAddModal'

const tpl: SpecKeyTemplate[] = [
  { specKey: '냉방성능', defaultUnit: 'kW', displayOrder: 1, isRecommended: true },
  { specKey: '전원선', defaultUnit: 'mm2', displayOrder: 2, isRecommended: true },
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
    const chip = screen.getByRole('button', { name: /냉방성능/ }) as HTMLButtonElement

    expect(chip.disabled).toBe(true)
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

    expect((screen.getByPlaceholderText('예: 냉방성능') as HTMLInputElement).value).toBe('냉방성능')
    expect((screen.getByPlaceholderText('예: kW') as HTMLInputElement).value).toBe('kW')
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

    fireEvent.change(screen.getByPlaceholderText('예: 냉방성능'), { target: { value: '냉방성능' } })

    expect(screen.getByText('이미 등록된 키입니다.')).toBeTruthy()
    expect((screen.getByRole('button', { name: '추가' }) as HTMLButtonElement).disabled).toBe(true)
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

    fireEvent.change(screen.getByPlaceholderText('예: 냉방성능'), { target: { value: '신규키' } })
    fireEvent.change(screen.getByPlaceholderText('예: 5.6'), { target: { value: '값1' } })
    fireEvent.change(screen.getByPlaceholderText('예: kW'), { target: { value: '단위1' } })
    fireEvent.click(screen.getByRole('button', { name: '추가' }))

    expect(onAdd).toHaveBeenCalledWith('신규키', '값1', '단위1')
    expect(onClose).toHaveBeenCalled()
  })
})
