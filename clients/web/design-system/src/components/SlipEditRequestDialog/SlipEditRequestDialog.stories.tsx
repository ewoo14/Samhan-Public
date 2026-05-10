/**
 * SlipEditRequestDialog Storybook — PR-H3 의무 3 story.
 *
 * - EditRequest    : EDIT 모드 (정상 경로) — 사유 입력 → 전송
 * - DeleteRequest  : DELETE 모드 (위험 경로) — 빨강 액션 + 다른 placeholder/안내
 * - SubmittingWithError: 서버 에러 + 진행 중 상태 — 버튼 loading + 인라인 에러 표기
 *
 * UX docs (Designer 본): docs/uiux/phase12/H3-edit-request-workflow.md
 *
 * 본 컴포넌트는 사유 textarea + 전송 버튼만 담당하는 MVP shell 이며,
 * H3 UX docs 의 비즈니스 카테고리 / 수정 항목 체크박스 / 수락자 알림 대상 등
 * 확장 항목은 후속 PR (FE-1 follow-up) 에서 합류한다.
 */
import { useState } from 'react'
import type { Meta, StoryObj } from '@storybook/react'
import { SlipEditRequestDialog } from './SlipEditRequestDialog'
import { Button } from '../Button/Button'

const meta: Meta<typeof SlipEditRequestDialog> = {
  title: 'Phase 12/SlipEditRequestDialog',
  component: SlipEditRequestDialog,
  parameters: {
    layout: 'centered',
    docs: {
      description: {
        component:
          'PR-H3 SlipEditRequestDialog — 잠금된 슬립의 수정/삭제 요청 사유 입력 다이얼로그. ' +
          '본인 SALES 가 사유 (10~500자) 를 입력하면 SSE 채널 (`edit.requested` / `delete.requested`) 로 ' +
          '권한자(MANAGER+/MASTER) 에게 실시간 알림 push. ' +
          '잠금 정책 표 + 워크플로우 flow chart 는 docs/uiux/phase12/H3-edit-request-workflow.md 참조.',
      },
    },
  },
}
export default meta

type Story = StoryObj<typeof SlipEditRequestDialog>

/**
 * EditRequest — 1차 잠금 (SENT~PROCESSING) 슬립의 수정 요청 정상 경로.
 *
 * 본인 SALES 가 사유 50자 이상 입력 → [수정 요청 전송] 클릭 → MANAGER 에게 SSE push.
 * 본 story 는 다이얼로그 자체를 노출만 하며, 실제 mutation 은 호출자가 처리.
 */
export const EditRequest: Story = {
  name: 'EDIT 모드 (1차 잠금)',
  render: () => {
    const [open, setOpen] = useState(true)
    const [submitted, setSubmitted] = useState<string | null>(null)
    return (
      <div style={{ minWidth: 560 }}>
        <Button onClick={() => { setSubmitted(null); setOpen(true) }}>
          수정 요청 다이얼로그 열기
        </Button>
        {submitted ? (
          <p style={{ marginTop: 16, color: '#16A34A' }}>
            전송된 사유 ({submitted.length}자): {submitted}
          </p>
        ) : null}
        <SlipEditRequestDialog
          open={open}
          onClose={() => setOpen(false)}
          type="EDIT"
          slipNo="2026-05-09-001"
          submitting={false}
          errorMessage={null}
          onSubmit={(reason) => {
            setSubmitted(reason)
            setOpen(false)
          }}
        />
      </div>
    )
  },
  parameters: {
    docs: {
      description: {
        story:
          'EDIT 모드 — 1차 잠금 슬립 (status: SENT/ACCEPTED/PROCESSING/REJECTED) 의 수정 요청. ' +
          '사유 10자 미만이면 [전송] 비활성 + 인라인 에러 노출. 500자 카운터 표시. ' +
          'primary 색상 액션. 수락 시 24시간 unlock banner 노출 (별도 컴포넌트).',
      },
    },
  },
}

/**
 * DeleteRequest — 슬립 삭제 요청 (DRAFT/SAVED 외 모든 status).
 *
 * primary 액션이 빨강(danger) 으로 분기 + placeholder/안내 문구가 삭제 컨텍스트로 변환된다.
 * 삭제 = soft delete + CANCELED status + audit log 영구 보존.
 */
export const DeleteRequest: Story = {
  name: 'DELETE 모드 (삭제 요청)',
  render: () => {
    const [open, setOpen] = useState(true)
    const [submitted, setSubmitted] = useState<string | null>(null)
    return (
      <div style={{ minWidth: 560 }}>
        <Button variant="danger" onClick={() => { setSubmitted(null); setOpen(true) }}>
          삭제 요청 다이얼로그 열기
        </Button>
        {submitted ? (
          <p style={{ marginTop: 16, color: '#DC2626' }}>
            삭제 요청 전송됨 ({submitted.length}자): {submitted}
          </p>
        ) : null}
        <SlipEditRequestDialog
          open={open}
          onClose={() => setOpen(false)}
          type="DELETE"
          slipNo="2026-05-09-001"
          submitting={false}
          errorMessage={null}
          onSubmit={(reason) => {
            setSubmitted(reason)
            setOpen(false)
          }}
        />
      </div>
    )
  },
  parameters: {
    docs: {
      description: {
        story:
          'DELETE 모드 — 삭제 요청 다이얼로그. EDIT 와 동일한 사유 입력 UI 이지만 ' +
          'primary 액션이 빨강(danger variant) 으로 분기되며, placeholder/intro 문구가 ' +
          '삭제 컨텍스트로 변환된다. 수락 시 soft delete + status=CANCELED 즉시 실행.',
      },
    },
  },
}

/**
 * SubmittingWithError — 서버 에러 + 진행 중 상태 동시 demo.
 *
 * - errorMessage prop 으로 BE 에러 (예: "잠금 단계 검증 실패") 인라인 노출
 * - submitting=true 시 버튼 loading + close 차단 + textarea disabled
 */
export const SubmittingWithError: Story = {
  name: '서버 에러 + 전송 중',
  args: {
    open: true,
    onClose: () => {},
    type: 'EDIT',
    slipNo: '2026-05-09-007',
    submitting: true,
    errorMessage:
      '권한자(MANAGER) 에게 알림 발송 실패 — 네트워크 오류로 인해 재시도가 필요합니다. ' +
      '잠시 후 다시 시도해 주세요.',
    onSubmit: () => {},
  },
  parameters: {
    docs: {
      description: {
        story:
          'submitting=true + errorMessage 동시 demo. ' +
          '버튼 loading spinner + close 차단(X 버튼 hide) + textarea disabled + ' +
          '인라인 빨강 에러 메시지 표시. 사용자가 동일 요청을 이중 발사하는 것을 방지.',
      },
    },
  },
}
