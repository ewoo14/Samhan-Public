/**
 * BatchStatusCell — LinkDispatchListPage 표 6열 (SMS 발송완료) 전용 셀.
 *
 * notification-slice-B Designer wireframes.md § 1 + components.md § BatchStatusCell.
 *
 * 표시 분기:
 * - smsSentAt === null → primary 버튼 [SMS 발송] (행 클릭 이벤트는 stopPropagation)
 * - smsSentAt !== null → ☑ 아이콘 + HH:mm + ghost 링크 [재발송]
 *
 * 본 컴포넌트는 LinkDispatchListPage 전용 (도메인 결합 강함) 이라 디자인 시스템 외부 위치.
 */
import { Button } from '@samhan/design-system'
import type { MouseEvent } from 'react'

export interface BatchStatusCellProps {
  /** SMS 발송 시각 (ISO) — null 이면 미발송. */
  smsSentAt: string | null
  /** [SMS 발송] / [재발송] 버튼 클릭 핸들러. */
  onSendClick: () => void
}

/**
 * "2026-05-04T14:32:18+09:00" → "14:32" — DispatchView 와 동일 규칙.
 */
function formatHHmm(iso: string): string {
  return iso.slice(11, 16)
}

export function BatchStatusCell({ smsSentAt, onSendClick }: BatchStatusCellProps) {
  // 행 클릭으로 모달이 열리지 않도록 propagation 차단.
  const handleClick = (e: MouseEvent<HTMLButtonElement>) => {
    e.stopPropagation()
    onSendClick()
  }

  if (smsSentAt) {
    return (
      <span className="batch-status-sent">
        <span aria-hidden="true" className="batch-status-icon">☑</span>
        <span className="batch-status-time">{formatHHmm(smsSentAt)}</span>
        <Button variant="ghost" size="sm" onClick={handleClick} aria-label="재발송">
          재발송
        </Button>
      </span>
    )
  }

  return (
    <span className="batch-status-unsent">
      <Button variant="primary" size="sm" onClick={handleClick}>
        SMS 발송
      </Button>
    </span>
  )
}
