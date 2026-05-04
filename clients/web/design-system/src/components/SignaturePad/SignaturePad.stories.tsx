import { useRef, useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { SignaturePad, type SignaturePadHandle } from './SignaturePad'

const meta: Meta<typeof SignaturePad> = {
  title: 'Components/SignaturePad',
  component: SignaturePad,
  args: {
    width: 320,
    height: 200,
  },
}
export default meta

type Story = StoryObj<typeof SignaturePad>

/** 기본 — 320×200, 빈 상태 placeholder 표시. */
export const Default: Story = {}

/** 넓은 캔버스 — iPhone 13+ 기본 (400×200). */
export const Wide: Story = {
  args: { width: 400, height: 200 },
}

/** 비활성화 — 전송 중 lock 시뮬레이션. */
export const Disabled: Story = {
  args: { disabled: true },
}

/** 외부 ref 로 clear / isEmpty / toDataURL 호출. */
export const WithRefControls: Story = {
  render: (args) => {
    /* eslint-disable react-hooks/rules-of-hooks */
    const ref = useRef<SignaturePadHandle>(null)
    const [empty, setEmpty] = useState(true)
    const [preview, setPreview] = useState<string | null>(null)
    /* eslint-enable react-hooks/rules-of-hooks */
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'flex-start' }}>
        <SignaturePad
          {...args}
          ref={ref}
          onChange={(e) => setEmpty(e)}
        />
        <div style={{ display: 'flex', gap: 8 }}>
          <button type="button" onClick={() => ref.current?.clear()}>
            다시 서명
          </button>
          <button
            type="button"
            disabled={empty}
            onClick={() => setPreview(ref.current?.toDataURL() ?? null)}
          >
            서명 완료 (PNG 추출)
          </button>
        </div>
        <div style={{ fontSize: 12, color: '#666' }}>isEmpty: {String(empty)}</div>
        {preview ? (
          <img
            src={preview}
            alt="서명 미리보기"
            style={{ border: '1px solid #ccc', maxWidth: 320 }}
          />
        ) : null}
      </div>
    )
  },
}
