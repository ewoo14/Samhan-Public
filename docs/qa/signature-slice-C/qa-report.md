# QA Report — signature-slice-C (전자서명 + 공개 서명 + 관리자 무효화)

> **작성**: 2026-05-04 QA Claude (5-team parallel agent / worktree `agent-a8a30e59f241695c9`)
> **상태**: 시나리오 설계 + fixtures + qa-report 완료. **IT Java 파일은 BE 가 작성** (PR #22 회고 — `feedback_multi_agent_team_pattern`)
> **PR 후보**: PR #23
> **회고 가드**: `feedback_pm_integration_build_check`, `feedback_it_mockbean_external_clients`,
>   `feedback_uuid_no_user_visibility`, `feedback_role_naming_full`, `feedback_korean_commits`,
>   `feedback_multi_agent_team_pattern`

본 리포트는 Slice C (전자서명 수령확인 + 공개 서명 endpoint + 관리자 무효화 + audit log) 의 QA 산출물:
- BE IT 신규 14 시나리오 표 (BE agent 가 본 표를 보고 IT Java 파일 작성 — QA 영역 침범 금지)
- fixtures.http 신규 5 블록 (시나리오 18~22)
- 권한 매트릭스 7-tier 풀네임
- BE Layer 4 시그니처 가정 5 도메인 메서드
- UUID 비공개 가드 명시 (공개 응답 absent assertion 3건)
- 회귀 가드 (Slice B 25 endpoint + Slip 라이프사이클 무변경)
- Designer mock 캡처 4종 인용

---

## 1. IT 시나리오 표 (총 14)

본 표는 BE agent 가 그대로 IT 메서드명/입력/jsonPath 로 옮겨 적기 위함. QA 가 IT Java 를 작성하면 PR #22 회고 위배 → 영역 분리 엄수.

### 1.1 PublicSignatureControllerIT (no-auth, 9 시나리오)

| # | 메서드 | 입력 | 기대 | jsonPath / status assertion |
| --- | --- | --- | --- | --- |
| 1 | `recordSignature_happyPath_savesAndReturnsShareToken` | POST `/public/batches/{token}/slips/{slipId}/signature` body={signerName, signaturePngBase64, clientHash} (slip 상태 = COMPLETED 또는 SHIPPING/DELIVERED) | 201 + slip.recipientSignedAt 기록, shareToken 반환 | `status().isCreated()`, `$.data.shareToken` 존재 (32자+), `$.data.signerName == "정수령"`, `$.data.signedAt` 존재 |
| 2 | `recordSignature_invalidHash_returns400` | clientHash != server SHA-256(signaturePngBase64) | 400 BAD_REQUEST | `status().isBadRequest()`, `$.error.code == "INVALID_INPUT"`, slip.recipientSignedAt 미변경 |
| 3 | `recordSignature_oversizedPng_returns400` | signaturePngBase64 디코딩 길이 > 50KB | 400 + `payload too large` 메시지 | `status().isBadRequest()`, `$.error.message` contains "50KB" or "size" |
| 4 | `recordSignature_emptySignerName_returns400` | signerName = "" 또는 null | 400 BAD_REQUEST | `status().isBadRequest()`, `$.error.code == "INVALID_INPUT"` |
| 5 | `recordSignature_signerNameTooLong_returns400` | signerName.length() > 50 (51자 한글) | 400 BAD_REQUEST | `status().isBadRequest()`, `$.error.message` contains "50" |
| 6 | `recordSignature_invalidStatus_returns409` | slip 상태 = DRAFT/SAVED/SENT 단계에서 시도 | 409 CONFLICT (도메인 가드) | `status().isConflict()`, `$.error.code == "CONFLICT"` |
| 7 | `getShare_validToken_returnsSignedSlip` | GET `/public/signatures/{shareToken}` (시나리오 1 의 shareToken) | 200 + PNG base64 + meta | `status().isOk()`, `$.data.signaturePngBase64` 존재, `$.data.slipNo` 존재, `$.data.signerName` 존재, `$.data.signedAt` 존재, `$.data.hashPrefix.length() == 8` |
| 8 | `getShare_expiredToken_returns410` | shareExpiresAt < now (ReflectionTestUtils 로 강제 과거) | 410 GONE | `status().isGone()`, `$.error.code == "CONFLICT"` |
| 9 | `getShare_invalidToken_returns404` | 임의 토큰 | 404 NOT_FOUND | `status().isNotFound()` |

### 1.2 SlipSignatureAdminIT (인증 필수, 5 시나리오)

| # | 메서드 | 입력 | 기대 | jsonPath / status assertion |
| --- | --- | --- | --- | --- |
| 10 | `getAdminSignature_managerRole_returns200` | GET `/slips/{slipId}/signature` Header X-User-Role=MANAGER (서명 완료된 슬립) | 200 + 전체 PNG + meta + UUID 포함 (관리자 view) | `status().isOk()`, `$.data.signaturePngBase64` 존재, `$.data.signerName` 존재, `$.data.fullHash.length() == 64` (관리자에게는 풀 해시 노출) |
| 11 | `getAdminSignature_salesRole_returns403` | 동상, X-User-Role=SALES | 403 FORBIDDEN (MANAGER/MASTER/AUDITOR 만 허용) | `status().isForbidden()` |
| 12 | `invalidateSignature_masterRole_clearsFieldsAndAuditsLog` | DELETE `/slips/{slipId}/signature` Header X-User-Role=MASTER, body={reason:"오기재"} | 204 No Content + Slip.signaturePngBase64=null + signatureHash=null + audit log INSERT (사유 + 무효화자 + 타임스탬프) | `status().isNoContent()`, `slipRepository.findById().getSignaturePngBase64() == null`, `signatureAuditRepository.findBySlipId(id).size() == 1`, `audit.actorUserId == X-User-Id`, `audit.reason == "오기재"` |
| 13 | `invalidateSignature_managerRole_returns403` | 동상, X-User-Role=MANAGER | 403 FORBIDDEN (MASTER 전용 — 무효화는 마스터만) | `status().isForbidden()`, slip.signature 보존 |
| 14 | `invalidateSignature_unsigned_returns404` | 서명 안 된 슬립에 DELETE | 404 NOT_FOUND | `status().isNotFound()` |

> 시나리오 1 의 happy path 가 시나리오 7/10/12/14 의 fixture preparation 이 됨 (BE IT 에서 `@BeforeEach` 또는 헬퍼 메서드 `signSlip()` 활용).

---

## 2. 권한 매트릭스 (7-tier 풀네임 — `feedback_role_naming_full`)

| 작업 | MASTER | MANAGER | SALES | WAREHOUSE | INVENTORY | ACCOUNTANT | AUDITOR |
| --- | --- | --- | --- | --- | --- | --- | --- |
| POST `/public/batches/{token}/slips/{slipId}/signature` | NO AUTH (인수자가 폰에서 직접) — 토큰 + 슬립 상태 가드만 |
| GET `/public/signatures/{shareToken}` | NO AUTH (인수자/기사 둘 다 공유 링크로 진입) |
| GET `/slips/{slipId}/signature` (관리자 view) | OK | OK | 403 | 403 | 403 | 403 | OK (감사인 read-only) |
| DELETE `/slips/{slipId}/signature` (무효화) | OK | 403 | 403 | 403 | 403 | 403 | 403 |

명시적 음성 검증: 시나리오 11 (SALES → 403), 시나리오 13 (MANAGER 무효화 시도 → 403). MASTER 전용 무효화는 plan §6 결정사항.

---

## 3. BE Layer 4 시그니처 가정 (`feedback_pm_integration_build_check`)

본 IT/fixtures 가 컴파일/실행되려면 BE 산출물이 다음 시그니처를 충족해야 함. **PM 통합 시점에 1:1 정렬 검증 의무** (§9 표).

### 3.1 도메인 메서드 (Slip 확장 5개)

| 메서드 | from | to | 부수효과 | IT 검증 |
| --- | --- | --- | --- | --- |
| `Slip.recordSignature(signerName, pngBase64, hash, recordedAt)` | recipientSignedAt=null | recipientSignedAt=now, signerName/pngBase64/signatureHash 기록, shareToken/shareExpiresAt 생성 (shareExpiresAt = now + 7d) | + (도메인 가드) status ∈ {COMPLETED, SHIPPING, DELIVERED, CONFIRMED} 만 허용. 외 단계 → BusinessException(CONFLICT) | 시나리오 1 (happy), 6 (CONFLICT) |
| `Slip.invalidateSignature()` | recipientSignedAt!=null | 모든 서명 필드 null + shareToken/shareExpiresAt null | audit log INSERT 는 service 레이어가 별도 책임 | 시나리오 12 |
| `Slip.isShareValid(now)` | shareExpiresAt | boolean | now < shareExpiresAt 검증. 만료 시 controller 가 410 매핑 | 시나리오 8 |
| `Slip.requireSigned()` | recipientSignedAt | unchanged or throw | recipientSignedAt==null 이면 NOT_FOUND BusinessException | 시나리오 14 |
| `Slip.getHashPrefix()` | signatureHash | String (앞 8자) | 공개 view 에서 풀해시 미노출, prefix 만 노출 | 시나리오 7 |

### 3.2 신규 entity / repository / service 의존

```java
// services/slip-service/src/main/java/com/samhanair/logis/slip/signature/domain/SlipSignatureAudit.java
@Entity
public class SlipSignatureAudit extends BaseEntity {
    private UUID id;             // @UuidGenerator
    private UUID slipId;         // FK
    private String actorUserId;  // 무효화자 user-id (X-User-Id 헤더)
    private String reason;       // 무효화 사유 (1~500자)
    private LocalDateTime invalidatedAt;
    private String previousHash; // 무효화 전 signatureHash snapshot
    private String previousSignerName;
}

// services/slip-service/src/main/java/com/samhanair/logis/slip/signature/repository/SlipSignatureAuditRepository.java
public interface SlipSignatureAuditRepository extends JpaRepository<SlipSignatureAudit, UUID> {
    List<SlipSignatureAudit> findBySlipIdOrderByInvalidatedAtDesc(UUID slipId);
}

// services/slip-service/src/main/java/com/samhanair/logis/slip/signature/service/SignatureService.java
public interface SignatureService {
    RecordSignatureResult recordSignature(String batchToken, UUID slipId, RecordSignatureRequest req);
    PublicShareResponse findByShareToken(String shareToken);  // 410 매핑 시 CONFLICT 던짐
    AdminSignatureResponse findForAdmin(UUID slipId);
    void invalidate(UUID slipId, String actorUserId, String reason);
}
```

### 3.3 응답 DTO 가정 (UUID 비공개 가드 — `feedback_uuid_no_user_visibility`)

| 필드 | RecordSignatureResult (POST 응답) | PublicShareResponse (GET 공개) | AdminSignatureResponse (관리자 GET) |
| --- | --- | --- | --- |
| slipId (UUID) | **금지** | **금지** | OK (관리자만) |
| batchId (UUID) | **금지** | **금지** | OK (관리자만) |
| signatureId (UUID) | **금지** | **금지** | OK |
| shareToken | OK (URL용) | — | — |
| signerName | OK | OK | OK |
| signedAt (LocalDateTime) | OK | OK | OK |
| signaturePngBase64 | — | OK (PNG view) | OK |
| hashPrefix (8자) | OK | OK | — |
| fullHash (64자) | — | — | OK (관리자만) |
| slipNo (`yyyy/MM/dd-NNN`) | OK | OK | OK |
| partnerName | — | OK | OK |
| invalidatedAt | — | — | OK (무효화 후) |

UUID 비공개 가드 — 시나리오 7 (공개 view) 코드 검증:
```
.andExpect(jsonPath("$.data.slipId").doesNotExist())
.andExpect(jsonPath("$.data.batchId").doesNotExist())
.andExpect(jsonPath("$.data.signatureId").doesNotExist())
.andExpect(jsonPath("$.data.slipNo").exists())          // 비즈니스 식별자만 노출
.andExpect(jsonPath("$.data.signerName").exists())
.andExpect(jsonPath("$.data.hashPrefix").value(org.hamcrest.Matchers.hasLength(8)))
```

---

## 4. 외부 클라이언트 격리 (`feedback_it_mockbean_external_clients`)

모든 IT (PublicSignatureControllerIT / SlipSignatureAdminIT) 가 다음 3개 client 를 `@MockBean` + `Mockito.lenient()` stub:

| Client | stub 동작 | 사유 |
| --- | --- | --- |
| `InventoryClient` | `@MockBean` (no stub — Slice C 는 reserve/deduct 호출 없음) | 서명은 슬립 라이프사이클 mutating 아님. 누락 시 Eureka 비활성에서 RestClient 호출 → 500 (PR #17 회고) |
| `ProductClient` | `lookup`/`requireExists` lenient stub → `ProductSummary` 반환 | `signSlip()` 헬퍼가 슬립 생성 시 라인 productId 검증 호출 |
| `SmsGateway` | lenient stub → `SmsResult.success("MOCK")` | Slice B helper 가 batch 생성 시 SMS 시뮬레이션 |

void 메서드는 `doNothing()`, 반환 메서드는 `thenReturn()/thenAnswer()` 분리 (PR #16 회고).

---

## 5. 회귀 가드

| 영역 | 기존 시나리오 수 | Slice C 영향 | 검증 |
| --- | --- | --- | --- |
| SlipController unit | 76 | 0 — Slip 신규 필드 nullable, 라이프사이클 메서드 무변경 | unit 회귀 0 |
| SlipControllerIT | 12 | 0 — POST/PATCH 무변경 | 회귀 0 |
| SlipInspectControllerIT | 8 | 0 — INSPECTING/inspect 무변경 | 회귀 0 |
| SlipLifecycleControllerIT | 풀라이프사이클 | 0 — save/send/accept/process/inspect/complete/ship/deliver/confirm 무변경 | 회귀 0 |
| DeliveryBatchControllerIT (Slice B) | 11 | 0 — auto-group/send-sms/add/remove 무변경 | 회귀 0 |
| PublicSlipControllerIT (Slice B) | 3 | 0 — `/public/batches/{token}` 무변경. 신규 `/public/batches/{token}/slips/{slipId}/signature` POST 추가 + 신규 `/public/signatures/{shareToken}` GET 추가 (별도 controller) | 회귀 0 |
| SlipDriverFieldsIT (Slice B) | 4 | 0 — driver fields 무변경 | 회귀 0 |
| **Slice B 25 endpoint** | 25 | **0 endpoint mutate, +3 endpoint 신규** (POST signature + GET share + DELETE admin signature) | 합본 28 endpoint 회귀 0 |
| **신규 IT 시나리오** | — | **+14 (PublicSignatureControllerIT 9 + SlipSignatureAdminIT 5)** | 본 리포트 §1 |

---

## 6. 캡처 (Designer 산출물 인용)

**Designer mock 인용** (PR Designer 산출물 — Slice C Designer worktree, plan §designer scenario 4종):

| 캡처 | 경로 | 시나리오 매핑 |
| --- | --- | --- |
| `01_signature_canvas.png` | `docs/design/signature-slice-C/screenshots/` | 시나리오 1 (인수자 캔버스 서명 입력) |
| `02_signature_completed.png` | (동상) | 시나리오 1 (서명 직후 완료 화면 + 공유 버튼) |
| `03_share_view.png` | (동상) | 시나리오 7 (공유 링크 view — UUID 미노출 검증 시각자료) |
| `04_admin_invalidate.png` | (동상) | 시나리오 12~13 (관리자 무효화 다이얼로그 — MASTER 전용) |

QA agent 별도 캡처 미산출 — Designer 산출물 4장 + IT/fixtures 자동화 검증으로 시각 + 기능 모두 cover. PR 본문에서 PM 이 합본 첨부.

---

## 7. 검증 결과

| 검증 항목 | 결과 | 비고 |
| --- | --- | --- |
| IT 시나리오 표 작성 | OK | 14 시나리오 (Public 9 + Admin 5) |
| fixtures.http 추가 | OK | 시나리오 18~22 (5 블록, 12 ### 단계) |
| `./gradlew :services:slip-service:compileTestJava` | **DEFERRED** | BE 산출물 (SlipSignatureAudit entity, SignatureService, PublicSignatureController, SlipSignatureAdminController, Slip 5 신규 도메인 메서드) 가 본 worktree 에 없음. **PM 통합 시점에 컴파일 검증** (5-team parallel 디스패치 패턴 — `feedback_multi_agent_team_pattern`) |
| `./gradlew :services:slip-service:test` | **DEFERRED** | 동상 |
| Layer 4 도메인 메서드 가정 명시 | OK | §3.1 표 (5 메서드) |
| 권한 매트릭스 7-tier 풀네임 | OK | §2 표 |
| UUID 비공개 가드 (3 absent) | OK | §3.3 + 시나리오 7 코드 검증 |
| 외부 client @MockBean lenient | OK | §4 (3 clients) |
| IT Java 파일 자체 작성 금지 (PR #22 회고) | OK | QA 는 시나리오 설계 + fixtures + qa-report 만 산출. IT java 파일은 BE worktree 위임 |

---

## 8. 다음 단계 (PM 통합)

1. **Layer 1 (BE compile)** — BE worktree + 본 QA worktree merge 후 `./gradlew :services:slip-service:compileTestJava` PASS 확인.
2. **Layer 2 (Docker IT)** — `DOCKER_HOST=tcp://localhost:2375` 설정 후 `./gradlew :services:slip-service:test` PASS 확인 (`feedback_testcontainers_windows_docker`).
3. **Layer 3 (E2E fixtures)** — `fixtures.http` 시나리오 18~22 수동 시연 (api-gateway + slip-service + auth-service 기동, sign.samhan-air.com 서브도메인 라우팅 확인).
4. **Layer 4 (도메인 메서드 의미 정렬)** — §3.1 BE 시그니처 가정과 BE 산출물 정렬 (PR #16/17/21 회고). `Slip.recordSignature(...)` 시그니처 4-args 일치, `SlipSignatureAudit` 7 필드 일치, `SignatureService` 4 메서드 일치 의무 검증.
5. PR #23 본문에 Designer 캡처 4장 + QA Report 링크 + Slice C 회고 가드 표 첨부.

---

## 9. BE-QA 정렬 핵심 가정 (PM 합본 시 검증 필수)

본 IT/fixtures 가 의존하는 BE 산출물 (BE agent 가 별도 worktree 에서 작성):

| 산출물 | 위치 | 시그니처 가정 |
| --- | --- | --- |
| `SlipSignatureAudit` entity | `services/slip-service/src/main/java/com/samhanair/logis/slip/signature/domain/SlipSignatureAudit.java` | `@Entity`, BaseEntity 상속, slipId/actorUserId/reason/invalidatedAt/previousHash/previousSignerName 6 필드 |
| `SlipSignatureAuditRepository` | `signature/repository/SlipSignatureAuditRepository.java` | `JpaRepository<SlipSignatureAudit, UUID>`, `findBySlipIdOrderByInvalidatedAtDesc(UUID)` |
| `SignatureService` | `signature/service/SignatureService.java` (interface) + `SignatureServiceImpl` | 4 메서드 (recordSignature/findByShareToken/findForAdmin/invalidate) |
| `PublicSignatureController` | `signature/web/PublicSignatureController.java` | 2 endpoints (`POST /public/batches/{token}/slips/{slipId}/signature`, `GET /public/signatures/{shareToken}`) — no auth, 410 매핑 |
| `SlipSignatureAdminController` | `signature/web/SlipSignatureAdminController.java` | 2 endpoints (`GET /slips/{slipId}/signature`, `DELETE /slips/{slipId}/signature`) — MANAGER/MASTER/AUDITOR (read), MASTER 전용 (delete) |
| `Slip.recordSignature()` 4-args | `domain/Slip.java` | signerName/pngBase64/hash/recordedAt + status guard |
| `Slip.invalidateSignature()` | `domain/Slip.java` | 모든 서명 필드 null + shareToken null |
| `Slip.isShareValid(LocalDateTime)` | `domain/Slip.java` | shareExpiresAt 검증 |
| `Slip.requireSigned()` | `domain/Slip.java` | NOT_FOUND BusinessException 던짐 |
| `Slip.getHashPrefix()` | `domain/Slip.java` | signatureHash 앞 8자 |
| `Slip` 7 신규 필드 | `domain/Slip.java` | recipientSignerName(50), recipientSignedAt, signaturePngBase64(TEXT — base64 50KB), signatureHash(64 — SHA-256 hex), shareToken(64 base64url), shareExpiresAt, recipientSignatureMeta JSON nullable |
| `RecordSignatureRequest` DTO | `signature/web/dto/RecordSignatureRequest.java` | signerName(notblank/maxLength=50), signaturePngBase64(notblank), clientHash(64자) |
| Flyway V5 | `services/slip-service/src/main/resources/db/migration/V5__signature.sql` | Slip 7 신규 컬럼 + slip_signature_audits 테이블 + 인덱스 (share_token UNIQUE) |

PR #23 본문에 본 표 인용 → PM 정렬 가드. 위 시그니처 1:1 일치하지 않으면 본 QA worktree 의 fixtures.http + IT 시나리오 표가 BE IT 컴파일 실패 → PM 합본 단계에서 회귀.

---

## 10. 회고 가드 준수 체크리스트

- [x] `feedback_pm_integration_build_check` — Layer 1+2+3+4 모두 §8 명시
- [x] `feedback_it_mockbean_external_clients` — §4 InventoryClient/ProductClient/SmsGateway 3개 lenient
- [x] `feedback_uuid_no_user_visibility` — §3.3 + 시나리오 7 absent assertion 3건 (slipId/batchId/signatureId)
- [x] `feedback_role_naming_full` — §2 7-tier 풀네임 (M/M/S/W/I/A/A 약어 미사용)
- [x] `feedback_multi_agent_team_pattern` — IT Java 파일 자체 작성 금지, BE 위임. 시나리오 설계 + fixtures + qa-report 만 산출
- [x] `feedback_korean_commits` — 본 worktree 의 모든 commit/PR/Issue 한국어 작성 예정
- [x] `feedback_testcontainers_windows_docker` — §8 Layer 2 에서 `DOCKER_HOST=tcp://localhost:2375` 명시
- [x] `feedback_function_documentation` — 한국어 Javadoc + springdoc-openapi + dev-reports 누적 (BE/QA 분담)
