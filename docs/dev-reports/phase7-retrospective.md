# Phase 7 회고 보고서

## 1. 개요

Phase 7 (호스팅 인프라 + e2e QA + 운영 가드 + UI 통합) 의 모든 슬라이스가 머지를 완료하여,
본 보고서로 슬라이스별 산출, 학습 매트릭스, Phase 8 위임 항목을 정리한다.

- 시작 commit (Phase 6 종료 후 첫 머지): PR #81 (Phase 7 1차)
- 종료 commit: 본 PR (Phase 7 5/6차 통합 — DevOps 후속 3 + QA 후속 1)
- 총 머지 PR 수: 7건 (#81 / #82 / #83 / #84 / #85 / #86 / 본 PR)

## 2. 머지된 PR 목록 (Phase 7)

| PR | 차수 | 제목/요지 | 비고 |
|---|---|---|---|
| #81 | 1차 | 카페24 SSH dry-run + Render Blueprint + QA 60 cell | env 이름 정정 + OOM 가드 + autoDeploy 비활성 + action SHA pin |
| #82 | 2차 | QA edge + visual + FE schema + DevOps + Detox 6 | CSP / getStock schema / Slack 비동기 / visual selector |
| #83 | 3차 | product by-code + QA tautology + FE selector + DevOps render+vitest + Designer dark-mode | reviewer 토론 종합 적용 |
| #84 | 4차 | DS 토큰 + body 바인딩 + toggleTheme + visual baseline | dark-mode 정식 도입 |
| #85 | 5차 (docs) | README + ROADMAP.md 신규 + 각 client/service README 갱신 + DECISIONS Phase 7 추가 | 문서 통합 |
| #86 | 4차 잔여 | 통일 토큰 (폰트 / spacing / radius / shadow) + Pretendard web font + RN graceful 폰트 hook | UI 통합 정착 |
| 본 PR | 5/6차 | DevOps self-host font + helmet+CSP + desktop CSP + QA fonts.ready 가드 + Phase 7 회고 + Phase 8 진입 plan | jsdelivr SPOF 회피 + Phase 8 위임 |

## 3. 학습 사항 매트릭스

### 3.1 PR 발행 패턴 (Phase 6 학습 강화)

| 학습 | 트리거 | 적용 |
|---|---|---|
| TM 종합 + 5 reviewer 토론 + 종합 TM 패턴 정착 | Phase 7 1~3차 모두 단일 통합 PR 발행 | 메모리 가드 4종 추가 |
| 단편 PR 금지 + 통합 PR 의무 일관 적용 | Phase 6 PR #66/71/74/77/78/79 close 회고 | Phase 7 1차부터 일관 |
| Blocker 우선 fix → nit 후속 분리 패턴 | PR #83 reviewer 토론 (BE/FE/Designer/QA/DevOps 5 평행) | Blocker fix → 통합 머지, nit 는 후속 통합 |

### 3.2 cascade 우선순위 (CSS) 패턴

| 학습 | 트리거 | 적용 |
|---|---|---|
| 신규 토큰 cascade 후순위 위치 (legacy 보존) | order-app v4 / estimate-app v2 의 legacy `<style>` 안 body font-family 보존 의무 | head 의 `@font-face` 는 등록만 + 신규 토큰은 `html` selector 에만 적용 |
| FOUC 방지 = html, body 양쪽 data-theme 적용 | PR #84 dark-mode 적용 시 body 만 적용하면 html 배경 light 깜빡 | tokens.css 에서 `html[data-theme="dark"], body[data-theme="dark"]` selector |
| WCAG AA 대비비 4.5:1 충족 | PR #84 reviewer 토론 (Designer P1) | dark text-tertiary 색 #888 → #9a9a9a 갱신 |

### 3.3 외부 CDN 의존 회피 (self-host)

| 학습 | 트리거 | 적용 |
|---|---|---|
| jsdelivr CDN SPOF 회피 = self-host 의무 | 외부 font CDN 장애 시 FOUC + 한글 깨짐 | `scripts/download-pretendard-fonts.sh` + `public/fonts/` |
| CSP `font-src 'self'` 만 허용 | 운영 환경 외부 네트워크 출구 차단 정책 호환 | Pretendard self-host 적용 후 외부 font 도메인 제거 |
| Express helmet middleware 정식화 | inline CSP middleware 보다 보강 (HSTS / X-DNS-Prefetch-Control 등 추가) | `helmet` dependency 도입 |

### 3.4 visual regression baseline 가드

| 학습 | 트리거 | 적용 |
|---|---|---|
| visual baseline = staging stack 의존 | backend 미가동 시 baseline 의미 없음 | 5 spec 모두 `isBackendAvailable` skip 가드 |
| 폰트 로드 race 방지 = `document.fonts.ready` 대기 | self-host 적용 후에도 woff2 fetch 비동기 | 5 spec 모두 `await page.evaluate(() => document.fonts.ready)` |
| baseline PNG 생성 = staging stack 활성 후 별도 수행 | CI 환경에서 6 PNG 미생성 시 spec skip | Phase 8 활성 후 `playwright test --update-snapshots` 별도 PR |

## 4. Phase 7 미결 (Phase 8 위임)

| ID  | 주제                                       | 위임 사유          | 답변 시점          |
| --- | ------------------------------------------ | ------------------ | ------------------ |
| D6  | 카페24 SSH 배포 대상 앱                    | 카페24 plan 업그레이드 결정 미정 | Phase 8 진입 답변  |
| D7  | 카페24 호스트 내 배포 디렉토리             | D6 답변 후         | Phase 8 진입 답변  |
| D8  | 카페24 pm2 process 명명 규약               | D6 / D7 답변 후    | Phase 8 진입 답변  |
| D9  | 14 backend MSA 운영 호스팅 옵션 (X1 ~ X4) | 호스팅 결정 회의   | Phase 8 1주차      |

| 산출 | 위임 사유 | 후속 |
|---|---|---|
| visual baseline 6 PNG (staging 생성) | staging stack 미가동 | Phase 8 staging endpoint 활성 후 별 PR |
| 14 backend MSA production cutover | D9 답변 의존 | Phase 8 1~3주차 |
| Render production cutover (estimate-app v2) | DNS + smoke 의존 | Phase 8 4주차 |

## 5. Phase 8 진입 plan

상세는 `docs/migration/phase8/M-PHASE-8-readiness.md` 참조.

요약:

- W1 — 호스팅 결정 + DB 준비
- W2 — Eureka cluster + Resilience4j prod
- W3 — API Gateway + 모니터링
- W4 — DNS cutover + smoke
- W5 — 운영 안정화 + 회고

## 6. 참조

- 누적 결정: `migration/decisions/DECISIONS.md`
- Phase 6 회고: `docs/dev-reports/phase6-retrospective.md`
- Phase 7 1~4차 dev report: `docs/dev-reports/phase7-step-{1,2,3,4}.md`
- Phase 8 진입 plan: `docs/migration/phase8/M-PHASE-8-readiness.md`
