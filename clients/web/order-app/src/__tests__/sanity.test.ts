import { describe, it, expect } from 'vitest';

/**
 * order-app v4 환경 sanity 테스트 (Phase 7 3차).
 *
 * 목적: deploy workflow 의 `npm test --if-present` gate 가 silent skip 대신
 * 실제 vitest 실행 + PASS 를 기록하도록 보장. 추가 shim/DOM 단위 테스트는
 * 후속 슬라이스에서 도입.
 */
describe('order-app sanity', () => {
  it('환경 sanity 검증 — vitest 실행 가능', () => {
    expect(1 + 1).toBe(2);
  });

  it('runtime 기본 글로벌 객체 접근 (process/Node 의존성 없음)', () => {
    expect(typeof globalThis).toBe('object');
    expect(Number.isFinite(0.99)).toBe(true);
  });
});
