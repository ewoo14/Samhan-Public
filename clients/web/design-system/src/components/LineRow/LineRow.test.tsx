import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { LineRow, type LineDraft } from './LineRow'

function line(priceSource: LineDraft['priceSource']): LineDraft {
  return {
    id: 'line-1',
    productId: 'product-1',
    modelName: 'MODEL-1',
    productName: '품목 1',
    specification: '',
    quantity: '1',
    unitPrice: '123000',
    priceSource,
    catalogUnitPrice: '100000',
    priceMemoryUpdatedAt: priceSource === 'REMEMBERED' ? '2026-07-10T09:00:00' : null,
    lookupError: null,
    lookupLoading: false,
  }
}

function renderRow(
  priceSource: LineDraft['priceSource'],
  priceRefreshChanged = false,
  partnerSelected?: boolean,
) {
  return render(
    <div role="table">
      <LineRow
        lineNumber={1}
        line={{ ...line(priceSource), priceRefreshChanged }}
        selected={false}
        onSelect={vi.fn()}
        onModelNameChange={vi.fn()}
        onModelNameBlur={vi.fn()}
        onSpecificationChange={vi.fn()}
        onQuantityChange={vi.fn()}
        onUnitPriceChange={vi.fn()}
        onDelete={vi.fn()}
        dragHandleProps={{}}
        {...(partnerSelected === undefined ? {} : { partnerSelected })}
      />
    </div>,
  )
}

describe('LineRow price source marker', () => {
  it('REMEMBERED renders the real marker, saved-at meaning, and input description link', () => {
    renderRow('REMEMBERED', true)

    const note = screen.getByRole('note', {
      name: '이 거래처에 마지막으로 저장된 단가 · 2026-07-10 저장',
    })
    expect(note.textContent).toBe('거래처 최근단가')
    expect(note.getAttribute('title')).toContain('2026-07-10 저장')
    expect(screen.getByLabelText('라인 1 단가').getAttribute('aria-describedby')).toBe(note.id)
    const row = screen.getByRole('row')
    expect(row.className).toContain('priceRefreshed')
    const changedStatus = screen.getByText('단가 변경')
    expect(changedStatus.querySelector('svg')).not.toBeNull()
    expect(changedStatus.hasAttribute('aria-live')).toBe(false)
    expect(row.getAttribute('aria-describedby')).toBe(changedStatus.id)
  })

  // D-R4-1: 자동채움 실체 = 제품 등록 화면 '판매가'(sellingPrice) — '정가' 라벨 금지(출고가 별칭 오도).
  it('CATALOG exposes an explicit 판매가 miss state and description', () => {
    renderRow('CATALOG')

    const note = screen.getByRole('note', {
      name: '이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다',
    })
    expect(note.textContent).toBe('판매가')
    expect(screen.getByLabelText('라인 1 단가').getAttribute('aria-describedby')).toBe(note.id)
  })

  // R4-D2: 라인별 aria-live 는 라인 N개 flip 시 N회 낭독 폭주 — 칩에서 제거(배너 1곳이 전역 고지).
  it.each(['REMEMBERED', 'CATALOG'] as const)('%s marker never carries aria-live (R4-D2)', (source) => {
    renderRow(source)

    expect(screen.getByRole('note').hasAttribute('aria-live')).toBe(false)
  })

  // R4-D4(a): 거래처 미선택이면 CATALOG 설명이 거래처를 단정하지 않는다.
  it('CATALOG without a partner does not claim partner-specific copy', () => {
    renderRow('CATALOG', false, false)

    const note = screen.getByRole('note', { name: '판매가를 적용했습니다' })
    expect(note.textContent).toBe('판매가')
    expect(note.getAttribute('title')).toBe('판매가를 적용했습니다')
    expect(note.getAttribute('aria-label')).not.toContain('거래처')
    expect(screen.getByLabelText('라인 1 단가').getAttribute('aria-describedby')).toBe(note.id)
  })

  // R4-D4(b)·D-R4-4: 거래처 해제 시 마커(저장일 포함)만 해제 — 단가값 유지는 호출자(state) 책임.
  it('REMEMBERED without a partner hides the marker and keeps the unit price rendering', () => {
    renderRow('REMEMBERED', false, false)

    expect(screen.queryByRole('note')).toBeNull()
    const priceInput = screen.getByLabelText('라인 1 단가') as HTMLInputElement
    expect(priceInput.hasAttribute('aria-describedby')).toBe(false)
    // 단가값 유지 — locale 무관 숫자만 비교(toLocaleString 천단위 구분자 회피).
    expect(priceInput.value.replace(/[^0-9]/g, '')).toBe('123000')
  })

  it.each(['USER', null] as const)('%s does not claim an automatic price source', (source) => {
    renderRow(source)

    expect(screen.queryByRole('note')).toBeNull()
    expect(screen.getByLabelText('라인 1 단가').hasAttribute('aria-describedby')).toBe(false)
  })
})
