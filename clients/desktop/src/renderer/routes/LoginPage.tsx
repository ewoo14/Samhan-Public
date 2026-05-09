/**
 * 로그인 화면 — 디자인 시스템 `Card` + `FormField` + `Input` + `Button` 사용.
 *
 * 흐름:
 * 1) 사용자가 loginId/password 입력
 * 2) `useMutation` 으로 `POST /auth/login` 호출
 * 3) 성공 시 메인 프로세스에 토큰 저장 (`window.samhanAuth.setToken`) +
 *    렌더러 세션 store 갱신 → `/` 로 navigate
 * 4) 실패 시 카드 안에 빨간 에러 배너 표시
 */
import { useState, type FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Card,
  FormField,
} from '@samhan/design-system'
import axios from 'axios'
import { login, type LoginResponse } from '../api/auth'
import { useSessionStore } from '../stores/session'

export function LoginPage() {
  const [loginId, setLoginId] = useState('')
  const [password, setPassword] = useState('')
  const setAuth = useSessionStore((s) => s.setAuth)
  const navigate = useNavigate()

  const mutation = useMutation<LoginResponse, unknown, void>({
    mutationFn: () => login({ loginId, password }),
    onSuccess: async (res) => {
      await setAuth({
        token: res.token,
        userId: res.userId,
        role: res.role,
        fullName: res.displayName,
      })
      navigate('/', { replace: true })
    },
  })

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (mutation.isPending) return
    mutation.mutate()
  }

  /**
   * 사용자에게 노출할 에러 메시지 — axios 응답 message 우선, 그다음 일반 텍스트.
   */
  const errorMessage = (() => {
    if (!mutation.isError) return null
    const err = mutation.error
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string } | undefined
      return data?.message ?? '로그인에 실패했습니다. 자격 증명을 확인하세요.'
    }
    return '알 수 없는 오류로 로그인하지 못했습니다.'
  })()

  return (
    <div className="login-shell">
      <Card padding={6} shadow="lg">
        <form className="login-card-inner" onSubmit={handleSubmit}>
          <h2 style={{ margin: 0, color: 'var(--color-brand-700)' }}>
            Samhan Public 로그인
          </h2>
          <FormField
            label="사용자 ID"
            required
            render={({ id }) => (
              <input
                id={id}
                type="text"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                autoFocus
                autoComplete="username"
                style={{
                  padding: '8px 12px',
                  borderRadius: 6,
                  border: '1px solid var(--color-neutral-300)',
                  fontSize: 14,
                }}
              />
            )}
          />
          <FormField
            label="비밀번호"
            required
            render={({ id }) => (
              <input
                id={id}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                style={{
                  padding: '8px 12px',
                  borderRadius: 6,
                  border: '1px solid var(--color-neutral-300)',
                  fontSize: 14,
                }}
              />
            )}
          />
          {errorMessage ? (
            <div className="error-banner" role="alert">
              {errorMessage}
            </div>
          ) : null}
          <Button
            type="submit"
            variant="primary"
            size="lg"
            fullWidth
            loading={mutation.isPending}
            disabled={!loginId || !password}
          >
            로그인
          </Button>
        </form>
      </Card>
    </div>
  )
}
