/**
 * MobileSignaturePage — `/mobile/d/:token/s/:slipNo` (signature-slice-C 모바일 mock).
 *
 * Slice C2-UX (2026-05-05) — 2-step 흐름 + 캔버스 fullscreen UX.
 * - 1단계: **기사 서명** (POST `/driver-signature`)
 * - 2단계: **인수자 서명** (인수자명 입력 + POST `/signature`) → share 페이지 이동
 * - 캔버스 클릭 시 자동 전체화면 → 하단 [다시 서명] / [완료] 버튼 → 닫으면 서명 보존
 *
 * UUID 비공개: URL 은 `{token}/{slipNo}` 만. 응답값도 비즈니스 식별자만.
 */
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, SignaturePad, type SignaturePadHandle } from '@samhan/design-system'
import { recordDriverSignature, recordSignature } from '../api/signature'

type Stage = 'driver' | 'recipient'

/** Canvas 사이즈 — embed (innerWidth ≥375 이면 wide 400×200, 아니면 narrow 320×200) */
function pickEmbedSize(): { width: number; height: number } {
  if (typeof window === 'undefined') return { width: 320, height: 200 }
  return window.innerWidth >= 375
    ? { width: 400, height: 200 }
    : { width: 320, height: 200 }
}

/** Canvas 사이즈 — fullscreen (viewport 의 ~90% × 60%) */
function pickFullscreenSize(): { width: number; height: number } {
  if (typeof window === 'undefined') return { width: 320, height: 400 }
  return {
    width: Math.floor(window.innerWidth * 0.92),
    height: Math.floor(window.innerHeight * 0.6),
  }
}

/** dataURL 의 base64 부분 → SHA-256 hex (Web Crypto API). */
async function sha256OfDataURL(dataURL: string): Promise<string> {
  const base64 = dataURL.split(',')[1] ?? ''
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  const buf = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export function MobileSignaturePage() {
  const params = useParams<{ token: string; slipNo: string }>()
  const navigate = useNavigate()
  const padRef = useRef<SignaturePadHandle>(null)
  const [stage, setStage] = useState<Stage>('driver')
  const [signerName, setSignerName] = useState('')
  const [empty, setEmpty] = useState(true)
  const [embedSize, setEmbedSize] = useState(pickEmbedSize())
  const [fullscreenSize, setFullscreenSize] = useState(pickFullscreenSize())
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const token = params.token ?? ''
  const slipNo = params.slipNo ?? ''
  const size = isFullscreen ? fullscreenSize : embedSize

  // 화면 크기 변경 (회전 등) 대응
  useEffect(() => {
    const onResize = () => {
      setEmbedSize(pickEmbedSize())
      setFullscreenSize(pickFullscreenSize())
    }
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  // fullscreen 진입/해제 시 body scroll 막기
  useEffect(() => {
    if (isFullscreen) {
      document.body.style.overflow = 'hidden'
      return () => { document.body.style.overflow = '' }
    }
    return undefined
  }, [isFullscreen])

  /** 단계별 submit 가능 여부 */
  const submitDisabled
    = empty
    || submitting
    || (stage === 'recipient' && signerName.trim().length === 0)

  /** [완료] 버튼 — 단계별 BE 호출. */
  const handleSubmit = async () => {
    const pad = padRef.current
    if (!pad) return
    const dataURL = pad.toDataURL()
    if (!dataURL) {
      setErrorMsg('캔버스 데이터를 가져올 수 없습니다.')
      return
    }
    setSubmitting(true)
    setErrorMsg(null)
    try {
      const hash = await sha256OfDataURL(dataURL)
      if (stage === 'driver') {
        await recordDriverSignature(token, slipNo, {
          signaturePngBase64: dataURL,
          clientHash: hash,
        })
        // 단계 전환 — 인수자 서명으로 진행
        pad.clear()
        setEmpty(true)
        setStage('recipient')
        setIsFullscreen(false)
        setSubmitting(false)
      } else {
        const res = await recordSignature(token, slipNo, {
          signerName: signerName.trim(),
          signaturePngBase64: dataURL,
          clientHash: hash,
        })
        navigate(`/mobile/share/${res.shareToken}?from=signed`)
      }
    } catch (err) {
      console.error('[signature] 저장 실패', err)
      setErrorMsg('전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
      setSubmitting(false)
    }
  }

  /** [다시 서명] — 캔버스 비우기. */
  const handleClear = () => {
    padRef.current?.clear()
    setEmpty(true)
    setErrorMsg(null)
  }

  /** 캔버스 영역 클릭 → fullscreen 진입 (이미 fullscreen 이면 무시). */
  const handleEnterFullscreen = () => {
    if (isFullscreen) return
    setIsFullscreen(true)
  }

  /** Fullscreen 의 [완료] — 서명 보존 + 모달 닫기 (실제 BE 전송은 embed 의 [완료]). */
  const handleConfirmFullscreen = () => {
    // 서명 데이터는 같은 SignaturePad ref 에 보존됨
    setIsFullscreen(false)
  }

  const stageLabel = stage === 'driver' ? '배송기사 서명' : '인수자 서명'
  const completeLabel = stage === 'driver' ? '서명 완료 (다음: 인수자)' : '서명 완료'

  return (
    <div className="m-mock-frame">
      <div className="m-brand-bar">(주)삼한공조시스템</div>
      <div className="m-page">
        <a
          className="m-back-link"
          href="#"
          onClick={(e) => {
            e.preventDefault()
            navigate(-1)
          }}
        >
          ◀ 목록으로
        </a>

        {/* 단계 표시 */}
        <div className="m-stage-indicator" aria-label="진행 단계">
          <span className={stage === 'driver' ? 'is-active' : 'is-done'}>
            ① 배송기사 서명
          </span>
          <span className="m-stage-arrow">→</span>
          <span className={stage === 'recipient' ? 'is-active' : ''}>
            ② 인수자 서명
          </span>
        </div>

        {/* 전표 헤더 (mock) */}
        <section className="m-slip-card" aria-label="전표 상세">
          <h2 className="m-slip-card-title">전표 상세</h2>
          <p className="m-slip-info">
            <span className="m-slip-info-label">거래처</span>
            ○○종합건설
          </p>
          <p className="m-slip-info">
            <span className="m-slip-info-label">전표번호</span>
            <span className="m-slip-info-sm">{slipNo}</span>
          </p>
          <p className="m-slip-info">
            <span className="m-slip-info-label">배송지</span>
            <span className="m-slip-info-sm">경기도 성남시 분당구 판교로 235</span>
          </p>
          <hr className="m-slip-divider" />
          <p className="m-slip-info-sm">시스템에어컨 4Way 4HP · 2EA</p>
          <p className="m-slip-info-sm">유선 리모컨 (WE10N) · 2EA</p>
          <p className="m-slip-info-sm">WIFI 판넬 (PC1NWSK3NW) · 1EA</p>
          <hr className="m-slip-divider" />
          <p className="m-slip-info">
            <strong>합계: 3,990,000 원</strong>
          </p>
        </section>

        {/* 인수자명 — recipient 단계만 */}
        {stage === 'recipient' ? (
          <>
            <label className="m-input-label" htmlFor="signer-name">
              인수자 정보
            </label>
            <input
              id="signer-name"
              type="text"
              className="m-input"
              value={signerName}
              onChange={(e) => setSignerName(e.target.value)}
              maxLength={50}
              placeholder="인수자 성함"
              disabled={submitting}
              autoComplete="off"
            />
          </>
        ) : null}

        {/* 서명 캔버스 — embed (클릭 시 fullscreen) */}
        <label className="m-input-label">{stageLabel}</label>
        <div
          className={`m-canvas-wrap${isFullscreen ? ' is-fullscreen' : ' is-embed'}`}
          onClick={isFullscreen ? undefined : handleEnterFullscreen}
          role="button"
          tabIndex={isFullscreen ? -1 : 0}
          aria-label={isFullscreen ? '서명 캔버스 (전체화면)' : '서명 캔버스 (클릭하여 전체화면)'}
        >
          <SignaturePad
            ref={padRef}
            width={size.width}
            height={size.height}
            disabled={submitting}
            onChange={setEmpty}
          />
          {!isFullscreen && empty ? (
            <div className="m-canvas-hint">손가락으로 서명하시려면 클릭하세요</div>
          ) : null}
          {isFullscreen ? (
            <div className="m-canvas-fullscreen-actions">
              <Button variant="secondary" onClick={handleClear} disabled={empty || submitting}>
                다시 서명
              </Button>
              <Button variant="primary" onClick={handleConfirmFullscreen} disabled={empty || submitting}>
                완료
              </Button>
            </div>
          ) : null}
        </div>

        {/* 임베드 액션 버튼 (실제 BE 전송) */}
        {!isFullscreen ? (
          <div className="m-actions-grid">
            <Button variant="secondary" onClick={handleClear} disabled={empty || submitting}>
              다시 서명
            </Button>
            <Button
              variant="primary"
              onClick={() => void handleSubmit()}
              disabled={submitDisabled}
              loading={submitting}
            >
              {completeLabel}
            </Button>
          </div>
        ) : null}

        {errorMsg ? (
          <div className="m-error" role="alert">
            {errorMsg}
          </div>
        ) : null}

        <hr className="m-slip-divider" />
        <p className="m-footer-note">문의: 02-XXXX-XXXX</p>
      </div>
    </div>
  )
}
