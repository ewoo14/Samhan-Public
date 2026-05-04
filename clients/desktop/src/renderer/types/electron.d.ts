/**
 * 렌더러용 ambient 타입 — preload 가 contextBridge 로 노출한
 * `window.samhanAuth` API 의 시그니처를 TypeScript 에 알려준다.
 *
 * 본 파일은 import/export 가 없는 ambient 모듈이며, tsconfig `include`
 * 에 잡혀 있는 한 별도 import 없이 전역 타입으로 인식된다.
 */
export interface AuthSnapshot {
  token: string
  userId: string
  role: string
  fullName: string
}

declare global {
  interface Window {
    /**
     * 메인 프로세스 인증 토큰 게이트웨이.
     * 모든 메서드는 IPC 비동기 호출이다.
     */
    samhanAuth: {
      getToken: () => Promise<AuthSnapshot | null>
      setToken: (payload: AuthSnapshot) => Promise<void>
      clearToken: () => Promise<void>
    }
  }
}

export {}
