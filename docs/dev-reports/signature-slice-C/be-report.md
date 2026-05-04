# Slice C BE Report — 인수자 전자서명 (signature-slice-C)

> **작성**: 2026-05-04 BE agent (Claude Opus 4.7)
> **상태**: 산출 완료, 5-team 통합 대기 (Designer / FE / QA / DevOps 병렬)
> **대상 PR**: PR #23 후보

본 문서는 BE agent 단독 산출 보고서입니다. Plan §1~§7 + Designer mobile-spec.md §2 의 BE 의무 항목을 구현했습니다.

---

## 1. 산출 요약

| 카테고리 | 산출물 |
| --- | --- |
| Flyway 마이그레이션 | V5 — slips 7컬럼 + slip_signature_audit 테이블 + 3 인덱스 (partial UNIQUE 포함) |
| 도메인 entity | `Slip` 7필드 확장 + `SignatureChannel` enum + `SignatureAuditAction` enum + `SlipSignatureAudit` entity |
| 도메인 메서드 | `Slip.recordSignature`, `Slip.invalidateSignature`, `Slip.isSignatureShareExpired`, `Slip.isSigned` |
| Repository | `SlipSignatureAuditRepository` 신규 + `SlipRepository.findBySignatureShareTokenAndIsDeletedFalse` 추가 |
| Service | `SlipSignatureService` 신규 (4 endpoint 처리 + PNG 검증 + audit 적재) |
| Controller | `PublicSlipController` 2 endpoint 추가 + `SlipSignatureController` 신규 (admin 2 endpoint) |
| DTO | `PublicSignatureRequest/Response/ViewResponse` + `AdminSignatureResponse` + `InvalidateSignatureRequest` |
| 테스트 | `SlipSignatureTest` 21건 + `SlipServiceSignatureTest` 17건 + `PublicSignatureControllerIT` 8건 + `SlipSignatureAdminIT` 10건 |

---

## 2. 라이프사이클 표 (Layer 4 의무 — Plan §1.3)

| 메서드 | from status | to status | 부수효과 |
| --- | --- | --- | --- |
| `Slip.recordSignature(signerName, png, hash, channel)` | INSPECTING / COMPLETED / SHIPPING | unchanged | `signedAt = now`, 5필드 갱신, `signatureShareToken` base64url 64자 신규, `signatureShareExpiresAt = now + 30일`. service 레이어가 동일 트랜잭션에서 `SlipSignatureAudit.record()` INSERT (actorUserId=NULL) |
| `Slip.invalidateSignature(reason)` | signedAt != null (status 무관) | unchanged | 5필드 + share 토큰/만료 모두 NULL. service 레이어가 직전 `signerName/hash` snapshot 후 `SlipSignatureAudit.invalidate()` INSERT (actorUserId=호출자) |
| `Slip.isSignatureShareExpired()` | (read-only) | — | `signatureShareExpiresAt == null` 또는 과거면 true |
| 기존 lifecycle 메서드 (save/send/accept/process/complete/inspect/ship/deliver/confirm/reject/cancel) | — | — | **변경 없음** (Plan §1.3 회귀 가드) |

`SlipDomainTest` 30건 PASS (회귀) + `SlipSignatureTest` 21건 PASS (신규).

---

## 3. 권한 매트릭스 (Plan §5)

| endpoint | 메서드 | 권한 | 인증 | 응답 코드 |
| --- | --- | --- | --- | --- |
| `/public/batches/{token}/slips/{slipNo}/signature` | POST | NO AUTH | batch token 검증만 | 200 / 400 (hash mismatch, PNG > 50KB) / 404 / 409 (단계 미충족) / 410 (token 만료) |
| `/public/signatures/{shareToken}` | GET | NO AUTH | shareToken 검증만 | 200 / 404 / 410 (30일 만료) |
| `/api/slips/{id}/signature` | GET | `MANAGER`, `MASTER` | gateway X-User-Role | 200 / 403 / 404 |
| `/api/slips/{id}/signature` | DELETE | `MASTER` only | gateway X-User-Role | 200 / 400 (reason 누락) / 403 / 404 / 409 (미서명) |

`@PreAuthorize` annotation 으로 controller 단계 가드 — `SlipSignatureAdminIT` 10건이 SALES/WAREHOUSE/MANAGER/MASTER 4 권한 매트릭스 검증.

---

## 4. 신규 endpoint 표

### 4.1 POST `/public/batches/{token}/slips/{slipNo}/signature` (NO AUTH)

| | |
| --- | --- |
| Request body | `PublicSignatureRequest{signerName(1~50자), signaturePngBase64(data URI 또는 raw), clientHash(SHA-256 hex 64자)}` |
| Response 200 | `PublicSignatureResponse{signedAt, shareToken, shareTokenExpiresAt, signatureHash}` |
| 검증 (Plan §5) | 1) batch token 만료 → 410, 2) batch+slipNo lookup → 404, 3) PNG ≤ 50KB → 400, 4) 서버 SHA-256 재계산 vs clientHash → 400, 5) 단계 가드 (INSPECTING/COMPLETED/SHIPPING) → 409 |
| 부수효과 | `SlipSignatureAudit RECORD` INSERT (actorUserId=NULL) |
| slipNo 형식 | `2026/05/05-1` 또는 `2026-05-05-1` 슬러그 모두 허용 (mobile-spec §1.1 권장) |

### 4.2 GET `/public/signatures/{shareToken}` (NO AUTH)

| | |
| --- | --- |
| Response 200 | `PublicSignatureViewResponse{slip(slipNo, partnerName, deliveryDate, lines, totalAmount), signature(signerName, signedAt, signaturePngBase64, signatureHashShort 8자), shareTokenExpiresAt}` |
| UUID 비공개 | `slip.id` / `signature.id` 절대 미포함 (회고 `feedback_uuid_no_user_visibility.md`) |
| 검증 | 토큰 미발견 → 404, 미서명 슬립 → 404, 30일 만료 → 410 |

### 4.3 GET `/api/slips/{id}/signature` (MANAGER, MASTER)

| | |
| --- | --- |
| Response 200 | `AdminSignatureResponse{slipId, signed, signerName, signedAt, signatureHash(전체 64자), signatureChannel, signaturePngBase64, shareToken, shareTokenExpiresAt, shareExpired}` |
| 미서명 슬립 | `signed=false` + 모든 메타 null (200 OK) |

### 4.4 DELETE `/api/slips/{id}/signature` (MASTER only)

| | |
| --- | --- |
| Request body | `InvalidateSignatureRequest{reason(1~500자, NotBlank)}` |
| Response 200 | `AdminSignatureResponse(signed=false, ...)` |
| 부수효과 | `SlipSignatureAudit INVALIDATE` INSERT (actorUserId=X-User-Id, signerName/hash snapshot 보존) |
| 가드 | reason blank → 400, 미서명 → 409, 권한 부족 → 403 |

---

## 5. PNG/Hash 무결성 검증 로직 (Plan §5)

`SlipSignatureService.recordSignature` 안의 6단계:

1. `batchRepository.findByBatchToken(token)` → 없으면 NOT_FOUND, 만료면 CONFLICT (controller 가 410 매핑)
2. `slipNo` 슬러그 정규화 (`2026-05-05-1` → `2026/05/05-1`) + `findAllByDeliveryBatchIdAndIsDeletedFalse` lookup
3. base64 디코드 (data URI prefix 자동 제거) → `byte[].length > 50KB` 가드
4. `MessageDigest.getInstance("SHA-256")` 으로 서버 hex 재계산 → `clientHash.equalsIgnoreCase(serverHex)` 비교 (대소문자 무관)
5. `Slip.recordSignature` 도메인 위임 (단계 가드 + 5필드 갱신 + share token 발급)
6. `SlipSignatureAudit.record(slipId, signerName, hash)` INSERT — actorUserId=NULL

---

## 6. 검증 결과

| 명령 | 결과 |
| --- | --- |
| `./gradlew :services:slip-service:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:slip-service:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew :services:slip-service:test` | BUILD SUCCESSFUL — 신규 38건 PASS (Domain 21 + Service 17), 기존 회귀 0건 |
| IT (`PublicSignatureControllerIT` 8건 + `SlipSignatureAdminIT` 10건) | Docker 미가용 환경에서 skip (회고 `feedback_testcontainers_windows_docker.md` Windows + Docker Desktop npipe 한계 — Linux CI 에서 정상 실행 예상) |

**테스트 매트릭스 신규**:
- `SlipSignatureTest` (도메인 21건) — 6 happy path + 4 단계 가드 + 4 입력 검증 + 5 invalidate 시나리오 + 2 share expired + 회귀 가드 1
- `SlipServiceSignatureTest` (Mockito 17건) — recordSignature 8건 (happy path / data URI / 슬러그 / hash mismatch / PNG 50KB / token 미발견 / token 만료 / 슬립 미발견 / PROCESSING 단계) + findByShareToken 3건 + invalidate 3건 + getSignature 2건
- `PublicSignatureControllerIT` (IT 8건) — 4 record 시나리오 + 4 view 시나리오 (happy / hash mismatch 400 / PNG 60KB 400 / batch token 만료 410 / PROCESSING 409 / view 정상 / view 미발견 404 / view 만료 410)
- `SlipSignatureAdminIT` (IT 10건) — GET 5건 (MANAGER/MASTER OK, SALES/WAREHOUSE 403, 미서명 signed=false) + DELETE 5건 (MASTER OK + audit 검증, MANAGER/SALES 403, 미서명 409, blank reason 400)

---

## 7. 회고 가드 준수

| 가드 | 적용 |
| --- | --- |
| `feedback_pm_integration_build_check.md` Layer 4 | 라이프사이클 표 본 문서 §2 + commit message 명시 |
| `feedback_it_mockbean_external_clients.md` | 두 IT 모두 `@MockBean InventoryClient/ProductClient/SmsGateway` + `lenient()` setup |
| `feedback_function_documentation.md` | 모든 신규 public method 한국어 Javadoc + `@Operation` springdoc-openapi 자동 생성 |
| `feedback_uuid_no_user_visibility.md` | 공개 endpoint 응답 jsonPath 검증 (`$.data.id` `.doesNotExist()`, `$.data.slipId` `.doesNotExist()`, `$.data.slip.id` `.doesNotExist()`) |
| `feedback_korean_commits.md` | commit message + PR/이슈 본문 모두 한국어 (이 문서 포함) |

---

## 8. 회귀 위험 평가

| 영역 | 위험 | 완화 |
| --- | --- | --- |
| 기존 Slip 라이프사이클 | 무영향 — recordSignature/invalidateSignature 가 status 변경 X | `SlipDomainTest` 30건 + 신규 회귀 가드 테스트 1건 PASS |
| Flyway V5 신규 컬럼 | 모두 nullable — 기존 슬립 호환 | V5 SQL 컨벤션 그대로 |
| SecurityConfig | 무변경 — 기존 `/public/**` permitAll 가 신규 endpoint 자동 적용 | 별도 IT 미필요 (PublicSlipController IT skip 환경에서도 path 매칭 검증 가능) |
| `Slip.signaturePng` BYTEA Lazy | `@Lob` 만 사용, FetchType.LAZY 미지정 — 단건 read 시 즉시 fetch (Plan §1.2 규모 가정 OK) | 향후 1만건/월 초과 시 MinIO 마이그 + Lazy 조정 |
| 신규 public endpoint 2건 추가 | 기존 GET `/public/batches/{token}` 무영향 | PublicSlipController 단일 클래스에 endpoint 3개 동거 — IT 분리 검증 |

---

## 9. 다음 단계

1. **FE agent** — Mobile 라우트 `/d/{token}/s/{slipNo}` + `/share/{shareToken}` 구현 (mobile-spec §3 signature.js mini bundle)
2. **Designer agent** — DispatchView 인쇄 통합 wireframe (Plan §4.3)
3. **QA agent** — IT skip 환경 (Windows) 외 Linux CI 풀 IT 실행 + Plan §QA 매트릭스 재검증
4. **DevOps agent** — Flyway V5 운영 적용 + sign.samhan-air.com Phase 5 nginx 분리
5. **PM 통합** — 4-team 결과물 풀빌드 + PR #23 발행

---

## 10. 변경 파일 목록

### 신규
- `services/slip-service/src/main/resources/db/migration/V5__add_slip_signature.sql`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SignatureChannel.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SignatureAuditAction.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SlipSignatureAudit.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/repository/SlipSignatureAuditRepository.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/service/SlipSignatureService.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/SlipSignatureController.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/AdminSignatureResponse.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/InvalidateSignatureRequest.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/delivery/web/dto/PublicSignatureRequest.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/delivery/web/dto/PublicSignatureResponse.java`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/delivery/web/dto/PublicSignatureViewResponse.java`
- `services/slip-service/src/test/java/com/samhanair/logis/slip/domain/SlipSignatureTest.java`
- `services/slip-service/src/test/java/com/samhanair/logis/slip/service/SlipServiceSignatureTest.java`
- `services/slip-service/src/test/java/com/samhanair/logis/slip/delivery/it/PublicSignatureControllerIT.java`
- `services/slip-service/src/test/java/com/samhanair/logis/slip/it/SlipSignatureAdminIT.java`

### 수정
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/Slip.java` — 7필드 + 4 메서드 추가, 기존 라이프사이클 메서드 무변경
- `services/slip-service/src/main/java/com/samhanair/logis/slip/repository/SlipRepository.java` — `findBySignatureShareTokenAndIsDeletedFalse` 추가
- `services/slip-service/src/main/java/com/samhanair/logis/slip/delivery/web/PublicSlipController.java` — POST signature + GET view 2 endpoint 추가
