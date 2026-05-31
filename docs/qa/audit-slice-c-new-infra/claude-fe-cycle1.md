# audit Slice C FE cycle 1 리뷰 — claude-fe-cycle1.md

작성일: 2026-05-19
슬라이스: audit Slice C (PR #260) — 신규 인프라 3건 + DEFECT-C1 식별
검토자: FE agent (Claude)
판정: **APPROVE (DEFECT-C1 별도 슬라이스 분리 조건)**

---

## 총평

PR #260 의 FE 변경은 `clients/arologis-mobile/README.md` E2E 섹션 추가와
`docs/design/sp-d1-dynamic-rbac/arologis-desktop-policy.md` 정책 문서 신규 작성으로 구성된다.
실행 코드 변경은 0건이다. typecheck PASS, 코드 결함 없음. DEFECT-C1 은 scope 외 별도 슬라이스로
올바르게 분리되었으며 false green 가드(test.fixme 4건)가 정상 적용되어 있다.

---

## 검증 항목별 결과

### (1) arologis-mobile README.md E2E 섹션 검증

`clients/arologis-mobile/README.md` 에 E2E (Detox Android) 섹션이 추가되었다.
기재 내용 확인:

- 사전 조건: Node 20+, Java 17, Android Studio, AVD `Pixel_6_API_34` (Android 14)
- 빌드 명령: `npm run detox-build:arologis` / `npm run detox-test:arologis` (debug 변형 포함)
- 시나리오 표 3건: 앱 부팅/로그인 화면, 수동 입력 카드 testID, 빈 번호 Alert
- CI job 이름 명시: `.github/workflows/qa-e2e.yml` `detox-android-arologis`
- AVD 이름 변경 안내 (`qa/detox/.detoxrc.js` `arologisEmulator.device.avdName`)
- mobile-staff / mobile-v4 패턴과 구조 일관

기존 README 내용(빌드, 환경 변수, Pretendard, 디렉토리 구조, 인증 흐름, UUID 비공개 가드 등) 변경 없음.

**결함 없음.**

### (2) arologis-desktop SP-D1 hidden 정책 문서 검증

`docs/design/sp-d1-dynamic-rbac/arologis-desktop-policy.md` 의 Option B (비대상) 결정 근거를 검토했다.

근거 4항목이 논리적으로 타당하다:

1. arologis-desktop 접근 role 이 `AROLOGIS_MASTER` / `AROLOGIS_MANAGER` 두 가지뿐
2. 메뉴 2개 — 양 role 모두 전체 접근 가능, 숨길 대상 항목 없음
3. SP-D1 hidden 정책의 전제 조건(멀티 role 환경 + 동적 RBAC 매트릭스 + 카테고리 헤더) 미충족
4. 아로로지스 독립 운영 단위(`project_arologis_independent.md`) — Samhan Public RBAC 매트릭스와 분리

`AppLayout.tsx`, `ProtectedRoute.tsx`, `authStore.ts`, `routes/index.tsx` 현재 상태 표도 정확하다.
`routes/index.tsx` 는 `clients/arologis-desktop/src/renderer/routes/index.tsx` 로 존재가 확인되며
createHashRouter 기반 SP-D1 미적용 상태가 정상이다.

향후 의무 적용 조건 3항목(role 확대 / 메뉴 3개 이상 확대 / 동적 RBAC 범위 확대)도 명시되어 있어
미래 회귀 가드로 충분하다.

**결함 없음.**

### (3) DEFECT-C1 권고 검토 — 별도 슬라이스 분리

`docs/qa/signature-slice-C/spec-validation.md` DEFECT-C1 기재 내용을 검토했다.

- 미구현 항목 10건이 명시적으로 나열되어 있다 (`signature.js`, `mobile.css`, HTML 서빙 라우트 등)
- 영향 범위(SMS/카카오톡 링크 → 404)가 정확하다
- 권고 내용 5항목이 구체적이다 (signature.html 위치, vite entry, slip-service Controller, nginx 규칙, CSP 유지)
- Playwright spec 에서 FE 미구현 4건(`SC-6`, `SC-7`, `SC-9`, `SC-10`)을 `test.fixme()` 로 처리하여
  false green 불허 원칙이 준수되었다

별도 슬라이스(signature-slice-C-fe) 분리 결정은 적절하다. 본 PR 에서 FE 번들을 구현하지 않은 채
DEFECT-C1 을 명시하고 fixme 처리한 방식은 audit 체계상 올바른 접근이다.

**결함 없음. 권고 명확.**

---

## 결함 목록

결함 없음.

---

## 판정

**APPROVE**

FE 코드 변경 0건, typecheck PASS, README E2E 섹션 내용 정확, SP-D1 정책 문서 근거 타당,
DEFECT-C1 별도 슬라이스 분리 결정 적절. false green 가드(test.fixme 4건) 정상 적용 확인.
