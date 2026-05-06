/**
 * CopyButton — 클립보드 복사 + "복사됨" 토스트 (3초) 컴포넌트.
 *
 * link-dispatch-slice (LinkDispatchListPage / BatchDetailModal) 의 e-sign URL
 * 복사 등에 사용된다.
 *
 * 동작:
 * - `navigator.clipboard.writeText` 우선 사용 (보안 컨텍스트).
 * - 실패 시 (file:// 환경 또는 비보안 컨텍스트) `document.execCommand('copy')`
 *   폴백 — `<textarea>` 임시 생성 → 선택 → execCommand → 제거.
 * - 복사 성공 시 버튼 라벨 옆에 "복사됨" 토스트가 3초 노출.
 * - onCopy 콜백 (옵션) 으로 부모에서 추가 동작 가능 (analytics 등).
 *
 * Designer `components.md` § CopyButton spec 그대로 반영 (label / text / onCopy).
 *
 * UUID 비공개 가드: text 는 부모가 결정 (URL 등). 본 컴포넌트는 화면에 text 자체를
 * 노출하지 않으므로 (라벨만 표시), UUID 노출 책임은 부모에게 있다.
 */
import { forwardRef, useCallback, useEffect, useRef, useState } from 'react'
import styles from './CopyButton.module.css'

export interface CopyButtonProps {
  /** 클립보드에 복사할 문자열. */
  text: string
  /** 버튼 텍스트. 기본 "복사". */
  label?: string
  /** 복사 성공 시 호출되는 옵션 콜백. */
  onCopy?: (text: string) => void
  /** 비활성화 여부. */
  disabled?: boolean
  /** 토스트 노출 시간 (ms). 기본 3000. */
  toastDurationMs?: number
  /** ARIA 라벨 — 버튼 라벨이 아이콘만일 때 사용. */
  'aria-label'?: string
  /** 추가 className (조립용). */
  className?: string
}

/**
 * navigator.clipboard 가 가용하면 우선 사용, 아니면 execCommand 폴백.
 *
 * @return Promise<boolean> 성공 여부 (실패 시 false)
 */
async function copyToClipboard(text: string): Promise<boolean> {
  // 1) 보안 컨텍스트 + Clipboard API 가용 시
  try {
    if (typeof navigator !== 'undefined' && navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // fallthrough
  }
  // 2) 폴백 — textarea + execCommand
  if (typeof document === 'undefined') return false
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.style.position = 'fixed'
  textarea.style.opacity = '0'
  textarea.style.left = '-9999px'
  textarea.setAttribute('readonly', '')
  document.body.appendChild(textarea)
  try {
    textarea.select()
    textarea.setSelectionRange(0, text.length)
    const ok = document.execCommand('copy')
    return ok
  } catch {
    return false
  } finally {
    document.body.removeChild(textarea)
  }
}

export const CopyButton = forwardRef<HTMLButtonElement, CopyButtonProps>(function CopyButton(
  {
    text,
    label = '복사',
    onCopy,
    disabled,
    toastDurationMs = 3000,
    'aria-label': ariaLabel,
    className,
  },
  ref,
) {
  const [copied, setCopied] = useState(false)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // unmount 시 타이머 정리.
  useEffect(() => {
    return () => {
      if (timeoutRef.current) clearTimeout(timeoutRef.current)
    }
  }, [])

  const handleClick = useCallback(async () => {
    if (disabled) return
    const ok = await copyToClipboard(text)
    if (!ok) return
    setCopied(true)
    onCopy?.(text)
    if (timeoutRef.current) clearTimeout(timeoutRef.current)
    timeoutRef.current = setTimeout(() => setCopied(false), toastDurationMs)
  }, [disabled, onCopy, text, toastDurationMs])

  const buttonClasses = [styles['button'], className].filter(Boolean).join(' ')

  return (
    <span className={styles['wrapper']}>
      <button
        ref={ref}
        type="button"
        className={buttonClasses}
        onClick={() => void handleClick()}
        disabled={disabled}
        aria-label={ariaLabel ?? label}
      >
        {label}
      </button>
      {copied ? (
        <span className={styles['toast']} role="status" aria-live="polite">
          복사됨
        </span>
      ) : null}
    </span>
  )
})

export default CopyButton
