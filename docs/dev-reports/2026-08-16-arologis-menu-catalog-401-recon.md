# 아로로지스 배차 메뉴 catalog 401 원인 정찰

```text
cwd   C:/dev/Samhan-Public   (main, 읽기 전용)
```

> 조사 기준: 로컬 `main` HEAD `79b072d55` (2026-08-16). 조사 시점의 로컬 `main`은 `origin/main`보다 40커밋 뒤였다. 사용자 금지 조건에 따라 pull/fetch/checkout은 하지 않았다. 제품 코드·컨테이너·환경변수·DB는 건드리지 않았고 로그인도 수행하지 않았다. 이 문서만 작성했다.

## 결론 요약

- 아로로지스 데스크톱은 자체 `arologis-service` 로그인에서 받은 **아로로지스 access token**을 Samhan `api-gateway`의 `GET /auth/admin/menu-catalog`에 그대로 보낸다.
- 게이트웨이는 `SAMHAN_JWT_SECRET`만 사용해 보호 라우트의 JWT를 먼저 검증한다. 현재 관측 환경에서 아로로지스 토큰의 서명 키는 `SAMHAN_AROLOGIS_JWT_SECRET`이고, 게이트웨이에 그 키가 없다. 따라서 요청은 auth-service에 도달하기 전에 `401 INVALID_TOKEN`으로 끝난다.
- `arologis-service`에는 자체 메뉴 catalog가 없다. 메뉴 5개의 서버 정본은 Samhan `auth-service`의 `MenuCatalog`에 있다. 이는 2026-08-15 메뉴 catalog 설계가 의도적으로 택한 구조다.
- 그러나 2026-05-14 독립 분리 결정 D-AX-07은 계정/인증을 Samhan auth-service와 무관한 별도 경계로 정했다. 이후 D-AROLO-HR-03은 **계정은 독립, 권한 grant는 중앙 auth-service 공유**로 범위를 좁혀 예외를 만들었다. 현재 코드는 이 두 결정 위에 “중앙 메뉴 정본을 아로로지스 클라이언트가 Samhan 게이트웨이로 직접 조회”하는 세 번째 결합을 추가했다.
- 현재 증상은 기존 정상 기능의 회귀가 아니라 catalog 도입 당시부터 존재한 인증 경계 불일치다. 중간 수정은 401이 아로로지스 세션을 지우는 부작용만 막았고, 게이트웨이가 아로로지스 토큰을 검증할 수 없는 조건은 유지했다.

## ① 호출 지도 — 누가, 무엇을, 어떤 토큰으로

### 실패 경로

```text
아로로지스 관리자
  └─ POST {VITE_AROLOGIS_API_BASE}/auth/admin/login
       └─ arologis-service가 아로로지스 JWT 발급
            └─ authStore.auth.accessToken에 저장
                 └─ /dispatches/*의 DispatchesLayout mount
                      └─ useMenuCatalog()
                           └─ fetchMenuCatalog()
                                └─ GET {VITE_VERSION_API_BASE_URL}/auth/admin/menu-catalog
                                     Authorization: Bearer <아로로지스 access token>
                                          └─ api-gateway JwtAuthentication
                                               └─ SAMHAN_JWT_SECRET로 검증
                                                    └─ 서명 불일치 → 401 INVALID_TOKEN
                                                         └─ auth-service MenuCatalogController에는 도달하지 않음
```

근거:

| 단계 | 코드 근거 | 사실 |
|---|---|---|
| 자체 로그인 | `clients/arologis-desktop/src/renderer/api/auth.ts:39-47` | 공통 `apiClient`로 `/auth/admin/login` 호출 |
| 자체 API base | `clients/arologis-desktop/src/renderer/api/client.ts:11-14,36-47` | `VITE_AROLOGIS_API_BASE`, 기본 `http://localhost:8097`; 게이트웨이 우회 및 arologis 자체 JWT 검증을 명시 |
| 토큰 저장/조회 | `clients/arologis-desktop/src/renderer/stores/authStore.ts:39-59` | 로그인 응답 snapshot을 저장하고 `getAccessToken()`이 같은 `auth.accessToken` 반환 |
| catalog 호출 시작 | `clients/arologis-desktop/src/renderer/routes/dispatches/DispatchesLayout.tsx:6-14` | 모든 `/dispatches/*` 화면의 공통 layout이 `useMenuCatalog()` 호출 |
| query | `clients/arologis-desktop/src/renderer/hooks/useMenuCatalog.ts:11-19` | `fetchMenuCatalog`, retry 1, fail-closed 캐시 |
| gateway base | `clients/arologis-desktop/src/renderer/api/menuCatalog.ts:16,25-29` | `VITE_VERSION_API_BASE_URL`, 기본 `http://localhost:8080`의 별도 axios client |
| 실은 토큰 | `clients/arologis-desktop/src/renderer/api/menuCatalog.ts:31-35` | `useAuthStore.getState().getAccessToken()`을 Bearer로 주입. Samhan 토큰을 별도로 얻거나 선택하는 코드 없음 |
| endpoint | `clients/arologis-desktop/src/renderer/api/menuCatalog.ts:37-40` | `GET /auth/admin/menu-catalog`; 응답 중 `app === 'arologis'`만 사용 |
| gateway route | `services/api-gateway/src/main/resources/application.yml:213-234` | `/auth/admin/**`가 `JwtAuthentication` 보호 라우트에 먼저 매칭 |
| gateway 검증 | `services/api-gateway/src/main/java/com/samhanair/logis/gateway/filter/JwtAuthenticationGatewayFilterFactory.java:167-172` | gateway JWT secret으로 parse 실패 시 `401 INVALID_TOKEN` |
| gateway secret binding | `services/api-gateway/src/main/resources/application.yml:773-776` | `SAMHAN_JWT_SECRET`/legacy `JWT_SECRET`만 바인딩 |
| 분리된 compose secret | `infrastructure/docker-compose.prod.yml:168-181,763-773` | gateway는 `SAMHAN_JWT_SECRET`, arologis는 별도 `SAMHAN_AROLOGIS_JWT_SECRET`; gateway에 후자 주입 없음 |

관측된 해시 앞 8자리도 이 코드 경계와 일치한다: gateway `SAMHAN_JWT_SECRET=153cd5d4`, arologis `SAMHAN_AROLOGIS_JWT_SECRET=4c3f6e4f`, gateway의 `SAMHAN_AROLOGIS_JWT_SECRET=ABSENT`. 평문 secret은 조사·기록하지 않았다.

### 왜 `/admin/arologis/permissions/my`는 200인가

이 요청은 동일 토큰을 **아로로지스 자체 base URL**로 보내므로 arologis-service가 자기 secret으로 검증할 수 있다(`client.ts:36-47`, `permissions.ts:51-58`). 그 뒤 `ArologisMyPermissionService`가 중앙 auth-service의 role matrix를 서버 간 internal 호출로 조회한다(`ArologisMyPermissionService.java:13-18,55-63`). 브라우저가 Samhan gateway에 아로로지스 JWT를 제출하는 catalog 경로와 인증 경계가 다르다.

### auth-service에 도달했다면 존재하는 경로

gateway 검증을 통과했다는 전제에서 gateway는 JWT의 `AROLOGIS_*` role을 `X-Arologis-Role`로 주입한다(`JwtAuthenticationGatewayFilterFactory.java:193,258-260`). auth-service의 `MenuCatalogController`는 이 헤더를 중앙 role로 정규화해 `arologis` 항목만 권한과 교집합한다(`MenuCatalogController.java:32-53`). 즉 downstream에는 아로로지스용 분기가 이미 있지만, 현재 실패는 그보다 앞선 gateway 서명 검증 단계다.

## ② 설계 의도 — 독립 인증과 중앙 권한/메뉴의 겹침

### 독립 운영 결정

- `.claude/memory/project_arologis_independent.md:8-10,18-26` 및 `migration/decisions/DECISIONS.md:1450-1464`
  - monorepo와 Eureka/AWS 환경은 공유한다.
  - 클라이언트는 별도 앱이다.
  - D-AX-07: 계정/인증은 완전 별도이며 Samhan auth-service/user-service와 무관하다.
  - D-AX-08: 자체 auth는 arologis-service에 내장한다.
- 이 결정은 `clients/arologis-desktop/src/renderer/api/client.ts:11-14`와 실제 자체 `/auth/*` API에 반영돼 있다.

### 이후 명시된 중앙 권한 예외

- `migration/decisions/DECISIONS.md:2854-2861`의 D-AROLO-HR-03은 “독립은 계정뿐”이라고 명시하고 `arologis.*` 권한 grant를 중앙 auth-service의 `role_page_permissions`에 유지한다.
- `ArologisMyPermissionService.java:16-18,55-63`는 arologis-service가 중앙 role matrix를 조회해 자체 `/admin/arologis/permissions/my` 응답으로 변환한다.
- 서버 간 실제 호출은 `AuthPermissionAdminClientImpl.java:80-90,104-121`의 다음 2종이다.
  1. `GET /auth/internal/permissions/role-matrix?pagePrefix=arologis.`
  2. `PUT /auth/internal/permissions/role-grant`
- 이 경로들은 브라우저 JWT가 아니라 `X-Internal-Token`과 gateway attestation을 사용한다(`AuthPermissionAdminClientImpl.java:22-30,41-49,83-88,113-118,156-160`). 따라서 이번 `SAMHAN_JWT_SECRET` 불일치와 같은 실패 부류가 아니다.

### 메뉴 catalog의 원래 설계

- `docs/superpowers/specs/2026-08-15-901-894-menu-catalog-design.md:7-10,19-40`은 auth-service를 두 데스크톱 공통 메뉴 메타데이터의 단일 서버 정본으로 명시한다.
- `docs/superpowers/plans/2026-08-15-901-894-menu-catalog.md:1-6,31-57`도 auth-service endpoint와 두 FE 소비를 명시한다.
- 실제 아로로지스 메뉴 5개는 `services/auth-service/.../menu/MenuCatalog.java:111-115`에 있다.
- arologis-service 자체에는 `MenuCatalog`/`menu-catalog` endpoint가 없다. 전수 검색 결과, 제품 코드에서 아로로지스용 catalog 구현은 auth-service의 위 5개 항목과 arologis-desktop 소비 코드뿐이다.

따라서 “왜 삼한 것을 부르는가”의 코드·문서상 답은 **2026-08-15 설계가 두 앱과 향후 Claude가 같은 auth-service 정본을 사용하도록 의도했기 때문**이다. 다만 그 설계와 별도 JWT 신뢰 경계를 연결할 인증 방식은 spec·plan과 구현 어디에도 정의되지 않았다.

## ③ 도입 시점 — 회귀인가, 처음부터인가

| 시점 | 커밋 | 사실 |
|---|---|---|
| 2026-08-15 01:15 KST | `609f180f` `feat(901,894): 메뉴 catalog 를 서버가 결정한다 — 권한 교집합 (S1)` | `menuCatalog.ts`, hook, layout 소비 최초 도입. 최초 버전부터 gateway URL에 기존 아로로지스 `apiClient`를 사용해 같은 아로로지스 토큰을 실었다. 이 커밋은 현재 HEAD의 직접 ancestor는 아닌 PR 작업 커밋이다. |
| 2026-08-15 09:29 KST | `bf2547e3` `[FIX] #1218 아로로지스 배차 메뉴 도달 실패 — catalog 401 이 세션을 지우던 것` | gateway 전용 axios client를 분리해 401 시 아로로지스 logout/redirect가 일어나지 않게 했다. 그러나 request interceptor가 계속 `authStore`의 아로로지스 access token을 gateway에 실었다. 이 커밋도 현재 HEAD의 직접 ancestor는 아니다. |
| 2026-08-15 23:37 KST | `ecb465a1` `[FEAT] #901+#894 ... (#1218)` | PR #1218 squash merge로 현재 `main`에 기능 유입. 현재 파일 전체 blame가 이 커밋을 가리킨다. gateway client 분리와 아로로지스 토큰 전달을 함께 포함한다. |

판정: **catalog 기능은 현재 main에 들어온 첫날부터 이 인증 경계에서 동작할 수 없었다.** 하드코딩 메뉴가 서버 catalog로 교체되면서 새로 생긴 결함이며, 정상 동작하던 catalog가 후속 변경으로 깨진 회귀는 아니다. `bf2547e3`는 “세션 삭제” 증상만 완화했기 때문에 현재의 “본문 유지 + 경고 + 하위 메뉴 미표시” 형태가 됐다.

## ④ 같은 문제를 갖는 지점 전수

### A. 아로로지스 데스크톱 → Samhan gateway 직접 호출: 2곳

| # | 호출 | 파일:라인 | 인증 | 이번 401 동일 여부 |
|---|---|---|---|---|
| 1 | `GET /auth/admin/menu-catalog` | `api/menuCatalog.ts:16,25-39` | 아로로지스 Bearer token | **동일 문제**. gateway의 Samhan JWT 검증을 통과할 수 없음 |
| 2 | `GET /app/version?clientType=AROLOGIS_DESKTOP&...` | `components/common/AppVersionGate.tsx:12-13,127-143`; `version/versionCheck.ts:42-66` | 무인증 public GET | 동일 문제 아님. gateway route도 `JwtAuthentication` 미적용(`application.yml:526-533`) |

`VITE_VERSION_API_BASE_URL`을 쓰는 제품 호출은 위 두 종류뿐이다. axios 생성과 `fetch()`도 전수 검색했다. 따라서 **Samhan gateway 직접 호출 2곳, Samhan auth 보호 endpoint 직접 호출 1곳, 동일 JWT 불일치 문제 1곳**이다.

### B. 영향받는 아로로지스 화면: 6개

`useMenuCatalog`의 제품 코드 소비자는 `DispatchesLayout` 하나뿐이다. 그러나 그 layout이 다음 6개 실제 child route를 공통으로 감싼다(`routes/index.tsx:151-176`). catalog가 실패하면 `menus`가 없으므로 5개 링크 모두 비고, layout이 경고를 표시한다(`DispatchesLayout.tsx:8-14,24-64`). 본문 route는 별도로 렌더링되므로 관측처럼 본문은 남을 수 있다.

1. `/dispatches/manual`
2. `/dispatches/pre-classify`
3. `/dispatches/unassigned`
4. `/dispatches/reconcile`
5. `/dispatches/received-groups`
6. `/dispatches/detail/:dispatchCode`

앞의 5개는 catalog가 정의하는 메뉴 후보다. 상세 화면은 catalog 항목은 아니지만 같은 layout 아래 있으므로 하위 메뉴 공백과 경고가 동일하게 나타난다. `/drivers`, `/admin/employees`, `/admin/departments`, `/admin/cashbook`, `/admin/accounts`, `/admin/permissions` 등 dispatch layout 밖 화면은 이 hook을 사용하지 않아 이 catalog 401의 직접 영향 대상이 아니다.

### C. arologis-service → 중앙 auth-service 서버 간 호출: endpoint 2종

1. role matrix 조회 — `AuthPermissionAdminClientImpl.java:80-90`
2. role grant 갱신 — `AuthPermissionAdminClientImpl.java:104-121`

이는 중앙 auth 의존성 전수에는 포함되지만, 클라이언트 Bearer/gateway 경로가 아니므로 이번 401과 분리해야 한다. `/admin/arologis/permissions/my`와 `/admin/arologis/permissions`가 이 내부 client를 소비한다.

## ⑤ 선택지와 각각의 대가

아래는 판정이나 권고가 아니라 가능한 경계 선택지를 나열한 것이다.

| # | 선택지 | 보안 표면 | 운영 대가 | 독립성 대가 |
|---|---|---|---|---|
| 1 | **아로로지스 자체 menu-catalog**: 메뉴 메타데이터와 필터 endpoint를 arologis-service가 소유 | 아로로지스 JWT는 자기 서비스에서만 검증. 새 외부 신뢰 키 배포 없음. catalog/권한 조합 로직을 새로 보호·시험해야 함 | 메뉴 정의·권한 seed·route 정합을 아로로지스 릴리스에서 관리. Samhan catalog와 중복 변경 가능 | 메뉴 운영까지 아로로지스 경계로 들어가 독립성 증가. 중앙 단일 정본은 포기/분리 |
| 2 | **arologis-service facade/proxy**: 클라이언트는 자체 endpoint를 호출하고, 서버가 internal token으로 중앙 catalog/권한을 조회 | 아로로지스 JWT를 gateway에 노출하지 않음. 서버 간 internal token/attestation 표면과 응답 스코프 검증이 추가됨 | 중앙 auth 장애가 아로로지스 메뉴에 계속 전파. proxy 계약·timeout·장애 매핑 운영 필요 | 클라이언트 인증은 독립 유지, 메뉴 정본/권한은 중앙 종속 유지 |
| 3 | **기존 `/permissions/my` + 아로로지스 로컬 메뉴 메타데이터**: 이미 200인 자체 권한 응답과 FE/서비스 로컬 route 목록을 교집합 | 새 신뢰 경계 없음. 클라이언트 메타데이터 변조가 인가를 대신하지 않도록 기존 route/BE guard 유지 필요 | 서버 공통 catalog/Claude 단일 정본을 잃고 FE route·label 정합 테스트를 별도 유지 | 런타임 중앙 gateway 의존 감소. 권한 grant의 중앙 저장 의존은 그대로 |
| 4 | **gateway가 별도 아로로지스 JWT issuer/key를 신뢰**: 보호 라우트에서 dual-issuer/dual-key 검증 | gateway에 아로로지스 검증 키와 role/claim 변환 로직이 추가됨. 잘못된 route scope나 issuer 혼동 시 신뢰 범위 확대 | 두 secret의 배포·rotation·감사·장애 대응 필요. compose/Secrets Manager/CI 구성 동기화 필요 | 계정 발급은 별도지만 Samhan gateway가 아로로지스 인증기관을 신뢰하므로 인증 운영 결합 증가 |
| 5 | **서명 secret 통합**: arologis JWT도 `SAMHAN_JWT_SECRET`으로 발급 | key 하나의 유출 blast radius가 두 제품으로 합쳐짐. claim/issuer/role 격리 검증이 필수 | secret 배포·rotation은 단순해지지만 두 제품을 동시에 회전·장애 처리해야 함 | D-AX-07의 “인증 완전 별도”와 가장 크게 충돌하며 독립 배포·키 격리 감소 |
| 6 | **token exchange/federation**: arologis token을 검증한 서버가 catalog 전용의 짧은 Samhan token을 발급/교환 | 교환 endpoint, audience/scope, replay, subject mapping이라는 새 인증 표면 추가. 최소권한 토큰 설계 가능 | 키·issuer·교환 실패·clock skew·revocation·관측성 운영이 가장 복잡 | 계정 저장소는 분리 가능하나 두 인증 시스템 사이 공식 연합 계약이 생김 |

## 정찰 수치

- Samhan gateway 직접 호출 지점: **2곳**
- 그중 Samhan auth 보호 endpoint 및 동일 JWT 불일치 지점: **1곳**
- 같은 layout에서 증상을 공유하는 아로로지스 화면: **6개**
- arologis-service의 중앙 auth 내부 endpoint: **2종**
- 도입 시점: **2026-08-15**, 작업 커밋 `609f180f`; 현재 main 유입 `ecb465a1` (PR #1218)
- 선택지: **6개**

