/**
 * `<DragHandle>` — sales-form-polish 슬라이스 신규 컴포넌트.
 *
 * Designer `components.md` § 3 spec 충실 반영:
 * - 24px × 40px 영역의 ⠿ Braille pattern dots-12345678 (한국어 폰트 fallback 안전)
 * - cursor: grab → grabbing 전환
 * - `@dnd-kit/sortable` 의 listeners + attributes 그대로 부착
 * - 키보드 접근성을 위한 setActivatorNodeRef 노출 (dnd-kit 권장 패턴)
 * - hover 시 색 변화 (`--ink-tertiary` → `--ink-secondary`)
 *
 * 본 컴포넌트는 `@dnd-kit/core` 의존성을 가지지 않는다 — caller (SlipFormPage 등)
 * 가 useSortable() 결과를 풀어서 props 로 전달. 이를 통해 design-system 패키지가
 * dnd-kit 에 결합되지 않고 독립 유지된다.
 */
import { forwardRef, type ButtonHTMLAttributes } from 'react'
import styles from './DragHandle.module.css'

export interface DragHandleProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children' | 'aria-label'> {
  /** dnd-kit useSortable() 의 listeners. */
  listeners?: Record<string, unknown> | undefined
  /** dnd-kit useSortable() 의 attributes (role, aria-roledescription 등). */
  attributes?: Record<string, unknown>
  /** dnd-kit useSortable() 의 setActivatorNodeRef — 키보드 sensor 활성화용. */
  setActivatorNodeRef?: (node: HTMLElement | null) => void
  /** ARIA 라벨 — 라인 번호 기반. 예: "라인 3 드래그". */
  label: string
  /** 현재 drag 진행 중 — cursor 변경. */
  dragging?: boolean
}

/**
 * Drag handle button — `⠿` 6-dot Braille glyph.
 *
 * @param listeners dnd-kit listeners (없으면 noop — 본 핸들 자체가 비활성)
 * @param attributes dnd-kit attributes (role, aria 등)
 * @param setActivatorNodeRef 키보드 활성 ref
 * @param label ARIA 라벨
 * @param dragging drag 진행 중 여부 (cursor + 색상 변화)
 */
export const DragHandle = forwardRef<HTMLButtonElement, DragHandleProps>(
  function DragHandle(
    {
      listeners,
      attributes,
      setActivatorNodeRef,
      label,
      dragging = false,
      className,
      ...rest
    },
    _ref,
  ) {
    const classes = [styles['dragHandle'], dragging ? styles['dragging'] : null, className]
      .filter(Boolean)
      .join(' ')

    return (
      <button
        type="button"
        ref={setActivatorNodeRef ?? null}
        className={classes}
        aria-label={label}
        {...attributes}
        {...listeners}
        {...rest}
      >
        <span aria-hidden="true">⠿</span>
      </button>
    )
  },
)

export default DragHandle
