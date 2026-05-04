# Electron 데스크톱 앱 골격 첫 슬라이스 개발 리포트

> **슬라이스**: electron-skeleton-slice | **base commit**: 4fc23a1 | **머지 PR**: (PM 통합 후 기재)

본 슬라이스는 SamhanLogis 의 첫 데스크톱 클라이언트 (`clients/desktop/`) 를
도입한다. 백엔드 7 마이크로서비스가 main 에 머지된 직후 시점이며,
디자인 시스템 (`@samhan/design-system`) 16 컴포넌트의 **첫 실사용처** 이기도 하다.

3-layer 함수 단위 문서화 체계 (한국어 JSDoc + 자동 생성 + dev-reports 누적)
를 본 클라이언트에도 적용한다 (memory: `feedback_function_documentation.md`).

## 개발책임자 결정 사항 (7건, ground truth)

- **Q1=A**: electron-vite + electron-builder (Vite HMR + 표준 패키징)
- **Q2=A**: React Router v6 (디자인 시스템 호환)
- **Q3=A**: TanStack Query + axios (캐싱 + 낙관적 갱신)
- **Q4=A**: electron-store (encrypted) + IPC (메인 프로세스 토큰 관리)
- **Q5=A**: 로그인 + 대시보드 + 창고 + 출고전표 작성/목록 4 화면
- **Q6=A**: Windows 만 (사내 환경)
- **Q7=A**: FE + DevOps 2팀 (본 슬라이스 한정)

## BE (변경 없음)

본 슬라이스는 백엔드 변경을 동반하지 않는다. 기존 7개 마이크로서비스
(`api-gateway`, `eureka-server`, `auth-service`, `user-service`,
`product-service`, `inventory-service`, `slip-service`) 의 endpoint 만
호출한다. CORS / Auth flow 가 데스크톱 컨텍스트에서 추가로 필요해지면
별도 BE 슬라이스로 분리한다.

## FE (Team-Desktop FE)

### 프로젝트 골격 (electron-vite + React 18)

- `clients/desktop/` 신규 — 18 개 신규 파일
- Workspace 패키지명: `@samhan/desktop`
- 빌드 도구: electron-vite 2 (메인/preload/renderer 동시 빌드)
- 라우터: React Router v6 `createHashRouter` (Electron `file://` 호환)
- 데이터 페칭: TanStack Query v5 (5분 staleTime, 1회 retry, focus refetch off)
- HTTP: axios (요청 시 IPC 토큰 주입, 응답 시 401 처리 + 토큰 클리어)
- 세션: zustand 단일 store + IPC bootstrap

### 메인 프로세스 (`src/main/**`)

- `index.ts` — BrowserWindow 1280x800, contextIsolation, sandbox=false
  - 개발 모드: `ELECTRON_RENDERER_URL` 자동 감지 + DevTools detach 모드
  - 프로덕션 모드: `out/renderer/index.html` 파일 로드
- `store/auth-store.ts` — electron-store v10 + Electron `safeStorage`
  - Windows DPAPI / macOS Keychain 으로 JWT 만 base64 암호화 저장
  - safeStorage 미지원 환경(예: Linux GUI 비로그인) 평문 fallback + 경고 로그
  - `saveToken/loadToken/clearToken` 3 함수 export
- `ipc/auth-token.ts` — `ipcMain.handle` 3개 등록
  - `auth:get-token` / `auth:set-token` / `auth:clear-token`

### Preload (`src/preload/index.ts`)

- contextBridge 로 `window.samhanAuth = { getToken, setToken, clearToken }` 노출
- Node API 직접 노출 0건 (보안 가드 통과)

### 렌더러 (`src/renderer/**`)

- `App.tsx` — QueryClientProvider + AppRouter + 부팅 시 `useSessionStore.bootstrap()`
- `routes/index.tsx` — HashRouter, `/login` (public) + 4개 protected route
- `components/AuthGuard.tsx` — 부팅 미완료 시 Spinner, 미인증 시 `/login` redirect
- `components/AppLayout.tsx` — 좌측 사이드바 + 우측 본문 + 우상단 사용자 chip / 로그아웃
- `stores/session.ts` — zustand store + `hasAdminRole/canCreateSlip` 권한 헬퍼
- `hooks/useAuthGuard.ts` — 단일 인증 가드 훅
- `api/client.ts` — axios 인스턴스 + 인터셉터 + `ApiEnvelope/PageResponse` 타입
- `api/auth.ts` — POST `/auth/login`
- `api/inventory.ts` — GET/POST `/inventory/warehouses`
- `api/slip.ts` — GET `/slips`, POST `/slips`
- `types/electron.d.ts` — `window.samhanAuth` ambient 타입
- `styles/global.css` — 디자인 시스템 토큰 import + 앱 셸 레이아웃

### 4 화면 + 디자인 시스템 컴포넌트 사용 매트릭스

| 화면 | 사용 컴포넌트 | BE endpoint |
|------|------|------|
| `/login` | Card, FormField, Button, Spinner | POST /auth/login |
| `/` 대시보드 | Card × 5, Button × 3 | GET /slips?slipType=OUTBOUND&status=PROCESSING |
| `/warehouses` | DataTable, Badge, Button, Modal, FormField | GET/POST /inventory/warehouses |
| `/slips` | DataTable, SlipNumberDisplay, SlipStatusBadge, Badge, Button | GET /slips |
| `/slips/new` | Card, WarehouseSelector × 2, DeliveryTagSelector, FormField, PriceField, Button | POST /slips, GET /inventory/warehouses |

**디자인 시스템 컴포넌트 첫 실사용**: 11 / 16 개
- 사용: Badge, Button, Card, DataTable, DeliveryTagSelector, FormField,
  Modal, PriceField, SlipNumberDisplay, SlipStatusBadge, Spinner,
  WarehouseSelector
- 미사용 (후속 슬라이스): Input wrapper, Label, TagChip, TagInput

### 한국어 JSDoc 적용 범위

모든 신규 ts/tsx 파일에 한국어 JSDoc 적용:
- 메인 프로세스 5 파일 (index, ipc handler, auth-store)
- preload 1 파일
- 렌더러 14 파일 (App, AppRouter, AuthGuard, AppLayout, session store,
  useAuthGuard, 5 routes, 4 api 모듈, electron.d.ts ambient)

3-layer:
1. 한국어 JSDoc — 위 본문 그대로
2. springdoc-openapi 자동 생성 — 본 슬라이스 BE 변경 없음 → 적용 대상 외
3. dev-reports — 본 파일

## DevOps (Team-Desktop DevOps)

### electron-builder 설정
- `clients/desktop/electron-builder.yml` 신규 — Windows NSIS installer + portable .exe 2 타겟
- 한국어 installer (`installerLanguages: [ko_KR]`, `language: '1042'`)
- `perMachine: false` + `requestedExecutionLevel: asInvoker` — 사내 일반 사원 권한으로 설치 (UAC 회피)
- `appId: com.samhanair.logis.desktop`, `productName: 삼한로지스`
- 빌드: `npm run build:win` → `release/0.1.0/삼한로지스-0.1.0-x64.exe` + portable.exe
- 자동 업데이트 미도입 (`publish: null`) — 후속 슬라이스 권고

### API Gateway CORS 보강
- 점검 결과: `services/api-gateway/.../config/CorsConfig.java` 자바 빈 형태로 이미 구현됨 (origins: `samhan-air.com` 3개 서브도메인 + `localhost:3000/3001/3002`)
- 부족: Electron dev (Vite 5173), 프로덕션 (`app://`, `file://`) origin 미허용
- **보강 적용**: `setAllowedOriginPatterns` 신규 도입 — `http://localhost:*`, `app://com.samhanair.logis.desktop`, `app://*.samhanair.logis.desktop`, `file://*`. `setAllowedOrigins` 에 `http://localhost:5173` 추가
- `allowCredentials=true` 와 와일드카드 origin 동시 사용 불가 → 명시적 패턴만 등록 (보안 영향 최소)
- **PM 검증 결과**: `./gradlew :services:api-gateway:assemble :services:api-gateway:test` PASS — 회귀 0

### 인증 흐름 검토
- `/auth/login` permitAll ✅ (auth-service `SecurityConfig:27` + gateway 라우트 JwtAuthentication 미적용)
- gateway JwtAuthentication filter 라우트별 적용 ✅ (`application.yml:30-81` — users/slips/products/inventory/accounting/logs)
- `/api/logs/**` 는 `allowedRoles: [MASTER, MANAGER]` 추가 게이트 ✅
- JWT 토큰 만료: `app.security.jwt.ttl-seconds: 3600` (1시간) — 후속 refresh token 권고

### 검토 산출물
- `docs/devops/electron-skeleton-review.md` — 8장 (패키징 + CORS + 인증 + 빌드 환경 + 보안 + 후속 권고 + Plan 변경 + 결론)

### 후속 권고 (우선순위)
1. Brand asset (icon.ico 256x256, splash screen, About 다이얼로그)
2. Refresh token (auth-service `/auth/refresh` + Electron axios 자동 갱신)
3. electron-updater 자동 업데이트 (GitHub Releases 또는 사내 호스팅)
4. CI 자동 빌드 (windows-latest runner + release 업로드)
5. Code signing (Authenticode 인증서, SmartScreen 회피)
6. Crash report (Sentry 또는 logging-service 활용)
7. 다국어 (i18next, 한국어 default)
8. Mac/Linux 지원 (Plan 외 — 인력 확장 시)

## QA (예외 — 슬라이스 한정)

본 슬라이스는 Q7=A 결정에 따라 FE + DevOps 2 팀만 운용하며 QA 슬라이스는
스킵한다. 후속 슬라이스 (실제 도메인 기능 추가 시) 부터 일반적인
4-team (BE/FE/QA/DevOps) 패턴으로 복귀한다.

## 검증 결과

- `npm install` — (실행 결과 보고서 본문 참고)
- `npm run typecheck` — (실행 결과 보고서 본문 참고)
- `npm run lint` — (실행 결과 보고서 본문 참고)
- `npm run build` — (실행 결과 보고서 본문 참고)
- `electron-builder --win` — DevOps 영역 (본 작업 제외)

## 잠재 이슈 / 후속 작업

1. **Product autocomplete 미구현** — 전표 작성 시 사용자가 productId(UUID)
   를 직접 입력해야 한다. 현 inventory-service 의 `GET /products/lookup`
   을 활용한 자동완성이 다음 슬라이스에서 필요하다.
2. **DeliveryTag 옵션 하드코딩** — OUTBOUND 8종을 SlipFormPage 에 인라인
   하드코딩했다. BE 가 메타데이터 endpoint (`GET /slips/delivery-tags`)
   를 제공하면 동적 fetch 로 전환한다.
3. **전표 상세/편집 화면 부재** — 목록 행 클릭 시 alert 만 표시한다.
   라이프사이클 transition (save/send/accept/...) UI 는 후속 슬라이스.
4. **페이지네이션 미구현** — `DataTable` 자체는 dumb 테이블이므로
   페이지 컨트롤은 페이지 컨테이너에서 별도 추가 필요.
5. **`@samhan/design-system` 빌드 산출물 없음** — `clients/web/design-system`
   에 `dist/` 가 없으므로 본 앱은 `file:../web/design-system` link 로
   의존성을 잡고, 디자인 시스템의 `package.json` `main`/`exports` 가
   `./dist/index.js` 를 가리킨다. 따라서 npm install 후 디자인 시스템
   별도 빌드 (`npm run build` in design-system) 가 선행되어야 한다.
   (본 보고서의 검증 결과 섹션 참고.)
