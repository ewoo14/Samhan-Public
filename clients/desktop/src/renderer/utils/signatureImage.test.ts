import { describe, expect, it } from 'vitest'
import { dataUrlByteLength, sha256OfDataUrl, PNG_MAX_BYTES } from './signatureImage'

// 1x1 투명 PNG (base64) — 알려진 dataURL.
const ONE_PX_PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=='

describe('signatureImage 순수 함수', () => {
  it('PNG_MAX_BYTES 는 51200', () => {
    expect(PNG_MAX_BYTES).toBe(50 * 1024)
  })

  it('dataUrlByteLength 는 base64 디코딩 바이트 수 반환', () => {
    // "AAAA" base64 = 3바이트
    expect(dataUrlByteLength('data:image/png;base64,AAAA')).toBe(3)
  })

  it('sha256OfDataUrl 은 64자 hex 반환 (결정적)', async () => {
    const h = await sha256OfDataUrl(ONE_PX_PNG)
    expect(h).toMatch(/^[0-9a-f]{64}$/)
    expect(await sha256OfDataUrl(ONE_PX_PNG)).toBe(h)
  })
})
