# arologis-desktop 백오피스 page-code canAccess 정렬 (후속3)

> 2026-06-22~23. 동적 결재라인 에픽 후속3. PR #569. 설계: `docs/superpowers/specs/2026-06-22-arologis-desktop-pagecode-canaccess-alignment-design.md`.

## 0. 정찰 premise 정정
핸드오프 후속3는 "6 page-code 중 5개가 arologis-desktop 미배선(0파일)"로 기록했으나 **오판**. 5-agent 전면 정찰로 5개 백오피스 페이지(Employees/Departments/Cashbook/Accounts/Permissions)가 **전부 존재·라우팅·머지됨(#426~#433)** 확인. "0파일" = page-code 문자열 grep 0매치를 미배선으로 오해한 것 — 실제 원인은 **FE가 page-code가 아니라 롤로 게이팅**. 따라서 실제 작업 = FE 롤→canAccess 정렬(spec §4.2 "page-code 권한으로만 통제" 원 의도 실현).

## 1. 변경 (additive, Flyway 0)
### BE (arologis-service, 슬1 + fix)
- **`GET /admin/arologis/permissions/my`** (`ArologisMyPermissionsController`, `@PreAuthorize("isAuthenticated()")`) — 본인 effective `arologis.*` 권한을 `pageCode→action enum명 목록`으로 반환(FE canAccess 소비). 신규 auth 엔드포인트/Flyway 0 — 기존 `AuthPermissionAdminClient.getRoleMatrix("arologis.")` 재사용.
- `ArologisMyPermissionService` — 롤 정규화 → 매트릭스 row 추출 → canView→VIEW, canEdit→CREATE/UPDATE/DELETE/RESTORE/DOWNLOAD/PRINT 변환. **빈 action page 제외**(fix B1). 빈 롤=빈 맵(fail-closed).
- `ArologisRoleCodeNormalizer` — AROLOGIS_*→중앙코드 정규화를 `DynamicPermissionClientConfig`에서 공유 util로 추출(동작 불변).
- **보안(fix B2)**: 롤을 raw `X-User-Role` 헤더가 아니라 **SecurityContext `ROLE_AROLOGIS_*` authority**(서명 JWT claim 원천)에서 도출 → inbound 헤더 위조 권한상승 차단([[identity-header-authz-antipattern]]).
- 계약 IT(`ArologisMyPermissionsControllerIT`): MockMvc→client→MockRestServiceServer 실 HTTP 변환 경로 + 미인증→401/403 음성(fix B3).

### FE (arologis-desktop, 슬2 + fix)
- `api/permissions.ts`(fetchMyPermissions + canAccess fail-closed), `hooks/usePermissions.ts`(react-query 5분), `components/PermissionGuard.tsx`(메인 desktop 패턴 복제).
- 5 라우트 PermissionGuard 래핑 + `AppLayout` 사이드바 5메뉴 canAccess(view) 게이트 + 5페이지 CRUD 버튼 canAccess(update). `canGrantMaster`(MASTER 롤 부여)는 page-code 아닌 롤정책이라 유지(D-AF3-04).
- **누출차단(fix F1)**: 로그인/로그아웃 시 `['permissions','my']` 캐시 제거(SPA navigate 세션 간 잔류 방지). isLoading 메뉴 깜빡임 제거(F4). mock summary 추가(F2). dead code 제거(F3).

## 2. 해소된 갭
- **G2 결함 수정**: `회계(cashbook)` 메뉴가 `canManageHr=MASTER|MANAGER`로 게이트되어 **ACCOUNTANT/DEVELOPER가 권한 있는 회계 메뉴를 못 보던 버그** → page-code 정렬로 BE seed(V51/V53) 정합. (widening 아님 — 기존 BE 정책 반영, [[pgc-c2-widening-option-a]].)
- **G3**: PermissionsPage 매트릭스 grant/revoke가 FE 메뉴/접근에 반영(매트릭스=FE 진실원).

## 3. 듀얼리뷰 (0-수렴)
- **Opus 5-agent**(BE/FE/Designer/QA/Security): BLOCKING 0. P1 2건=테스트/QA 커버 갭(라이브 QA로 충족). P2/P3 fix(B1~B5/F1~F7) 적용.
- **Codex 5-agent 교차리뷰**: 신규 BLOCKING/P1 0, fix 0, Opus fix 전건 무회귀 확인. P2 1건(cashbook→summary 별도 page-code 호출, seed 동반 grant라 무해) 보고.
- CI: 전 잡 green(백엔드 빌드+테스트·데스크톱 빌드·Desktop Playwright mock 회귀·Detox·Playwright·GitGuardian·JUnit).

## 4. 라이브 Docker 실QA (실 arologis-service:8097 + 실 auth + 실 Postgres, mock OFF)
실 admin 로그인 JWT로 standalone 렌더러(vite proxy CORS 회피) 구동. 3롤 메뉴가 **전부 다르고 BE 매트릭스와 정확 일치** — canAccess end-to-end 실증.

| 캡처 | 롤 | 결과 |
|---|---|---|
| 01-master-permissions-matrix | MASTER | 7메뉴 전부 + 권한 매트릭스 UI(롤×page-code, 마스터열 읽기전용) — G3 진실원 |
| 02-accountant-cashbook-access | ACCOUNTANT | **회계·계정과목 노출** + 현금출납장 정상 접근 — **G2 결함수정 실증** |
| 03-accountant-employees-blocked | ACCOUNTANT | /admin/employees 진입→**리다이렉트**(PermissionGuard 라우트차단), 인사/부서/권한 미노출 |
| 04-manager-employees | MANAGER | 인사·부서·회계 (계정과목·권한 미노출 — V54/V52 정합) |
| 05-master-employees | MASTER | 7메뉴 전부 |

**BE /my 라이브 실증**: MASTER=14 page 전부 / ACCOUNTANT=회계 3종만(빈항목 제거) / **spoof 차단**(JWT 없이 `X-User-Role:AROLOGIS_MASTER` 헤더 → `data:{}` 빈 맵).

증빙: `docs/qa/arologis-pagecode-canaccess-alignment-s/*.png`

## 5. 잔여/후속
- arologis-desktop **vitest 인프라 추가**(canAccess 단위테스트) — 본 PR에선 design-system file: junction npm install churn(package-lock 1639줄) 분리 위해 클린 후속으로 분리. 라이브 Docker 실QA가 본 PR 검증 게이트.
- (B4 주석 박제) MASTER divergence: 신규 arologis page-code 추가 시 MASTER 시드 행 필수(없으면 /my가 MASTER UI 차단). 카탈로그 가드는 G4 후속.
