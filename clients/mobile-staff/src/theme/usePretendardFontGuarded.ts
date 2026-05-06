/**
 * usePretendardFontGuarded — Phase 7 4차 잔여 통일 폰트 hook (graceful guard).
 *
 * 본 mobile-staff (v3) 는 100% WebView wrapper — RN native Text/View 가 사실상 0개.
 * UI 의 폰트는 WebView 안 legacy estimate-app 의 Pretendard web font (`<link>`) 가 결정한다.
 *
 * 따라서 native font 등록 자체가 불필요. 그러나 5 client 통일 톤 정책 정비 일환으로
 * 본 hook 을 두어 다음 단계 (RN native screen 신규 추가 시) 진입점 마련.
 *
 * 동작 (현재):
 *  - `expo-font` 미설치 + asset 미존재 → 항상 `true` 반환 (no-op).
 *
 * 동작 (향후 native UI 추가 시):
 *  - `clients/mobile-staff/package.json` 에 `expo-font` 추가 + `assets/fonts/Pretendard-*.otf`
 *    배치 + 본 파일 안 `loadPretendardIfAvailable()` 본문 활성화 (현재는 주석).
 *
 * = "graceful 가드" — Hooks Rules 안전 (조건부 hook 호출 X), bundler / lint 가 정적
 * require 추적 시 fail 위험 X.
 */
export function usePretendardFontGuarded(): boolean {
  // 현재 (Phase 7 4차 잔여) — RN native UI 부재로 즉시 ready.
  // 후속 슬라이스에서 expo-font 추가 시 useFonts hook 도입 (본 함수가 그 시점에 hook 으로 변환).
  return true;
}
