import type {
  Classification,
  ClassificationLevel,
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

export interface ClassificationSelection {
  catLId: string | null
  catMId: string | null
  catSId: string | null
}

/** 부모 분류에 종속되는 자식 분류를 displayOrder/name 순으로 반환한다. */
export function filterClassificationsByParent(
  classifications: Classification[],
  catLevel: ClassificationLevel,
  parentId: string | null,
  options: { activeOnly?: boolean } = {},
): Classification[] {
  if (catLevel !== 'L' && !parentId) return []
  const activeOnly = options.activeOnly ?? true
  return classifications
    .filter((item) =>
      (!activeOnly || item.active) &&
      item.catLevel === catLevel &&
      (catLevel === 'L' ? item.parentId == null : item.parentId === parentId),
    )
    .sort((a, b) => {
      const orderDiff = a.displayOrder - b.displayOrder
      return orderDiff !== 0 ? orderDiff : a.name.localeCompare(b.name, 'ko-KR')
    })
}

/** 견적품목 행에는 분류 전체 편집 UI 대신 한 줄 요약만 노출한다. */
export function formatClassificationPath(row: ProductCatalogRow): {
  pathText: string
  fixedDiscountText: string
} {
  const path = [row.catL?.name, row.catM?.name, row.catS?.name]
    .filter((value): value is string => Boolean(value && value.trim()))
  const fixedRate = row.fixedDiscountRate == null || String(row.fixedDiscountRate).trim() === ''
    ? null
    : Number(row.fixedDiscountRate)

  return {
    pathText: path.length > 0 ? path.join(' > ') : '미설정',
    fixedDiscountText: fixedRate == null || !Number.isFinite(fixedRate)
      ? '-'
      : `${fixedRate.toFixed(2)}%`,
  }
}

/** 대→중→소 종속 선택. 상위 변경 시 하위 선택을 즉시 무효화한다. */
export function nextClassificationSelection(
  current: ClassificationSelection,
  level: ClassificationLevel,
  selectedId: string | null,
): ClassificationSelection {
  if (level === 'L') {
    return { catLId: selectedId, catMId: null, catSId: null }
  }
  if (level === 'M') {
    return { ...current, catMId: selectedId, catSId: null }
  }
  return { ...current, catSId: selectedId }
}

/** 고정DC% 입력값을 BE 전송용 numeric 문자열로 정규화한다. */
export function normalizeFixedDiscountRateInput(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const parsed = Number(trimmed)
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 100) return null
  return parsed.toFixed(2)
}
