import { afterEach, describe, expect, it } from 'vitest'
import { sha256OfDataUrl } from './api'

const originalCryptoDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto')

function restoreCrypto(): void {
  if (originalCryptoDescriptor) {
    Object.defineProperty(globalThis, 'crypto', originalCryptoDescriptor)
    return
  }

  Reflect.deleteProperty(globalThis, 'crypto')
}

describe('sha256OfDataUrl', () => {
  afterEach(() => {
    restoreCrypto()
  })

  it('crypto.subtle 이 없으면 순수 JS SHA-256 fallback 으로 동일한 64자 hex 를 계산한다', async () => {
    Object.defineProperty(globalThis, 'crypto', { configurable: true, value: undefined })

    await expect(sha256OfDataUrl('data:text/plain;base64,YWJj')).resolves.toBe(
      'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad',
    )
  })
})
