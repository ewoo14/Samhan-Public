import type { Meta, StoryObj } from '@storybook/react'
import { SlipNumberDisplay } from './SlipNumberDisplay'

const meta: Meta<typeof SlipNumberDisplay> = {
  title: 'Components/SlipNumberDisplay',
  component: SlipNumberDisplay,
  args: {
    slipDate: '2026-05-04',
    seqNo: 7,
    size: 'md',
  },
  argTypes: {
    size: {
      control: 'inline-radio',
      options: ['sm', 'md', 'lg'],
    },
  },
}
export default meta

type Story = StoryObj<typeof SlipNumberDisplay>

/** 기본 — `2026/05/04 - 7` (size=md). */
export const Default: Story = {}

/**
 * 3종 사이즈 비교 — 목록(sm) / 본문(md) / 헤더(lg).
 */
export const SmallMediumLarge: Story = {
  name: '사이즈 sm/md/lg 비교',
  render: () => (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div>
        <span style={{ fontSize: 12, color: '#6B7280', marginRight: 12 }}>
          sm (목록)
        </span>
        <SlipNumberDisplay slipDate="2026-05-04" seqNo={7} size="sm" />
      </div>
      <div>
        <span style={{ fontSize: 12, color: '#6B7280', marginRight: 12 }}>
          md (본문)
        </span>
        <SlipNumberDisplay slipDate="2026-05-04" seqNo={7} size="md" />
      </div>
      <div>
        <span style={{ fontSize: 12, color: '#6B7280', marginRight: 12 }}>
          lg (헤더)
        </span>
        <SlipNumberDisplay slipDate="2026-05-04" seqNo={7} size="lg" />
      </div>
    </div>
  ),
}

/**
 * UUID 호버 tooltip — 마우스를 올리면 `UUID: ...` 표시.
 * 권한 있는 개발자/매니저 디버깅용.
 */
export const WithUUID: Story = {
  name: 'UUID hover tooltip',
  args: {
    uuid: '01H8XK3T-ABCD-1234-5678-9F0123456789',
    size: 'lg',
  },
}

/**
 * 큰 순번 (`2026/05/04 - 999`) — tabular-nums 가 자릿수 정렬을 유지하는지 확인.
 */
export const LongSequence: Story = {
  args: { seqNo: 999, size: 'lg' },
}

/**
 * 오늘 날짜 (storybook 시점). today 표시 케이스 시각화용.
 */
export const TodaysSlip: Story = {
  name: '오늘 날짜 전표',
  render: () => {
    const today = new Date()
    const yyyy = today.getFullYear()
    const mm = String(today.getMonth() + 1).padStart(2, '0')
    const dd = String(today.getDate()).padStart(2, '0')
    const iso = `${yyyy}-${mm}-${dd}`
    return <SlipNumberDisplay slipDate={iso} seqNo={1} size="md" />
  },
}

/**
 * 자릿수 정렬 검증 — 같은 컬럼에 여러 행이 있을 때 monospace 효과 확인.
 */
export const TableColumnAlignment: Story = {
  name: '목록 컬럼 정렬 (sm)',
  render: () => {
    const rows: Array<{ date: string; seq: number }> = [
      { date: '2026-05-01', seq: 1 },
      { date: '2026-05-01', seq: 12 },
      { date: '2026-05-02', seq: 3 },
      { date: '2026-05-04', seq: 7 },
      { date: '2026-05-04', seq: 999 },
    ]
    return (
      <div
        style={{
          display: 'inline-flex',
          flexDirection: 'column',
          gap: 4,
          padding: 8,
          border: '1px solid #D6DCE3',
          borderRadius: 4,
        }}
      >
        {rows.map((r, i) => (
          <SlipNumberDisplay
            key={`${r.date}-${r.seq}-${i}`}
            slipDate={r.date}
            seqNo={r.seq}
            size="sm"
          />
        ))}
      </div>
    )
  },
}
