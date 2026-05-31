# Audit Slice 4 — FE Pretendard/lint QA Cycle 1

**날짜**: 2026-05-19
**검토자**: QA Agent (Claude)
**대상 커밋**: d7136105 fix(audit-slice-4): P1 FE Pretendard/lint 3건 통합 fix

---

## 1. 변경 범위 확인

| 파일 | 분류 | 내용 |
|---|---|---|
| `clients/arologis-mobile/App.tsx` | 모바일 FE | Pretendard 훅 마운트 + fontsReady 가드 |
| `clients/arologis-mobile/assets/fonts/*.otf` (4종) | 모바일 에셋 | Pretendard OTF 폰트 번들 |
| `clients/arologis-mobile/src/theme/usePretendardFontGuarded.ts` | 모바일 FE | expo-font graceful guard 훅 신규 |
| `clients/web/design-system/eslint.config.js` | 웹 DS | `eslint-plugin-react-hooks` 추가 |
| `clients/web/design-system/package.json` | 웹 DS | devDep 정렬 + `eslint-plugin-react-hooks ^7.1.1` |
| `clients/web/design-system/package-lock.json` | 웹 DS | lock 동기화 |
| `clients/web/design-system/src/index.ts` | 웹 DS | `./styles/fonts.css` side-effect import 추가 |

---

## 2. BE IT 영향

`services/` 변경 0건. SpringBootTest / Testcontainers / AbstractPostgresIT 영향 없음.

## 3. Playwright spec 영향

`qa/playwright/` 변경 0건. 기존 spec 회귀 위험 없음.

## 4. 스크린샷 / domain integrity 영향

`docs/qa/` 내 기존 시나리오 파일 변경 0건. SQL 정합성 검증 대상 없음.

## 5. FE lint 회귀 가드

- `eslint-plugin-react-hooks ^7.1.1` devDep 추가, `eslint.config.js` 에 플러그인 등록.
- `react-hooks/rules-of-hooks` = **warn** (error 아님) — Storybook render 함수 내 훅 허용 의도적 완화.
- `react-hooks/exhaustive-deps` = **warn** — 기존 55건 warnings 수준 유지, 0 errors 불변.
- `no-var-requires` / `no-require-imports` 인라인 eslint-disable 정당: graceful dynamic require 패턴으로 정적 import 불가.

회귀 위험: **없음**. warn 수준 추가이므로 기존 빌드/lint pipeline 에러 수 증가 없음.

## 6. arologis-mobile Detox 영향

- `e2e/` 디렉토리 미존재 — arologis-mobile 에 Detox 테스트 미구성 상태.
- `usePretendardFontGuarded` 훅: expo-font 미설치/에셋 누락 시 `ready=true` 즉시 반환 (graceful). Detox 에뮬레이터 환경에서 폰트 로딩 실패해도 앱 렌더링 차단 없음.
- App.tsx `if (!fontsReady)` 분기: SafeAreaProvider + StatusBar 만 반환 → Navigator 미마운트. 폰트 로딩 성공 후 Navigator 마운트. 정상 동작.

## 7. 회귀 위험 종합

| 영역 | 위험 수준 | 근거 |
|---|---|---|
| BE IT | 없음 | services/ 변경 0 |
| Playwright | 없음 | qa/playwright/ 변경 0 |
| design-system lint | 없음 | warn 추가, error 0 유지 |
| arologis-mobile Detox | 없음 | graceful guard, Detox 미구성 |
| fonts.css side-effect import | 낮음 | @font-face 는 cascade 비의존, URL 경로 `/fonts/` 서버 정적 배포 전제 |

---

## 8. 판정

**APPROVE**

BE/IT/Playwright/domain-integrity 영향 0건. FE lint 변경은 warn-level 추가로 회귀 없음. mobile Pretendard graceful guard 구현은 expo-font 미설치 환경에서도 앱 차단 없이 동작하며 mobile-staff 동명 훅 패턴과 일관됨. Detox 미구성 상태이므로 모바일 E2E 회귀 검증 대상 외.
