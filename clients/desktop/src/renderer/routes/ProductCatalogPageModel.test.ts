import { describe, expect, it } from 'vitest'
import {
  buildCategoryDisplayOrderInputs,
  estimateCategoryValues,
  filterClassificationsByParent,
  formatClassificationPath,
  nextClassificationSelection,
  nextScopeForEstimateCategoryRemoval,
  exposureDisplayOrder,
  isVariableDiscountEligible,
  normalizeEstimateCategoryExposures,
  normalizeFixedDiscountRateInput,
  resolveEstimateItemsPageTotals,
} from './ProductCatalogPageModel'
import type { Classification, ProductCatalogRow } from '../api/productCatalogApi'

const baseRow: ProductCatalogRow = {
  modelCode: 'AC-1000',
  name: '테스트 품목',
  usageScope: 'BOTH',
  estimateCategories: [],
  productCategory: 'HOME_MULTI',
  usageScopeManual: false,
  releasePrice: 1000,
  deliveryPrice: 1000,
  hasVariableDiscount: false,
  variableDiscountManual: false,
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

  it('신규 estimateCategories 빈 배열은 legacy 단일 필드로 fallback 하지 않는다', () => {
    expect(
      normalizeEstimateCategoryExposures({
        ...baseRow,
        estimateCategory: 'OTHER',
        displayOrder: 9,
        estimateCategories: [],
      }),
    ).toEqual([])
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

  it('순서 저장 payload 는 카테고리 노출이 남은 usageScope NONE 품목을 제외한다', () => {
    expect(
      buildCategoryDisplayOrderInputs(
        [
          { ...baseRow, modelCode: 'AC-2000' },
          {
            ...baseRow,
            modelCode: 'MOCK-NONE-ITEM',
            usageScope: 'NONE',
            estimateCategories: [{ category: 'HOME_MULTI', displayOrder: 99 }],
          },
          { ...baseRow, modelCode: 'AC-3000' },
        ],
        'HOME_MULTI',
      ),
    ).toEqual([
      { modelCode: 'AC-2000', estimateCategory: 'HOME_MULTI', displayOrder: 1 },
      { modelCode: 'AC-3000', estimateCategory: 'HOME_MULTI', displayOrder: 2 },
    ])
  })

  it('견적 카테고리 마지막 chip 제거 시 견적 노출 성분을 함께 제거한다', () => {
    expect(nextScopeForEstimateCategoryRemoval('ESTIMATE')).toBe('NONE')
    expect(nextScopeForEstimateCategoryRemoval('BOTH')).toBe('PARTNER_ORDER')
  })

  it('견적품목 페이지네이션은 현재 페이지 client-side NONE 제외 여부와 무관하게 서버 total/pages 를 유지한다', () => {
    expect(
      resolveEstimateItemsPageTotals({
        totalElements: 123,
        totalPages: 3,
      }),
    ).toEqual({
      totalElements: 123,
      totalPages: 3,
    })
  })

  it('변동DC 토글은 홈멀티/상업멀티 품목에만 표시한다', () => {
    expect(isVariableDiscountEligible({ ...baseRow, productCategory: 'HOME_MULTI' })).toBe(true)
    expect(isVariableDiscountEligible({ ...baseRow, productCategory: 'COMMERCIAL_MULTI' })).toBe(true)
    expect(isVariableDiscountEligible({ ...baseRow, productCategory: 'SINGLE_SET' })).toBe(false)
    expect(isVariableDiscountEligible({ ...baseRow, productCategory: 'OLD' })).toBe(false)
    expect(isVariableDiscountEligible({ ...baseRow, productCategory: null })).toBe(false)
  })

  it('분류 옵션은 견적품목 편집 기본값에서 사용 중인 항목만 부모 선택에 맞춰 반환한다', () => {
    const classifications: Classification[] = [
      { id: 'l-panel', estimateCategory: 'HOME_MULTI', catLevel: 'L', parentId: null, name: '판넬', displayOrder: 1, active: true },
      { id: 'm-air', estimateCategory: 'HOME_MULTI', catLevel: 'M', parentId: 'l-panel', name: '공청', displayOrder: 1, active: true },
      { id: 'm-remote', estimateCategory: 'HOME_MULTI', catLevel: 'M', parentId: 'l-remote', name: '유선', displayOrder: 1, active: true },
      { id: 's-round', estimateCategory: 'HOME_MULTI', catLevel: 'S', parentId: 'm-air', name: '360원형', displayOrder: 1, active: true },
      { id: 's-hidden', estimateCategory: 'HOME_MULTI', catLevel: 'S', parentId: 'm-air', name: '비활성', displayOrder: 2, active: false },
    ]

    expect(filterClassificationsByParent(classifications, 'M', 'l-panel').map((item) => item.id)).toEqual(['m-air'])
    expect(filterClassificationsByParent(classifications, 'S', 'm-air').map((item) => item.id)).toEqual(['s-round'])
    expect(filterClassificationsByParent(classifications, 'S', null)).toEqual([])
  })

  it('분류 관리 화면용 조회는 중지 분류도 숨기지 않고 순서대로 반환한다', () => {
    const classifications: Classification[] = [
      { id: 'l-active', estimateCategory: 'HOME_MULTI', catLevel: 'L', parentId: null, name: '사용', displayOrder: 2, active: true },
      { id: 'l-stopped', estimateCategory: 'HOME_MULTI', catLevel: 'L', parentId: null, name: '중지', displayOrder: 1, active: false },
      { id: 'm-stopped', estimateCategory: 'HOME_MULTI', catLevel: 'M', parentId: 'l-active', name: '중지 중분류', displayOrder: 1, active: false },
    ]

    expect(
      filterClassificationsByParent(classifications, 'L', null, { activeOnly: false }).map((item) => item.id),
    ).toEqual(['l-stopped', 'l-active'])
    expect(
      filterClassificationsByParent(classifications, 'M', 'l-active', { activeOnly: false }).map((item) => item.id),
    ).toEqual(['m-stopped'])
  })

  it('품목 행 분류 요약은 대/중/소 경로와 고정DC 값을 한 줄로 만든다', () => {
    expect(
      formatClassificationPath({
        ...baseRow,
        catL: { id: 'l', name: '실내기' },
        catM: { id: 'm', name: '1-Way' },
        catS: { id: 's', name: '소형' },
        fixedDiscountRate: 12.345,
      }),
    ).toEqual({ pathText: '실내기 > 1-Way > 소형', fixedDiscountText: '12.35%' })

    expect(
      formatClassificationPath({
        ...baseRow,
        fixedDiscountRate: null,
      }),
    ).toEqual({ pathText: '미설정', fixedDiscountText: '-' })
  })

  it('대분류 변경은 중/소분류를 초기화하고 중분류 변경은 소분류만 초기화한다', () => {
    expect(
      nextClassificationSelection(
        { catLId: 'l-old', catMId: 'm-old', catSId: 's-old' },
        'L',
        'l-new',
      ),
    ).toEqual({ catLId: 'l-new', catMId: null, catSId: null })

    expect(
      nextClassificationSelection(
        { catLId: 'l-new', catMId: 'm-old', catSId: 's-old' },
        'M',
        'm-new',
      ),
    ).toEqual({ catLId: 'l-new', catMId: 'm-new', catSId: null })
  })

  it('고정DC 입력은 빈 값 null, 숫자는 소수 2자리 문자열로 정규화한다', () => {
    expect(normalizeFixedDiscountRateInput('')).toBeNull()
    expect(normalizeFixedDiscountRateInput(' 12.345 ')).toBe('12.35')
    expect(normalizeFixedDiscountRateInput('150')).toBeNull()
    expect(normalizeFixedDiscountRateInput('-1')).toBeNull()
    expect(normalizeFixedDiscountRateInput('abc')).toBeNull()
  })
})
