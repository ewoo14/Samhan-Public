import { useMemo } from 'react'
import styles from './ProductSpecList.module.css'

/**
 * ProductSpec 동적 목록 — `key: value (unit)` 표 형식.
 *
 * Legacy migration 사전 작업 (DS 6 신규 컴포넌트 중 3번).
 *
 * DOMAIN-EXTENSIONS §4 D18 정렬 정책:
 * - `mode='screen'` — `ProductSpec.displayOrder` 기준 정렬 (사용자가 drag&drop 으로 조정한 순서)
 * - `mode='print'` — `SpecKeyTemplate.displayOrder` 기준 정렬 (카테고리 표준 순서로 인쇄 일관성)
 *   — `templateOrder` map 을 prop 으로 받아 적용. 없는 키는 ProductSpec.displayOrder 로 후순위 정렬.
 *
 * 사용처:
 * - EstimateLineRow `spec` slot 에 `layout='inline'` 으로 단일 행 요약
 * - SpecModal 본문에 `layout='table'` 로 전체 표시
 * - PrintPreview 안에 `mode='print'` + `layout='table'` 로 인쇄용 표시
 *
 * 출처: `migration/analysis/06-frontend-design.md` §3.2 / DOMAIN-EXTENSIONS.md §4 D18
 */
export interface ProductSpec {
  /** 스펙 키 (예: "냉방성능", "전원선") */
  specKey: string
  /** 스펙 값 (예: "5.6kW", "2.5") */
  specValue: string
  /** 단위 (선택). 값에 단위가 포함된 경우 비움. */
  unit?: string | null
  /** 화면 표시 순서 (사용자가 drag&drop 조정한 순서). */
  displayOrder: number
}

export type ProductSpecListMode = 'screen' | 'print'
export type ProductSpecListLayout = 'inline' | 'card' | 'table'

export interface ProductSpecListProps {
  /** 표시할 스펙 목록. */
  specs: ProductSpec[]
  /**
   * 정렬 모드 (DOMAIN-EXTENSIONS §4 D18):
   * - `'screen'`: ProductSpec.displayOrder 기준 (사용자 정의 순서)
   * - `'print'`: SpecKeyTemplate.displayOrder 기준 (카테고리 표준 순서)
   * 기본값 `'screen'`.
   */
  mode?: ProductSpecListMode
  /**
   * `mode='print'` 시 SpecKeyTemplate.displayOrder map.
   * `Record<specKey, templateDisplayOrder>` — 없는 키는 ProductSpec.displayOrder 후순위 정렬.
   */
  templateOrder?: Record<string, number>
  /**
   * 표시 layout:
   * - `'inline'`: 한 줄 요약 (예: "냉방 5.6kW · 220V · Φ12.7") — EstimateLineRow `spec` slot 용
   * - `'card'`: dl/dd 카드 형식 — SpecModal 본문
   * - `'table'`: table 형식 — PrintPreview / 상세 화면
   */
  layout?: ProductSpecListLayout
  /** 빈 목록일 때 메시지. 기본 "스펙 정보 없음". */
  emptyMessage?: string
  /** 추가 className */
  className?: string
}

/** specValue 에 unit 합성 (단, unit 이 이미 specValue 끝에 있으면 중복 방지). */
const composeValue = (specValue: string, unit?: string | null): string => {
  if (!unit) return specValue
  if (specValue.endsWith(unit)) return specValue
  // 숫자 + 단위 패턴 (예: "5.6" + "kW" → "5.6 kW")
  return `${specValue} ${unit}`.trim()
}

/**
 * mode/templateOrder 에 따라 정렬된 스펙 배열 반환.
 * - screen: displayOrder asc
 * - print: templateOrder[key] asc (없으면 +Infinity 후 displayOrder)
 */
const sortSpecs = (
  specs: ProductSpec[],
  mode: ProductSpecListMode,
  templateOrder?: Record<string, number>,
): ProductSpec[] => {
  const arr = [...specs]
  if (mode === 'print' && templateOrder) {
    arr.sort((a, b) => {
      const aOrder = templateOrder[a.specKey] ?? Number.POSITIVE_INFINITY
      const bOrder = templateOrder[b.specKey] ?? Number.POSITIVE_INFINITY
      if (aOrder !== bOrder) return aOrder - bOrder
      return a.displayOrder - b.displayOrder
    })
  } else {
    arr.sort((a, b) => a.displayOrder - b.displayOrder)
  }
  return arr
}

/**
 * ProductSpecList — ProductSpec 동적 목록 표시 (3 layout × 2 sort mode).
 *
 * @param props specs / mode / layout
 * @example
 * ```tsx
 * <ProductSpecList
 *   specs={[
 *     { specKey: '냉방성능', specValue: '5.6', unit: 'kW', displayOrder: 1 },
 *     { specKey: '전원', specValue: '220V/60Hz', displayOrder: 2 },
 *   ]}
 *   mode="screen"
 *   layout="table"
 * />
 * ```
 */
export function ProductSpecList({
  specs,
  mode = 'screen',
  templateOrder,
  layout = 'table',
  emptyMessage = '스펙 정보 없음',
  className,
}: ProductSpecListProps) {
  const sorted = useMemo(
    () => sortSpecs(specs, mode, templateOrder),
    [specs, mode, templateOrder],
  )

  const wrapperClasses = [styles['wrapper'], styles[`layout-${layout}`], className]
    .filter(Boolean)
    .join(' ')

  if (sorted.length === 0) {
    return <div className={wrapperClasses}><span className={styles['empty']}>{emptyMessage}</span></div>
  }

  if (layout === 'inline') {
    return (
      <span className={wrapperClasses} data-mode={mode}>
        {sorted.map((s, i) => (
          <span key={`${s.specKey}-${i}`} className={styles['inlineItem']}>
            <span className={styles['inlineKey']}>{s.specKey}</span>
            <span className={styles['inlineValue']}>{composeValue(s.specValue, s.unit)}</span>
          </span>
        ))}
      </span>
    )
  }

  if (layout === 'card') {
    return (
      <dl className={wrapperClasses} data-mode={mode}>
        {sorted.map((s, i) => (
          <div key={`${s.specKey}-${i}`} className={styles['cardRow']}>
            <dt className={styles['cardKey']}>{s.specKey}</dt>
            <dd className={styles['cardValue']}>{composeValue(s.specValue, s.unit)}</dd>
          </div>
        ))}
      </dl>
    )
  }

  // layout === 'table'
  return (
    <table className={wrapperClasses} data-mode={mode}>
      <tbody>
        {sorted.map((s, i) => (
          <tr key={`${s.specKey}-${i}`} className={styles['tableRow']}>
            <th scope="row" className={styles['tableKey']}>
              {s.specKey}
            </th>
            <td className={styles['tableValue']}>
              {composeValue(s.specValue, s.unit)}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export default ProductSpecList
