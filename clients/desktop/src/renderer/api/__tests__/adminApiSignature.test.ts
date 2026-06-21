import { describe, expect, it, vi, beforeEach } from 'vitest'
import { apiClient } from '../client'
import {
  uploadUserSignature,
  createSignatureHandoffToken,
  fetchSignatureHandoffStatus,
} from '../adminApi'

describe('adminApi 서명 함수', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('uploadUserSignature 는 PATCH .../signature 호출 + data unwrap', async () => {
    const patch = vi.spyOn(apiClient, 'patch').mockResolvedValue({
      data: { success: true, code: 'OK', message: null, timestamp: '', data: { registered: true, signedAt: '2026-06-21T10:00:00', signatureChannel: 'UPLOAD' } },
    } as never)
    const res = await uploadUserSignature('u-1', {
      signaturePngBase64: 'data:image/png;base64,AAA',
      signatureHash: 'a'.repeat(64),
      channel: 'UPLOAD',
    })
    expect(patch).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature', {
      signaturePngBase64: 'data:image/png;base64,AAA',
      signatureHash: 'a'.repeat(64),
      channel: 'UPLOAD',
    })
    expect(res).toEqual({ registered: true, signedAt: '2026-06-21T10:00:00', signatureChannel: 'UPLOAD' })
  })

  it('createSignatureHandoffToken 는 POST .../handoff-token 호출 + data unwrap', async () => {
    const post = vi.spyOn(apiClient, 'post').mockResolvedValue({
      data: { success: true, code: 'OK', message: null, timestamp: '', data: { token: 'tok-x', qrUrl: 'https://sign.samhan-air.com/s/tok-x', expiresAt: '2026-06-21T10:10:00' } },
    } as never)
    const res = await createSignatureHandoffToken('u-1')
    expect(post).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature/handoff-token')
    expect(res.qrUrl).toBe('https://sign.samhan-air.com/s/tok-x')
  })

  it('fetchSignatureHandoffStatus 는 GET .../handoff/{token}/status 호출', async () => {
    const get = vi.spyOn(apiClient, 'get').mockResolvedValue({
      data: { success: true, code: 'OK', message: null, timestamp: '', data: { used: true, expired: false } },
    } as never)
    const res = await fetchSignatureHandoffStatus('u-1', 'tok-x')
    expect(get).toHaveBeenCalledWith('/api/v1/admin/users/u-1/signature/handoff/tok-x/status')
    expect(res).toEqual({ used: true, expired: false })
  })
})
