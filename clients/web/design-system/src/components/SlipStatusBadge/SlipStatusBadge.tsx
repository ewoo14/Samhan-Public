import { forwardRef, type HTMLAttributes } from 'react'
import styles from './SlipStatusBadge.module.css'

/**
 * 전표 상태 코드 (Plan §3.1 — 9단계 라이프사이클 + 분기 2종).
 *
 * 9단계 정상 흐름:
 * 1. `DRAFT`      작성중 — 사용자가 입력 중인 임시 저장 단계
 * 2. `SAVED`      저장완료 — 작성자가 저장만 한 상태 (전송 전)
 * 3. `SENT`       전송완료 — 거래처/내부 처리 담당자에게 전송됨
 * 4. `ACCEPTED`   수락 — 수신자가 접수 확인. 이 시점부터 작성자 수정 잠금
 * 5. `PROCESSING` 처리중 — 출고/입고 작업이 실제로 진행 중
 * 6. `COMPLETED`  처리완료 — 출고/입고 작업 종료. 배송 단계 진입 직전
 * 7. `SHIPPING`   배송중 — 운송 중 상태
 * 8. `DELIVERED`  배송완료 — 수령자가 물품을 받은 상태
 * 9. `CONFIRMED`  확정 — 회계 확정. 더 이상 변경 불가
 *
 * 분기 (정상 흐름에서 빠져나가는 종결 상태):
 * - `REJECTED` 반려 — 수신자가 접수 거부 (작성자 재작성 필요)
 * - `CANCELED` 취소 — 작성자/관리자가 취소 처리
 */
export type SlipStatus =
  | 'DRAFT'
  | 'SAVED'
  | 'SENT'
  | 'ACCEPTED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'CONFIRMED'
  | 'REJECTED'
  | 'CANCELED'

export interface SlipStatusBadgeProps
  extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  /** 전표 상태 코드. */
  status: SlipStatus
  /**
   * 텍스트 옆에 단계 번호 표시 (1~9).
   * 분기(REJECTED/CANCELED) 는 단계 번호가 없어 항상 미표시.
   * 기본값 `false`.
   */
  showStep?: boolean
}

/**
 * 단계 번호 매핑 (1~9). 분기는 number 가 없으므로 `null`.
 *
 * @internal — `showStep` 옵션 렌더링용.
 */
const STEP_NUMBER: Record<SlipStatus, number | null> = {
  DRAFT: 1,
  SAVED: 2,
  SENT: 3,
  ACCEPTED: 4,
  PROCESSING: 5,
  COMPLETED: 6,
  SHIPPING: 7,
  DELIVERED: 8,
  CONFIRMED: 9,
  REJECTED: null,
  CANCELED: null,
}

/**
 * 한국어 표시 라벨 (사용자 노출 텍스트).
 *
 * @internal — Plan §3.1 표기와 동일.
 */
const STATUS_LABEL: Record<SlipStatus, string> = {
  DRAFT: '작성중',
  SAVED: '저장완료',
  SENT: '전송완료',
  ACCEPTED: '수락',
  PROCESSING: '처리중',
  COMPLETED: '처리완료',
  SHIPPING: '배송중',
  DELIVERED: '배송완료',
  CONFIRMED: '확정',
  REJECTED: '반려',
  CANCELED: '취소',
}

/**
 * 색상 그룹 분류:
 * - `editable`  편집 가능 단계 (DRAFT/SAVED/SENT) — 회색~파란색 계열
 * - `process`   처리 단계 (ACCEPTED/PROCESSING/COMPLETED) — 주황색 계열
 * - `delivery`  배송/완결 단계 (SHIPPING/DELIVERED/CONFIRMED) — 녹색 계열
 * - `rejected`  반려 — 빨간색
 * - `canceled`  취소 — 회색 + 취소선
 *
 * @internal — variantClass 매핑 키.
 */
type ColorGroup = 'editable' | 'process' | 'delivery' | 'rejected' | 'canceled'

const COLOR_GROUP: Record<SlipStatus, ColorGroup> = {
  DRAFT: 'editable',
  SAVED: 'editable',
  SENT: 'editable',
  ACCEPTED: 'process',
  PROCESSING: 'process',
  COMPLETED: 'process',
  SHIPPING: 'delivery',
  DELIVERED: 'delivery',
  CONFIRMED: 'delivery',
  REJECTED: 'rejected',
  CANCELED: 'canceled',
}

/**
 * 같은 그룹 내에서도 진행도에 따른 미세한 강도 조절을 위한 sub-tier (`-1`/`-2`/`-3`).
 *
 * @internal — CSS 클래스 suffix.
 */
const TIER: Record<SlipStatus, 1 | 2 | 3> = {
  DRAFT: 1,
  SAVED: 2,
  SENT: 3,
  ACCEPTED: 1,
  PROCESSING: 2,
  COMPLETED: 3,
  SHIPPING: 1,
  DELIVERED: 2,
  CONFIRMED: 3,
  REJECTED: 1,
  CANCELED: 1,
}

/**
 * SlipStatusBadge — 전표 9단계 + 분기 2종(11종) 상태 시각 구분 Badge.
 *
 * 색상 규약 (Plan §3.1 라이프사이클):
 * - 편집 가능 단계 (DRAFT/SAVED/SENT): 회색~파란색 계열
 * - 처리 단계 (ACCEPTED/PROCESSING/COMPLETED): 주황색 계열
 * - 완료 단계 (SHIPPING/DELIVERED/CONFIRMED): 녹색 계열
 * - REJECTED: 빨간색
 * - CANCELED: 회색 + 취소선
 *
 * 같은 그룹 내에서도 단계 진행도에 따라 채도 강도를 1→3 으로 점증시켜
 * 한눈에 진행 단계를 식별할 수 있다.
 *
 * @example
 * ```tsx
 * <SlipStatusBadge status="ACCEPTED" />          // "수락"
 * <SlipStatusBadge status="ACCEPTED" showStep /> // "4. 수락"
 * ```
 */
export const SlipStatusBadge = forwardRef<HTMLSpanElement, SlipStatusBadgeProps>(
  function SlipStatusBadge({ status, showStep = false, className, ...rest }, ref) {
    const group = COLOR_GROUP[status]
    const tier = TIER[status]
    const step = STEP_NUMBER[status]
    const label = STATUS_LABEL[status]

    const groupClass = styles[`group-${group}`]
    const tierClass = styles[`tier-${tier}`]

    const classes = [styles['badge'], groupClass, tierClass, className]
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
        {showStep && step !== null ? (
          <span className={styles['step']} aria-hidden="true">
            {step}.
          </span>
        ) : null}
        <span className={styles['label']}>{label}</span>
      </span>
    )
  },
)

export default SlipStatusBadge
