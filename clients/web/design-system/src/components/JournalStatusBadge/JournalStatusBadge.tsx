import { forwardRef, type HTMLAttributes } from 'react'
import styles from './JournalStatusBadge.module.css'

/**
 * 분개 상태 코드 (accounting-slice-A — 회계 첫 슬라이스).
 *
 * 3단계 라이프사이클:
 * 1. `DRAFT`    임시저장 — 작성자가 입력 중인 분개. 마감 미반영, 자유롭게 수정 가능.
 * 2. `POSTED`   확정 — 분개가 원장에 반영됨. 수정 불가, 시산표 합산 대상.
 * 3. `REVERSED` 역분개 — 확정된 분개가 역분개로 무효화됨. 원본은 보관, 대응 분개 한 건 추가.
 *
 * SlipStatusBadge 패턴과 일치하도록 색상 그룹을 정의:
 * - DRAFT    회색 (편집 가능)
 * - POSTED   녹색 (확정/완결)
 * - REVERSED 회색 + 취소선 (취소 종결)
 */
export type JournalStatus = 'DRAFT' | 'POSTED' | 'REVERSED'

export interface JournalStatusBadgeProps
  extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  /** 분개 상태 코드. */
  status: JournalStatus
}

/**
 * 한국어 표시 라벨 (사용자 노출 텍스트).
 * @internal
 */
const STATUS_LABEL: Record<JournalStatus, string> = {
  DRAFT: '임시저장',
  POSTED: '확정',
  REVERSED: '역분개',
}

/**
 * 색상 그룹 분류:
 * - `editable` 편집 가능 (DRAFT) — 회색
 * - `posted`   확정 (POSTED) — 녹색
 * - `reversed` 역분개 (REVERSED) — 회색 + 취소선
 *
 * @internal
 */
type ColorGroup = 'editable' | 'posted' | 'reversed'

const COLOR_GROUP: Record<JournalStatus, ColorGroup> = {
  DRAFT: 'editable',
  POSTED: 'posted',
  REVERSED: 'reversed',
}

/**
 * JournalStatusBadge — 분개 3종 상태 시각 구분 Badge.
 *
 * SlipStatusBadge 와 동일한 pill 모양 + 색상 의미를 사용해 UI 일관성을 유지한다.
 *
 * @example
 * ```tsx
 * <JournalStatusBadge status="DRAFT" />     // "임시저장" (회색)
 * <JournalStatusBadge status="POSTED" />    // "확정" (녹색)
 * <JournalStatusBadge status="REVERSED" />  // "역분개" (회색 + 취소선)
 * ```
 */
export const JournalStatusBadge = forwardRef<
  HTMLSpanElement,
  JournalStatusBadgeProps
>(function JournalStatusBadge({ status, className, ...rest }, ref) {
  const group = COLOR_GROUP[status]
  const label = STATUS_LABEL[status]

  const classes = [styles['badge'], styles[`group-${group}`], className]
    .filter(Boolean)
    .join(' ')

  return (
    <span
      ref={ref}
      className={classes}
      data-status={status}
      data-color-group={group}
      {...rest}
    >
      {label}
    </span>
  )
})

export default JournalStatusBadge
