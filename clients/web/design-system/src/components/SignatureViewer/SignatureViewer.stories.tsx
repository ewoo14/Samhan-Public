import type { Meta, StoryObj } from '@storybook/react'
import { SignatureViewer } from './SignatureViewer'

/** 1x1 픽셀 빨강 PNG (스토리북 미리보기 fixture). 실제 서명은 base64 50KB 이내 PNG. */
const PIXEL_PNG =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAAC0lEQVR4nGNgAAIAAAUAAen63NgAAAAASUVORK5CYII='

const meta: Meta<typeof SignatureViewer> = {
  title: 'Components/SignatureViewer',
  component: SignatureViewer,
  args: {
    signaturePngBase64: PIXEL_PNG,
    signerName: '김인수',
    signedAt: '2026-05-05T14:32:18Z',
    signatureHash: 'a3f2b1c9d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1',
  },
}
export default meta

type Story = StoryObj<typeof SignatureViewer>

/** 데스크톱 — 150×80 PNG + 메타 우측 (SlipDetailPage). */
export const Desktop: Story = {}

/** 모바일 fluid — PNG 100% width + 메타 하단 (인수자 view). */
export const Fluid: Story = {
  args: { size: 'fluid' },
}

/** hash 미제공 시 검증코드 행 숨김. */
export const WithoutHash: Story = {
  args: { signatureHash: null },
}

/** 빈 PNG fallback. */
export const EmptyPng: Story = {
  args: { signaturePngBase64: '' },
}
