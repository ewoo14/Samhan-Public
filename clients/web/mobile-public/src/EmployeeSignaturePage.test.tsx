import { describe, expect, it, vi, beforeEach, type Mock } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { forwardRef, useImperativeHandle } from 'react'
import { EmployeeSignaturePage } from './EmployeeSignaturePage'
import * as api from './api'

// api 모듈 전체를 vi.mock 으로 hoisting — ESM live-binding 한계 우회.
vi.mock('./api', () => ({
  sha256OfDataUrl: vi.fn().mockResolvedValue('a'.repeat(64)),
  submitPublicSignature: vi.fn().mockResolvedValue(undefined),
}))

// design-system SignaturePad 를 jsdom 친화적 stub 으로 교체.
// 실제 canvas getContext 는 jsdom 미구현 → stub 이 toDataURL 직접 제공.
vi.mock('@samhan/design-system', async (importOriginal) => {
  const original = await importOriginal<typeof import('@samhan/design-system')>()

  const StubSignaturePad = forwardRef<
    { clear: () => void; isEmpty: () => boolean; toDataURL: () => string; toBlob: () => Promise<null> },
    { onChange?: (isEmpty: boolean) => void; disabled?: boolean }
  >(function StubSignaturePad({ onChange }, ref) {
    useImperativeHandle(ref, () => ({
      clear: () => onChange?.(true),
      isEmpty: () => false,
      toDataURL: () => 'data:image/png;base64,AAAA',
      toBlob: async () => null,
    }))
    return <div data-testid="stub-signature-pad" onClick={() => onChange?.(false)} />
  })

  return { ...original, SignaturePad: StubSignaturePad }
})

describe('EmployeeSignaturePage', () => {
  beforeEach(() => {
    ;(api.sha256OfDataUrl as Mock).mockResolvedValue('a'.repeat(64))
    ;(api.submitPublicSignature as Mock).mockResolvedValue(undefined)
  })

  it('빈 토큰이면 invalid-token 표시', () => {
    render(<EmployeeSignaturePage token="" />)
    expect(screen.getByTestId('mobile-signature-invalid-token')).toBeTruthy()
  })

  it('유효 토큰이면 서명 패드 + 제출 버튼 렌더', () => {
    render(<EmployeeSignaturePage token="tok-1" />)
    expect(screen.getByTestId('mobile-signature-pad-area')).toBeTruthy()
    expect(screen.getByTestId('mobile-signature-submit')).toBeTruthy()
  })

  it('제출이 410 으로 실패하면 expired 화면', async () => {
    ;(api.submitPublicSignature as Mock).mockRejectedValue(
      Object.assign(new Error('gone'), { isAxiosError: true, response: { status: 410 } }),
    )
    render(<EmployeeSignaturePage token="tok-1" />)
    // stub SignaturePad 클릭으로 onChange(false) → empty=false → 버튼 활성
    await act(async () => {
      fireEvent.click(screen.getByTestId('stub-signature-pad'))
    })
    const btn = screen.getByTestId('mobile-signature-submit') as HTMLButtonElement
    await act(async () => {
      fireEvent.click(btn)
    })
    await waitFor(() => expect(screen.getByTestId('mobile-signature-expired')).toBeTruthy())
  })
})
