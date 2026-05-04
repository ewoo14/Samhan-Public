/**
 * MobileSignaturePage — `/mobile/d/:token/s/:slipNo` (signature-slice-C 모바일 mock).
 *
 * 본 슬라이스는 sign.samhan-air.com 분리 (Phase 5 nginx) deferred — desktop 앱 라우트 안에
 * **375×812 viewport mock** 으로 모바일 서명 페이지를 시뮬레이션합니다. Designer
 * `wireframes.md` §1 + `mobile-spec.md` §2.1 / §3.5 / §4 충실 반영.
 *
 * 흐름 (Designer ux-flow.md):
 * 1) 입수: `/mobile/d/:token/s/:slipNo` 진입 → 전표 헤더 + 라인 표시 (mock).
 * 2) 인수자명 입력 (≥1자) + Canvas 서명 (≥1 stroke) → [서명 완료] enabled.
 * 3) [서명 완료] 누르면 SHA-256 해시 → POST `/public/.../signature` →
 *    응답 shareToken 으로 `/mobile/share/:shareToken?from=signed` 리다이렉트.
 * 4) [다시 서명] 누르면 캔버스 clear + 입력값 보존.
 *
 * UUID 비공개:
 * - URL 은 `{token}/{slipNo}` 만 (UUID 없음).
 * - 응답 객체 안에서 받는 값도 비즈니스 식별자만 표시.
 *
 * Web Crypto API (`crypto.subtle.digest`) — iOS 14+ / Android Chrome 90+ 지원.
 * 미지원 환경 fallback 은 본 슬라이스 범위 외 (이후 Phase 5 nginx 로 분리 시 polyfill 검토).
 */
import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, SignaturePad, type SignaturePadHandle } from '@samhan/design-system'
import { recordSignature } from '../api/signature'

/** Canvas 사이즈 — innerWidth ≥375 시 wide (400×200), 아니면 narrow (320×200). */
function pickCanvasSize(): { width: number; height: number } {
  if (typeof window === 'undefined') return { width: 320, height: 200 }
  return window.innerWidth >= 375
    ? { width: 400, height: 200 }
    : { width: 320, height: 200 }
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
  const [signerName, setSignerName] = useState('')
  const [empty, setEmpty] = useState(true)
  const [size, setSize] = useState<{ width: number; height: number }>(pickCanvasSize())
  const [submitting, setSubmitting] = useState(false)
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const token = params.token ?? ''
  const slipNo = params.slipNo ?? ''

  // 화면 크기 변경 (회전 등) 대응
  useEffect(() => {
    const onResize = () => setSize(pickCanvasSize())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const submitDisabled
    = empty || signerName.trim().length === 0 || submitting

  /** [서명 완료] — SHA-256 해시 + POST. 성공 시 share 페이지로 이동. */
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
      const res = await recordSignature(token, slipNo, {
        signerName: signerName.trim(),
        signaturePngBase64: dataURL,
        clientHash: hash,
      })
      navigate(`/mobile/share/${res.shareToken}?from=signed`)
    } catch (err) {
      console.error('[signature] 서명 저장 실패', err)
      setErrorMsg('전송에 실패했습니다. 잠시 후 다시 시도해주세요.')
      setSubmitting(false)
    }
  }

  /** [다시 서명] — 캔버스 비우기 (인수자명 보존). */
  const handleClear = () => {
    padRef.current?.clear()
    setErrorMsg(null)
  }

  return (
    <div className="m-mock-frame">
      <div className="m-brand-bar">삼한물류</div>
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

        {/* 전표 헤더 (mock — 실제 BE 는 GET /public/batches/{token} 로 전표 정보 조회) */}
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
          <p className="m-slip-info-sm">
            시스템에어컨 4Way 4HP &nbsp;·&nbsp; 2EA
          </p>
          <p className="m-slip-info-sm">
            유선 리모컨 (WE10N) &nbsp;·&nbsp; 2EA
          </p>
          <p className="m-slip-info-sm">
            WIFI 판넬 (PC1NWSK3NW) &nbsp;·&nbsp; 1EA
          </p>
          <hr className="m-slip-divider" />
          <p className="m-slip-info">
            <strong>합계: 3,990,000 원</strong>
          </p>
        </section>

        {/* 인수자명 */}
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

        {/* 서명 캔버스 */}
        <label className="m-input-label" htmlFor="signature-canvas">
          서명
        </label>
        <div id="signature-canvas">
          <SignaturePad
            ref={padRef}
            width={size.width}
            height={size.height}
            disabled={submitting}
            onChange={setEmpty}
          />
        </div>

        {/* 액션 버튼 */}
        <div className="m-actions-grid">
          <Button
            variant="secondary"
            onClick={handleClear}
            disabled={empty || submitting}
          >
            다시 서명
          </Button>
          <Button
            variant="primary"
            onClick={() => void handleSubmit()}
            disabled={submitDisabled}
            loading={submitting}
          >
            서명 완료
          </Button>
        </div>

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
