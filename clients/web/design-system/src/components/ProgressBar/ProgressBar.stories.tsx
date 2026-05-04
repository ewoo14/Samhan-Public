import type { Meta, StoryObj } from '@storybook/react'
import { ProgressBar } from './ProgressBar'
import type { SlipStatus } from '../SlipStatusBadge/SlipStatusBadge'

const meta: Meta<typeof ProgressBar> = {
  title: 'Sales-Polish-2/ProgressBar',
  component: ProgressBar,
  args: {
    currentStatus: 'ACCEPTED',
  },
  argTypes: {
    currentStatus: {
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
    branchReason: { control: 'text' },
  },
  decorators: [
    (Story) => (
      <div style={{ width: 1100, padding: 24, background: 'var(--surface-app)' }}>
        <Story />
      </div>
    ),
  ],
  parameters: {
    docs: {
      description: {
        component:
          'sales-polish-2-slice (Slice A) 신규 컴포넌트. 사용자 피드백 #1 ("라이프사이클" 모호) 해결. 10단계 + 분기 (REJECTED/CANCELED) 시각화. INSPECTING 신규 단계 포함.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof ProgressBar>

/** 작성 단계 (DRAFT — 첫 단계 current). */
export const Draft: Story = { args: { currentStatus: 'DRAFT' } }

/** 수락 단계 (ACCEPTED — 출고인 자동 채움 시점). */
export const Accepted: Story = { args: { currentStatus: 'ACCEPTED' } }

/** 처리 단계 (PROCESSING — 창고원이 picking 중). */
export const Processing: Story = { args: { currentStatus: 'PROCESSING' } }

/** 검수 단계 (INSPECTING) — Slice A 신규 단계. 검수인 자동 채움 시점. */
export const Inspecting: Story = { args: { currentStatus: 'INSPECTING' } }

/** 확정 단계 (CONFIRMED — 마지막 단계). */
export const Confirmed: Story = { args: { currentStatus: 'CONFIRMED' } }

/** 분기 — REJECTED 반려 (마지막 done = SENT 가정). */
export const Rejected: Story = {
  args: {
    currentStatus: 'REJECTED',
    branchReason: '재고 부족 — 모델 AJ040 1대 부족',
  },
}

/** 분기 — CANCELED 취소 (마지막 done = SAVED 가정). */
export const Canceled: Story = {
  args: {
    currentStatus: 'CANCELED',
    branchReason: '거래처 주문 취소 요청',
  },
}

/** 클릭 가능 — onStepClick 핸들러 제공 시 노드가 button 처럼 동작 (히스토리 모달 등). */
export const Clickable: Story = {
  args: {
    currentStatus: 'PROCESSING',
    onStepClick: (status) => {
      // Storybook demo only — alert 사용 (실제 앱은 history 모달 등 호출)
      window.alert(`${status} 노드 클릭`)
    },
  },
}

/** history 정보 포함 — 노드 hover 시 actor 이름 표시. */
export const WithHistory: Story = {
  args: {
    currentStatus: 'INSPECTING',
    history: [
      { status: 'DRAFT', transitionedAt: '2026-05-04T09:00:00+09:00', actorFullName: '오병승' },
      { status: 'SAVED', transitionedAt: '2026-05-04T09:30:00+09:00', actorFullName: '오병승' },
      { status: 'SENT', transitionedAt: '2026-05-04T10:00:00+09:00', actorFullName: '오병승' },
      { status: 'ACCEPTED', transitionedAt: '2026-05-04T10:15:00+09:00', actorFullName: '홍지수' },
      { status: 'PROCESSING', transitionedAt: '2026-05-04T11:00:00+09:00', actorFullName: '홍지수' },
      { status: 'INSPECTING', transitionedAt: '2026-05-04T13:30:00+09:00', actorFullName: '김기철' },
    ],
  },
}

/** 전체 10단계 정상 흐름 한 화면 (CONFIRMED 마지막 단계). */
export const FullFlowConfirmed: Story = {
  args: { currentStatus: 'CONFIRMED' },
}
