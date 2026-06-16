import { useEffect, useId, useMemo, useState } from 'react'
import styles from './SpecAddModal.module.css'
import { Modal } from '../Modal/Modal'
import { Button } from '../Button/Button'
import { Input } from '../Input/Input'
import { Label } from '../Label/Label'
import type { EstimateCategory } from '../CategoryTabs/CategoryTabs'

/**
 * 스펙 추가 모달 — 추천 키 chip + 자유 입력.
 *
 * Legacy migration 사전 작업 (DS 6 신규 컴포넌트 중 4번).
 *
 * DOMAIN-EXTENSIONS §4 D15 — 409 strict + Frontend disabled 가드:
 * - 추천 키 chip 중 `existingKeys` 와 중복되는 키는 **disabled** 표시 (선택 차단)
 * - 자유 입력으로 중복 키 입력 시 inline 경고 + "추가" 버튼 disabled
 * - Backend 는 unique constraint (productMasterId, specKey) → 409 Conflict 반환 (strict)
 *
 * 사용처:
 * - product-service admin 의 ProductSpecEditor 에서 [+ 스펙 추가] 버튼 클릭 시 오픈
 * - estimateCategory 별로 SpecKeyTemplate 의 isRecommended=TRUE 키들을 chip 으로 표시
 *
 * 출처: `migration/analysis/06-frontend-design.md` §3.2 / DOMAIN-EXTENSIONS §4 D15
 */
export interface SpecKeyTemplate {
  /** 표준 specKey */
  specKey: string
  /** 단위 default (선택). */
  defaultUnit?: string | null
  /** 추천 표시 순서. */
  displayOrder: number
  /** 추천 여부 — true 인 키만 chip 으로 표시. */
  isRecommended: boolean
}

export interface SpecAddModalProps {
  /** 모달 open 상태 */
  open: boolean
  /** 모달 닫기 콜백 */
  onClose: () => void
  /**
   * 견적 카테고리 — title 표시 + 추천 키 필터 안내 용.
   * (실제 추천 키 목록은 `recommended` prop 으로 따로 받음 — backend 에서 카테고리 조회 후 주입)
   */
  category: EstimateCategory
  /** 추천 키 목록 (SpecKeyTemplate 의 isRecommended=true 만). */
  recommended: SpecKeyTemplate[]
  /** 이미 등록된 specKey 목록 (중복 가드 용). */
  existingKeys: string[]
  /** 추가 버튼 클릭 시 호출. */
  onAdd: (specKey: string, specValue: string, unit?: string) => void
}

const CATEGORY_LABEL: Record<EstimateCategory, string> = {
  HOME_MULTI: '홈멀티',
  SINGLE_SET: '싱글중대형',
  COMMERCIAL_MULTI: '상업멀티',
  LEGACY: '구형',
  OTHER: '기타',
}

/**
 * SpecAddModal — 추천 키 chip + 자유 입력으로 ProductSpec 1건 추가.
 *
 * @param props open / category / recommended / existingKeys / onAdd
 * @example
 * ```tsx
 * <SpecAddModal
 *   open={open}
 *   onClose={() => setOpen(false)}
 *   category="HOME_MULTI"
 *   recommended={recommendedKeys}
 *   existingKeys={specs.map(s => s.specKey)}
 *   onAdd={(key, val, unit) => api.addSpec(productId, key, val, unit)}
 * />
 * ```
 */
export function SpecAddModal({
  open,
  onClose,
  category,
  recommended,
  existingKeys,
  onAdd,
}: SpecAddModalProps) {
  const reactId = useId()
  const keyId = `sam-key-${reactId}`
  const valueId = `sam-value-${reactId}`
  const unitId = `sam-unit-${reactId}`

  const [specKey, setSpecKey] = useState('')
  const [specValue, setSpecValue] = useState('')
  const [unit, setUnit] = useState('')

  // 모달 닫힐 때 입력 초기화
  useEffect(() => {
    if (!open) {
      setSpecKey('')
      setSpecValue('')
      setUnit('')
    }
  }, [open])

  const existingSet = useMemo(() => new Set(existingKeys), [existingKeys])

  const sortedChips = useMemo(
    () =>
      [...recommended]
        .filter((t) => t.isRecommended)
        .sort((a, b) => a.displayOrder - b.displayOrder),
    [recommended],
  )

  const trimmedKey = specKey.trim()
  const trimmedValue = specValue.trim()
  const trimmedUnit = unit.trim()

  const isDuplicate = trimmedKey.length > 0 && existingSet.has(trimmedKey)
  const canSubmit = trimmedKey.length > 0 && trimmedValue.length > 0 && !isDuplicate

  const handleChipSelect = (tpl: SpecKeyTemplate) => {
    if (existingSet.has(tpl.specKey)) return
    setSpecKey(tpl.specKey)
    if (tpl.defaultUnit && !unit.trim()) {
      setUnit(tpl.defaultUnit)
    }
  }

  const handleSubmit = () => {
    if (!canSubmit) return
    onAdd(trimmedKey, trimmedValue, trimmedUnit || undefined)
    onClose()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`스펙 추가 — ${CATEGORY_LABEL[category]}`}
      description="추천 키를 선택하거나 자유 입력하세요. 이미 등록된 키는 비활성됩니다."
      size="md"
      footer={
        <div className={styles['footer']}>
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button variant="primary" onClick={handleSubmit} disabled={!canSubmit}>
            추가
          </Button>
        </div>
      }
    >
      <div className={styles['body']}>
        {/* 추천 키 chip */}
        <section className={styles['section']}>
          <div className={styles['sectionLabel']}>추천 스펙 키</div>
          {sortedChips.length === 0 ? (
            <span className={styles['empty']}>해당 카테고리의 추천 키가 없습니다.</span>
          ) : (
            <div className={styles['chipGroup']} role="group" aria-label="추천 스펙 키">
              {sortedChips.map((tpl) => {
                const isExisting = existingSet.has(tpl.specKey)
                const isSelected = specKey === tpl.specKey
                const chipClasses = [
                  styles['chip'],
                  isSelected ? styles['chipSelected'] : null,
                  isExisting ? styles['chipDisabled'] : null,
                ]
                  .filter(Boolean)
                  .join(' ')
                return (
                  <button
                    key={tpl.specKey}
                    type="button"
                    className={chipClasses}
                    onClick={() => handleChipSelect(tpl)}
                    disabled={isExisting}
                    aria-pressed={isSelected}
                    title={
                      isExisting
                        ? `이미 등록된 키입니다 (${tpl.specKey})`
                        : tpl.defaultUnit
                          ? `${tpl.specKey} (단위: ${tpl.defaultUnit})`
                          : tpl.specKey
                    }
                  >
                    {tpl.specKey}
                    {tpl.defaultUnit ? (
                      <span className={styles['chipUnit']}>{tpl.defaultUnit}</span>
                    ) : null}
                  </button>
                )
              })}
            </div>
          )}
        </section>

        {/* 입력 */}
        <section className={styles['section']}>
          <div className={styles['inputRow']}>
            <div className={styles['inputCell']}>
              <Label htmlFor={keyId} required>
                스펙 키
              </Label>
              <Input
                id={keyId}
                value={specKey}
                onChange={(e) => setSpecKey(e.target.value)}
                placeholder="예: 냉방성능"
                aria-invalid={isDuplicate || undefined}
                aria-describedby={isDuplicate ? `${keyId}-err` : undefined}
              />
              {isDuplicate ? (
                <span id={`${keyId}-err`} className={styles['error']} role="alert">
                  이미 등록된 키입니다.
                </span>
              ) : null}
            </div>

            <div className={styles['inputCell']}>
              <Label htmlFor={valueId} required>
                값
              </Label>
              <Input
                id={valueId}
                value={specValue}
                onChange={(e) => setSpecValue(e.target.value)}
                placeholder="예: 5.6"
              />
            </div>

            <div className={styles['inputCell']}>
              <Label htmlFor={unitId}>단위</Label>
              <Input
                id={unitId}
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                placeholder="예: kW"
              />
            </div>
          </div>
        </section>
      </div>
    </Modal>
  )
}

export default SpecAddModal
