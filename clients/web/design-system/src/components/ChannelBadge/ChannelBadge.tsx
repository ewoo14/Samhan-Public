import { forwardRef, type HTMLAttributes } from 'react'
import styles from './ChannelBadge.module.css'

/**
 * 알림 채널 코드 — notification-service 의 3 채널 (PUSH / EMAIL / SMS) 시각 구분.
 *
 * - `PUSH`  — FCM / APNs 푸시 알림 (Google Blue `#4285f4`)
 * - `EMAIL` — AWS SES 이메일 (Google Red `#ea4335`)
 * - `SMS`   — Aligo / Solapi 단문 (Google Green `#34a853`)
 */
export type ChannelType = 'PUSH' | 'EMAIL' | 'SMS'

/**
 * Badge size — 기본 12px / small 11px (QA HTML inline 일관용).
 */
export type ChannelBadgeSize = 'md' | 'sm'

export interface ChannelBadgeProps
  extends Omit<HTMLAttributes<HTMLSpanElement>, 'children'> {
  /** 알림 채널 코드 (PUSH / EMAIL / SMS) — 대소문자 구분. */
  channel: ChannelType
  /** Badge size — `md` (12px, 기본) / `sm` (11px, QA HTML matrix 호환). */
  size?: ChannelBadgeSize
  /** 표시 텍스트 override (default = channel 대문자). */
  label?: string
}

/**
 * 채널별 한국어 라벨 (사용자 노출 텍스트). 기본은 channel 대문자 그대로 표시하되,
 * `label` prop 으로 override 가능.
 *
 * @internal
 */
const CHANNEL_LABEL: Record<ChannelType, string> = {
  PUSH: 'PUSH',
  EMAIL: 'EMAIL',
  SMS: 'SMS',
}

/**
 * ChannelBadge — 알림 채널 3종 (PUSH / EMAIL / SMS) 시각 구분 Badge.
 *
 * 본 컴포넌트는 `tokens.css` 의 `.b-channel-*` utility class 와 동일한 색상 토큰 (CSS variable
 * `--color-channel-*`) 을 사용하여 React / 비-React 양쪽 환경에서 색상 일관 보장.
 *
 * Designer D-W4-2 + FE-W4-1/2/3 통합 채택 — PR #94 W4 후속 fix.
 *
 * @example
 * ```tsx
 * <ChannelBadge channel="PUSH" />            // 12px (기본)
 * <ChannelBadge channel="EMAIL" size="sm" /> // 11px (QA HTML 호환)
 * <ChannelBadge channel="SMS" label="문자" />  // 라벨 override
 * ```
 */
export const ChannelBadge = forwardRef<HTMLSpanElement, ChannelBadgeProps>(
  function ChannelBadge({ channel, size = 'md', label, className, ...rest }, ref) {
    const channelClass = styles[`channel-${channel.toLowerCase()}`]
    const sizeClass = styles[`size-${size}`]

    const classes = [styles['badge'], channelClass, sizeClass, className]
      .filter(Boolean)
      .join(' ')

    return (
      <span
        ref={ref}
        className={classes}
        data-channel={channel}
        data-size={size}
        {...rest}
      >
        {label ?? CHANNEL_LABEL[channel]}
      </span>
    )
  },
)

export default ChannelBadge
