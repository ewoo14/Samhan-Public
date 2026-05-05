/**
 * `<PrintPreview>` 단위 테스트 — Vitest 도입 시 활성화 (EstimateLineRow.test.tsx 참조).
 *
 * 출처: migration/analysis/06-frontend-design.md §3.2 / DECISIONS.md F3
 */

/*
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { PrintPreview } from './PrintPreview'

describe('PrintPreview', () => {
  it('mode=pdf + pdfRenderer 미주입 → 자동 fallback 표시', () => {
    render(
      <PrintPreview mode="pdf">
        <div>본문</div>
      </PrintPreview>,
    )
    expect(screen.getByText(/fallback/)).toBeInTheDocument()
    expect(screen.getByText('본문')).toBeInTheDocument()
  })

  it('mode=pdf + pdfRenderer 주입 → renderer 호출', () => {
    const renderer = vi.fn((node) => <div data-testid="pdf-frame">{node}</div>)
    render(
      <PrintPreview mode="pdf" pdfRenderer={renderer}>
        <div>본문</div>
      </PrintPreview>,
    )
    expect(renderer).toHaveBeenCalled()
    expect(screen.getByTestId('pdf-frame')).toBeInTheDocument()
  })

  it('paperSize / orientation 에 따라 paper 크기 변경 (A4 portrait)', () => {
    const { container } = render(
      <PrintPreview mode="browser" paperSize="A4" orientation="portrait">
        <div>본문</div>
      </PrintPreview>,
    )
    const paper = container.querySelector('[data-printable="true"]') as HTMLElement
    expect(paper.style.width).toBe('210mm')
    expect(paper.style.height).toBe('297mm')
  })

  it('A4 landscape — width/height swap', () => {
    const { container } = render(
      <PrintPreview mode="browser" paperSize="A4" orientation="landscape">
        <div>본문</div>
      </PrintPreview>,
    )
    const paper = container.querySelector('[data-printable="true"]') as HTMLElement
    expect(paper.style.width).toBe('297mm')
    expect(paper.style.height).toBe('210mm')
  })

  it('인쇄 버튼 클릭 — onPrint 우선 호출', () => {
    const onPrint = vi.fn()
    render(
      <PrintPreview mode="browser" onPrint={onPrint}>
        <div>본문</div>
      </PrintPreview>,
    )
    fireEvent.click(screen.getByRole('button', { name: '인쇄' }))
    expect(onPrint).toHaveBeenCalled()
  })
})
*/

export {}
