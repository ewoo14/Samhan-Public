/**
 * 비밀번호 재설정 dialog — Phase 10 P0-2 (manual 06-트러블슈팅/01-로그인-실패.md §1).
 *
 * 2-step wizard:
 * 1) STEP 1 — 이메일 + loginId 입력 → POST /auth/password/reset/request
 *    (enumeration 방지를 위해 사용자 존재 여부와 무관하게 항상 200 OK)
 * 2) STEP 2 — 발송된 토큰 + 신규 비밀번호 입력 → POST /auth/password/reset/confirm
 *
 * 30분 만료 안내 (BE PasswordResetService.RESET_TOKEN_TTL).
 *
 * data-testid (DevOps spec):
 * - password-reset-email-input
 * - password-reset-token-input
 * - password-reset-new-password-input
 * - password-reset-submit-button
 */
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Button, FormField, Modal } from '@samhan/design-system'
import axios from 'axios'
import {
  confirmPasswordReset,
  getPasswordPolicy,
  requestPasswordReset,
} from '../api/passwordApi'

export interface PasswordResetDialogProps {
  open: boolean
  onClose: () => void
  /** 초기 loginId — login 실패 후 자동 prefill 용 (선택). */
  initialLoginId?: string
}

type Step = 'request' | 'confirm'

export function PasswordResetDialog({
  open,
  onClose,
  initialLoginId,
}: PasswordResetDialogProps) {
  const [step, setStep] = useState<Step>('request')
  const [loginId, setLoginId] = useState(initialLoginId ?? '')
  const [email, setEmail] = useState('')
  const [token, setToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordRepeat, setNewPasswordRepeat] = useState('')
  const [completed, setCompleted] = useState(false)

  // 정책 조회 — STEP 2 helper text 표시용
  const policyQuery = useQuery({
    queryKey: ['password-policy'],
    queryFn: getPasswordPolicy,
    enabled: open,
    staleTime: 5 * 60 * 1000,
  })

  const requestMutation = useMutation({
    mutationFn: () => requestPasswordReset({ loginId, email }),
    onSuccess: () => {
      setStep('confirm')
    },
  })

  const confirmMutation = useMutation({
    mutationFn: () => confirmPasswordReset({ token, newPassword }),
    onSuccess: () => {
      setCompleted(true)
    },
  })

  const handleRequestSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (requestMutation.isPending) return
    requestMutation.mutate()
  }

  const handleConfirmSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (confirmMutation.isPending) return
    if (newPassword !== newPasswordRepeat) return
    confirmMutation.mutate()
  }

  const handleClose = () => {
    // 모달 닫을 때 상태 초기화 (다음 열림에서 깨끗한 시작)
    setStep('request')
    setLoginId(initialLoginId ?? '')
    setEmail('')
    setToken('')
    setNewPassword('')
    setNewPasswordRepeat('')
    setCompleted(false)
    requestMutation.reset()
    confirmMutation.reset()
    onClose()
  }

  /** 사용자 친화 한국어 에러 메시지. */
  const errorOf = (err: unknown): string | null => {
    if (!err) return null
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string } | undefined
      return data?.message ?? '요청 처리 중 오류가 발생했습니다.'
    }
    return '알 수 없는 오류가 발생했습니다.'
  }

  const requestError = requestMutation.isError ? errorOf(requestMutation.error) : null
  const confirmError = confirmMutation.isError ? errorOf(confirmMutation.error) : null
  const passwordMismatch =
    newPassword.length > 0
    && newPasswordRepeat.length > 0
    && newPassword !== newPasswordRepeat

  // 입력 필드 공통 스타일 (LoginPage 와 동일 톤)
  const inputStyle: React.CSSProperties = {
    padding: '8px 12px',
    borderRadius: 6,
    border: '1px solid var(--color-neutral-300)',
    fontSize: 14,
    width: '100%',
    boxSizing: 'border-box',
  }

  if (completed) {
    return (
      <Modal
        open={open}
        onClose={handleClose}
        title="비밀번호 변경 완료"
        size="sm"
        footer={
          <Button variant="primary" onClick={handleClose}>
            확인
          </Button>
        }
      >
        <p style={{ margin: 0, lineHeight: 1.6 }}>
          비밀번호가 변경되었습니다. 새 비밀번호로 다시 로그인해 주세요.
        </p>
      </Modal>
    )
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={step === 'request' ? '비밀번호 재설정 요청' : '재설정 토큰 입력'}
      description={
        step === 'request'
          ? '가입 시 등록한 이메일로 재설정 링크(토큰)를 발송합니다. 메일이 도착하지 않아도 30분 안에 다시 시도해 주세요.'
          : '발송된 메일에 포함된 토큰과 새 비밀번호를 입력해 주세요. 토큰은 30분 후 만료됩니다.'
      }
      size="sm"
    >
      {step === 'request' ? (
        <form
          onSubmit={handleRequestSubmit}
          style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
        >
          <FormField
            label="사용자 ID"
            required
            render={({ id }) => (
              <input
                id={id}
                type="text"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                autoComplete="username"
                style={inputStyle}
              />
            )}
          />
          <FormField
            label="이메일"
            required
            render={({ id }) => (
              <input
                id={id}
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                data-testid="password-reset-email-input"
                style={inputStyle}
              />
            )}
          />
          {requestError ? (
            <div className="error-banner" role="alert">
              {requestError}
            </div>
          ) : null}
          <p
            style={{
              margin: 0,
              fontSize: 12,
              color: 'var(--color-neutral-600, #6B7280)',
              lineHeight: 1.5,
            }}
          >
            보안 정책에 따라 입력하신 ID/이메일이 등록되어 있는지 여부는 알려드리지 않습니다.
            등록된 계정인 경우 메일이 발송됩니다.
          </p>
          <div
            style={{
              display: 'flex',
              gap: 8,
              justifyContent: 'flex-end',
              marginTop: 4,
            }}
          >
            <Button type="button" variant="ghost" onClick={handleClose}>
              취소
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={requestMutation.isPending}
              disabled={!loginId || !email}
              data-testid="password-reset-submit-button"
            >
              재설정 메일 발송
            </Button>
          </div>
        </form>
      ) : (
        <form
          onSubmit={handleConfirmSubmit}
          style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
        >
          <FormField
            label="재설정 토큰"
            required
            render={({ id }) => (
              <input
                id={id}
                type="text"
                value={token}
                onChange={(e) => setToken(e.target.value)}
                autoComplete="one-time-code"
                placeholder="이메일로 받은 토큰을 붙여넣으세요"
                data-testid="password-reset-token-input"
                style={inputStyle}
              />
            )}
          />
          <FormField
            label="새 비밀번호"
            required
            render={({ id }) => (
              <input
                id={id}
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                autoComplete="new-password"
                data-testid="password-reset-new-password-input"
                style={inputStyle}
              />
            )}
          />
          <FormField
            label="새 비밀번호 확인"
            required
            render={({ id }) => (
              <input
                id={id}
                type="password"
                value={newPasswordRepeat}
                onChange={(e) => setNewPasswordRepeat(e.target.value)}
                autoComplete="new-password"
                style={inputStyle}
              />
            )}
          />
          {policyQuery.data ? (
            <p
              style={{
                margin: 0,
                fontSize: 12,
                color: 'var(--color-neutral-600, #6B7280)',
                lineHeight: 1.5,
              }}
              data-testid="password-policy-hint"
            >
              {policyQuery.data.description}
            </p>
          ) : null}
          {passwordMismatch ? (
            <div className="error-banner" role="alert">
              새 비밀번호와 확인 입력이 일치하지 않습니다.
            </div>
          ) : null}
          {confirmError ? (
            <div className="error-banner" role="alert">
              {confirmError}
            </div>
          ) : null}
          <div
            style={{
              display: 'flex',
              gap: 8,
              justifyContent: 'flex-end',
              marginTop: 4,
            }}
          >
            <Button
              type="button"
              variant="ghost"
              onClick={() => setStep('request')}
            >
              이전
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={confirmMutation.isPending}
              disabled={
                !token || !newPassword || !newPasswordRepeat || passwordMismatch
              }
            >
              비밀번호 변경
            </Button>
          </div>
        </form>
      )}
    </Modal>
  )
}
