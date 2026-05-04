/**
 * SignaturePad — Canvas 기반 인수자 서명 캡처 컴포넌트.
 *
 * Slice C (signature-slice-C) 신규 — Designer `components.md` § 1 + `tokens.md` § 1.1
 * 충실 반영. signature_pad lib **미사용** (의존성 zero), 자체 vanilla canvas 구현.
 *
 * 핵심 동작:
 * - touch + mouse 통합 이벤트 (`pointerdown/move/up`) 사용 — iOS Safari / Android Chrome /
 *   desktop 동일 코드 경로. 단, iOS 14 미만 호환을 위해 fallback 으로 touch 이벤트도 바인딩.
 * - `passive: false` 로 등록하여 캔버스 영역에서 페이지 스크롤 차단 (`preventDefault`).
 * - `devicePixelRatio` 스케일 — retina 디스플레이에서 stroke 흐림 방지.
 * - `isEmpty()` 는 모든 alpha 채널 0 검사 — placeholder 활성/비활성 토글에 사용.
 * - `clear()` / `toDataURL()` / `toBlob()` 은 부모가 ref 로 호출 (`useImperativeHandle`).
 *
 * Props:
 * - width / height — 캔버스 표시 사이즈 (기본 320×200, 모바일 좁을 때 디폴트).
 * - onChange — `(isEmpty) => void` — 캔버스 내용 변경 시 호출 (제출 버튼 disabled 토글).
 * - disabled — true 면 pointer-events: none + opacity 0.6 (전송 중 lock).
 * - penColor — 펜 색 (`--signature-pen-color`, 기본 #000).
 * - penWidth — 펜 두께 px (기본 2.5).
 *
 * 데스크톱 SlipDetailPage 의 [무효화] flow 와는 무관 — 본 컴포넌트는 입력 캡처 전용.
 * 표시(read-only) 는 `<SignatureViewer>` 사용.
 *
 * UUID 비공개: 본 컴포넌트는 어떤 ID 도 노출하지 않는다 (canvas 영역만 렌더).
 */
import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import styles from './SignaturePad.module.css'

export interface SignaturePadProps {
  /** 캔버스 표시 너비 (px). 기본 320 (iPhone SE 호환). */
  width?: number
  /** 캔버스 표시 높이 (px). 기본 200. */
  height?: number
  /** 캔버스 내용 변경 시 호출 — 제출 버튼 disabled 토글 등에 사용. */
  onChange?: (isEmpty: boolean) => void
  /** true 면 입력 차단 (전송 중 lock). */
  disabled?: boolean
  /** 펜 색상. 기본 #000. */
  penColor?: string
  /** 펜 두께 px. 기본 2.5. */
  penWidth?: number
  /** placeholder 텍스트. 기본 "여기에 서명해주세요". */
  placeholder?: string
  /** 추가 className (조립용). */
  className?: string
  /** 접근성 ARIA 라벨. 기본 "서명 입력 영역". */
  'aria-label'?: string
}

/**
 * SignaturePad ref 로 노출되는 외부 API.
 *
 * 부모 컴포넌트가 캔버스 내용을 직접 비우거나 PNG / Blob 으로 추출할 때 사용.
 */
export interface SignaturePadHandle {
  /** 캔버스 비우기 + onChange(true) 콜백 호출. */
  clear: () => void
  /** 비어있는지 검사 (alpha 채널 검사). */
  isEmpty: () => boolean
  /** PNG dataURL ("data:image/png;base64,...") 반환. 빈 캔버스도 PNG 자체는 반환됨. */
  toDataURL: () => string
  /** PNG Blob 반환 (Promise). API 업로드 시 multipart 등에 사용. */
  toBlob: () => Promise<Blob | null>
}

export const SignaturePad = forwardRef<SignaturePadHandle, SignaturePadProps>(
  function SignaturePad(
    {
      width = 320,
      height = 200,
      onChange,
      disabled,
      penColor = '#000000',
      penWidth = 2.5,
      placeholder = '여기에 서명해주세요',
      className,
      'aria-label': ariaLabel = '서명 입력 영역',
    },
    ref,
  ) {
    const canvasRef = useRef<HTMLCanvasElement | null>(null)
    const drawingRef = useRef<boolean>(false)
    const lastPosRef = useRef<{ x: number; y: number } | null>(null)
    const [empty, setEmpty] = useState<boolean>(true)

    /**
     * 캔버스 초기화 — devicePixelRatio 스케일 + 흰 배경.
     *
     * width/height props 가 변하면 다시 호출. 내용은 보존되지 않으므로 호출 전 외부에서
     * `clear()` 로 빈 상태를 전제로 한다.
     */
    const initCanvas = useCallback(() => {
      const canvas = canvasRef.current
      if (!canvas) return
      const ratio = window.devicePixelRatio || 1
      canvas.width = Math.round(width * ratio)
      canvas.height = Math.round(height * ratio)
      canvas.style.width = `${width}px`
      canvas.style.height = `${height}px`
      const ctx = canvas.getContext('2d')
      if (!ctx) return
      ctx.scale(ratio, ratio)
      ctx.lineCap = 'round'
      ctx.lineJoin = 'round'
      ctx.strokeStyle = penColor
      ctx.lineWidth = penWidth
    }, [width, height, penColor, penWidth])

    useLayoutEffect(() => {
      initCanvas()
    }, [initCanvas])

    /** 화면 좌표 → 캔버스 좌표 변환 (boundingRect 기준). */
    const getPoint = useCallback(
      (e: PointerEvent | MouseEvent | Touch): { x: number; y: number } => {
        const canvas = canvasRef.current!
        const rect = canvas.getBoundingClientRect()
        return {
          x: e.clientX - rect.left,
          y: e.clientY - rect.top,
        }
      },
      [],
    )

    /** alpha 채널 검사 — 0 아닌 픽셀이 1개라도 있으면 false. */
    const isEmptyImpl = useCallback((): boolean => {
      const canvas = canvasRef.current
      if (!canvas) return true
      const ctx = canvas.getContext('2d')
      if (!ctx) return true
      const data = ctx.getImageData(0, 0, canvas.width, canvas.height).data
      for (let i = 3; i < data.length; i += 4) {
        if (data[i] !== 0) return false
      }
      return true
    }, [])

    /** stroke 시작. */
    const startStroke = useCallback(
      (clientX: number, clientY: number) => {
        if (disabled) return
        const canvas = canvasRef.current
        if (!canvas) return
        const ctx = canvas.getContext('2d')
        if (!ctx) return
        const rect = canvas.getBoundingClientRect()
        const x = clientX - rect.left
        const y = clientY - rect.top
        drawingRef.current = true
        lastPosRef.current = { x, y }
        ctx.beginPath()
        ctx.moveTo(x, y)
      },
      [disabled],
    )

    /** stroke 진행. */
    const moveStroke = useCallback(
      (clientX: number, clientY: number) => {
        if (!drawingRef.current || disabled) return
        const canvas = canvasRef.current
        if (!canvas) return
        const ctx = canvas.getContext('2d')
        if (!ctx) return
        const rect = canvas.getBoundingClientRect()
        const x = clientX - rect.left
        const y = clientY - rect.top
        const last = lastPosRef.current
        if (!last) return
        // 부드러운 곡선을 위해 quadraticCurve (이전 점 → 중간점)
        const midX = (last.x + x) / 2
        const midY = (last.y + y) / 2
        ctx.quadraticCurveTo(last.x, last.y, midX, midY)
        ctx.stroke()
        lastPosRef.current = { x, y }
      },
      [disabled],
    )

    /** stroke 종료 — empty 상태 재계산 + onChange 호출. */
    const endStroke = useCallback(() => {
      if (!drawingRef.current) return
      drawingRef.current = false
      lastPosRef.current = null
      const canvas = canvasRef.current
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      if (ctx) ctx.closePath()
      // empty 상태 재계산
      const nowEmpty = isEmptyImpl()
      if (nowEmpty !== empty) {
        setEmpty(nowEmpty)
        onChange?.(nowEmpty)
      } else if (!nowEmpty && empty) {
        // 첫 stroke 직후
        setEmpty(false)
        onChange?.(false)
      }
    }, [empty, isEmptyImpl, onChange])

    /**
     * 이벤트 바인딩 — passive: false 강제 (touch 이벤트 페이지 스크롤 차단).
     *
     * pointer 이벤트 우선 + touch fallback (iOS 13 미만 호환).
     */
    useEffect(() => {
      const canvas = canvasRef.current
      if (!canvas) return

      const onPointerDown = (e: PointerEvent) => {
        if (disabled) return
        e.preventDefault()
        canvas.setPointerCapture(e.pointerId)
        startStroke(e.clientX, e.clientY)
      }
      const onPointerMove = (e: PointerEvent) => {
        if (!drawingRef.current) return
        e.preventDefault()
        moveStroke(e.clientX, e.clientY)
      }
      const onPointerUp = (e: PointerEvent) => {
        if (!drawingRef.current) return
        e.preventDefault()
        try {
          canvas.releasePointerCapture(e.pointerId)
        } catch {
          // ignored — 일부 브라우저에서 capture 실패 시 throw
        }
        endStroke()
      }

      // touch fallback (pointer 이벤트 미지원 브라우저)
      const onTouchStart = (e: TouchEvent) => {
        if (disabled) return
        e.preventDefault()
        const t = e.touches[0]
        if (!t) return
        startStroke(t.clientX, t.clientY)
      }
      const onTouchMove = (e: TouchEvent) => {
        if (!drawingRef.current) return
        e.preventDefault()
        const t = e.touches[0]
        if (!t) return
        moveStroke(t.clientX, t.clientY)
      }
      const onTouchEnd = (e: TouchEvent) => {
        if (!drawingRef.current) return
        e.preventDefault()
        endStroke()
      }

      const supportsPointer = typeof window !== 'undefined' && 'PointerEvent' in window
      if (supportsPointer) {
        canvas.addEventListener('pointerdown', onPointerDown, { passive: false })
        canvas.addEventListener('pointermove', onPointerMove, { passive: false })
        canvas.addEventListener('pointerup', onPointerUp, { passive: false })
        canvas.addEventListener('pointercancel', onPointerUp, { passive: false })
      } else {
        canvas.addEventListener('touchstart', onTouchStart, { passive: false })
        canvas.addEventListener('touchmove', onTouchMove, { passive: false })
        canvas.addEventListener('touchend', onTouchEnd, { passive: false })
        canvas.addEventListener('touchcancel', onTouchEnd, { passive: false })
      }

      return () => {
        if (supportsPointer) {
          canvas.removeEventListener('pointerdown', onPointerDown)
          canvas.removeEventListener('pointermove', onPointerMove)
          canvas.removeEventListener('pointerup', onPointerUp)
          canvas.removeEventListener('pointercancel', onPointerUp)
        } else {
          canvas.removeEventListener('touchstart', onTouchStart)
          canvas.removeEventListener('touchmove', onTouchMove)
          canvas.removeEventListener('touchend', onTouchEnd)
          canvas.removeEventListener('touchcancel', onTouchEnd)
        }
      }
    }, [disabled, startStroke, moveStroke, endStroke])

    /** 외부 ref API — clear / isEmpty / toDataURL / toBlob. */
    useImperativeHandle(
      ref,
      () => ({
        clear: () => {
          const canvas = canvasRef.current
          if (!canvas) return
          const ctx = canvas.getContext('2d')
          if (!ctx) return
          // setTransform 후 clearRect — devicePixelRatio scale 누적 방지
          ctx.save()
          ctx.setTransform(1, 0, 0, 1, 0, 0)
          ctx.clearRect(0, 0, canvas.width, canvas.height)
          ctx.restore()
          setEmpty(true)
          onChange?.(true)
        },
        isEmpty: isEmptyImpl,
        toDataURL: () => {
          const canvas = canvasRef.current
          if (!canvas) return ''
          return canvas.toDataURL('image/png')
        },
        toBlob: () =>
          new Promise<Blob | null>((resolve) => {
            const canvas = canvasRef.current
            if (!canvas) {
              resolve(null)
              return
            }
            canvas.toBlob((b) => resolve(b), 'image/png')
          }),
      }),
      [isEmptyImpl, onChange],
    )

    const wrapClass = [
      styles['wrapper'],
      empty ? styles['is-empty'] : styles['is-filled'],
      disabled ? styles['is-disabled'] : null,
      className,
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <div
        className={wrapClass}
        data-placeholder={placeholder}
        style={{ width, height }}
      >
        <canvas
          ref={canvasRef}
          className={styles['canvas']}
          role="img"
          aria-label={ariaLabel}
          // touch-action: none — Chrome 의 default scroll/zoom 차단 (CSS 와 중복 방지용 inline 추가)
          style={{ touchAction: 'none' }}
        />
      </div>
    )
  },
)

export default SignaturePad
