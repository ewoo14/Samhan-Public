/**
 * SignatureViewer — read-only 서명 정보 표시 컴포넌트.
 *
 * Slice C 신규 — Designer `components.md` § 2 + `wireframes.md` § 2.2 / § 3.1 인용.
 *
 * 사용처:
 * - 데스크톱 SlipDetailPage 의 "전자서명 정보" 카드
 * - 모바일 인수자 view (`/share/{shareToken}`) 안에 임베드 — 모바일은 mini bundle 권장이나
 *   디자인 시스템 React 컴포넌트 재사용도 가능 (본 슬라이스 desktop mock 라우트에서 활용).
 *
 * 표시 정보:
 * - PNG `<img>` (base64 dataURL)
 * - 서명자명 (signerName)
 * - 서명 시각 (signedAt — ISO 8601, "YYYY/MM/DD HH:mm" 포맷)
 * - 검증코드 (signatureHash 의 앞 8자, mono 폰트) — 위변조 식별용
 *
 * UUID 비공개 가드: signatureHash 는 hex 64자 (UUID 아님). 표시도 앞 8자 short form 만.
 */
import styles from './SignatureViewer.module.css'

export interface SignatureViewerProps {
  /** PNG base64 dataURL ("data:image/png;base64,..."). 빈 문자열 시 빈 이미지 fallback. */
  signaturePngBase64: string
  /** 서명자명 (자유 입력, ≤50자). */
  signerName: string
  /** 서명 시각 ISO 8601 (예: "2026-05-05T14:32:18Z"). */
  signedAt: string
  /** SHA-256 hex (64자) — 표시는 앞 8자만. null 시 검증코드 행 숨김. */
  signatureHash?: string | null
  /** PNG 미리보기 사이즈 (기본 desktop 카드 150×80). 모바일 view 에서는 'fluid' 권장. */
  size?: 'desktop' | 'fluid'
  /** 추가 className (조립용). */
  className?: string
}

/**
 * "2026-05-05T14:32:18Z" → "2026/05/05 14:32".
 *
 * 빈 ISO 시 빈 문자열. UTC offset 무시 (사용자 로컬 표시는 별도 디자인 결정 필요 — 본 슬라이스는
 * 서버 응답 그대로 슬라이싱).
 */
function formatSignedAt(iso: string): string {
  if (!iso || iso.length < 16) return iso
  const datePart = iso.slice(0, 10).replace(/-/g, '/')
  const timePart = iso.slice(11, 16)
  return `${datePart} ${timePart}`
}

export function SignatureViewer({
  signaturePngBase64,
  signerName,
  signedAt,
  signatureHash,
  size = 'desktop',
  className,
}: SignatureViewerProps) {
  const wrapperClass = [
    styles['wrapper'],
    size === 'fluid' ? styles['fluid'] : styles['desktop'],
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const hashShort = signatureHash ? signatureHash.slice(0, 8) : null

  return (
    <section className={wrapperClass} aria-label="전자서명 정보">
      <div className={styles['img-wrap']}>
        {signaturePngBase64 ? (
          <img
            className={styles['img']}
            src={signaturePngBase64}
            alt={`${signerName} 인수자 서명`}
          />
        ) : (
          <div className={styles['img-empty']}>(서명 이미지 없음)</div>
        )}
      </div>
      <dl className={styles['meta']}>
        <div className={styles['meta-row']}>
          <dt className={styles['meta-label']}>서명자</dt>
          <dd className={styles['meta-value']}>{signerName}</dd>
        </div>
        <div className={styles['meta-row']}>
          <dt className={styles['meta-label']}>서명시각</dt>
          <dd className={styles['meta-value']}>{formatSignedAt(signedAt)}</dd>
        </div>
        {hashShort ? (
          <div className={styles['meta-row']}>
            <dt className={styles['meta-label']}>검증코드</dt>
            <dd className={styles['meta-hash']}>{hashShort}</dd>
          </div>
        ) : null}
      </dl>
    </section>
  )
}

export default SignatureViewer
