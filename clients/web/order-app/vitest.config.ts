import { defineConfig } from 'vitest/config';

/**
 * order-app v4 vitest 설정 (Phase 7 3차).
 *
 * 본 프로젝트는 legacy partner-order/index.html 임베드 + 경량 shim 구조라
 * 단위 테스트 영역이 좁다. sanity 1건으로 시작해 .github/workflows/deploy-order-app.yml
 * 의 `npm test --if-present` gate 가 silent skip 대신 실 PASS 를 기록하도록 한다.
 *
 * jsdom 환경은 추후 shim DOM 검증 시 도입; 현재는 node 기본 환경.
 */
export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    environment: 'node',
    reporters: 'default',
    passWithNoTests: false,
  },
});
