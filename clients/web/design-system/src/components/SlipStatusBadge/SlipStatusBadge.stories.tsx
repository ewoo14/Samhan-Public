import type { Meta, StoryObj } from '@storybook/react'
import { SlipStatusBadge, type SlipStatus } from './SlipStatusBadge'

const meta: Meta<typeof SlipStatusBadge> = {
  title: 'Components/SlipStatusBadge',
  component: SlipStatusBadge,
  args: {
    status: 'DRAFT',
    showStep: false,
  },
  argTypes: {
    status: {
      control: 'select',
      options: [
        'DRAFT',
        'SAVED',
        'SENT',
        'ACCEPTED',
        'PROCESSING',
        'INSPECTING',
        'COMPLETED',
        'SHIPPING',
        'DELIVERED',
        'CONFIRMED',
        'REJECTED',
        'CANCELED',
      ] satisfies SlipStatus[],
    },
    showStep: { control: 'boolean' },
  },
}
export default meta

type Story = StoryObj<typeof SlipStatusBadge>

/**
 * 9단계 정상 흐름 + 분기 2종 = 총 11개 상태를 한 화면에 grid 로 비교.
 * 색상 그룹별로 묶여 진행도가 시각적으로 드러나는지 확인.
 */
export const AllStatuses: Story = {
  name: '11종 상태 전체 (grid)',
  render: () => {
    const groups: { title: string; statuses: SlipStatus[] }[] = [
      { title: '편집 가능 (1~3)', statuses: ['DRAFT', 'SAVED', 'SENT'] },
      {
        title: '처리 (4~7) — Slice A INSPECTING 추가',
        statuses: ['ACCEPTED', 'PROCESSING', 'INSPECTING', 'COMPLETED'],
      },
      {
        title: '배송/완결 (8~10)',
        statuses: ['SHIPPING', 'DELIVERED', 'CONFIRMED'],
      },
      { title: '분기 (종결)', statuses: ['REJECTED', 'CANCELED'] },
    ]
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        {groups.map((g) => (
          <div key={g.title}>
            <div style={{ fontSize: 12, color: '#6B7280', marginBottom: 6 }}>
              {g.title}
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {g.statuses.map((s) => (
                <SlipStatusBadge key={s} status={s} />
              ))}
            </div>
          </div>
        ))}
      </div>
    )
  },
}

/**
 * `showStep=true` — 단계 번호(1~9) 가 함께 표시된다.
 * 분기 (REJECTED/CANCELED) 는 단계 번호가 없어 표시되지 않는다.
 */
export const WithStep: Story = {
  name: '단계 번호 표시 (showStep)',
  render: () => {
    const all: SlipStatus[] = [
      'DRAFT',
      'SAVED',
      'SENT',
      'ACCEPTED',
      'PROCESSING',
      'INSPECTING',
      'COMPLETED',
      'SHIPPING',
      'DELIVERED',
      'CONFIRMED',
      'REJECTED',
      'CANCELED',
    ]
    return (
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        {all.map((s) => (
          <SlipStatusBadge key={s} status={s} showStep />
        ))}
      </div>
    )
  },
}

/** DRAFT — 작성중. 가장 옅은 회색. 작성자가 자유롭게 수정 가능한 단계. */
export const DraftEditable: Story = {
  args: { status: 'DRAFT', showStep: true },
}

/**
 * ACCEPTED — 수락. 처리 단계 진입. 작성자 수정이 잠기는 시점.
 * 주황 그룹의 가장 옅은 채도(tier-1).
 */
export const AcceptedLocked: Story = {
  args: { status: 'ACCEPTED', showStep: true },
}

/**
 * REJECTED — 반려. 빨간색 단색 + bold.
 * 정상 흐름에서 빠져나간 종결 상태이므로 단계 번호가 없다.
 */
export const RejectedFlow: Story = {
  args: { status: 'REJECTED', showStep: true },
}

/** CONFIRMED — 확정. 녹색 그룹 가장 진한 채도(tier-3) + bold. 회계 확정 단계. */
export const Confirmed: Story = {
  args: { status: 'CONFIRMED', showStep: true },
}

/** CANCELED — 취소. 회색 + 취소선. 정상 흐름에서 빠져나간 종결 상태. */
export const Canceled: Story = {
  args: { status: 'CANCELED' },
}
