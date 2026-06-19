import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { BundleExpandToggle } from './BundleExpandToggle'

describe('BundleExpandToggle', () => {
  it('현재 모드의 button 이 aria-pressed=true', () => {
    render(<BundleExpandToggle mode="EXPAND" onChange={() => {}} />)

    expect(screen.getByRole('button', { name: '펼침' }).getAttribute('aria-pressed')).toBe('true')
    expect(screen.getByRole('button', { name: '유지' }).getAttribute('aria-pressed')).toBe('false')
  })

  it('비활성 버튼 클릭 시 onChange 호출', () => {
    const onChange = vi.fn()
    render(<BundleExpandToggle mode="EXPAND" onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '유지' }))

    expect(onChange).toHaveBeenCalledWith('KEEP')
  })

  it('활성 버튼 재클릭 시 onChange 호출 안함', () => {
    const onChange = vi.fn()
    render(<BundleExpandToggle mode="EXPAND" onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: '펼침' }))

    expect(onChange).not.toHaveBeenCalled()
  })

  it('disabled 상태에서는 클릭 시 onChange 호출 안함', () => {
    const onChange = vi.fn()
    render(<BundleExpandToggle mode="EXPAND" onChange={onChange} disabled />)

    fireEvent.click(screen.getByRole('button', { name: '유지' }))

    expect(onChange).not.toHaveBeenCalled()
  })
})
