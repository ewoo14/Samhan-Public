import { describe, expect, it, vi } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { PrintPreview } from './PrintPreview'

describe('PrintPreview', () => {
  it('mode=pdf + pdfRenderer 미주입 → 자동 fallback 표시', () => {
    render(
      <PrintPreview mode="pdf">
        <div>본문</div>
      </PrintPreview>,
    )

    expect(screen.getByText(/fallback/)).toBeTruthy()
    expect(screen.getByText('본문')).toBeTruthy()
  })

  it('mode=pdf + pdfRenderer 주입 → renderer 호출', () => {
    const renderer = vi.fn((node: ReactNode) => <div data-testid="pdf-frame">{node}</div>)
    render(
      <PrintPreview mode="pdf" pdfRenderer={renderer}>
        <div>본문</div>
      </PrintPreview>,
    )

    expect(renderer).toHaveBeenCalled()
    expect(screen.getByTestId('pdf-frame')).toBeTruthy()
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
