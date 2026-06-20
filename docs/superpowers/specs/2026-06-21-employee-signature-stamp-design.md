# 사원 서명 등록 → 출고전표 결재란 인감 (설계 spec)

> 작성일: 2026-06-21 · 작성: PM(Opus) brainstorming 종합 · 상태: **설계 승인(개발책임자 2026-06-21) → 9-agent 적대 검증·교정 완료 → 구현 대기**
>
> 관련 에픽 메모리: [[project_slip_shipout_print_form]] (슬라이스 C). 슬라이스 A(양식 1:1)·B(기사/인수자 전자서명)는 완료, 본 spec = **슬라이스 C(사원 서명 등록 + 결재란 인감)**.
>
> **⚠️ 검증 교정 이력**: 본 spec 초안은 9-agent 워크플로우(`wf_7bef788f`)로 실코드 적대 검증됨. 초안의 핵심 전제 1건이 **REFUTED**(§6 enrichment "기존 lookup 확장" → 실제 "신규 구축"), 2건 **PARTIAL**(C 모바일 공개 웹페이지 미실재·Phase5 deferred / F 컨트롤러 배선 오류). 리뷰가 BLOCKER 3건(모바일 호스트·게이트웨이 라우팅·join key 모호) 적발. 아래 본문은 **교정 후 ground truth**.

---

## 1. 배경 / 목표

개발책임자 지시(2026-06-10 원문, [[project_slip_shipout_print_form]]):
> "사원등록 메뉴에서 해당 사원의 서명(추후 전자서명 활용) 등록 가능해야 함 → 양식의 출고인/검수인/담당자 결재란 스탬프로 활용."

개발책임자 추가 지시(2026-06-21):
> "사원 서명 등록 시 모바일을 통해 직접 손으로 그린 서명을 넣을 수 있게 하거나, 서명 이미지를 넣을 수 있도록 한다."

**목표**: 각 사원의 서명을 1회 등록해 두면, 그 사원이 출고전표 결재란의 **작성자 / 출고인 / 검수인** 자리에 들어갈 때마다 등록 서명이 **인감(도장)처럼 자동 스탬프**된다. 스탬프 대상은 정확히 이 **3자**(담당부서·결제예정일 셀은 stamp 미적용 — §6.3).

**비목표 (YAGNI)**:
- 전표별 명시 결재(서명) 워크플로우 신설 — 하지 않음 (인감 모델, §2 D2).
- 결재란 서명 등록 시각(`signed_at`) 표시 — 하지 않음 (§2 D2 / §6.3).
- 등록완료 SSE 푸시 — 하지 않음 (폴링으로 갈음, §5.2).
- 거래명세서 공급자 "인감" — 별도 트랙([[project_company_config_menu]], 회사 도장 ≠ 사원 서명). **본 에픽 완전 제외** (재론 없음).
- arologis 사원 서명 — arologis는 독립 도메인([[project_arologis_independent]]), `arologis_employee` 별도 엔티티(`ArologisEmployee.java:30-35`). 제외.

---

## 2. 결정 (brainstorming 2026-06-21 + 검증 후 확정)

### 2.1 개발책임자 확정 (D1~D4)
| # | 항목 | 확정 |
|---|---|---|
| D1 | 등록 주체·흐름 | **관리자 desktop + 모바일 핸드오프** + 이미지 업로드 대체 경로. |
| D2 | 스탬프 적용 | **인감(도장) 모델 · 실시간 조회**. 전표 출력/조회 시점에 현재 서명을 그때그때 조회. 전표별 결재 액션·서명 시각 신설 **없음**. 스냅샷 동결 **거부**(순수 live). |
| D3 | 입력 경로 | **두 경로, 한 저장소**: (a) 이미지 업로드(desktop 즉시), (b) 모바일 손그림(핸드오프). 동일 Employee 서명 필드, `signature_channel`로 구분. |
| D4 | 모바일 시퀀싱 | **한 에픽으로 둘 다 구축** — 이미지 업로드 + **모바일 공개 웹앱 신규 구축**(`mobile-public` 번들 빌드 + 게이트웨이 공개 라우팅 + 실 origin). 부수 효과로 Phase 5 deferred slip 인수자 공개 서명도 재사용 자산화. |

### 2.2 PM 자율 확정 (검증 결과 반영, [[feedback_pm_permission_autonomy]])
| # | 항목 | 확정 + 근거 |
|---|---|---|
| P1 | 서명 엔드포인트 컨트롤러 | **AdminUserController**(`/api/v1/admin/users/{id}/signature`). desktop 사원등록 메뉴(`UsersPage`)가 실제로 호출하는 컨트롤러(`adminApi.ts`→`/api/v1/admin/users`). EmployeeController(`/users/employees`)는 **UI 호출자 0**이라 거기 붙이면 메뉴와 분리됨. page-code = AdminUserController 기존 게이트(`admin.users`). |
| P2 | 무효화 권한 | **MASTER 한정** — 검증된 slip `SlipSignatureController` 무효화(MASTER only DELETE) 패턴 정렬. 인감 위조는 다수 전표 소급 영향 → slip보다 약한 게이트 부적절. (초안의 `admin.employees UPDATE` 기본값 폐기) |
| P3 | 감사 action 집합 | **RECORD / INVALIDATE** (slip V5 `slip_signature_audit` 진짜 미러). 재등록 = 새 `RECORD`. (초안의 `REPLACE` 폐기 — slip 패턴에 없음, CHECK 불일치 회피) |
| P4 | join key | **Employee.id (PK)**. `InternalUserController.findOne`=`employeeRepository.findById(userId)`이고 `Slip.createdBy`로 owner fullName이 이미 정상 조회됨 → `createdBy`=`dispatcherUserId`=`inspectorUserId`=`Employee.id`(게이트웨이 X-User-Id=canonical user UUID). silent no-op 없음. |
| P5 | 내부 인증 | **X-Internal-Token + `@PreAuthorize("hasRole('MASTER')")`** (user-service `InternalUserController` 기존 패턴). slip-style P0-B header 아님(초안 오기 교정). |
| P6 | 핸드오프 토큰 저장 | **별도 테이블** `employee_signature_handoff_token`. used(1회용)·재발급·동시발급·감사 자연 지원(Employee 컬럼 1슬롯 경합 회피). |
| P7 | 스탬프 적용 뷰 | **DispatchView + OutboundView 둘 다**. OutboundView도 출고인 stamp slot 보유 → 한 뷰만 찍히면 결함 family 누락([[feedback_defect_family_sweep_fix]]). 출고인 stamp 들어가는 print/*View 전수 grep 체크리스트. |

---

## 3. 아키텍처 개요

```
[관리자 desktop · 사원등록 메뉴 = UsersPage (/admin/users, adminApi→AdminUserController)]
   │  (a) 이미지 업로드 ─────────► PATCH /api/v1/admin/users/{id}/signature {png(base64), hash, channel: UPLOAD}
   │  (b) "모바일로 그리기"
   │        └─► POST /api/v1/admin/users/{id}/signature/handoff-token ─► {token, qrUrl, expiresAt}
   │              desktop: QR + 복사링크 표시 + 등록완료 폴링(2s, GET .../handoff/{token}/status)
   ▼
[사원 폰 브라우저]  qrUrl(실 origin) 열기  ── ※ NEW 모바일 공개 웹앱(mobile-public 번들)
   └─► design-system SignaturePad 페이지 → 손서명 → 제출
         └─► POST /api/public/employee-signatures/{token} {png(base64), hash}  (NO-AUTH, 토큰 게이트)
               user-service: 토큰 검증(미만료·미사용) → registerSignature(MOBILE_CANVAS) → 토큰 used 소진
   ▼
[user-service · Employee 서명 저장]  signature_png / signature_hash / signed_at / signature_channel
   ▲
   │  POST /internal/users/signatures {userIds[]}  (X-Internal-Token, hasRole MASTER)  ── display-names 미러
   │     → {employeeId: {signaturePngBase64, signedAt}}
[slip-service]  GET /slips/{id} (getOne) enrichment ── ※ dispatcher/inspector/owner 이름+서명 신규 구축
   │   기존: owner(createdBy)만 fullName resolve. 신규: dispatcher/inspector도 resolve + 3자 서명 동봉.
   ▼
[desktop DispatchView + OutboundView]  RoleCell signaturePng stub 주입 (작성자/출고인/검수인)
```

핵심: 결재란 stamp slot(`DispatchView.tsx:60-83` RoleCell `signaturePng` stub)·owner 이름 enrichment(`SlipService.getOne→resolveOwnerFullName`)는 **이미 존재**. 본 에픽은 (1) 서명 저장소·핸드오프·공개 웹앱을 만들고 (2) **dispatcher/inspector enrichment를 신규 구축**해 서명을 얹고 (3) 기존 stub에 주입한다. **전표별 결재 워크플로우 0.**

---

## 4. 데이터 모델 — user-service

slip 서명 모델(검증된 패턴, `Slip.java` / `V5__add_slip_signature.sql`)을 복제. ⚠️ slip의 `signature_source`(V10)·`driver_*`(V6)는 미러 대상 아님(MVP 7컬럼만).

### 4.1 `Employee` 서명 필드 (4컬럼 — 기존 `employees` 테이블)
- `signature_png BYTEA` — PNG 원본. JPA `byte[] signaturePng` `@Column(name="signature_png")` (기본 bytea 매핑; Employee/BaseEntity에 BYTEA 선례 없음 → 매핑 명시). 서비스 레이어 **≤50KB 가드**(slip `PNG_MAX_BYTES=50*1024` 미러).
- `signature_hash VARCHAR(64)` — SHA-256 hex. 클라 계산·전송, BE 재검증(불일치 400).
- `signed_at TIMESTAMP` — 최종 **등록**(관리) 시각. **결재란에 표시 안 함**(§6.3).
- `signature_channel VARCHAR(20)` — **SignatureChannel enum 단일 진실원 = {MOBILE_CANVAS, UPLOAD}**. CHECK 제약 IN 목록·도메인 enum·FE 타입 3곳 정확 일치([[feedback_enum_expansion_check_constraint]]). slip의 `PAPER_SCAN`과 도메인 다름(혼용 금지).
- 전부 nullable (미등록 = NULL).
- ※ 컬럼명≠필드명 허용 선례 있음(`position`→`job_title`) — 단 서명은 필드명=컬럼명 카멜/스네이크 1:1.

### 4.2 도메인 메서드 (직접 set 금지, `@Getter` only + change*/update* 컨벤션, [[project_build_conventions]])
- `registerSignature(byte[] png, String hash, SignatureChannel channel)` — 4필드 원자 set. 재등록 = 교체(+ audit RECORD).
- `invalidateSignature(String reason)` — 서명 4필드 NULL + audit INVALIDATE. 이미 NULL이면 409(slip 미러).

### 4.3 감사 테이블 `employee_signature_audit` (slip `slip_signature_audit` 미러)
`id UUID PK, employee_id UUID NOT NULL, action VARCHAR(20) NOT NULL CHECK(action IN ('RECORD','INVALIDATE')), signature_hash VARCHAR(64), signature_channel VARCHAR(20), reason VARCHAR(500), actor_user_id VARCHAR(50)` + BaseEntity 7 audit.

### 4.4 핸드오프 토큰 테이블 `employee_signature_handoff_token` (P6)
`id UUID PK, employee_id UUID NOT NULL, token VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMP NOT NULL, used_at TIMESTAMP NULL, actor_user_id VARCHAR(50)` + BaseEntity. token = SecureRandom→base64url(slip 패턴). 1회용(`used_at` 소진), TTL=10분, 재발급 시 동일 사원 미사용 토큰 무효화.

### 4.5 Flyway (user-service)
- VXX(다음 번호) — `employees` 4컬럼 ADD + CHECK + audit 테이블 + handoff 토큰 테이블.
- **fresh Postgres probe 의무**([[feedback_migration_fresh_postgres_probe]]): DROP/CREATE DB + 대상테이블 seed + `cat VXX.sql | psql ON_ERROR_STOP`. Windows 로컬 Testcontainers skip이 syntax error를 가린 전례. 적용 후 불변([[feedback_applied_migration_immutable]]).

---

## 5. 등록 흐름 (D1/D3)

### 5.1 경로 (a) — 이미지 업로드 (desktop 즉시)
1. UsersPage → 사원 행 "서명 등록" 모달 → 파일 선택(PNG/JPG).
2. 클라 정규화: **브라우저 canvas 기반(외부 의존성 0)**. 리사이즈(인감 비율) + 용량 가드(≤50KB). 흰배경 투명화 = best-effort(실패 시 원본 보존). 미리보기(SignatureViewer).
3. SHA-256 → `PATCH /api/v1/admin/users/{id}/signature {png(base64), hash, channel: UPLOAD}`.
4. BE(AdminUserController): hash 재검증 + PNG magic-byte 검증 + ≤50KB 서버 가드(초과 422) → `Employee.registerSignature` → 200. 권한 = `admin.users`.

### 5.2 경로 (b) — 모바일 손그림 (핸드오프)
1. 모달 "모바일로 그리기" → `POST /api/v1/admin/users/{id}/signature/handoff-token` → `{token, qrUrl(실 origin), expiresAt}`.
2. desktop: **QR(주) + 복사 링크(부)** + **폴링**(`GET /api/v1/admin/users/{id}/signature/handoff/{token}/status → {used, expired}`, 2s 간격, 최대=TTL 10분, used/expired/취소 시 종료). *SSE 비채택(YAGNI).*
3. 사원이 폰으로 QR 스캔 → `qrUrl` 열기 (**NEW 모바일 공개 웹앱**, D4).
4. design-system `SignaturePad`(`toDataURL('image/png')`) → 손서명 → 제출.
5. `POST /api/public/employee-signatures/{token} {png(base64), hash}` → user-service: 토큰 검증(미만료·미사용) → `registerSignature(MOBILE_CANVAS)` → `used_at` 소진. **NO-AUTH 토큰 게이트.**
6. desktop 폴링이 used 감지 → 모달 미리보기 반영.

### 5.3 모바일 공개 웹앱 (D4 — NEW, Phase 5 deferred 해소)
- slip 인수자 "공개 서명 페이지"는 **실재 배포본이 없음** — 현 MobileSignaturePage/MobileRecipientPage는 **Electron desktop 렌더러 내 '모바일 mock'**(`createHashRouter`), 실 공개 웹앱(`sign.samhan-air.com`/`clients/mobile-public/dist`)은 **Phase 5 deferred**(DNS만, nginx 404, 번들 미빌드 — `docs/dev-reports/signature-slice-C/nginx-sign-deferred.md`).
- **신규 산출물**: `clients/mobile-public`(또는 동등) vite 번들 — 단일 SignaturePad 서명 페이지(`@samhan/design-system` 재사용) + 제출. 빌드 산출물·배포 origin(실 URL 베이스)·게이트웨이 정적 서빙 경로 정의.
- **재사용 자산**: 동일 앱이 slip 인수자 공개 서명(Phase 5)도 호스트 가능 → 향후 slip 핸드오프 unblock.

---

## 6. 결재란 스탬프 (D2 — 인감 · 실시간 조회) ⚠️ 신규 구축

### 6.1 식별 키 (P4 = Employee.id)
- **출고인** = `slip.dispatcherUserId` (`accept()`, `Slip.java:178-179,926-934`).
- **검수인** = `slip.inspectorUserId` (`inspect()`, `Slip.java:189-190,968-974`).
- **작성자** = `slip.createdBy` (BaseEntity audit). **FE 미노출** — slip-service enrichment 단계 내부 키로만 사용, 응답엔 `ownerUserId` 신규 노출 안 함(UUID 비공개, [[feedback_uuid_no_user_visibility]]). 응답엔 `ownerFullName` + `ownerSignaturePng`만.

### 6.2 enrichment — **"확장"이 아니라 "구축"** (검증 REFUTED 반영)
> **ground truth**: slip-service는 오늘 dispatcher/inspector fullName을 **resolve하지 않음**(raw UUID 문자열 저장·노출, `SlipDetailResponse.java:79`). desktop `SlipApprovalActor{userId,fullName,signedAt}`(`slip.ts:87-94`)는 **FE 선(先)정의, BE 미생산**. owner만 `SlipService.getOne()→resolveOwnerFullName()→UserInternalClient.resolveFullName()→GET /internal/users/{userId}`(`SlipService.java:1162,1410`)로 enrich되고 **단일 GET 한정**.

신규 작업 3전선:
1. **user-service**: `POST /internal/users/signatures {userIds[]} → {employeeId: {signaturePngBase64, signedAt}}` 신규 — 기존 `display-names`/`verify-bulk` 배치 패턴(`findAllByIdIn`) 1:1 미러. X-Internal-Token + hasRole MASTER(P5).
2. **slip-service `SlipService.getOne()`**: dispatcher/inspector도 이름 resolve(기존 `display-names` 재사용 가능) + 3자 서명 배치 조회(신규 endpoint). RestClient graceful fallback(404/5xx/토큰미설정 → 빈 서명, 500 금지 — `UserInternalClient` 기존 정책 미러).
3. **`SlipDetailResponse` reshape**: dispatcher/inspector를 actor 객체(또는 `*FullName`+`*SignaturePng`)로 노출 + `ownerSignaturePng` 추가. ⚠️ **enrichment-on-GET-only**: mutation 응답(`SlipDetailResponse.from(slip)`)은 null → 인쇄/상세는 반드시 `GET /slips/{id}` 재조회 경로 사용(현 print 경로가 getOne 사용함 확인).

### 6.3 렌더 (DispatchView + OutboundView, P7)
- `DispatchView.tsx` `RoleCell` **기존 `signaturePng` stub 주입**(`:63,74`) — 작성자(`:152`)/출고인(`:153`)/검수인(`:154`) 셀만. **담당부서·결제예정일 셀 = stamp 미적용**(이름/값만). `OutboundView.tsx` 출고인 stamp slot도 동일 주입.
- 서명 없으면 현행 빈 공간 fallback 유지(CSS-only, 셀 grid·폭 무변경). **`signedAt`은 렌더하지 않음**(인감=등록시각 무관; dispatcher/inspector SlipApprovalActor.signedAt 경로 살아있어 회귀 위험 → RoleCell이 time 미표시 계약테스트 박제).
- 실시간 의미: 재출력마다 현재 서명. **재등록/무효화 시 과거 교부본과 화면 불일치 = 의도된 동작으로 수용**(스냅샷 거부, D2). 퇴사 사원 = 등록 서명 그대로 stamp(과거 사실 보존).

---

## 7. 권한 / 게이트웨이 / 범위

- 등록·업로드 = `admin.users` UPDATE(AdminUserController 기존 게이트, P1). 무효화 = **MASTER 한정**(P2).
- 모바일 제출 = `/api/public/employee-signatures/**` **NO-AUTH 토큰 게이트**.
- 내부 서명 조회 = `/internal/users/signatures` X-Internal-Token + hasRole MASTER(P5).
- **게이트웨이 라우팅(신규, BLOCKER 교정)**: 현 `/api/public/**`는 slip-service 단독 바인딩(`slip-service-public`, `application.yml:77-86`). user-service 공개 라우트는 **신규 정의 + 경로 충돌 회피** 필요 → `/api/public/employee-signatures/**`를 user-service로 (더 구체 경로 우선순위 or 전용 prefix), JWT 필터 없이 strip-only + `StripInboundIdentityHeaders`. user-service SecurityConfig에 해당 공개 경로 permitAll + identity 헤더 fail-CLOSED([[feedback_identity_header_authz_antipattern]]).
- page-code/시드 **신규 0** 목표(기존 `admin.users` 재사용). 무효화 MASTER는 기존 MASTER seed로 통과(신규 page-code 불요).
- 범위 = **출고전표(DispatchView+OutboundView) 결재란만**. 거래명세서·arologis 제외.
- ⚠️ 기존 불일치 상속 기록: FE 메뉴는 `admin.employees` 가드인데 호출 엔드포인트는 `admin.users` 가드(선재 FE/BE mismatch). 서명 엔드포인트는 호출 컨트롤러(AdminUserController=`admin.users`)에 맞춤.

---

## 8. 미해결 / 개발책임자 확인 대기

(검증·결정으로 대부분 해소. 잔여 경미 항목만.)
- **이미지 투명화 정책**: best-effort canvas(외부 의존성 0) 기본. 디자이너 가이드에서 인감 비율·최대 치수 확정. *투명화를 비목표로 뺄지(업로드 원본+리사이즈·용량만)는 디자이너 검토.*
- **모바일 공개 웹앱 origin/배포**: 실 URL 베이스·게이트웨이 정적 서빙·DNS(Phase 11 cutover 연계?)는 C2 착수 시 DevOps 확정.

> B2(무효화 권한)·B3(거래명세서)는 §2 P2 / §1 비목표로 **확정 해소**(미해결 아님).

---

## 9. 슬라이스 계획

각 슬라이스: 조기 PR([[feedback_open_pr_early]]) → Codex 구현 → Opus 5-agent → Codex 5-agent → PM 종합 → CI green → **Docker 2-디바이스 실QA**(데스크톱 QR 발급 + 실 폰 손서명 제출 + 전표 결재란 스탬프 라이브 캡처, [[feedback_no_fake_data_ever]]·[[feedback_overnight_live_capture]]) → 머지. **배포 순서 = user-service(C1) 먼저 → slip-service(C3)**; 서명 미배선 구간 graceful fallback(빈 공간, 500 아님).

- **C1a (서명 저장소 · 인증 경로)**: Employee 4컬럼 + Flyway(CHECK+audit) + `registerSignature`/`invalidateSignature` 도메인 + AdminUserController `PATCH .../signature`(업로드) + `DELETE .../signature`(무효화 MASTER) + `POST /internal/users/signatures` 배치. JUnit IT(Testcontainers) + fresh Postgres probe.
- **C1b (핸드오프 토큰 · 공개 인증우회 표면)**: `employee_signature_handoff_token` 테이블 + 토큰 발급/상태 엔드포인트 + **공개 제출 `POST /api/public/employee-signatures/{token}`** + 게이트웨이 공개 라우트 + user-service SecurityConfig. ⚠️ **보안 표면 집중** → 5-agent 리뷰가 토큰 위협모델(만료/재사용/위조/충돌)에 집중하도록 분리.
- **C2 (등록 UX + 모바일 공개 웹앱)**: desktop 서명 모달(업로드 + QR + 폴링) + **NEW `clients/mobile-public` 서명 페이지**(빌드·배포·게이트웨이 정적). Playwright(desktop) + 실 폰 캡처.
- **C3 (스탬프 · enrichment 구축)**: slip-service `getOne` dispatcher/inspector 이름+서명 resolve(신규) + `SlipDetailResponse` reshape(+`ownerSignaturePng`) + DispatchView·OutboundView RoleCell 주입. RestClient 계약테스트 다운스트림 선검증([[feedback_restclient_contract_test_false_green]]) + 라이브 전표 스탬프 캡처.

> 의존: C3는 C1a `POST /internal/users/signatures` 응답 DTO 확정 후 착수. C1a 머지 후 internal DTO 고정 전제 하에 C2/C3 병행 가능.

---

## 10. 테스트 / QA 전략

- **BE**: Employee 서명 IT(저장·해시검증·≤50KB·재등록교체·무효화409·**slip userId로 조회 시 서명 반환=join key 회귀**), 토큰 IT(만료·재사용 거부·재발급 무효화), internal 배치 IT, fresh Postgres probe. 외부 client @MockBean 격리.
- **계약**: slip-service → user-service `/internal/users/signatures` 실HTTP 계약테스트(MockRestServiceServer, 다운스트림 컨트롤러·DTO·404 fallback 선검증).
- **FE**: desktop 모달 Playwright(업로드·QR 발급·폴링), 모바일 공개 웹앱 서명 제출, real-qa(vite mock off).
- **라이브 실QA**: 관리자 QR 발급 → 실 폰 손서명 제출 → 등록 확인 → DispatchView·OutboundView 작성자/출고인/검수인 스탬프 캡처. UUID 비노출 실증([[feedback_uuid_no_user_visibility]]). + 이미지 업로드 경로 별도 캡처.

---

## 11. 관련 메모리 / 검증된 코드 앵커

**메모리**: [[project_slip_shipout_print_form]] · [[feedback_identity_header_authz_antipattern]] · [[feedback_migration_fresh_postgres_probe]] · [[feedback_enum_expansion_check_constraint]] · [[feedback_applied_migration_immutable]] · [[feedback_restclient_contract_test_false_green]] · [[feedback_no_fake_data_ever]] · [[feedback_overnight_live_capture]] · [[feedback_defect_family_sweep_fix]] · [[feedback_uuid_no_user_visibility]] · [[project_company_config_menu]] · [[feedback_temp_multimodel_workflow]] · [[feedback_pm_permission_autonomy]]

**코드 앵커 (9-agent 검증 완료)**:
- `clients/desktop/src/renderer/print/DispatchView.tsx:60-83,151-155` — RoleCell `signaturePng` stub + 3자 셀
- `clients/desktop/src/renderer/print/OutboundView.tsx` — 출고인 stamp slot (P7)
- `services/user-service/.../domain/Employee.java:46,58` — 서명 필드 부재, BaseEntity 7 audit, `@Getter`+도메인메서드
- `services/user-service/.../web/AdminUserController.java` (P1 대상) / `web/EmployeeController.java:50` (UI 호출자 0) / `web/InternalUserController.java:89,199` (findById join key + display-names 배치 선례)
- `clients/desktop/src/renderer/api/adminApi.ts` (메뉴 실 backend) / `routes/admin/UsersPage.tsx`
- `clients/desktop/src/renderer/api/slip.ts:87-94,117-126` — SlipApprovalActor(FE 선정의) + ownerUserId 부재
- `services/slip-service/.../domain/Slip.java:178-194,926-934,968-974,1199-1295` — dispatcher/inspector userId + recordSignature/invalidateSignature
- `services/slip-service/.../service/SlipService.java:1162,1410` · `client/UserInternalClient.java:75` · `web/dto/SlipDetailResponse.java:79`
- `services/slip-service/.../db/migration/V5__add_slip_signature.sql` — 미러 원본
- `clients/web/design-system/src/components/SignaturePad/SignaturePad.tsx:64,292-314` · `SignatureViewer` — 재사용(파일업로드 affordance 없음=신규 uploader 필요)
- `services/api-gateway/.../application.yml:77-86` — `/api/public/**`→slip(충돌 회피 대상)
- `docs/dev-reports/signature-slice-C/nginx-sign-deferred.md` — mobile-public Phase5 deferred 근거
