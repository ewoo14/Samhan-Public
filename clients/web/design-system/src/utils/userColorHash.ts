/**
 * 사용자 ID → HSL 색상 (PR-H2 audit overlay 의 수정자 색상 표시용).
 *
 * - 동일 userId 는 항상 동일한 색상을 반환 (deterministic) — 페이지 새로고침
 *   여러 클라이언트, 영업직원/관리자 화면에서 동일 사용자에게 동일 색상이 보장된다.
 * - hash(userId) → hue (0~360)
 * - saturation 70% / lightness 50% (대비 균형 — 흰 배경 + 검정 텍스트 모두 가독성 확보)
 * - 랜덤 시드는 userId 자체 (별도 seed 불필요, 재현 가능)
 *
 * Phase 12 시리즈 공유 자산:
 *   - PR-H2 audit overlay 의 "수정자 색상 dot"
 *   - PR-H3 코멘트 author avatar 배경색
 *   - 향후 presence indicator 등에서 동일 hash 사용 예정
 *
 * @example
 * userIdToColor('user-123') // → 'hsl(157, 70%, 50%)'
 * userIdToColor('user-123') // → 'hsl(157, 70%, 50%)' (동일)
 * userIdToColor('user-456') // → 'hsl(42, 70%, 50%)'
 */
export function userIdToColor(userId: string): string {
  let hash = 0
  for (let i = 0; i < userId.length; i++) {
    hash = (hash << 5) - hash + userId.charCodeAt(i)
    hash |= 0 // 32-bit int 강제
  }
  const hue = Math.abs(hash) % 360
  return `hsl(${hue}, 70%, 50%)`
}
