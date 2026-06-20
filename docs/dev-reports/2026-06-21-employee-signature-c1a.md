# 사원 서명 인감 에픽 — 슬라이스 C1a 개발 리포트 (서명 저장소·인증 경로)

> 2026-06-21 · PR #547 · user-service 단독 · 에픽: [사원 서명 등록 → 출고전표 결재란 인감](../superpowers/specs/2026-06-21-employee-signature-stamp-design.md) 슬라이스 C

## 1. 개요
출고전표 결재란(작성자/출고인/검수인)에 사원 서명을 **인감(도장)처럼 자동 스탬프**하기 위한 4슬라이스(C1a/C1b/C2/C3) 중 **첫 슬라이스 = 서명 저장소 + 인증 경로**. user-service에 서명 영속·감사·등록/무효화 API + slip-service(C3)가 소비할 내부 배치 조회 API를 구축한다. slip-service/desktop/gateway 무변경.

## 2. 산출물
| 레이어 | 항목 |
|---|---|
| 도메인 | `SignatureChannel{MOBILE_CANVAS,UPLOAD}`, `Employee` 서명 4필드(`signaturePng`/`signatureHash`/`signedAt`/`signatureChannel`, 전부 nullable), `registerSignature`/`invalidateSignature` 도메인 메서드(직접 set 금지) |
| 감사 | `SignatureAuditAction{RECORD,INVALIDATE}`, `EmployeeSignatureAudit`(@UuidGenerator + BaseEntity 7 audit), `EmployeeSignatureAuditRepository` |
| 마이그레이션 | `V10` — employees 4컬럼 + `ck_employees_signature_channel` + `employee_signature_audit` 테이블(action/channel CHECK + 인덱스 2) |
| DTO | `EmployeeSignatureUploadRequest`, `EmployeeSignatureResponse`, `InternalSignatureBatchRequest`, `EmployeeSignatureDto` |
| 서비스 | `EmployeeSignatureService` — base64 디코드(400) + PNG magic-byte(422) + ≤50KB(422) + SHA-256 재검증(400) + 등록/무효화/배치 |
| API | `PATCH /api/v1/admin/users/{id}/signature`, `DELETE .../signature?reason=`, `POST /internal/users/signatures` |

## 3. API 계약
- **PATCH .../signature** (업로드/모바일 공통 저장) — body `EmployeeSignatureUploadRequest`, 200 `EmployeeSignatureResponse`. 권한 `@RequireDepartment(EXECUTIVE_OFFICE)` + `@RequirePermission(admin.users, UPDATE)`.
- **DELETE .../signature?reason=** (무효화) — 204. `@RequirePermission(admin.users, DELETE)` → seed상 **MASTER 한정**.
- **POST /internal/users/signatures** — body `{userIds[]}` (≤50), 200 `Map<UUID, EmployeeSignatureDto{signaturePngBase64(data URI), signedAt}>`, 미등록 사원 생략. `X-Internal-Token` + `@PreAuthorize hasRole('MASTER')`. **join key = `Employee.id` = slip `createdBy`/`dispatcherUserId`/`inspectorUserId`(P4)** → C3 enrichment 소비.

## 4. 검증
- 단위/IT **skipped=0**: EmployeeSignatureTest 5 · AuditTest 2 · ServiceTest 10 · AdminUserSignatureControllerIT 12 · InternalUserSignatureBatchControllerIT 7 · UserPermissionControllerIT 73 — 실 Testcontainers Postgres
- **V10 fresh Postgres probe** + **standalone 라이브 부팅**(ddl validate 통과) — [라이브 QA](../qa/employee-signature-c1a/live-qa.md)
- CI 2회(초기+fix) 전부 green

## 5. 듀얼 리뷰 (Opus 4.8 ↔ Codex, 사이클 2 수렴)
- 라운드 1: 🔵Opus 5-lens 36→19 confirmed + 🟣Codex P1·P2×3 → 교차합의 4 + 단독 confirmed
- fix(`b6822d3ed`): DELETE reason→500 차단(service INVALID_INPUT + MissingParam 핸들러), base64/batch DoS 가드(@Size), 데드코드 삭제, 테스트 +8, 권한 매트릭스
- 라운드 2: 양 엔진 **CONVERGED**(회귀 0)
- 의도된 미변경: audit FK 무(slip 미러), @Version(아키텍처), DTO 필드명(C3 계약 안정)

## 6. 다음 슬라이스 의존
- **C1b**: 핸드오프 토큰(V11) + 공개 제출 `/api/public/employee-signatures/{token}` + 게이트웨이 공개 라우트
- **C2**: desktop 서명 모달 + `clients/mobile-public` 서명 페이지
- **C3**: slip-service `getOne` enrichment(`/internal/users/signatures` 응답 DTO 계약 고정 전제) + DispatchView/OutboundView RoleCell 주입
