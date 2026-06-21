/** 모바일 공개 서명 제출 — NO-AUTH 토큰 게이트. POST /api/public/employee-signatures/{token}. */
import axios from 'axios'

const apiClient = axios.create({
  baseURL: '',
  timeout: 10_000,
  headers: { 'Content-Type': 'application/json' },
})

export interface PublicEmployeeSignatureRequest {
  signaturePngBase64: string
  signatureHash: string
}

/** 토큰 검증(미만료·미사용) 후 서명 저장. 404 무효 토큰 / 409 사용됨 / 410 만료 → axios error throw. */
export async function submitPublicSignature(
  token: string,
  body: PublicEmployeeSignatureRequest,
): Promise<void> {
  await apiClient.post(`/api/public/employee-signatures/${encodeURIComponent(token)}`, body)
}

/** dataURL base64 → SHA-256 hex 64자 (Web Crypto). BE 재검증 키. */
export async function sha256OfDataUrl(dataUrl: string): Promise<string> {
  const base64 = dataUrl.split(',')[1] ?? ''
  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
  const buf = await crypto.subtle.digest('SHA-256', bytes)
  return Array.from(new Uint8Array(buf)).map((b) => b.toString(16).padStart(2, '0')).join('')
}
