import { useEffect, useId, useState } from 'react'
import styles from './SlipEditRequestDialog.module.css'
import { Modal } from '../Modal/Modal'
import { Button } from '../Button/Button'

/**
 * 전표 수정/삭제 요청 type — BE `SlipEditRequest.type` enum 과 1:1.
 *
 * - `EDIT`   사용자(작성자/SALES) 가 본인 전표의 헤더/라인 정정을 요청
 * - `DELETE` 사용자가 본인 전표의 취소(soft-delete) 를 요청
 *
 * CONFIRMED 단계에서만 노출 (DRAFT/SAVED 는 본인 직접 수정 가능, IN_INSPECTION+ 는 변경 자체 차단).
 */
export type SlipEditRequestType = 'EDIT' | 'DELETE'

/**
 * `<SlipEditRequestDialog>` props.
 *
 * @property open           모달 표시 여부
 * @property onClose        닫기 콜백 (취소 / X 버튼 / Esc)
 * @property type           요청 종류 (EDIT/DELETE) — 다이얼로그 제목/안내 텍스트가 분기됨
 * @property slipNo         전표번호 (사용자 노출 식별자) — 안내 문구에 표시. UUID 노출 금지.
 * @property submitting     서버 요청 진행 중 여부 (외부 mutation isPending)
 * @property errorMessage   서버 에러 메시지 (외부 mutation error → 사용자 노출 텍스트)
 * @property onSubmit       전송 콜백 — 사유(trim 결과, 10~500자) 를 인자로 호출. 호출자 mutation 발사.
 * @property minLength      사유 최소 길이 (기본 10)
 * @property maxLength      사유 최대 길이 (기본 500)
 */
export interface SlipEditRequestDialogProps {
  open: boolean
  onClose: () => void
  type: SlipEditRequestType
  slipNo: string
  submitting?: boolean
  errorMessage?: string | null
  onSubmit: (reason: string) => void
  minLength?: number
  maxLength?: number
}

const TYPE_LABEL: Record<SlipEditRequestType, string> = {
  EDIT: '수정',
  DELETE: '삭제',
}

/**
 * `<SlipEditRequestDialog>` — CONFIRMED 전표의 수정/삭제 요청 사유 입력 다이얼로그.
 *
 * <p>PR-H3 FE-1. 작성자(SALES) 가 본인 전표의 정정/취소를 요청 → 창고 직원(WAREHOUSE/MANAGER)
 * 이 별도 화면에서 수락/거절. 본 다이얼로그는 사유 textarea + 전송 버튼만 담당하며,
 * 실제 BE 호출은 호출자의 mutation 으로 처리한다 (`onSubmit(reason)`).
 *
 * <h3>UX 규칙</h3>
 * <ul>
 *   <li>사유 ≥ 10자 + ≤ 500자 강제 (전송 버튼 disabled / inline 에러).</li>
 *   <li>500자 카운터 표시.</li>
 *   <li>모달 열릴 때 사유 입력 초기화. 닫을 때도 초기화.</li>
 *   <li>submitting 중에는 X / Esc / 백드롭 모두 무시 (이중 호출 방지).</li>
 * </ul>
 *
 * <h3>UUID 비공개 가드</h3>
 * <p>본 컴포넌트는 slipId 를 받지 않는다. 사용자 노출 식별자는 {@code slipNo} 만 사용 (예:
 * "2026/05/04-1"). 호출자가 mutation 의 path variable 로 slipId 를 별도 관리한다.
 *
 * @example
 * ```tsx
 * <SlipEditRequestDialog
 *   open={open}
 *   onClose={() => setOpen(false)}
 *   type="EDIT"
 *   slipNo={slip.slipNo}
 *   submitting={mutation.isPending}
 *   errorMessage={mutation.error?.message ?? null}
 *   onSubmit={(reason) => mutation.mutate({ slipId: slip.id, type: 'EDIT', reason })}
 * />
 * ```
 */
export function SlipEditRequestDialog({
  open,
  onClose,
  type,
  slipNo,
  submitting = false,
  errorMessage = null,
  onSubmit,
  minLength = 10,
  maxLength = 500,
}: SlipEditRequestDialogProps) {
  const reactId = useId()
  const reasonId = `slip-edit-req-reason-${reactId}`

  const [reason, setReason] = useState('')

  // 모달 열고/닫을 때 입력 초기화
  useEffect(() => {
    if (!open) {
      setReason('')
    }
  }, [open])

  const trimmed = reason.trim()
  const tooShort = trimmed.length > 0 && trimmed.length < minLength
  const canSubmit = trimmed.length >= minLength && trimmed.length <= maxLength

  const typeLabel = TYPE_LABEL[type]
  const title = `전표 ${typeLabel} 요청`
  const description
    = type === 'EDIT'
      ? `[${slipNo}] 전표는 확정된 상태입니다. 창고 직원에게 수정 요청을 보냅니다.`
      : `[${slipNo}] 전표는 확정된 상태입니다. 창고 직원에게 삭제 요청을 보냅니다.`

  const handleSubmit = () => {
    if (!canSubmit || submitting) return
    onSubmit(trimmed)
  }

  // 닫기 시도 — submitting 중이면 무시 (Modal closeOnEsc/closeOnBackdropClick 도 차단)
  const handleClose = () => {
    if (submitting) return
    onClose()
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={title}
      size="md"
      closeOnEsc={!submitting}
      closeOnBackdropClick={!submitting}
      hideCloseButton={submitting}
      footer={
        <div className={styles['footer']}>
          <Button
            variant="ghost"
            onClick={handleClose}
            disabled={submitting}
            data-testid="slip-edit-request-dialog-cancel"
          >
            취소
          </Button>
          <Button
            variant={type === 'DELETE' ? 'danger' : 'primary'}
            onClick={handleSubmit}
            disabled={!canSubmit}
            loading={submitting}
            data-testid="slip-edit-request-dialog-submit"
          >
            {typeLabel} 요청 전송
          </Button>
        </div>
      }
    >
      <div className={styles['body']} data-testid="slip-edit-request-dialog">
        <p className={styles['intro']}>{description}</p>
        <label htmlFor={reasonId} className={styles['label']}>
          사유 (필수, 최소 {minLength}자)
        </label>
        <textarea
          id={reasonId}
          className={styles['textarea']}
          value={reason}
          onChange={(e) => setReason(e.target.value.slice(0, maxLength))}
          placeholder={
            type === 'EDIT'
              ? '예: 거래처 요청으로 수량을 5 → 7 로 변경 필요'
              : '예: 거래처 주문 취소로 인해 전표 삭제 필요'
          }
          maxLength={maxLength}
          aria-invalid={tooShort || undefined}
          aria-describedby={tooShort ? `${reasonId}-err` : undefined}
          disabled={submitting}
          data-testid="slip-edit-request-dialog-reason"
        />
        <div className={styles['counter']} aria-hidden="true">
          {trimmed.length} / {maxLength}
        </div>
        {tooShort ? (
          <p
            id={`${reasonId}-err`}
            className={styles['error']}
            role="alert"
            data-testid="slip-edit-request-dialog-error"
          >
            사유는 최소 {minLength}자 이상 입력해주세요.
          </p>
        ) : null}
        {errorMessage ? (
          <p
            className={styles['error']}
            role="alert"
            data-testid="slip-edit-request-dialog-server-error"
          >
            {errorMessage}
          </p>
        ) : null}
      </div>
    </Modal>
  )
}

export default SlipEditRequestDialog
