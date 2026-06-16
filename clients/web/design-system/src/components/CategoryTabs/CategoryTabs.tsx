import styles from './CategoryTabs.module.css'

/**
 * estimateCategory 4 카테고리 (+ OTHER) 탭 컴포넌트.
 *
 * Legacy migration 사전 작업 (DS 6 신규 컴포넌트 중 5번).
 *
 * DOMAIN-EXTENSIONS §3 estimateCategory enum 과 1:1 매핑:
 * - HOME_MULTI (홈멀티)
 * - SINGLE_SET (싱글중대형)
 * - COMMERCIAL_MULTI (상업멀티)
 * - LEGACY (구형)
 * - OTHER (기타)
 *
 * 사용처:
 * - `clients/desktop` EstimateFormPage / OrderFormPage 카테고리 선택
 * - `clients/web/order-app` ProductPickerModal 카테고리 필터 탭
 * - 각 탭에 라인 수 badge 표시 (`counts` prop)
 *
 * 출처: `migration/analysis/06-frontend-design.md` §3.2
 */
export type EstimateCategory =
  | 'HOME_MULTI'
  | 'SINGLE_SET'
  | 'COMMERCIAL_MULTI'
  | 'LEGACY'
  | 'OTHER'

export const ALL_CATEGORIES: readonly EstimateCategory[] = [
  'HOME_MULTI',
  'SINGLE_SET',
  'COMMERCIAL_MULTI',
  'LEGACY',
  'OTHER',
] as const

const CATEGORY_LABEL: Record<EstimateCategory, string> = {
  HOME_MULTI: '홈멀티',
  SINGLE_SET: '싱글중대형',
  COMMERCIAL_MULTI: '상업멀티',
  LEGACY: '구형',
  OTHER: '기타',
}

export interface CategoryTabsProps {
  /** 현재 선택된 카테고리 */
  value: EstimateCategory
  /** 카테고리 변경 콜백 */
  onChange: (next: EstimateCategory) => void
  /**
   * 표시할 카테고리 목록 (선택). 기본 = 5개 모두.
   * 커스터마이징 가능 — 예: 견적 화면에서 OTHER 숨김.
   */
  categories?: readonly EstimateCategory[]
  /** 비활성화할 카테고리 (선택). 라벨은 표시되지만 클릭 불가. */
  disabled?: readonly EstimateCategory[]
  /** 각 카테고리의 count badge (선택). undefined 면 badge 미표시. */
  counts?: Partial<Record<EstimateCategory, number>>
  /** 추가 className */
  className?: string
  /** 접근성 — tablist aria-label. 기본 "카테고리". */
  ariaLabel?: string
}

/**
 * CategoryTabs — estimateCategory 탭 (5종, count badge 지원).
 *
 * @param props value / onChange / categories / counts
 * @example
 * ```tsx
 * <CategoryTabs
 *   value={category}
 *   onChange={setCategory}
 *   counts={{ HOME_MULTI: 12, SINGLE_SET: 3, COMMERCIAL_MULTI: 7 }}
 * />
 * ```
 */
export function CategoryTabs({
  value,
  onChange,
  categories = ALL_CATEGORIES,
  disabled = [],
  counts,
  className,
  ariaLabel = '카테고리',
}: CategoryTabsProps) {
  const disabledSet = new Set(disabled)

  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={[styles['tablist'], className].filter(Boolean).join(' ')}
    >
      {categories.map((cat) => {
        const isActive = cat === value
        const isDisabled = disabledSet.has(cat)
        const count = counts?.[cat]
        const tabClasses = [
          styles['tab'],
          isActive ? styles['tabActive'] : null,
          isDisabled ? styles['tabDisabled'] : null,
        ]
          .filter(Boolean)
          .join(' ')

        return (
          <button
            key={cat}
            type="button"
            role="tab"
            aria-selected={isActive}
            aria-controls={`category-panel-${cat}`}
            tabIndex={isActive ? 0 : -1}
            disabled={isDisabled}
            className={tabClasses}
            onClick={() => {
              if (!isDisabled && !isActive) onChange(cat)
            }}
          >
            <span className={styles['tabLabel']}>{CATEGORY_LABEL[cat]}</span>
            {typeof count === 'number' ? (
              <span
                className={styles['tabBadge']}
                aria-label={`${count}개`}
                data-empty={count === 0 ? 'true' : 'false'}
              >
                {count}
              </span>
            ) : null}
          </button>
        )
      })}
    </div>
  )
}

export default CategoryTabs
