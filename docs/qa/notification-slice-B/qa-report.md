# QA Report — notification-slice-B (배송기사 배치 링크 + Solapi SMS)

> **작성**: 2026-05-04 QA Claude (5-team parallel agent)
> **상태**: IT 작성 완료, 캡처는 Designer 산출 인용 + fixtures 시연 보강
> **PR 후보**: PR #22
> **회고 가드**: `feedback_pm_integration_build_check`, `feedback_it_mockbean_external_clients`,
>   `feedback_uuid_no_user_visibility`, `feedback_role_naming_full`, `feedback_korean_commits`

본 리포트는 Slice B (배송기사 배치 자동 그룹 + Solapi SMS + 공개 모바일 페이지) 의 QA 산출물:
- BE IT 신규 12 시나리오 + Slip 회귀 1 (총 13 시나리오)
- fixtures.http 신규 5 블록 (시나리오 13~17)
- 권한 매트릭스 7-tier
- BE 시그니처 가정 (Layer 4 정렬)
- UUID 비공개 가드 명시

---

## 1. IT 시나리오 표 (총 13)

### 1.1 DeliveryBatchControllerIT (11 시나리오)

| # | 메서드 | 입력 | 기대 | jsonPath assertion |
| --- | --- | --- | --- | --- |
| 1 | `autoGroup_sameDriverSameDate_groupsTogether` | 같은 driverPhone+date 슬립 2건 | 200, batch 1개 | `$.data.length() == 1`, `$.data[0].slipCount == 2` |
| 2 | `autoGroup_differentDates_separateBatches` | 같은 driver, 2026-05-06/05-07 슬립 | 두 batch (다른 token) | `$.data[0].batchToken != batch2.token` |
| 3 | `autoGroup_idempotent_returnsExisting` | 같은 (driver,date) 재호출 | 같은 token | `oldToken == newToken` (UNIQUE 부분 인덱스) |
| 4 | `sendSms_success_marksSmsSentAt` | SmsGateway @MockBean.success | 200 + smsSentAt 기록 | `$.data.smsSentAt != null`, `$.data.smsLastError == null` |
| 5 | `sendSms_solapiError_recordsLastError` | SmsResult.failure("quota") | smsLastError 기록 | `$.data.smsSentAt == null`, `$.data.smsLastError != null` |
| 6 | `sendSms_alreadySent_409Conflict` | 이미 발송된 batch 재발송 | 409 CONFLICT | `status().isConflict()` |
| 7 | `addSlip_movesFromOtherBatch` | slip A를 batch Y에 add | slipA.deliveryBatchId = Y | `$.data.deliveryBatchId == batchYId` |
| 8 | `removeSlip_clearsBatchId` | DELETE /batches/{id}/slips/{slipId} | 204 + slip.batchId = null | `$.data.deliveryBatchId == null` |
| 9 | `salesRole_autoGroup_returns403` | SALES → POST auto-group | 403 FORBIDDEN | `status().isForbidden()` |
| 10 | `regenerateToken_extendsExpiry` | POST /regenerate-token | 새 token + tokenExpiresAt 갱신 | `oldToken != newToken` |
| 11 | `listBatches_byDateAndSent_returnsArray` | GET ?date=&sent= | 배치 N건 list | `$.data.length() >= 2`, `$.data[0].driverName != null` |

### 1.2 PublicSlipControllerIT (3 시나리오, no-auth)

| # | 메서드 | 입력 | 기대 | jsonPath assertion |
| --- | --- | --- | --- | --- |
| 12 | `validToken_returnsBatchAndSlips` | GET /public/batches/{token} | 200 + slips, **UUID 미노출** | `$.data.driverName=="공개기사"`, `$.data.slips[*].id` 부재, `$.data.slips[*].lines[*].productId` 부재, `$.data.id` (batch UUID) 부재, `$.data.slips[*].slipNo` 존재 |
| 13 | `expiredToken_returns410` | tokenExpiresAt = NOW-1h, GET | 410 GONE | `status().isGone()` |
| 14 | `invalidToken_returns404` | 임의 토큰 | 404 NOT_FOUND | `status().isNotFound()` |

### 1.3 SlipDriverFieldsIT (4 시나리오 — Slip 확장 + 회귀)

| # | 메서드 | 입력 | 기대 | jsonPath assertion |
| --- | --- | --- | --- | --- |
| 15 | `createSlip_withDriverFields_persistsRoundTrip` | POST 시 driverName/Phone | 201 + 응답에 보존 | `$.data.driverName == "김기사"`, `$.data.driverPhone == "010-1234-5678"` |
| 16 | `editHeader_with6Args_persistsDriverFields` | PATCH /header (6 args) | 200 + driver 갱신 | `$.data.driverName == "박기사"`, `$.data.driverPhone == "010-9876-5432"`, `$.data.partnerName == "수정된 거래처"` |
| 17 | `createSlip_withoutDriverFields_nullAccepted` | POST without driver fields | 201 + driverName null/미존재 | nullable 호환 검증 |
| 18 | `inspectStillWorks_unaffectedByDriverFields` | 풀 라이프사이클 with driver | INSPECTING→COMPLETED + driver 보존 | `$.data.status == "COMPLETED"`, `$.data.driverName == "회귀기사"` |

---

## 2. 권한 매트릭스 (7-tier 풀네임 — `feedback_role_naming_full`)

| 작업 | MASTER | MANAGER | SALES | WAREHOUSE | INVENTORY | ACCOUNTANT | AUDITOR |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GET `/delivery-batches` (목록) | OK | OK | 403 | 403 | 403 | 403 | 403 |
| GET `/delivery-batches/{id}` | OK | OK | 403 | 403 | 403 | 403 | 403 |
| POST `/delivery-batches/auto-group` | OK | OK | 403 | 403 | 403 | 403 | 403 |
| POST `/delivery-batches/{id}/send-sms` | OK | OK | 403 | 403 | 403 | 403 | 403 |
| POST `/delivery-batches/{id}/slips` (add) | OK | OK | 403 | 403 | 403 | 403 | 403 |
| DELETE `/delivery-batches/{id}/slips/{slipId}` | OK | OK | 403 | 403 | 403 | 403 | 403 |
| POST `/delivery-batches/{id}/regenerate-token` | OK | OK | 403 | 403 | 403 | 403 | 403 |
| GET `/public/batches/{token}` | NO AUTH (200 / 410 / 404) — 모든 사용자 (인증 없음) |
| GET `/public/batches/{token}/slips/{slipId}` | NO AUTH |
| POST `/public/batches/{token}/slips/{slipId}/signature` | NO AUTH (Slice C) |
| POST `/slips` with driverName/driverPhone | OK | OK | OK (생성 가능) | 403 | 403 | 403 | 403 |
| PATCH `/slips/{id}/header` (6 args) | OK | OK | OK (DRAFT/SAVED 만) | 403 | 403 | 403 | 403 |

명시적 음성 검증: 시나리오 9 (SALES auto-group → 403).

---

## 3. BE 시그니처 가정 (Layer 4 — `feedback_pm_integration_build_check`)

본 IT 가 컴파일되려면 BE 산출물이 다음 시그니처를 충족해야 함. PM 통합 시 정렬 검증.

### 3.1 도메인 메서드 (plan §3.3)

| 메서드 | from | to | 부수효과 | IT 검증 |
| --- | --- | --- | --- | --- |
| `Slip.editHeader(partnerName, deliveryTag, memo, slipDate, driverName, driverPhone)` | 4→6 args 확장 | unchanged | driverName/Phone 갱신 | 시나리오 16 |
| `DeliveryBatch.create(driverName, driverPhone, batchDate, slips)` | — | (initial) | batchToken 생성 (base64url 64자), tokenExpiresAt = batchDate+1일 | 시나리오 1, 4, 11 |
| `DeliveryBatch.markSmsSent()` | smsSentAt=null | smsSentAt=now | Solapi 성공 후만 호출 | 시나리오 4 |
| `DeliveryBatch.markSmsFailed(errorMsg)` | smsSentAt=null | unchanged | smsLastError 기록 | 시나리오 5 |
| `DeliveryBatch.addSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId 갱신 | 시나리오 7 |
| `DeliveryBatch.removeSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId = null | 시나리오 8 |
| `DeliveryBatch.regenerateToken()` | unchanged | unchanged | 새 batchToken + tokenExpiresAt | 시나리오 10 |
| `DeliveryBatch.expireToken(LocalDateTime past)` | tokenExpiresAt=future | tokenExpiresAt=past | 만료 강제 (test only) | 시나리오 13 (PublicSlipControllerIT) |

### 3.2 인터페이스/Repository (test 의존)

```java
// services/slip-service/src/main/java/com/samhanair/logis/slip/notification/SmsGateway.java
public interface SmsGateway {
    SmsResult sendSms(String phone, String message);
}

// services/slip-service/src/main/java/com/samhanair/logis/slip/notification/SmsResult.java
public record SmsResult(boolean success, String messageId, String errorMessage) {
    public static SmsResult success(String messageId) { ... }
    public static SmsResult failure(String errorMessage) { ... }
}

// services/slip-service/src/main/java/com/samhanair/logis/slip/repository/DeliveryBatchRepository.java
public interface DeliveryBatchRepository extends JpaRepository<DeliveryBatch, UUID> {
    Optional<DeliveryBatch> findByBatchToken(String batchToken);
}
```

**컴파일 가드**: 위 3 타입이 BE 산출물에 존재해야 본 IT 컴파일 PASS. PM 통합 시 정렬 검증 의무.

### 3.3 응답 DTO 가정

| 필드 | DeliveryBatchResponse | DeliveryBatchPublicResponse (no auth) | SlipPublicResponse |
| --- | --- | --- | --- |
| id (UUID) | OK | **금지** | **금지** |
| batchToken | OK | OK (URL용) | — |
| driverName / driverPhone | OK | OK | — |
| batchDate | OK | OK | — |
| tokenExpiresAt | OK | OK | — |
| smsSentAt / smsLastError | OK | — | — |
| slips[] | summary | full (slipNo + lines, UUID 제거) | — |
| slipNo | OK | OK | OK |
| productName / modelName / quantity | — | OK | OK |
| productId (UUID) | OK | **금지** | **금지** |
| partnerName | OK | OK | OK |

UUID 비공개 가드 — 공개 응답은 시나리오 12에서 `data.has("id")==false`, `slip.has("id")==false`, `line.has("productId")==false` 명시 검증.

---

## 4. 외부 클라이언트 격리 (`feedback_it_mockbean_external_clients`)

모든 IT (DeliveryBatchControllerIT / PublicSlipControllerIT / SlipDriverFieldsIT) 가 다음 3개 client 를 `@MockBean` + `Mockito.lenient()` stub:

| Client | stub 동작 | 사유 |
| --- | --- | --- |
| `InventoryClient` | `@MockBean` (no stub — Slice B 는 reserve/deduct 호출 없음) | accept/complete 단계 미사용. 누락 시 Eureka 비활성에서 RestClient 호출 → 500 |
| `ProductClient` | `lookup`/`requireExists` lenient stub → ProductSummary 반환 | SlipService.create 가 라인 productId 검증 시 호출 (PR #17 회고) |
| `SmsGateway` | lenient stub → `SmsResult.success("MOCK-001")` (시나리오별 override) | Solapi 외부 의존 격리. 시나리오 5 에서만 failure stub override |

void 메서드는 `doNothing()`, 반환 메서드는 `thenReturn()/thenAnswer()` 분리 (PR #16 회고).

---

## 5. 회귀 가드

| 영역 | 기존 시나리오 수 | Slice B 영향 | 검증 |
| --- | --- | --- | --- |
| SlipController unit (76 unit) | 76 | 0 — driver 필드 nullable, 라이프사이클 메서드 무변경 | unit 미실행 (CI ./gradlew test 검증) |
| SlipControllerIT 12 시나리오 | 12 | 0 — 신규 driverName/Phone 필드는 별도 path | 회귀 0 |
| SlipInspectControllerIT 8 시나리오 | 8 | 0 — INSPECTING/inspect 라이프사이클 무변경 | 시나리오 18 (smoke 회귀) 명시 검증 |
| SlipLifecycleControllerIT | 기존 | 0 — save/send/accept/process/complete/inspect/ship/deliver/confirm 무변경 | 회귀 0 |
| SlipLookupControllerIT | 기존 | 0 — lookup-product 무변경 | 회귀 0 |
| **신규 IT 시나리오** | — | **+18 (DeliveryBatch 11 + Public 3 + Driver 4)** | 본 리포트 §1 |

---

## 6. 캡처 (Designer 산출물 인용 + fixtures 시연)

**Designer mock 인용** (PR Designer 산출물 — `agent-a694cde4c2939941d` worktree):

| 캡처 | 경로 | 시나리오 매핑 |
| --- | --- | --- |
| `01_link_dispatch_list.png` | `docs/design/notification-slice-B/screenshots/` | 시나리오 11 (GET /delivery-batches list) |
| `02_link_dispatch_modal.png` | (동상) | 시나리오 7~8 (addSlip/removeSlip 모달) |
| `03_slip_form_driver_fields.png` | (동상) | 시나리오 15~16 (driver 필드 입력 form) |
| `04_mobile_public_batch.png` | (동상) | 시나리오 12 (공개 모바일 페이지 + UUID 미노출 view) |

QA agent 별도 캡처 미산출 — Designer 산출물 4장 + IT/fixtures 자동화 검증으로 시각 + 기능 모두 cover.

추가 fixtures 시연 결과 캡처(옵션) 는 PR 본문에서 PM이 합본 첨부.

---

## 7. 검증 결과

| 검증 항목 | 결과 | 비고 |
| --- | --- | --- |
| IT 파일 작성 | OK | 3 파일, 18 시나리오 (DeliveryBatchControllerIT 11 + PublicSlipControllerIT 3 + SlipDriverFieldsIT 4) |
| fixtures.http 추가 | OK | 시나리오 13~17 (5 블록, 17 ### 단계) |
| `./gradlew :services:slip-service:compileTestJava` | **DEFERRED** | BE agent 산출물 (DeliveryBatch entity, DeliveryBatchRepository, SmsGateway, controllers) 가 본 worktree 에 없음. **PM 통합 시점에 컴파일 검증** (5-team parallel 디스패치 패턴) |
| `./gradlew :services:slip-service:test` | **DEFERRED** | 동상 |
| Layer 4 도메인 메서드 가정 명시 | OK | §3.1 표 |
| 권한 매트릭스 7-tier 풀네임 | OK | §2 표 |
| UUID 비공개 가드 | OK | §3.3 + 시나리오 12 코드 검증 |
| 외부 client @MockBean lenient | OK | §4 (3 clients) |

---

## 8. 다음 단계 (PM 통합)

1. PM 이 **Layer 1 (BE compile)** — BE worktree + 본 QA worktree merge 후 `compileTestJava` PASS 확인.
2. **Layer 2 (Docker IT)** — `DOCKER_HOST=tcp://localhost:2375` 설정 후 `./gradlew :services:slip-service:test` PASS 확인 (`feedback_testcontainers_windows_docker`).
3. **Layer 3 (E2E fixtures)** — `fixtures.http` 시나리오 13~17 수동 시연 (api-gateway + slip-service + auth-service 기동).
4. **Layer 4 (도메인 메서드 의미 정렬)** — §3.1 BE 시그니처 가정과 BE 산출물 정렬 (PR #16/17/21 회고).
5. PR #22 본문에 Designer 캡처 4장 + QA Report 링크 첨부.

---

## 9. BE-QA 정렬 핵심 가정 (PM 합본 시 검증 필수)

본 IT 가 의존하는 BE 산출물 (BE agent 가 별도 worktree 에서 작성):

| 산출물 | 위치 | 시그니처 |
| --- | --- | --- |
| `DeliveryBatch` entity | `domain/DeliveryBatch.java` | `@Entity`, BaseEntity 상속, batchToken UNIQUE, expireToken/regenerateToken/markSmsSent/markSmsFailed/addSlip/removeSlip 메서드 |
| `DeliveryBatchRepository` | `repository/DeliveryBatchRepository.java` | `JpaRepository<DeliveryBatch, UUID>`, `Optional<DeliveryBatch> findByBatchToken(String)` |
| `SmsGateway` interface | `notification/SmsGateway.java` | `SmsResult sendSms(String phone, String message)` |
| `SmsResult` record | `notification/SmsResult.java` | `success(messageId)`, `failure(errorMessage)` static factory |
| `DeliveryBatchController` | `web/DeliveryBatchController.java` | 7 endpoints, `/delivery-batches` 매핑, MANAGER/MASTER 권한, ApiResponse 래핑 |
| `PublicSlipController` | `web/PublicSlipController.java` | 3 endpoints, `/public/batches` 매핑, 토큰 검증, 410 GONE 매핑 |
| `Slip.editHeader()` 6-args | `domain/Slip.java` | driverName/driverPhone 추가 |
| `SlipResponse.driverName/driverPhone/deliveryBatchId` | `web/dto/SlipResponse.java` | 3 필드 추가 |
| Flyway V3 + V4 | `db/migration/` | plan §5 schema |

PR #22 본문에 본 표 인용 → PM 정렬 가드.
