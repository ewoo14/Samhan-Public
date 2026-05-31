## designer 사이클 1 리뷰 (head `7cbbd13b`)

### 결함 표

| # | 심각도 | 위치 | 내용 |
|---|---|---|---|
| D-1 | MAJOR | `SlipDetailPage.tsx` L444-446 | 422 검수 차단 처리 `alert()` 네이티브 다이얼로그 — 요구사항 "danger-50 배경 + danger-700 텍스트" 인라인 배너. PNG 02 mock 과 실 코드 불일치. Modal 내 `.danger-banner` div 로 렌더 |
| D-2 | MINOR | `SlipDetailPage.tsx` L2024 | 409 충돌 배너 `className="error-banner"` — `.danger-banner` (SP-08-5-3 신규) 로 교체. `.error-banner` hardcoded `#fdecec` 토큰 미참조 |
| D-3 | MINOR | `SlipDetailPage.tsx` L2019 | "삭제된 전표는 복구할 수 없습니다" inline `style={{ color: 'var(--color-danger-700, #B91C1C)' }}` — `.danger-banner` className 으로 통합 + fallback `#B91C1C` 제거 |
| D-4 | INFO | `global.css` L3019 | `.danger-banner` color `--color-danger-700` vs `.warning-banner` color `--color-warning-800` — 단계 불일치. 800 통일 또는 의도 주석 |

### 긍정 사항

- `.danger-banner` CSS 패턴 `.warning-banner` 완전 동일 (padding/margin/border-radius/border/background/color/font-size) — SP-08-5-2 일관
- `--color-danger-50/300/700` design-system tokens.css 참조, 하드코딩 없음
- PNG 04 권한 가드 — INVENTORY 삭제 버튼 미렌더 + 비즈니스 식별자, UUID 미노출
- PNG 01/03 한국어 라벨 ("매입 전표 삭제", "삭제", "취소", "전표가 삭제되었습니다") 정합
- `canDirectDeletePurchase` (WAREHOUSE/MANAGER/MASTER + DRAFT/SAVED) FE 가드 명확

### 종합

CHANGES REQUESTED — D-1 핵심 (422 alert → Modal 내 `.danger-banner`). D-2 토큰 일관성 동반 수정.

**designer agent — 2026-05-18**
