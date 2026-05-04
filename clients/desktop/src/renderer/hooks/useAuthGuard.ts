/**
 * 인증 가드 hook — `<AuthGuard>` 래퍼에서 사용.
 *
 * 동작:
 * 1) 세션이 아직 부팅 안 됐으면 `null` 반환 (호출자가 splash 처리)
 * 2) 부팅 완료 + 미인증 상태면 `/login` 으로 즉시 리다이렉트
 * 3) 인증된 상태면 현재 auth 스냅샷을 반환
 */
import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSessionStore } from '../stores/session'
import type { AuthSnapshot } from '../types/electron'

/**
 * 보호된 라우트에서 사용. 미인증 시 자동으로 로그인 화면으로 이동.
 *
 * @return 현재 인증 스냅샷, 또는 부팅 미완료/미인증 시 `null`
 */
export function useAuthGuard(): AuthSnapshot | null {
  const auth = useSessionStore((s) => s.auth)
  const bootstrapped = useSessionStore((s) => s.bootstrapped)
  const navigate = useNavigate()

  useEffect(() => {
    if (bootstrapped && !auth) {
      navigate('/login', { replace: true })
    }
  }, [bootstrapped, auth, navigate])

  return auth
}
