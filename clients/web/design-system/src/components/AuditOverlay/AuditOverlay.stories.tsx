/**
 * AuditOverlay Storybook — PR-H2 의무 4 story.
 *
 * - SingleChange       : 단일 revision (가장 흔한 케이스 — inline 표시만)
 * - MultipleRevisions  : 다중 revision 누적 (inline + "이력 N개 보기" expand)
 * - EmptyHistory       : 빈 이력 (현재 값만, "변경 이력 없음" 표시)
 * - MultiUserShowcase  : 색상 다양성 — 5명의 서로 다른 수정자가 누적된 케이스
 *                        (userIdToColor deterministic hash 의 hue 분산을 시각적으로 검증)
 */
import type { Meta, StoryObj } from '@storybook/react'
import { AuditOverlay, type AuditLogEntry } from './AuditOverlay'

const meta: Meta<typeof AuditOverlay> = {
  title: 'Phase 12/AuditOverlay',
  component: AuditOverlay,
  parameters: {
    layout: 'padded',
    docs: {
      description: {
        component:
          'PR-H2 audit overlay — 한 필드의 현재 값과 과거 변경 이력을 취소선/수정자 색상/시각으로 함께 표시. PR-H1 fda4d8f 의 userIdToColor util 을 재사용한다.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof AuditOverlay>

const SAMPLE_HISTORY_SINGLE: AuditLogEntry[] = [
  {
    revisionNo: 2,
    beforeValue: '오전 배송 부탁드립니다',
    actorId: 'user-001-kim',
    actorName: '김영업',
    changedAt: '2026-05-09T14:32:18+09:00',
  },
]

const SAMPLE_HISTORY_MULTI: AuditLogEntry[] = [
  {
    revisionNo: 4,
    beforeValue: '오후 배송으로 변경 요청',
    actorId: 'user-003-park',
    actorName: '박관리',
    changedAt: '2026-05-09T16:48:02+09:00',
  },
  {
    revisionNo: 3,
    beforeValue: '오전 배송 부탁드립니다',
    actorId: 'user-001-kim',
    actorName: '김영업',
    changedAt: '2026-05-09T14:32:18+09:00',
  },
  {
    revisionNo: 2,
    beforeValue: null,
    actorId: 'user-002-lee',
    actorName: '이작성',
    changedAt: '2026-05-09T11:05:55+09:00',
  },
]

/**
 * 단일 revision — 가장 흔한 케이스. inline 으로 한 줄 표시 (현재 값 + 취소선 이전 값 + 수정자).
 */
export const SingleChange: Story = {
  args: {
    field: 'memo',
    currentValue: '긴급 출고 요청 (오늘 마감 전)',
    history: SAMPLE_HISTORY_SINGLE,
  },
}

/**
 * 다중 revision — 누적 변경. 가장 최근 1건만 inline + "이력 N개 보기" 버튼으로 expand.
 * 클릭 시 과거 revision 들이 하단에 expand 된다.
 */
export const MultipleRevisions: Story = {
  args: {
    field: 'memo',
    currentValue: '익일 오전 배송으로 확정',
    history: SAMPLE_HISTORY_MULTI,
  },
}

/**
 * 빈 이력 — 신규 작성 후 변경이 없는 케이스. 현재 값만 + "변경 이력 없음" 라벨.
 */
export const EmptyHistory: Story = {
  args: {
    field: 'shippingAddress',
    currentValue: '서울특별시 강남구 테헤란로 152',
    history: [],
  },
}

/**
 * 색상 다양성 — 5명의 서로 다른 수정자가 누적된 케이스.
 *
 * `userIdToColor` deterministic hash 의 hue 분산이 충분한지 시각 검증.
 * 5개 dot 이 서로 명확히 구분되는 색상으로 보여야 한다 (동일 색상 충돌 없음).
 */
const MULTI_USER_HISTORY: AuditLogEntry[] = [
  {
    revisionNo: 5,
    beforeValue: '서울 강남구 테헤란로 100',
    actorId: 'user-005-jung',
    actorName: '정마스터',
    changedAt: '2026-05-09T17:55:10+09:00',
  },
  {
    revisionNo: 4,
    beforeValue: '서울 강남구 테헤란로 99',
    actorId: 'user-004-choi',
    actorName: '최창고',
    changedAt: '2026-05-09T16:30:42+09:00',
  },
  {
    revisionNo: 3,
    beforeValue: '서울 강남구 역삼로 50',
    actorId: 'user-003-park',
    actorName: '박관리',
    changedAt: '2026-05-09T15:12:08+09:00',
  },
  {
    revisionNo: 2,
    beforeValue: '서울 강남구 테헤란로 1',
    actorId: 'user-002-lee',
    actorName: '이작성',
    changedAt: '2026-05-09T13:48:33+09:00',
  },
  {
    revisionNo: 1,
    beforeValue: null,
    actorId: 'user-001-kim',
    actorName: '김영업',
    changedAt: '2026-05-09T11:05:21+09:00',
  },
]

export const MultiUserShowcase: Story = {
  name: '색상 다양성 (5명 수정자)',
  args: {
    field: 'shippingAddress',
    currentValue: '서울특별시 강남구 테헤란로 152',
    history: MULTI_USER_HISTORY,
  },
  parameters: {
    docs: {
      description: {
        story:
          '5명의 서로 다른 수정자가 누적된 케이스. inline 1건 + expand 시 4건 추가 — 총 5개 dot 색상이 시각적으로 충분히 구분되는지 확인. PR-H1 userIdToColor (HSL hue hash) 의 분산 정상 검증용.',
      },
    },
  },
}
