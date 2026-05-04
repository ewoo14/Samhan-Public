/**
 * 보호된 라우트 래퍼 — 미인증 사용자는 `/login` 으로 자동 이동.
 *
 * 부팅 직후 (`bootstrapped: false`) 에는 spinner 만 노출하여,
 * 메인 프로세스에서 토큰을 읽어오는 동안 화면 깜빡임을 방지한다.
 */
import type { ReactNode } from 'react'
import { Spinner } from '@samhan/design-system'
import { useAuthGuard } from '../hooks/useAuthGuard'
import { useSessionStore } from '../stores/session'

interface AuthGuardProps {
  children: ReactNode
}

export function AuthGuard({ children }: AuthGuardProps) {
  const bootstrapped = useSessionStore((s) => s.bootstrapped)
  const auth = useAuthGuard()

  if (!bootstrapped) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: '100vh' }}>
        <Spinner size="lg" label="세션 확인 중" />
      </div>
    )
  }

  if (!auth) {
    // useAuthGuard 가 redirect 를 트리거 — 그 사이에는 빈 화면.
    return null
  }

  return <>{children}</>
}
