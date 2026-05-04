# Electron Skeleton 첫 슬라이스 — DevOps 검토 리포트

> 슬라이스: electron-skeleton-slice
> base: 4fc23a1
> 작성일: 2026-05-04
> 작성: Team-DevOps (worktree `agent-af98b2f82d29c85bc`)

---

## 1. 패키징 + 빌드 (electron-builder)

### 1.1 신규 설정 파일
- `clients/desktop/electron-builder.yml` 신규 작성
- 타겟: Windows x64 NSIS installer + portable .exe (2 형태 동시 출시)
- 한국어 installer (`installerLanguages: [ko_KR]`, `language: '1042'`)
- `perMachine: false` + `requestedExecutionLevel: asInvoker` — 사내 일반 사원 권한으로
  설치 가능 (UAC 회피). 데스크톱/시작메뉴 바로가기 자동 생성.
- `appId: com.samhanair.logis.desktop`, `productName: 삼한로지스`
- `extraResources` 로 `docs/PM/project_plan.md` 동봉 (사내 참조용 placeholder —
  후속에 사내 매뉴얼 PDF / 한국어 폰트 등으로 교체)

### 1.2 빌드 명령 (FE 가 별도 worktree 의 `package.json` 에 정의 예정)
- 개발: `npm run dev` (electron-vite dev server, Vite 기본 포트 5173)
- Windows 프로덕션: `npm run build:win` → electron-vite build → electron-builder --win
- 결과물 경로:
  - `release/0.1.0/삼한로지스-0.1.0-x64.exe` (NSIS installer)
  - `release/0.1.0/삼한로지스-0.1.0-x64-portable.exe` (portable)

### 1.3 자동 업데이트 (후속 권고)
- 본 슬라이스 미도입 (`publish: null`)
- 후속: GitHub Releases 또는 사내 정적 호스팅 + `electron-updater` npm 패키지

### 1.4 Brand asset (후속 권고)
- `clients/desktop/build/icon.ico` 미작성 — electron-builder default 아이콘 fallback
- 정식 출시 전 디자인팀 작업 필요 (256x256 ICO, About 다이얼로그 로고, splash 화면)

---

## 2. API Gateway CORS 보강

### 2.1 점검 결과
- 위치: `services/api-gateway/src/main/java/com/samhanair/logis/gateway/config/CorsConfig.java:24-43`
- 기존 설정: 자바 빈 형태로 `CorsWebFilter` 등록 완료 (application.yml 의 `globalcors` 가 아님).
  - origins: `https://app|order|sign.samhan-air.com` + `http://localhost:3000/3001/3002`
  - exposedHeaders: `Authorization`, `X-User-Id`, `X-User-Role` 포함 ✅
  - allowCredentials: true, maxAge: 3600 ✅

### 2.2 Electron 호환 부족 항목 — 보강 적용
- `localhost:5173` (electron-vite dev 서버 기본 포트) 미허용 → 추가
- Electron 프로덕션의 `app://com.samhanair.logis.desktop` / `file://` origin 미허용
  → `allowedOriginPatterns` 별도 등록
- 주의: `allowCredentials=true` + 와일드카드 origin 은 CORS 스펙상 동시 사용 불가.
  본 보강은 명시적 패턴(`http://localhost:*`, `app://com.samhanair.logis.desktop`,
  `file://*`)만 추가 — 임의 origin 허용은 아님.

### 2.3 적용 결과 — 변경된 라인
`services/api-gateway/src/main/java/com/samhanair/logis/gateway/config/CorsConfig.java`
- 36행: `setAllowedOrigins` 에 `http://localhost:5173` 추가 (electron-vite dev)
- 38-44행: `setAllowedOriginPatterns` 신규 추가
  - `http://localhost:*`, `http://127.0.0.1:*` (dev 동적 포트)
  - `app://com.samhanair.logis.desktop`, `app://*.samhanair.logis.desktop` (Electron 프로덕션)
  - `file://*` (Electron file:// fallback)
- Javadoc 에 "Electron 데스크톱 호환" 섹션 추가하여 의도 명시

### 2.4 BE 영향 (의도된 변경)
- 기존 SPA (`localhost:3000`) 영향 없음 — origin 추가만 했고 기존 항목 유지
- BE 단위 테스트/통합 테스트는 CORS 필터를 거치지 않으므로 영향 없음
- gateway 통합 테스트가 별도로 origin 화이트리스트를 검증하지는 않음 (확인 결과 부재)

---

## 3. 인증 흐름 검토

### 3.1 로그인 endpoint
- 라우트: `POST /api/auth/login` (gateway) → `POST /auth/login` (auth-service)
- gateway 라우트: `services/api-gateway/src/main/resources/application.yml:23-28`
  - `Path=/api/auth/**` → `lb://auth-service`, `StripPrefix=1`
  - **JwtAuthentication filter 미적용** ✅ (의도된 — 토큰 발급 endpoint)
- auth-service 시큐리티: `services/auth-service/src/main/java/.../config/SecurityConfig.java:27`
  - `requestMatchers("/auth/login").permitAll()` ✅
- 컨트롤러: `services/auth-service/src/main/java/.../web/AuthController.java:34-37`
  - 요청 DTO: `LoginRequest(loginId, password)` (NotBlank, password 8-100자)
  - 응답 DTO: `LoginResponse(token, userId, role, displayName)` — JWT + 최소 프로필

### 3.2 보호 endpoint — JwtAuthentication filter
- 위치: `services/api-gateway/src/main/java/.../filter/JwtAuthenticationGatewayFilterFactory.java`
- 적용 라우트 (`application.yml:30-81`):
  - `/api/users/**`, `/api/slips/**`, `/api/products/**`,
    `/api/inventory/**`, `/api/accounting/**` — 인증만 요구
  - `/api/logs/**` — `allowedRoles: [MASTER, MANAGER]` 권한 게이트 추가
- 동작 (filter 75-117행):
  1. `Authorization: Bearer <jwt>` 헤더 부재 → 401 `UNAUTHORIZED`
  2. 서명/만료 실패 → 401 `INVALID_TOKEN`
  3. allowedRoles 비매치 → 403 `FORBIDDEN`
  4. 정상 → `X-User-Id` + `X-User-Role` 헤더 mutate 후 다운스트림 라우팅

### 3.3 JWT 토큰 정책
- 발급/파싱: `shared/common/src/main/java/.../security/JwtTokenProvider.java`
  - HS256, jjwt 0.12.x fluent API
  - claims: `sub=userId`, `role`, `iat`, `exp`
- TTL: `services/api-gateway/src/main/resources/application.yml:92` → `app.security.jwt.ttl-seconds: 3600` (**1시간**)
- secret: 환경변수 `JWT_SECRET` (default `dev-secret-change-me-...`) — 프로덕션 배포 시 32바이트 이상 무작위 시크릿 필수

### 3.4 권고 (후속 슬라이스)
- **Refresh token 도입** — 1시간 만료는 사내 데스크톱 앱에서 자주 재로그인 발생.
  auth-service 에 `/auth/refresh` endpoint + Electron 측 axios 인터셉터에서 자동 갱신
- `/auth/me` endpoint 는 이미 존재 (`AuthController:46-64`) — Electron 부팅 시 토큰 검증용으로 활용 가능
- electron-store 의 토큰은 Electron `safeStorage` (Windows DPAPI) 로 암호화 저장 — 동일 사용자 + 동일 머신에서만 복호화 (FE 작업 영역, 검토만 명시)

---

## 4. 빌드 환경 가이드

### 4.1 Windows 개발자 머신
- Node 20.x LTS 권장 (electron-vite + electron-builder 호환)
- `npm install` 시 native module (electron-store 의 sqlite/keytar 등) 빌드 — node-gyp 필요
  - Visual Studio Build Tools 또는 `npm install --global windows-build-tools` 필요
- electron-builder 의 NSIS installer 생성은 **Windows native 환경에서 가장 안정적**
  - macOS/Linux 에서도 wine 으로 가능하지만 사내 Windows 우선이므로 권장 X

### 4.2 빌드 자동화 (CI — 후속 슬라이스)
- 본 슬라이스 미도입. 현재 CI 는 Java/Gradle 만 가동 (`.github/workflows/ci.yml`)
- 후속: GitHub Actions `windows-latest` runner 에서 electron-builder 실행 → 자동 release 업로드

### 4.3 환경변수 (Electron 측)
- FE 가 `clients/desktop/.env.example` 에 정의 예정 (DevOps 영역 아님)
- `VITE_API_BASE_URL=http://localhost:8080` (개발) → `https://api.samhan-air.com` (프로덕션)
- 본 슬라이스에선 dev 만 검증 (HTTP). 프로덕션 출시 전 gateway TLS 의무

---

## 5. 보안 가드 (검토)

| 항목 | 상태 | 비고 |
|---|---|---|
| `contextIsolation: true` | FE 책임 | Electron 권장 default — FE 측 BrowserWindow 옵션 확인 |
| `nodeIntegration: false` | FE 책임 | preload 가 contextBridge 로만 메인 API 노출 |
| `sandbox: true` | FE 책임 | 권장 — preload 도 sandbox 내 실행 |
| Token 암호화 저장 | FE 책임 | electron-store + safeStorage (Windows DPAPI) |
| HTTPS 의무 (프로덕션) | DevOps + Infra | dev=HTTP, prod=HTTPS — gateway TLS 후속 슬라이스 |
| CSP 헤더 | FE 책임 | Electron BrowserWindow 의 webPreferences + CSP meta |
| Auto-update 서명 | DevOps 후속 | electron-updater 도입 시 코드 서명 인증서 필요 |

---

## 6. 후속 슬라이스 권고 (우선순위)

1. **Brand asset** — icon.ico (256x256), splash screen, About 다이얼로그 (디자인팀 협업)
2. **Refresh token** — auth-service 에 `/auth/refresh` + Electron axios 인터셉터 자동 갱신
3. **Auto update** — electron-updater + GitHub Releases 또는 사내 호스팅
4. **CI 자동 빌드** — windows-latest runner 에서 electron-builder 실행 + release 업로드
5. **Code signing** — Authenticode 인증서 구매 (사내 또는 EV) → SmartScreen 경고 회피
6. **Crash report** — Sentry 또는 사내 로깅 서비스 (logging-service 활용 검토)
7. **다국어** — i18next 도입 (한국어 default, 향후 영어 / 베트남어)
8. **Mac/Linux 지원** — Plan §외 — 외주 인력 확장 시 검토

---

## 7. Plan 대비 의도적 변경

- Q1=A: electron-vite + electron-builder 채택
- Q4=A: electron-store + safeStorage (Windows DPAPI) 채택 — DevOps 검토 OK
- Q6=A: Windows x64 만 (사내 환경) — NSIS + portable 2 타겟
- Q7=A: FE + DevOps 2팀만 (BE/QA 변경 없음) — 단, **CORS 보강은 정당한 BE 사이드 변경**
  으로 분류 (Electron origin 호환 — FE 작업이 막히지 않도록)

---

## 8. 결론

- ✅ `clients/desktop/electron-builder.yml` 신규 — Windows NSIS + portable, 한국어 installer
- ✅ `services/api-gateway/.../CorsConfig.java` 보강 — Electron `app://`, `file://`, `localhost:*` origin 패턴 추가
- ✅ 인증 흐름 검토 완료 — `/auth/login` permitAll, JwtAuthentication filter 라우트별 적용,
  JWT TTL 1시간 (refresh token 후속 권고)
- ✅ 빌드 환경 가이드 작성 — Windows 개발자 머신 prerequisite + CI 후속 권고
- 🔜 후속: brand asset, refresh token, electron-updater, code signing, CI 빌드, 다국어
