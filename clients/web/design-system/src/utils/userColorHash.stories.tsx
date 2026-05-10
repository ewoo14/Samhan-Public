import type { Meta, StoryObj } from '@storybook/react'
import { userIdToColor } from './userColorHash'

/**
 * Phase 12 시리즈 공유 자산 — userIdToColor.
 *
 * - PR-H1 (본 PR) 에서는 export + Storybook swatch 만 시드한다.
 * - PR-H2 audit overlay 가 실제로 호출하여 "수정자 색상 dot" 으로 사용한다.
 * - 동일 userId → 동일 색상 (deterministic) 을 시각적으로 확인하는 용도.
 */
const meta: Meta = {
  title: 'Phase 12/userIdToColor',
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'userId 문자열을 HSL 색상으로 변환하는 deterministic hash 유틸. PR-H2 audit overlay / PR-H3 코멘트 author avatar 등 Phase 12 시리즈에서 공유 사용한다.',
      },
    },
  },
}
export default meta

type Story = StoryObj

const SAMPLE_USER_IDS = [
  'user-001-kim',
  'user-002-lee',
  'user-003-park',
  'user-004-choi',
  'user-005-jung',
]

const Swatch = ({ userId }: { userId: string }) => {
  const color = userIdToColor(userId)
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 12,
        padding: '8px 12px',
        border: '1px solid #E5E7EB',
        borderRadius: 6,
        marginBottom: 8,
        maxWidth: 480,
      }}
    >
      <div
        aria-label={`${userId} 색상`}
        style={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: color,
          flexShrink: 0,
        }}
      />
      <div style={{ flex: 1, fontFamily: 'monospace', fontSize: 13 }}>
        <div style={{ color: '#111827' }}>{userId}</div>
        <div style={{ color: '#6B7280', fontSize: 11 }}>{color}</div>
      </div>
    </div>
  )
}

/**
 * Default — 5명의 샘플 userId 색상 swatch.
 * 동일 userId 는 항상 동일한 색상 (deterministic) 임을 확인할 수 있다.
 */
export const Default: Story = {
  render: () => (
    <div style={{ padding: 16 }}>
      <h3 style={{ fontSize: 16, marginBottom: 12 }}>샘플 사용자 색상 (5명)</h3>
      <p style={{ fontSize: 13, color: '#6B7280', marginBottom: 16, maxWidth: 480 }}>
        동일 userId 는 페이지를 새로고침하거나 다른 화면에서 호출해도 항상 같은
        색상이 나온다. PR-H2 audit overlay 의 "수정자 색상 dot", PR-H3 코멘트
        author avatar 배경색에서 동일 hash 를 사용한다.
      </p>
      {SAMPLE_USER_IDS.map((id) => (
        <Swatch key={id} userId={id} />
      ))}
    </div>
  ),
}

/**
 * Determinism — 동일 userId 를 두 번 렌더링하여 같은 색상이 나오는지 시각 검증.
 */
export const Determinism: Story = {
  render: () => {
    const userId = 'user-001-kim'
    return (
      <div style={{ padding: 16 }}>
        <h3 style={{ fontSize: 16, marginBottom: 12 }}>
          동일 userId 두 번 호출 — 같은 색상 보장
        </h3>
        <Swatch userId={userId} />
        <Swatch userId={userId} />
        <p style={{ fontSize: 12, color: '#6B7280', marginTop: 8 }}>
          위 2개 swatch 는 동일 색상이어야 한다 (deterministic 검증).
        </p>
      </div>
    )
  },
}
