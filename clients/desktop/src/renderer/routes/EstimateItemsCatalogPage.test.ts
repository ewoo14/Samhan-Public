import { describe, expect, it, vi } from 'vitest'
import {
  applyClassificationSettingsSuccessEffects,
  applyFixedDiscountPatchSuccessEffects,
  applyUsagePatchSuccessEffects,
  type EstimateItemsCatalogSuccessEffects,
} from './EstimateItemsCatalogPage'

function effects(): EstimateItemsCatalogSuccessEffects & {
  clearMutationError: ReturnType<typeof vi.fn>
  clearPatchingCode: ReturnType<typeof vi.fn>
  closeClassificationModal: ReturnType<typeof vi.fn>
  invalidateCatalogQueries: ReturnType<typeof vi.fn>
} {
  return {
    clearMutationError: vi.fn(),
    clearPatchingCode: vi.fn(),
    closeClassificationModal: vi.fn(),
    invalidateCatalogQueries: vi.fn(),
  }
}

describe('EstimateItemsCatalogPage mutation success wiring', () => {
  it('분류/고정DC 저장 성공은 모달을 닫고 목록을 갱신한다', () => {
    const fns = effects()

    applyClassificationSettingsSuccessEffects(fns)

    expect(fns.clearMutationError).toHaveBeenCalledTimes(1)
    expect(fns.clearPatchingCode).toHaveBeenCalledTimes(1)
    expect(fns.closeClassificationModal).toHaveBeenCalledTimes(1)
    expect(fns.invalidateCatalogQueries).toHaveBeenCalledTimes(1)
  })

  it('usage scope PATCH 성공은 분류/고정DC 모달을 닫지 않는다', () => {
    const fns = effects()

    applyUsagePatchSuccessEffects(fns)

    expect(fns.clearMutationError).toHaveBeenCalledTimes(1)
    expect(fns.clearPatchingCode).toHaveBeenCalledTimes(1)
    expect(fns.closeClassificationModal).not.toHaveBeenCalled()
    expect(fns.invalidateCatalogQueries).toHaveBeenCalledTimes(1)
  })

  it('고정DC 자동저장 성공은 분류 모달을 닫지 않고 목록만 갱신한다', () => {
    const fns = effects()

    applyFixedDiscountPatchSuccessEffects(fns)

    expect(fns.clearMutationError).toHaveBeenCalledTimes(1)
    expect(fns.clearPatchingCode).toHaveBeenCalledTimes(1)
    expect(fns.closeClassificationModal).not.toHaveBeenCalled()
    expect(fns.invalidateCatalogQueries).toHaveBeenCalledTimes(1)
  })
})
