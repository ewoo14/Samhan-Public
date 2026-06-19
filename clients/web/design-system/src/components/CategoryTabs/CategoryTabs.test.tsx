import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { CategoryTabs } from './CategoryTabs'

describe('CategoryTabs', () => {
  it('5 탭 기본 렌더', () => {
    render(<CategoryTabs value="HOME_MULTI" onChange={() => {}} />)

    expect(screen.getByRole('tab', { name: /홈멀티/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /싱글중대형/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /상업멀티/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /구형/ })).toBeTruthy()
    expect(screen.getByRole('tab', { name: /기타/ })).toBeTruthy()
  })

  it('현재 value 의 tab 이 aria-selected=true', () => {
    render(<CategoryTabs value="SINGLE_SET" onChange={() => {}} />)

    expect(screen.getByRole('tab', { name: /싱글중대형/ }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByRole('tab', { name: /홈멀티/ }).getAttribute('aria-selected')).toBe('false')
  })

  it('비활성 탭 클릭 시 onChange 호출', () => {
    const onChange = vi.fn()
    render(<CategoryTabs value="HOME_MULTI" onChange={onChange} />)

    fireEvent.click(screen.getByRole('tab', { name: /상업멀티/ }))

    expect(onChange).toHaveBeenCalledWith('COMMERCIAL_MULTI')
  })

  it('disabled 탭 클릭 시 onChange 호출 안함', () => {
    const onChange = vi.fn()
    render(<CategoryTabs value="HOME_MULTI" onChange={onChange} disabled={['LEGACY']} />)
    const tab = screen.getByRole('tab', { name: /구형/ }) as HTMLButtonElement

    expect(tab.disabled).toBe(true)
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

    expect(screen.getByLabelText('12개')).toBeTruthy()
    expect(screen.getByLabelText('0개')).toBeTruthy()
  })
})
