import { forwardRef, type HTMLAttributes } from 'react'
import styles from './SlipNumberDisplay.module.css'

export interface SlipNumberDisplayProps
  extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  /** ISO `yyyy-MM-dd` 형식 전표 일자. */
  slipDate: string
  /** 해당 날짜 내 순번 (1부터 시작). */
  seqNo: number
  /**
   * 강조 사이즈.
   * - `sm` 목록/표 셀에서 사용
   * - `md` (기본) 일반 본문
   * - `lg` 상세 페이지 헤더
   */
  size?: 'sm' | 'md' | 'lg'
}

/**
 * ISO `yyyy-MM-dd` 를 사용자 표시 형식 `YYYY/MM/DD` 로 변환.
 * 잘못된 입력은 그대로 반환 (방어적).
 *
 * @internal — 출력 렌더 helper.
 */
function formatDate(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!m) return iso
  return `${m[1]}/${m[2]}/${m[3]}`
}

/**
 * SlipNumberDisplay — 전표 번호 표시 컴포넌트.
 *
 * Plan §3.1 표시 형식 `YYYY/MM/DD - {seq}` 을 일관되게 적용한다.
 *
 * UUID 비공개 가드 (`feedback_uuid_no_user_visibility.md`):
 * 본 컴포넌트는 사용자에게 노출되는 비즈니스 식별자만 표시한다. 전표 UUID
 * 같은 내부 식별자는 prop 으로 받지 않으며 화면 어디에도 노출하지 않는다.
 *
 * monospace 글꼴 + tabular-nums 로 목록에서 자릿수가 흔들리지 않도록 정렬.
 *
 * @example
 * ```tsx
 * <SlipNumberDisplay slipDate="2026-05-04" seqNo={7} />
 * // -> "2026/05/04 - 7"
 *
 * <SlipNumberDisplay slipDate="2026-05-04" seqNo={7} size="lg" />
 * ```
 */
export const SlipNumberDisplay = forwardRef<HTMLSpanElement, SlipNumberDisplayProps>(
  function SlipNumberDisplay(
    { slipDate, seqNo, size = 'md', className, ...rest },
    ref,
  ) {
    const sizeClass = styles[`size-${size}`]
    const classes = [styles['number'], sizeClass, className]
      .filter(Boolean)
      .join(' ')

    const formatted = `${formatDate(slipDate)} - ${seqNo}`

    return (
      <span
        ref={ref}
        className={classes}
        data-slip-date={slipDate}
        data-seq-no={seqNo}
        {...rest}
      >
        {formatted}
      </span>
    )
  },
)

export default SlipNumberDisplay
