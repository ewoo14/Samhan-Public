# 사원 서명 등록 → 출고전표 결재란 인감 Implementation Plan (에픽 인덱스)

> **For agentic workers / 실행 방식:** 본 에픽은 프로젝트 규율([[feedback_codex_implements_claude_reviews]])상 **Codex 가 슬라이스별 구현**하고 Claude 가 dual review/PR/git workflow 를 담당한다. 각 슬라이스 = 1 PR = 독립 실행/테스트 단위. 슬라이스 plan 의 Step 은 `- [ ]` 체크박스로 추적한다. 표준 워크플로우([[feedback_temp_multimodel_workflow]]): Opus 계획/PR → Codex 구현 → Opus 5-agent → Codex 5-agent → PM 종합 → CI green → Docker 실QA → 머지.

**Goal:** 각 사원의 서명(이미지 업로드 또는 모바일 손그림)을 1회 등록해 두면, 그 사원이 출고전표 결재란의 작성자/출고인/검수인 자리에 들어갈 때마다 등록 서명이 인감(도장)처럼 실시간 자동 스탬프된다.

**Architecture:** user-service `Employee` 에 서명 저장(BYTEA+hash+channel, slip 서명모델 미러) → 관리자 desktop 모달이 이미지 업로드(즉시) 또는 모바일 핸드오프(QR 일회용 토큰 → 신규 `mobile-public` 공개 웹앱에서 손서명 제출) → slip-service `getOne` enrichment 가 작성자/출고인/검수인(=Employee.id) 서명을 배치 조회해 `SlipDetailResponse` 에 동봉 → desktop `DispatchView`/`OutboundView` 의 기존 `RoleCell signaturePng` stub 에 주입. 전표별 결재 워크플로우 신설 없음(인감=실시간 조회).

**Tech Stack:** Spring Boot 3.3 / Java 17 / JPA / Flyway / Testcontainers (BE) · React 18 + Vite + Electron + @samhan/design-system (desktop FE) · 신규 Vite 공개 웹앱(mobile-public) · Spring Cloud Gateway · PostgreSQL service-per-DB.

**Spec:** [docs/superpowers/specs/2026-06-21-employee-signature-stamp-design.md](../specs/2026-06-21-employee-signature-stamp-design.md) (9-agent 적대 검증 완료).

---

## Global Constraints (모든 Task 에 암묵 적용)

- **BaseEntity 7 audit** 의무 + **Soft Delete only** (모든 신규 엔티티). [[project_build_conventions]]
- **도메인 메서드 chain, 직접 set 금지** — `@Getter` only, 생성자/정적 factory + 도메인 메서드. [[project_build_conventions]]
- **한국어** commit/PR/Issue/Javadoc 의무(prefix/trailer 예외). [[feedback_korean_commits]] · 커밋 = Write→`git commit -F` 파일. [[feedback_bash_commit_message_file]]
- **SignatureChannel = {MOBILE_CANVAS, UPLOAD}** 단일 진실원. CHECK 제약 IN 목록 = 도메인 enum = FE 리터럴 3곳 정확 일치. [[feedback_enum_expansion_check_constraint]] (slip 의 PAPER_SCAN 과 도메인 다름 — 혼용 금지)
- **PNG ≤ 50KB** 서비스레이어 가드(`PNG_MAX_BYTES = 50*1024`) + PNG magic-byte 검증 + SHA-256 hex 재검증(불일치 400, 초과 422). slip `SlipSignatureService` 미러.
- **join key = Employee.id** (= `Slip.createdBy` = `dispatcherUserId` = `inspectorUserId` = 게이트웨이 X-User-Id = canonical user UUID). assigned UUID(생성 아님). C1a IT 에 join-key 회귀 테스트 의무.
- **내부 인증 = X-Internal-Token + `@PreAuthorize("hasRole('MASTER')")`** (user-service `InternalUserController` 패턴). slip-style P0-B 아님.
- **신규 admin 엔드포인트 = `@RequireDepartment(Department.EXECUTIVE_OFFICE)` + `@RequirePermission(page="admin.users", action=...)` 둘 다** (AdminUserController 실 패턴 `:95-96`). 무효화 = `action=DELETE`(admin.users DELETE seed = MASTER 한정 → 신규 page-code/시드 0). [[feedback_pm_permission_autonomy]] · [[feedback_role_naming_full]]
- **마이그레이션**: fresh Postgres probe 의무([[feedback_migration_fresh_postgres_probe]]), 적용 후 불변([[feedback_applied_migration_immutable]]). user-service V10(C1a)·V11(C1b).
- **게이트웨이**: `/api/public/employee-signatures/**` → user-service(JWT 필터 없음, StripPrefix=1, identity 헤더 strip), 기존 `/api/public/**`→slip 보다 **구체 경로 우선**. [[feedback_identity_header_authz_antipattern]]
- **렌더링**: 스탬프 = 작성자/출고인/검수인 **3셀만**(담당부서/결제예정일 미적용). `signed_at` 렌더 금지(인감=등록시각 무관). DispatchView + OutboundView **둘 다**([[feedback_defect_family_sweep_fix]]).
- **UUID 비공개**: `ownerUserId` 응답 노출 금지(BE 내부 enrichment 키로만). [[feedback_uuid_no_user_visibility]]
- **실 QA**: 가짜 데이터/합성 금지([[feedback_no_fake_data_ever]]), Docker 라이브 캡처([[feedback_overnight_live_capture]]), RestClient 계약테스트 다운스트림 선검증([[feedback_restclient_contract_test_false_green]]).

---

## 공유 계약 (슬라이스 경계 시그니처 — 슬라이스 plan 이 글자 그대로 따름)

```
[user-service Employee 서명 필드]  byte[] signaturePng(signature_png BYTEA) · String signatureHash(signature_hash VARCHAR64) ·
   LocalDateTime signedAt(signed_at) · SignatureChannel signatureChannel(signature_channel VARCHAR20)
[도메인]  Employee.registerSignature(byte[] png, String hash, SignatureChannel channel) · Employee.invalidateSignature(String reason)
[감사]  EmployeeSignatureAudit  action∈{RECORD,INVALIDATE}
[토큰]  EmployeeSignatureHandoffToken{ id, employeeId, token(base64url UNIQUE), expiresAt, usedAt, actorUserId }  TTL=10분, 1회용
[AdminUserController]
   PATCH  /api/v1/admin/users/{id}/signature   {signaturePngBase64, signatureHash, channel} -> EmployeeSignatureResponse{registered, signedAt, signatureChannel}
   DELETE /api/v1/admin/users/{id}/signature?reason=...  -> 204 (admin.users DELETE = MASTER)
   POST   /api/v1/admin/users/{id}/signature/handoff-token -> {token, qrUrl, expiresAt}
   GET    /api/v1/admin/users/{id}/signature/handoff/{token}/status -> {used, expired}
[공개]  POST /api/public/employee-signatures/{token}  {signaturePngBase64, signatureHash} -> 200 (NO-AUTH 토큰게이트)
[내부]  POST /internal/users/signatures  {userIds:UUID[]} -> ApiResponse<Map<UUID, EmployeeSignatureDto>>
   EmployeeSignatureDto{ String signaturePngBase64; String signedAt }  (미등록 사원 = 맵 생략)
[slip-service]  UserInternalClient.resolveSignatures(List<UUID>) -> Map<UUID,EmployeeSignatureDto> (404/5xx→빈맵 graceful) ·
   SlipDetailResponse: dispatcher/inspector actor{fullName, signaturePngBase64} + ownerFullName + ownerSignaturePngBase64 (enrichment=getOne 한정)
[desktop]  RoleCell signaturePng?:string|null (기존 stub 주입) · slip.ts SlipApprovalActor 에 signaturePng 추가 + ownerSignaturePng 추가(ownerUserId 미노출)
```

---

## File Structure (영역별 — 정확 경로는 각 슬라이스 plan)

- **user-service BE (C1a)**: `domain/{SignatureChannel,EmployeeSignatureAudit,SignatureAuditAction}.java` 신규 · `domain/Employee.java` 4필드+도메인메서드 · `repository/EmployeeSignatureAuditRepository.java` · `service/EmployeeSignatureService.java` · `web/AdminUserController.java`(PATCH/DELETE) · `web/InternalUserController.java`(배치) · `web/dto/*`(5종) · `db/migration/V10__add_employee_signature.sql` · IT 4종.
- **user-service BE + gateway (C1b)**: `domain/EmployeeSignatureHandoffToken.java` · `repository/*` · `service/EmployeeSignatureHandoffService.java` · `web/AdminUserController.java`(handoff-token/status) · `web/PublicEmployeeSignatureController.java` · `config/SecurityConfig.java` · `db/migration/V11__*.sql` · `api-gateway/.../application.yml` · 보안 IT.
- **desktop FE + mobile-public (C2)**: `clients/desktop/.../routes/admin/UsersPage.tsx` + 서명 모달 컴포넌트 · `api/adminApi.ts` 신규 함수 · 신규 `clients/mobile-public/*`(Vite 앱, SignaturePad 페이지) · Playwright.
- **slip-service BE + desktop FE (C3)**: `slip/client/UserInternalClient.java`(resolveSignatures) · `slip/service/SlipService.java`(getOne enrichment) · `slip/web/dto/SlipDetailResponse.java`(reshape) · 계약테스트 · `clients/desktop/.../api/slip.ts` 타입 · `print/DispatchView.tsx`·`print/OutboundView.tsx` 주입.

---

## 슬라이스 순서 / 의존 / PR 경계

| 슬라이스 | 내용 | 의존 | PR |
|---|---|---|---|
| **[C1a](2026-06-21-employee-signature-C1a-store-plan.md)** | 서명 저장소·인증 경로 (user-service) | 없음 (먼저) | `[FEAT] C1a 서명 저장소` |
| **[C1b](2026-06-21-employee-signature-C1b-handoff-plan.md)** | 핸드오프 토큰·공개 표면 (user-service+gateway) | C1a 머지 | `[FEAT] C1b 핸드오프 토큰` |
| **[C2](2026-06-21-employee-signature-C2-ux-plan.md)** | 등록 UX + 모바일 공개 웹앱 (desktop+mobile-public) | C1a+C1b 엔드포인트 | `[FEAT] C2 등록 UX·모바일앱` |
| **[C3](2026-06-21-employee-signature-C3-stamp-plan.md)** | enrichment 구축 + 결재란 스탬프 (slip+desktop) | C1a `/internal/users/signatures` 배포 | `[FEAT] C3 결재란 인감` |

**배포 순서**: user-service(C1a→C1b) 먼저 → slip-service(C3). C3 미배선 구간 = 빈 서명 graceful fallback(빈 공간, 500 금지). C1a 머지 후 internal DTO 고정 전제 하에 C2/C3 병행 가능.

---

## 구현 시점 확인 항목 (정찰로 확정, Codex 착수 시 검증)

- **`BusinessException` errorCode getter 명**: C1a 테스트가 `getErrorCode()` 가정 — 실 클래스에서 컴파일 안 되면 실 getter 명으로 정렬(slip 동일 클래스 사용).
- **admin.users DELETE seed = MASTER 한정 실증**: 권한 seed(V6 계열)가 실제로 MASTER 만 `admin.users` DELETE 보유하는지 재확인. 다른 role 보유 시 spec P2 'MASTER 한정' 위배 → 별도 게이트 검토.
- **QR 라이브러리(C2)**: 기존 의존성 확인 후 없으면 추가 최소화(또는 BE 가 QR PNG 생성).
- **mobile-public origin/DNS(C2)**: 실 URL 베이스·게이트웨이 정적 서빙·Phase 11 cutover 연계 = DevOps 확정.

---

## Execution Handoff

각 슬라이스 plan(C1a→C1b→C2→C3)을 순서대로 실행. 프로젝트 규율상 **Codex 가 슬라이스별 구현** + Opus/Codex dual 5-agent review + Docker 실QA + 머지. 슬라이스 plan 의 Task 가 bite-sized TDD step(실패테스트→FAIL확인→최소구현→PASS확인→commit)을 글자 그대로 담는다.
