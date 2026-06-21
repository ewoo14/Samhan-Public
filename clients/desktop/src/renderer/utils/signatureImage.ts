/**
 * 사원 서명 이미지 정규화 유틸 — signature-slice-C C2 (외부 의존성 0).
 *
 * 업로드 경로(UPLOAD)에서 파일 → canvas 리사이즈 → PNG 재인코딩 → ≤50KB 가드 + SHA-256.
 * 모바일 손그림(MOBILE_CANVAS)은 SignaturePad.toDataURL() 이 이미 PNG dataURL 이라 본 유틸의
 * sha256OfDataUrl 만 사용. slip MobileSignaturePage.sha256OfDataURL 패턴과 동일 알고리즘.
 */

/** 서버 가드(slip PNG_MAX_BYTES=50*1024)와 동일 — 클라 선검증으로 UX 빠른 피드백. */
export const PNG_MAX_BYTES = 50 * 1024

/** dataURL("data:image/png;base64,XXXX")의 base64 디코딩 바이트 수. */
export function dataUrlByteLength(dataUrl: string): number {
  const base64 = dataUrl.split(',')[1] ?? ''
  const padding = (base64.match(/=+$/)?.[0]?.length ?? 0)
  return Math.floor((base64.length * 3) / 4) - padding
}

/** dataURL 의 base64 → SHA-256 hex 64자 (Web Crypto). BE 재검증 키. */
export async function sha256OfDataUrl(dataUrl: string): Promise<string> {
  const base64 = dataUrl.split(',')[1] ?? ''
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  const buf = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/** 인감 최대 박스 (px) — 디자이너 가이드 잠정. 비율 유지 contain. */
const MAX_W = 400
const MAX_H = 200

/**
 * 업로드 파일 → 정규화 PNG dataURL + hash + bytes.
 * 1) Image 로드 2) MAX_W×MAX_H contain 리사이즈 3) canvas PNG 재인코딩 4) ≤50KB 가드.
 * ≤50KB 초과 시 Error('SIGNATURE_TOO_LARGE'). 흰배경 투명화는 best-effort 미적용(원본 보존).
 */
export async function normalizeSignaturePng(
  file: File,
): Promise<{ dataUrl: string; hash: string; bytes: number }> {
  const sourceDataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('SIGNATURE_FILE_READ_FAILED'))
    reader.readAsDataURL(file)
  })
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const el = new Image()
    el.onload = () => resolve(el)
    el.onerror = () => reject(new Error('SIGNATURE_IMAGE_DECODE_FAILED'))
    el.src = sourceDataUrl
  })
  const scale = Math.min(MAX_W / img.width, MAX_H / img.height, 1)
  const w = Math.max(1, Math.round(img.width * scale))
  const h = Math.max(1, Math.round(img.height * scale))
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('SIGNATURE_CANVAS_UNAVAILABLE')
  ctx.drawImage(img, 0, 0, w, h)
  const dataUrl = canvas.toDataURL('image/png')
  const bytes = dataUrlByteLength(dataUrl)
  if (bytes > PNG_MAX_BYTES) throw new Error('SIGNATURE_TOO_LARGE')
  const hash = await sha256OfDataUrl(dataUrl)
  return { dataUrl, hash, bytes }
}
