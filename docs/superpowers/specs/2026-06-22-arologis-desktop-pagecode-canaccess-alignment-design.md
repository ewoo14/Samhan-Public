# arologis-desktop 백오피스 page-code canAccess 정렬 — 설계 (후속3)

> 2026-06-22 야간 자율 세션. 동적 결재라인 에픽 후속3. arologis-desktop 백오피스 5개 admin 페이지의 FE 접근 게이팅을 **롤 하드코딩 → page-code canAccess** 로 정렬하여 spec §4.2(2026-06-08, "인사/회계 접근은 page-code 권한으로만 통제") 원 설계 의도를 실현하고, PermissionsPage 매트릭스를 FE 접근의 진실원으로 만든다.
>
> 참조: [[arologis-desktop-backoffice]] · [[arologis-independent]] · [[fe-canaccess-pagecode-be-match]] · [[pgc-c2-widening-option-a]] · [[pm-permission-autonomy]] · [[arologis-extract-autopilot]]

---

## 0. 정찰 결과 — 핸드오프 premise 정정 (중요)

핸드오프 후속3 항목은 "6 page-code 중 5개가 arologis-desktop **미배선(0파일)**" 이라 기록했으나, **이는 오판이다.** 5-agent 전면 정찰(2026-06-22)로 확정:

- **5개 백오피스 페이지 전부 존재·라우팅·머지됨** (PR #426~#433): `EmployeesPage`·`DepartmentsPage`·`CashbookPage`·`AccountsPage`·`PermissionsPage` (`/admin/{employees,departments,cashbook,accounts,permissions}`). API(`arologisHr.ts`/`arologisAccounting.ts`/`arologisPermissions.ts`)도 전부 존재.
- 후속3 정찰이 본 "5개 0파일" = **page-code 문자열을 grep 해 0매치 → '미배선'으로 오판**. 실제로 page-code 문자열(`arologis.hr.employees` 등)이 FE 소스에 없는 이유는 **FE가 page-code가 아니라 롤로 게이팅하기 때문**.
- 따라서 후속3 brainstorming의 "기능 존부" 질문은 **해소**(전부 존재), "활성/보류·부활/폐기"는 **무의미**(전부 활성·머지). 실제 작업 = "**매트릭스/메뉴/시드 동기화**" 한 축뿐 = **FE 롤→canAccess 정렬**.

### 본 설계가 신규 정책이 아니라 기 결정의 미완 구현인 근거
- spec §4.2 (2026-06-08 개발책임자 확정): *"롤 = page-code 권한만. 인사/회계 접근은 신규 page-code(`arologis.hr.*`/`arologis.accounting.*`) 권한으로만 통제(롤 세분화 안 함)."* → FE를 page-code로 게이팅하는 것이 **원 설계 결정**. 구현(PR #427/#429/#431)이 롤 하드코딩으로 갈음한 것이 미완.
- 메인 desktop은 이미 동일한 롤→canAccess 마이그레이션 완료(C2/C5 슬라이스, [[fe-canaccess-pagecode-be-match]]). 본 작업은 그 **확립된 패턴을 arologis에 복제**.

---

## 1. 문제 (현 상태 — 정찰 확정)

| # | 갭 | 근거 |
|---|---|---|
| G1 | FE 5페이지 + 사이드바 전부 **롤 하드코딩 게이팅**, `canAccess(page-code)` 0건 | `authStore.ts` `canManageHr(MASTER\|MANAGER)`·`canManageAccounts(MASTER\|ACCOUNTANT)`·`canGrantMaster(MASTER)`. arologis-desktop엔 canAccess 인프라 전무(`AuthSnapshot`=role 문자열만 보관) |
| G2 | **실 결함(FE가 BE보다 좁음)**: `회계(cashbook)` 메뉴가 `canManageHr=MASTER\|MANAGER`로 게이트되어 **ACCOUNTANT/DEVELOPER가 권한 있는 회계 메뉴를 못 봄** | BE seed V51/V53: cashbook = MASTER/MANAGER/**ACCOUNTANT/DEVELOPER** V/E. FE 사이드바는 ACCOUNTANT/DEVELOPER 숨김 → [[fe-canaccess-pagecode-be-match]] 위반 |
| G3 | **PermissionsPage 매트릭스 grant/revoke가 FE에 무반영** — FE는 하드코딩 롤이라 매트릭스 변경이 메뉴/버튼 노출에 영향 없음 | 매트릭스가 BE enforcement에만 의미. spec §4.2 의도(page-code 통제)가 FE에서 깨짐 |
| G4 | (저가치) arologis 컨트롤러 `@RequirePermission(page=…)`이 **자유 문자열 리터럴** → 오타가 조용히 fail-closed로 샘 | 13종 리터럴. enum/seed 정합 가드 없음(시드↔enum은 메인 `PageCodeSeedConsistencyIT`가 이미 커버) |

### enforcement 정상 동작 (변경 불요)
- BE `@RequirePermission(page-code)`는 13종 전부 정상 enforce(arologis-service 컨트롤러). 시드↔enum 정합 완전(고아 1건 `arologis.admin`은 legacy 호환, 운영 무관). **본 작업은 FE 정렬 + (선택) 드리프트 가드뿐, BE enforcement·시드·enum 무변경.**

---

## 2. 설계 결정 (D-AF3-01 ~ 06)

| # | 결정 | 비고 |
|---|---|---|
| **D-AF3-01** | FE 게이팅을 **page-code canAccess(pageCode, action)** 로 정렬. 메인 desktop 패턴(permissionsApi + usePermissions + PermissionGuard) 복제 | 패턴 출처 = `clients/desktop/src/renderer/{api/permissionsApi.ts, hooks/usePermissions.ts, components/PermissionGuard.tsx}` |
| **D-AF3-02** | canAccess의 진실원 = **신규 BE 엔드포인트 `GET /admin/arologis/permissions/my`** (본인 effective arologis.* 권한). **신규 auth 엔드포인트/Flyway 0** — 기존 `AuthPermissionAdminClient.getRoleMatrix("arologis.")` 재사용 | arologis-desktop은 게이트웨이 우회(arologis-service:8097 직접)라 메인의 `/auth/admin/permissions/my`를 못 씀 → arologis-service 자체 엔드포인트 필요 |
| **D-AF3-03** | page-code/action은 **BE `@RequirePermission`과 정확 일치**(테마틱 금지, FE>BE widening 금지). §4 매핑표 | [[fe-canaccess-pagecode-be-match]] 준수 |
| **D-AF3-04** | `canGrantMaster`(EmployeesPage의 "MASTER 롤 부여 옵션" 게이트)는 **page-code가 아닌 롤-부여 정책**이라 **유지**. page/메뉴/CRUD 버튼 게이트만 canAccess로 이관 | 직교 업무규칙(누가 MASTER 롤을 줄 수 있나)이지 page 접근권이 아님 |
| **D-AF3-05** | 회계 메뉴가 ACCOUNTANT/DEVELOPER에 노출되는 변화(G2)는 **결함 수정**(BE seed가 이미 grant). [[pgc-c2-widening-option-a]] seed=진실원 선례 적용. **유일한 사용자 가시 변경** → PR에 명시 + 개발책임자 확인 플래그 | 신규 정책 아님 |
| **D-AF3-06** | G4 드리프트 가드(arologis 리터럴↔enum/seed)는 **stretch/후속** — 모듈 경계(arologis-service가 auth-service PageCode enum 미참조)로 구현 비대칭. 주 정렬 작업 비차단 | 시드↔enum은 메인 IT가 이미 커버 |

---

## 3. 아키텍처

### 3.1 BE (슬1) — arologis-service, additive
```
GET /admin/arologis/permissions/my   @PreAuthorize("isAuthenticated()")   (※ @RequirePermission 아님 — 모든 백오피스 사용자가 본인 권한 조회)
  ← @RequestHeader("X-User-Role") role   (ArologisJwtFilter가 JWT claim→주입)
  1) normalize: AROLOGIS_MASTER→MASTER 등 (기존 정규화 재사용: DynamicPermissionClientConfig/ArologisAdminPermissionGuard)
  2) authPermissionAdminClient.getRoleMatrix("arologis.")  → Map<roleCode, Map<pageCode, {canView,canEdit}>>
  3) row = matrix.get(normalizedRole)  (없으면 빈 맵 → FE fail-closed)
  4) 변환 Map<pageCode, List<action>>: canView→[VIEW]; canEdit→[CREATE,UPDATE,DELETE,RESTORE,DOWNLOAD,PRINT] 추가
  → ApiResponse<Map<String,List<String>>>   (대문자 enum명; FE가 소문자화 — 메인 /my 의미 동일)
```
- 신규 파일: `ArologisMyPermissionsController`(MASTER 전용 `ArologisPermissionAdminController`와 분리 — 단일 책임). 변환 로직은 작은 서비스/헬퍼.
- **MASTER 특수 bypass 불요**: 시드가 MASTER에 arologis.* 전권 부여 → getRoleMatrix가 전부 반환.
- 계약 IT: `MockRestServiceServer`로 auth-service `role-matrix` 응답 stub → `/my` 변환·정규화·빈롤 fail-closed 단언(실 HTTP, [[enforcement-real-http-test]]).

### 3.2 FE (슬2) — arologis-desktop, 메인 패턴 복제
- **신규**: `api/permissions.ts` — `PageCode`(arologis.* 한정 union), `MyPermission{pageCode,actions}`, `fetchMyPermissions()`(GET `/admin/arologis/permissions/my` + 대문자→소문자 변환 + 'edit'→'update' 정규화), module-level `_cache`/`setCache`/동기 `canAccess(pageCode,action='view')`(캐시 null=fail-closed).
- **신규**: `hooks/usePermissions.ts` — `useQuery(['permissions','my'], fetchMyPermissions, staleTime 5분, retry 1)`, useEffect로 setCache 동기화, `canAccess`(data 없으면 deny). (QueryClientProvider는 `App.tsx`에 이미 존재 — 5분/1retry 동일.)
- **신규**: `components/PermissionGuard.tsx` — `usePermissions().canAccess`로 isLoading 시 로더, !canAccess→`<Navigate to="/" replace>`. (MascotLoader는 `@samhan/design-system` 공용 재사용.)
- **정렬**: 5페이지 + `AppLayout` 사이드바 + `routes/index.tsx`의 롤 헬퍼 호출을 canAccess로 이관(§4 매핑). `authStore`의 `canManageHr`/`canManageAccounts`는 제거 또는 canAccess 위임. `canGrantMaster`는 D-AF3-04로 유지.
- 라우트: 5개 admin 라우트를 `PermissionGuard pageCode=… action="view"`로 감쌈(메뉴 숨김 우회 직접 진입 방어 — 메인 패턴).

### 3.3 page-code 매핑표 (§4)

| 화면/메뉴 | page-code | view 게이트(메뉴/진입) | manage 게이트(CRUD 버튼) | 현 FE | BE seed effective |
|---|---|---|---|---|---|
| 인사 EmployeesPage | `arologis.hr.employees` | canAccess(view) | canAccess(create/update/delete) | canManageHr | MASTER/MANAGER |
| 부서 DepartmentsPage | `arologis.hr.departments` | canAccess(view) | canAccess(create/update/delete) | canManageHr | MASTER/MANAGER |
| 회계 CashbookPage | `arologis.accounting.cashbook` | canAccess(view) | canAccess(create/update/delete) | canManageHr ⚠️ | MASTER/MANAGER/**ACCOUNTANT/DEVELOPER** |
| (회계 집계) | `arologis.accounting.summary` | (cashbook 화면 내 조회) | — (VIEW only) | — | MASTER/MANAGER/ACCOUNTANT/DEVELOPER |
| 계정과목 AccountsPage | `arologis.accounting.accounts` | canAccess(view) | canAccess(update) | canManageAccounts | MASTER/ACCOUNTANT |
| 권한 PermissionsPage | `arologis.admin.permissions` | canAccess(view) | canAccess(update) | canGrantMaster | MASTER |

- "MASTER 롤 부여 옵션"(EmployeesPage): **canGrantMaster 유지**(D-AF3-04).
- ⚠️ 회계 메뉴 view 게이트만 변경으로 ACCOUNTANT/DEVELOPER 노출(G2 결함 수정, D-AF3-05).

---

## 4. 슬라이스

1. **슬1 (BE, 선행)** — `GET /admin/arologis/permissions/my` 엔드포인트 + 변환 서비스 + 정규화 + 계약 IT(MockRestServiceServer). additive, Flyway 0. 조기 PR.
2. **슬2 (FE)** — permissions 인프라(api/hook/guard) 이식 + 5페이지·사이드바·라우트 canAccess 정렬 + authStore 롤 헬퍼 정리(canGrantMaster 유지) + mock 핸들러([[inprocess-mock-principles]]) + Playwright real-qa. 슬1 머지 후 착수(FE가 BE 엔드포인트 의존).
3. **(stretch) 슬3** — G4 드리프트 가드(리터럴↔enum/seed). 모듈 경계 해소 가능 시.

각 슬라이스: 듀얼리뷰(Opus 5-agent → Codex 5-agent, [[temp-multimodel-workflow]]) + 라운드별 Docker 실QA 스크린샷 인라인([[per-round-live-qa]]) + 조기 PR([[open-pr-early]]) + Codex 구현([[codex-implements-claude-reviews]], 다운 시 Claude 에이전트 대체).

---

## 5. 테스트 / QA
- **BE**: `/my` 계약 IT(정규화·canView/canEdit→action 변환·빈롤 fail-closed·실 HTTP). 변경 모듈 전체 test 완주 후 push([[changed-module-full-test-before-push]]).
- **FE**: typecheck(`npm run typecheck`) + lint + vitest. canAccess 가드 단위(캐시 null=deny, 정확 매칭). 구 롤 가드 UX 박제 깨짐 전체 mock suite 점검([[fe-guard-removal-contract-tests]]).
- **Docker 실QA**(라운드별): 실 arologis-service+auth+Postgres+admin 로그인. ① MASTER=전 메뉴, ② **ACCOUNTANT=회계/계정과목 메뉴 노출(결함 수정 실증)·인사/권한 숨김**, ③ 매트릭스로 grant 변경→재로그인 시 FE 메뉴 반영(G3 해소 실증). [[no-fake-data-ever]]·[[real-server-check-screenshot]] 실화면 캡처.

---

## 6. 개발책임자 결정점 (기상 시 확인 — 유일)
- **D-AF3-05 확인**: 회계(cashbook) 메뉴가 ACCOUNTANT/DEVELOPER에게 노출되는 것이 의도와 일치하는가? (BE seed V51/V53가 이미 grant 중 — 본 작업은 FE를 그 정책에 정합. 정찰상 회계사원이 회계 메뉴를 못 보던 것이 결함.) **기본값 = 정합 진행**(seed=진실원, [[pgc-c2-widening-option-a]]). 만약 "회계사원은 회계 메뉴 보면 안 됨"이 실제 업무규칙이면 → BE seed(V51/V53) 정정이 정답(FE만 좁히는 것은 G2 재발).
- **머지 권한**: [[pm-permission-autonomy]](권한코드 머지까지 PM 자율) + [[arologis-extract-autopilot]](머지 외 자율) 범위. 야간 자율 정책 = 슬1(BE additive, 무가시변경) 클린 시 PM 머지 가능; 슬2(FE 가시 변경)는 CI green+듀얼리뷰0+Docker QA 완료 후 PR 열어두고 **개발책임자 확인 후 머지**(보수적, 가시 RBAC 변경).

---

## 7. 비고 (야간 자율 brainstorming 갈음 근거)
사용자(개발책임자) 취침 + "무중단 7am까지 진행" 명시 위임 → superpowers:brainstorming의 인터랙티브 승인 단계는 수행 불가. using-superpowers 우선순위(**사용자 명시 지시 > 스킬**)에 따라 인터랙티브 승인을 본 설계 문서 + §6 결정 플래그 + 슬2 머지 보류로 갈음. 게이트의 정신(미검토 가정 방지)은 전면 정찰(premise 정정) + 기 결정(spec §4.2)·기 패턴(메인 desktop)·선례(Option A) 근거로 충족.
