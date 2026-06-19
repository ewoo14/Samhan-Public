import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

/**
 * design-system 컴포넌트 단위 테스트 설정.
 *
 * DOM 렌더링 계약을 검증하므로 jsdom 환경에서 `*.test.tsx`와 기존 contract 테스트를 실행한다.
 */
export default defineConfig({
  plugins: [react()],
  test: {
    include: ['src/**/*.test.tsx'],
    environment: 'jsdom',
    setupFiles: ['vitest.setup.ts'],
    reporters: 'default',
    passWithNoTests: false,
  },
})
