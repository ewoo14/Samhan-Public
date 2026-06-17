import type {
  DisplayOrderInput,
  EstimateCategory,
  EstimateCategoryExposure,
  ProductCatalogRow,
  UsageScope,
} from '../api/productCatalogApi'

/** 신규 M:N 노출 배열을 우선 사용하고, 과거 단일 필드는 하위호환 fallback 으로만 쓴다. */
export function normalizeEstimateCategoryExposures(
  row: ProductCatalogRow,
): EstimateCategoryExposure[] {
  if (Array.isArray(row.estimateCategories)) {
    return row.estimateCategories
  }
  if (row.estimateCategory) {
    return [{ category: row.estimateCategory, displayOrder: row.displayOrder ?? null }]
  }
  return []
}

/** PATCH /usage body 에 들어갈 EstimateCategory 배열. */
export function estimateCategoryValues(row: ProductCatalogRow): EstimateCategory[] {
  return normalizeEstimateCategoryExposures(row).map((entry) => entry.category)
}

/** 선택된 견적 카테고리 기준 표시순서. 전체 보기에서는 단일 순서를 보여주지 않는다. */
export function exposureDisplayOrder(
  row: ProductCatalogRow,
  category: EstimateCategory | '',
): number | null {
  if (!category) return null
  return normalizeEstimateCategoryExposures(row)
    .find((entry) => entry.category === category)
    ?.displayOrder ?? null
}

/** BE display-orders 계약: 한 요청은 같은 estimateCategory 의 1..N 재번호만 포함한다. */
export function buildCategoryDisplayOrderInputs(
  rows: ProductCatalogRow[],
  category: EstimateCategory,
): DisplayOrderInput[] {
  return rows
    .filter((row) => row.usageScope !== 'NONE')
    .map((row, idx) => ({
      modelCode: row.modelCode,
      estimateCategory: category,
      displayOrder: idx + 1,
    }))
}

/** 마지막 견적 카테고리 제거 시 usageScope 에서 견적 노출 성분을 제거한다. */
export function nextScopeForEstimateCategoryRemoval(scope: UsageScope): UsageScope {
  if (scope === 'BOTH') return 'PARTNER_ORDER'
  if (scope === 'ESTIMATE') return 'NONE'
  return scope
}

interface PageTotals {
  totalElements?: number
  totalPages?: number
}

/** 견적품목 목록은 현재 페이지의 client-side NONE 제외와 무관하게 서버 페이지 정보를 유지한다. */
export function resolveEstimateItemsPageTotals(page: PageTotals | null | undefined): {
  totalElements: number
  totalPages: number
} {
  return {
    totalElements: page?.totalElements ?? 0,
    totalPages: page?.totalPages ?? 1,
  }
}

/** 변동DC 수동 토글은 멀티 카탈로그 품목에만 노출한다. */
export function isVariableDiscountEligible(row: ProductCatalogRow): boolean {
  return row.productCategory === 'HOME_MULTI' || row.productCategory === 'COMMERCIAL_MULTI'
}
