import { describe, expect, it } from 'vitest'
import {
  buildCategoryDisplayOrderInputs,
  estimateCategoryValues,
  exposureDisplayOrder,
  normalizeEstimateCategoryExposures,
} from './ProductCatalogPageModel'
import type { ProductCatalogRow } from '../api/productCatalogApi'

const baseRow: ProductCatalogRow = {
  modelCode: 'AC-1000',
  name: '테스트 품목',
  usageScope: 'BOTH',
  estimateCategories: [],
  productCategory: 'HOME_MULTI',
  usageScopeManual: false,
  releasePrice: 1000,
  deliveryPrice: 1000,
  productType: 'SINGLE',
  componentCount: 0,
}

describe('ProductCatalogPageModel', () => {
  it('신규 estimateCategories 배열을 우선 사용하고 legacy 단일 필드는 fallback 으로만 사용한다', () => {
    expect(
      normalizeEstimateCategoryExposures({
        ...baseRow,
        estimateCategory: 'OTHER',
        displayOrder: 99,
        estimateCategories: [
          { category: 'HOME_MULTI', displayOrder: 1 },
          { category: 'SINGLE_SET', displayOrder: null },
        ],
      }),
    ).toEqual([
      { category: 'HOME_MULTI', displayOrder: 1 },
      { category: 'SINGLE_SET', displayOrder: null },
    ])

    expect(
      normalizeEstimateCategoryExposures({
        ...baseRow,
        estimateCategory: 'OTHER',
        displayOrder: 9,
        estimateCategories: undefined,
      }),
    ).toEqual([{ category: 'OTHER', displayOrder: 9 }])
  })

  it('토글 PATCH 에 보낼 카테고리 값은 다중 노출 카테고리 목록이다', () => {
    expect(
      estimateCategoryValues({
        ...baseRow,
        estimateCategories: [
          { category: 'HOME_MULTI', displayOrder: 1 },
          { category: 'COMMERCIAL_MULTI', displayOrder: 3 },
        ],
      }),
    ).toEqual(['HOME_MULTI', 'COMMERCIAL_MULTI'])
  })

  it('표시순서는 선택된 견적 카테고리의 displayOrder 만 반환한다', () => {
    const row = {
      ...baseRow,
      estimateCategories: [
        { category: 'HOME_MULTI', displayOrder: 4 },
        { category: 'OTHER', displayOrder: 12 },
      ],
    }

    expect(exposureDisplayOrder(row, 'OTHER')).toBe(12)
    expect(exposureDisplayOrder(row, 'SINGLE_SET')).toBeNull()
    expect(exposureDisplayOrder(row, '')).toBeNull()
  })

  it('순서 저장 payload 는 한 카테고리 estimateCategory 를 모든 항목에 포함한다', () => {
    expect(
      buildCategoryDisplayOrderInputs(
        [
          { ...baseRow, modelCode: 'AC-2000' },
          { ...baseRow, modelCode: 'AC-3000' },
        ],
        'HOME_MULTI',
      ),
    ).toEqual([
      { modelCode: 'AC-2000', estimateCategory: 'HOME_MULTI', displayOrder: 1 },
      { modelCode: 'AC-3000', estimateCategory: 'HOME_MULTI', displayOrder: 2 },
    ])
  })
})
