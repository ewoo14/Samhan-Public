/**
 * `<BundleExpandToggle>` 단위 테스트 — Vitest + @testing-library/react 도입 시 활성화.
 *
 * 본 design-system 패키지는 현재 vitest devDep 미설치 상태 (EstimateLineRow.test.tsx 참조).
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2
 */

/*
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { BundleExpandToggle } from './BundleExpandToggle'

describe('BundleExpandToggle', () => {
  it('현재 모드의 button 이 aria-pressed=true', () => {
    render(<BundleExpandToggle mode="EXPAND" onChange={() => {}} />)
    expect(screen.getByRole('button', { name: '펼침' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(screen.getByRole('button', { name: '유지' })).toHaveAttribute(
      'aria-pressed',
      'false',
    )
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
*/

export {}
