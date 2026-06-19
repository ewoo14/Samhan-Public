# 삼한이 마스코트 공용 적용 (로딩 + 빈 상태)

> 2026-06-19 개발책임자 지시: docs/character '삼한이' 마스코트를 로딩 등 여러 곳에 gif 자연스러운 움직임으로 적용. 범위 확정 = **공용 로딩+빈상태, 데스크톱+웹 전체**.

## 0. 자산 (PM 준비 완료)
- 원본: `docs/character/`(개발책임자 제공 — KakaoTalk gif 3.68MB + char_01~08.png 8프레임, 비최적).
- **최적화 산출**(Pillow, 크롭+투명배경+150px): `clients/web/design-system/src/assets/mascot/`
  - `samhani.webp` (8프레임 애니메이션, loop, ~70KB) — 로더용.
  - `samhani-static.png` (단일 프레임, ~26KB) — 정적/reduced-motion 폴백.
- 캐릭터 = 파란 라인아트 공기/바람 정령(삼한공조 컨셉).

## 1. 컴포넌트 (Codex — design-system)
- **`<MascotLoader>`**: 애니메이션 삼한이(`samhani.webp`) + optional `label`(기본 "불러오는 중") + size(sm 48 / md 80 / lg 120). `role="status"` `aria-label`. **prefers-reduced-motion: reduce → `samhani-static.png`**(애니메이션 정지).
- **`<MascotEmptyState>`**: 정적 삼한이 + `title` + optional `description` + optional `action`(slot/children). 빈 목록/결과 없음용.
- `index.ts` export. Spinner 는 유지(저수준), Mascot* 가 브랜드 표면.

## 2. 배선 (Codex — 공용 전파 우선, 105개 개별 금지)
- **고레버리지**: 
  - 공용 `DataTable`/`DataGrid` 의 **로딩 상태 → MascotLoader**, **빈 상태(empty/noData slot) → MascotEmptyState**(전 소비처 자동 반영).
  - 라우트/페이지 레벨 **로딩 스피너 → MascotLoader**(데스크톱 AppLayout suspense/route loading, 웹 estimate-app/order-app 초기 로딩).
- **앱**: 데스크톱(clients/desktop) + 웹(estimate-app, order-app). 자산은 design-system 에서 import(소비 앱 빌드가 자산 번들 — Vite asset). mobile/arologis 는 본 슬라이스 제외(후속).
- 기존 ad-hoc "데이터 없음" 텍스트 중 주요 목록(거래처/품목/전표/주문 리스트)만 MascotEmptyState 로 교체(전수 아님).

## 3. parity / 안전
- 순수 추가(브랜드 표면). 기능/계약/금액 무관. 기존 Spinner 유지(점진 교체).
- 자산 번들: design-system → 소비 앱 Vite 가 webp/png 처리(import URL). 번들 크기 +~96KB(1회 캐시).
- a11y: role/aria + reduced-motion 정적 폴백.

## 4. 검증
- design-system 빌드 + 소비 앱(desktop/estimate-app) 타입체크/빌드 green.
- **Docker/로컬 실QA**: 데스크톱+웹 실 기동 → 로딩 중 MascotLoader 애니메이션 + 빈 목록 MascotEmptyState 실 캡처(PR 인라인). reduced-motion 정적 확인.
- 자산 로드(webp) 네트워크 확인.

## 5. 리뷰
조기 PR → Codex 구현 → Opus(컴포넌트 a11y/reduced-motion·배선 누락·자산 번들) + Codex 교차 → Docker 실QA 스크린샷 → 머지. FE/Designer 포커스.
