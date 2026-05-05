/**
 * `<CategoryTabs>` 단위 테스트 — Vitest 도입 시 활성화 (EstimateLineRow.test.tsx 참조).
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2
 */

/*
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CategoryTabs } from './CategoryTabs'

describe('CategoryTabs', () => {
  it('5 탭 기본 렌더', () => {
    render(<CategoryTabs value="HOME_MULTI" onChange={() => {}} />)
    expect(screen.getByRole('tab', { name: /홈멀티/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /싱글 세트/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /상업멀티/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /구형/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /기타/ })).toBeInTheDocument()
  })

  it('현재 value 의 tab 이 aria-selected=true', () => {
    render(<CategoryTabs value="SINGLE_SET" onChange={() => {}} />)
    expect(screen.getByRole('tab', { name: /싱글 세트/ })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByRole('tab', { name: /홈멀티/ })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })

  it('비활성 탭 클릭 시 onChange 호출', () => {
    const onChange = vi.fn()
    render(<CategoryTabs value="HOME_MULTI" onChange={onChange} />)
    fireEvent.click(screen.getByRole('tab', { name: /상업멀티/ }))
    expect(onChange).toHaveBeenCalledWith('COMMERCIAL_MULTI')
  })

  it('disabled 탭 클릭 시 onChange 호출 안함', () => {
    const onChange = vi.fn()
    render(
      <CategoryTabs value="HOME_MULTI" onChange={onChange} disabled={['LEGACY']} />,
    )
    const tab = screen.getByRole('tab', { name: /구형/ })
    expect(tab).toBeDisabled()
    fireEvent.click(tab)
    expect(onChange).not.toHaveBeenCalled()
  })

  it('counts prop — 각 탭에 count badge 표시', () => {
    render(
      <CategoryTabs
        value="HOME_MULTI"
        onChange={() => {}}
        counts={{ HOME_MULTI: 12, SINGLE_SET: 0 }}
      />,
    )
    expect(screen.getByLabelText('12개')).toBeInTheDocument()
    expect(screen.getByLabelText('0개')).toBeInTheDocument()
  })
})
*/

export {}
