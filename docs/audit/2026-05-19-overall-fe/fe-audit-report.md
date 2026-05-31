# FE 전체 Audit 보고서 — 2026-05-19

> UI/UX 깊이 점검 SKIP (Figma 완성 후 진행 — 개발책임자 결정)

---

## 1. typecheck / lint / build 결과

| Client | typecheck | lint | build | 비고 |
|---|---|---|---|---|
| `clients/web/design-system` | PASS | **FAIL** (exit 1) | PASS | lint 오류 2건 (상세 §1-1) |
| `clients/web/order-app` | PASS | PASS | PASS | |
| `clients/web/estimate-app` | typecheck 스크립트 없음 | lint 스크립트 없음 | `node server.js` (빌드 단계 없음) | SSR Express 서버 — Vite 빌드 불해당 |
| `clients/desktop` | PASS | PASS (경고 2건) | PASS | |
| `clients/arologis-desktop` | PASS | PASS | PASS | |
| `clients/mobile-staff` | PASS | lint 스크립트 없음 | PASS (expo export web) | eslint.config 미존재 — Minor |
| `clients/arologis-mobile` | PASS | lint 스크립트 없음 | PASS (expo export web) | eslint.config 미존재 — Minor |

### 1-1. design-system lint 오류 (P1)

```
src/components/SignaturePad/SignaturePad.stories.tsx
  33:5  error  Definition for rule 'react-hooks/rules-of-hooks' was not found
  37:5  error  Definition for rule 'react-hooks/rules-of-hooks' was not found
```

원인: `eslint.config.mjs` 에 `@typescript-eslint` 플러그인만 등록, `eslint-plugin-react-hooks` 미등록.  
Stories 파일에 `eslint-disable react-hooks/rules-of-hooks` 주석이 존재하나 플러그인 자체가 없어 "rule not found" 오류로 전환됨.

### 1-2. 경고 목록 (P2/Minor)

| 위치 | 내용 | 등급 |
|---|---|---|
| `design-system/SignaturePad.tsx:123` | `getPoint` 미사용 변수 | Minor |
| `desktop/PurchaseSlipPrintPage.tsx:66` | `totalQty` 미사용 변수 | Minor |
| `desktop/mock.ts:4148` | 불필요 eslint-disable 지시어 | Minor |
| `design-system/DataGrid.stories.tsx:144` | 불필요 eslint-disable 지시어 | Minor |

---

## 2. design-system import 일관성

- `clients/desktop`: 121개 `@samhan/design-system` import — Button / Card / Badge / Input / Spinner 등 직접 사용 확인. 자체 재작성 위반 없음.
- `clients/arologis-desktop`: 8개 import — `package.json` 에 `"@samhan/design-system": "file:../web/design-system"` 의존성 정상. Badge / Button / Card / DataGrid / Spinner 직접 사용 확인.
- `clients/mobile-staff` / `clients/arologis-mobile`: RN 환경이므로 web 컴포넌트 직접 import 불해당. 자체 토큰(`tokens.ts`)에서 색상/타이포 변수 참조하는 방식으로 일관성 유지.
- 자체 Button/Badge/Input 재작성 위반 없음 (모두 design-system import 사용 또는 RN 제약 환경).

---

## 3. API contract 일관

- `clients/desktop`: `ApiResponse` wrapper 9건 이상 사용 확인. `axios` interceptor에서 401 refresh 패턴 적용.
- `clients/arologis-desktop`: `ApiEnvelope<T>` 타입 + `apiClient` 공통 클라이언트 일관 사용. 401 refresh interceptor 확인.
- 403 처리: `ForbiddenPage.tsx` 존재, 라우터에서 `PermissionGuard` + `RoleGuard` 이중 적용.
- 404 처리: `PasswordResetRequestPage` / `SalesPartnerOrderDetailPage` 등에서 `error.response?.status === 404` 분기 처리.
- 새 BE endpoint (SP-08-FU2) FE 영향: `order-app` / `desktop` typecheck PASS 이므로 계약 파단 없음.

---

## 4. RBAC 가드 일관 (SP-D1~D5)

- `usePermissions` hook 사용처: `clients/desktop/src` 내 175건 확인.
- `PermissionMatrixPage.tsx` 파일 존재 — `/c/dev/SamhanLogis/clients/desktop/src/renderer/routes/PermissionMatrixPage.tsx`.
- `PermissionGuard` 이중 가드 (`SP-D4` 마이그레이션): typecheck PASS이므로 회귀 없음.
- 사이드바 hidden 정책 (SP-D1): desktop typecheck/build PASS로 정상 유지 추정.

---

## 5. UUID 비공개 (`feedback_uuid_no_user_visibility`)

- 전 client TSX/TS 파일 grep 결과: UUID 관련 식별자는 모두 JSDoc 주석/가드 코멘트에만 등장.
- 실제 렌더 노출 패턴 없음 (`{id}` / `{uuid}` 형태의 JSX expression 없음).
- `VehicleMatchStatusBadge`, `DispatchDetailPage`, `DriverManagementPage`, `LoginPage` 등에서 UUID 차단 명시 주석 + `driverCode` / `slipNo` 만 노출하는 패턴 유지 확인.
- `arologis-mobile` — `DriverDashboardScreen.tsx` UUID-free guard 단위 테스트 존재.
- 위반 없음.

---

## 6. Pretendard 폰트 self-host

| Client | 상태 | 비고 |
|---|---|---|
| `clients/web/design-system` | `fonts.css` @font-face 선언 존재 (Variable + Regular + Bold 3종) | 실제 woff2 파일은 `.gitignore` 제외 — `scripts/download-pretendard-fonts.sh` 실행 필요 |
| `clients/web/order-app` | `public/fonts/fonts.css` + README 존재, woff2 미포함 | 동일 스크립트 선행 필요 (빌드 PASS — index.html 참조) |
| `clients/desktop` | `global.css`에서 `@samhan/design-system/style.css` import → Pretendard `font-family` 참조 | design-system dist에 @font-face 미포함 — 런타임 폰트 fallback 위험 (P1) |
| `clients/mobile-staff` | `assets/fonts/` 4 weight OTF 존재 확인 (`Pretendard-Bold/Medium/Regular/SemiBold.otf`) | `usePretendardFontGuarded` hook 정상 구현 |
| `clients/arologis-mobile` | `assets/fonts/` OTF 없음 (`find` 결과 없음) | `App.tsx` 주석에 font load 언급하나 hook 미호출 + 파일 없음 — P1 |

---

## 결함 요약

| 등급 | 항목 | 위치 |
|---|---|---|
| **P1** | design-system lint exit 1 — `react-hooks` 플러그인 미등록 | `clients/web/design-system/eslint.config.mjs` |
| **P1** | `clients/desktop` Pretendard @font-face 미선언 — design-system 빌드 CSS에 @font-face 미포함으로 런타임 fallback 위험 | `clients/web/design-system/src/styles/fonts.css` ↔ dist |
| **P1** | `clients/arologis-mobile` Pretendard OTF 파일 없음 + App.tsx font load hook 미호출 | `clients/arologis-mobile/assets/fonts/` |
| **P2** | `clients/mobile-staff` / `clients/arologis-mobile` lint 스크립트 없음 | `package.json scripts` |
| **Minor** | 미사용 변수 2건 (`getPoint`, `totalQty`) | design-system / desktop |
| **Minor** | 불필요 eslint-disable 지시어 2건 | design-system / desktop |

---

> 코드 수정 범위 외 (audit only). P1 수정 시 통합 PR 패턴 (`feedback_integrated_pr_pattern`) 적용 필요.
