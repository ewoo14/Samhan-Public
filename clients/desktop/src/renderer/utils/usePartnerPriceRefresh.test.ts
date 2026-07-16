// @vitest-environment jsdom
import { renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { usePartnerPriceRefresh, type PartnerRepriceCandidate } from './usePartnerPriceRefresh'

/** 재조회 후보 팩토리 — 필드만 덮어써서 테스트 의도를 좁게 표현한다. */
const candidate = (over: Partial<PartnerRepriceCandidate> = {}): PartnerRepriceCandidate => ({
  key: 'l1',
  productId: 'p1',
  currentUnitPrice: '1000',
  catalogFallback: '900',
  ...over,
})

describe('usePartnerPriceRefresh (D-R8-10 공용 재조회 훅)', () => {
  it('hit=REMEMBERED·miss=CATALOG fallback 로 해석하고 changed 를 판정한다', async () => {
    const fetchMemories = vi.fn().mockResolvedValue({
      hits: [{ productId: 'p1', unitPrice: 2000, source: 'LINE_SAVE', updatedAt: '2026-07-16' }],
      failedProductIds: [],
    })
    const { result } = renderHook(() => usePartnerPriceRefresh({ fetchMemories }))

    const run = await result.current.run('partnerX', [
      candidate({ key: 'l1', productId: 'p1', currentUnitPrice: '1000' }),
      candidate({ key: 'l2', productId: 'p2', currentUnitPrice: '500', catalogFallback: '500' }),
    ])

    expect(fetchMemories).toHaveBeenCalledWith('partnerX', ['p1', 'p2'])
    expect(run.isCurrent()).toBe(true)
    const byKey = Object.fromEntries(run.outcomes.map((o) => [o.key, o]))
    expect(byKey['l1']).toMatchObject({ unitPrice: '2000', source: 'REMEMBERED', changed: true, updatedAt: '2026-07-16' })
    expect(byKey['l2']).toMatchObject({ unitPrice: '500', source: 'CATALOG', changed: false, updatedAt: null })
  })

  it('fetch 자체가 실패하면 전량 CATALOG fallback 으로 수렴한다', async () => {
    const fetchMemories = vi.fn().mockRejectedValue(new Error('forbidden'))
    const { result } = renderHook(() => usePartnerPriceRefresh({ fetchMemories }))

    const run = await result.current.run('partnerX', [candidate({ currentUnitPrice: '1000', catalogFallback: '900' })])

    expect(run.isCurrent()).toBe(true)
    expect(run.outcomes[0]).toMatchObject({ unitPrice: '900', source: 'CATALOG', changed: true, updatedAt: null })
  })

  it('후속 run 이 이전 run 을 supersede 한다 (isCurrent 로 stale 폐기)', async () => {
    const fetchMemories = vi.fn().mockResolvedValue({ hits: [], failedProductIds: [] })
    const { result } = renderHook(() => usePartnerPriceRefresh({ fetchMemories }))

    const first = await result.current.run('A', [candidate()])
    const second = await result.current.run('B', [candidate()])

    expect(first.isCurrent()).toBe(false)
    expect(second.isCurrent()).toBe(true)
  })

  it('invalidate 후에는 진행 중 run 의 isCurrent 가 false 가 된다 (거래처 해제)', async () => {
    const fetchMemories = vi.fn().mockResolvedValue({ hits: [], failedProductIds: [] })
    const { result } = renderHook(() => usePartnerPriceRefresh({ fetchMemories }))

    const run = await result.current.run('A', [candidate()])
    result.current.invalidate()

    expect(run.isCurrent()).toBe(false)
  })

  it('후보 0건이면 fetch 하지 않고 빈 outcome 을 반환한다', async () => {
    const fetchMemories = vi.fn()
    const { result } = renderHook(() => usePartnerPriceRefresh({ fetchMemories }))

    const run = await result.current.run('A', [])

    expect(run.outcomes).toEqual([])
    expect(run.isCurrent()).toBe(true)
    expect(fetchMemories).not.toHaveBeenCalled()
  })
})
