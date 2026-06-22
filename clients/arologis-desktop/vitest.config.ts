import { defineConfig } from 'vitest/config'

/**
 * arologis-desktop vitest 설정.
 *
 * renderer 권한 유틸과 React 컴포넌트 단위 테스트를 jsdom 환경에서 실행한다.
 */
export default defineConfig({
  test: {
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    reporters: 'default',
    passWithNoTests: false,
  },
})
