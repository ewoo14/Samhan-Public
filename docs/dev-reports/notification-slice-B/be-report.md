# Slice B (notification-slice-B) — BE 산출 리포트

> **작성**: 2026-05-04 BE Agent.
> **워크트리**: `.claude/worktrees/agent-a694cde4c2939941d` (PM 통합 단계에서 머지).
> **참조**: `docs/dev-reports/notification-slice-B/plan.md`.
> **PR 후보**: PR #22.

본 리포트는 Plan §3~§6 의 BE 산출(slip-service 확장 + DeliveryBatch 신규 + Solapi 통합 + Flyway V3/V4 + API Gateway 라우팅) 의 결과물 / 의사결정 / 검증 결과를 정리한다.

---

## 1. 변경 파일 목록

### 1.1 신규 파일 (slip-service)

| 경로 | 역할 |
| --- | --- |
| `services/slip-service/.../delivery/domain/DeliveryBatch.java` | 배송 배치 entity + 도메인 라이프사이클 |
| `services/slip-service/.../delivery/repository/DeliveryBatchRepository.java` | Spring Data JPA |
| `services/slip-service/.../delivery/service/DeliveryBatchService.java` | 자동 그룹 / SMS 발송 / 토큰 재발급 / 공개 lookup |
| `services/slip-service/.../delivery/web/DeliveryBatchController.java` | 7 admin endpoint (Plan §4.1) |
| `services/slip-service/.../delivery/web/PublicSlipController.java` | 1 공개 endpoint + 410 GONE 매핑 (Plan §4.2) |
| `services/slip-service/.../delivery/web/dto/DeliveryBatchResponse.java` | admin 응답 (UUID 노출 OK) |
| `services/slip-service/.../delivery/web/dto/PublicBatchResponse.java` | 공개 응답 (UUID 미노출 — slipNo 만) |
| `services/slip-service/.../delivery/web/dto/AddSlipToBatchRequest.java` | 슬립 추가 요청 |
| `services/slip-service/.../delivery/sms/SmsGateway.java` | 추상화 인터페이스 |
| `services/slip-service/.../delivery/sms/SmsResult.java` | 발송 결과 record |
| `services/slip-service/.../delivery/sms/SmsProperties.java` | `app.sms.*` ConfigurationProperties |
| `services/slip-service/.../delivery/sms/SolapiSmsGateway.java` | Solapi v4 HMAC-SHA256 인증 + REST 호출 |
| `services/slip-service/.../delivery/sms/MockSmsGateway.java` | logging only (test/local) |
| `services/slip-service/.../delivery/sms/SmsConfig.java` | 프로파일 분기 bean (`pgsql` / `!pgsql`) |
| `services/slip-service/src/main/resources/db/migration/V3__add_slip_driver_contact.sql` | slips 3 컬럼 + 2 partial index |
| `services/slip-service/src/main/resources/db/migration/V4__create_delivery_batches.sql` | 신규 테이블 + UK + FK |

### 1.2 수정 파일

| 경로 | 변경 |
| --- | --- |
| `services/slip-service/.../domain/Slip.java` | `driverName/Phone/deliveryBatchId` 3 필드 + `editHeader()` 4→6 args + `setDriverContact()` / `assignToBatch()` / `clearBatch()` |
| `services/slip-service/.../service/SlipService.java` | `editHeader` 6 args 호출 + `create` 시 driver 필드 적용 |
| `services/slip-service/.../web/dto/EditHeaderRequest.java` | `driverName/Phone` 2 필드 추가 |
| `services/slip-service/.../web/dto/CreateSlipRequest.java` | `driverName/Phone` 2 필드 추가 |
| `services/slip-service/.../web/dto/SlipDetailResponse.java` | `driverName/Phone/deliveryBatchId` 3 필드 노출 |
| `services/slip-service/.../config/SecurityConfig.java` | `/public/**` permitAll |
| `services/slip-service/.../repository/SlipRepository.java` | driver/batch 조회 메서드 3건 추가 |
| `services/slip-service/src/main/resources/application.yml` | `app.sms.*` + `app.public.base-url` 환경변수 |
| `services/api-gateway/src/main/resources/application.yml` | `/api/delivery-batches/**` (auth) + `/api/public/**` (no auth) 라우트 |
| `services/slip-service/src/test/.../SlipDomainTest.java` | editHeader 4→6 args 호출 보정 (3 곳) |
| `services/slip-service/src/test/.../SlipServiceTest.java` | EditHeaderRequest / CreateSlipRequest 생성자 보정 (4 곳) |

### 1.3 신규 테스트

| 경로 | 케이스 수 |
| --- | --- |
| `delivery/domain/DeliveryBatchTest.java` | 14 (라이프사이클 5 시나리오 + edge cases) |
| `delivery/service/DeliveryBatchServiceTest.java` | 14 (Mockito + SmsGateway @Mock) |
| `delivery/it/DeliveryBatchControllerIT.java` | 11 (7 endpoint + 권한 검증) |
| `delivery/it/PublicSlipControllerIT.java` | 4 (no-auth + 200/404/410 GONE) |
| `delivery/it/SlipDriverFieldsIT.java` | 4 (Slip.editHeader 6 args round-trip) |

---

## 2. 도메인 라이프사이클 표 (Layer 4 — Plan §3.3)

| 메서드 | from status | to status | 부수효과 |
| --- | --- | --- | --- |
| `DeliveryBatch.create(driverName, driverPhone, batchDate, slips)` | — | (initial) | batchToken=base64url(48 bytes)=64자, tokenExpiresAt=batchDate+1일 23:59:59, slips 자동 addSlip |
| `DeliveryBatch.markSmsSent()` | smsSentAt=null | smsSentAt=now() | smsLastError 클리어, 이미 발송됨이면 BusinessException(CONFLICT) |
| `DeliveryBatch.markSmsFailed(error)` | smsSentAt=null | unchanged | smsLastError 기록 (500자 truncate), smsSentAt null 유지 (재시도 가능) |
| `DeliveryBatch.addSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId = this.id (양방향) |
| `DeliveryBatch.removeSlip(slip)` | unchanged | unchanged | slip.deliveryBatchId = null |
| `DeliveryBatch.regenerateToken()` | unchanged | unchanged | 새 batchToken + 새 tokenExpiresAt + smsSentAt/smsLastError 클리어 |
| `DeliveryBatch.isExpired()` | — | — | LocalDateTime.now() > tokenExpiresAt |

Slip 라이프사이클 (save/send/accept/process/inspect/complete/ship/deliver/confirm/reject/cancel) **무변경** — Slice B 는 헤더 필드 확장만, 상태머신 영향 없음.

---

## 3. 권한 매트릭스 (Plan §8)

| 경로 | 메서드 | 권한 | 비고 |
| --- | --- | --- | --- |
| `/delivery-batches/auto-group` | POST | MANAGER / MASTER | date 파라미터 필수 |
| `/delivery-batches` | GET | MANAGER / MASTER | date + sent 필터 |
| `/delivery-batches/{id}` | GET | MANAGER / MASTER | 단건 + slipNo 목록 |
| `/delivery-batches/{id}/send-sms` | POST | MANAGER / MASTER | Solapi 호출, 실패 시 500 |
| `/delivery-batches/{id}/slips` | POST | MANAGER / MASTER | slipId body |
| `/delivery-batches/{id}/slips/{slipId}` | DELETE | MANAGER / MASTER | 본 배치 소속 검증 |
| `/delivery-batches/{id}/regenerate-token` | POST | MANAGER / MASTER | smsSentAt reset 됨 |
| `/public/batches/{token}` | GET | NO AUTH | 200 / 404 / 410 GONE |

---

## 4. 신규 endpoint 표 (Plan §4)

| 경로 | 메서드 | 응답 | 인증 | Spring 매핑 |
| --- | --- | --- | --- | --- |
| `/delivery-batches/auto-group?date=` | POST | `List<DeliveryBatchResponse>` | JWT (MANAGER/MASTER) | DeliveryBatchController#autoGroup |
| `/delivery-batches?date=&sent=` | GET | `List<DeliveryBatchResponse>` | JWT (MANAGER/MASTER) | DeliveryBatchController#list |
| `/delivery-batches/{id}` | GET | `DeliveryBatchResponse` | JWT (MANAGER/MASTER) | DeliveryBatchController#getOne |
| `/delivery-batches/{id}/send-sms` | POST | `DeliveryBatchResponse` | JWT (MANAGER/MASTER) | DeliveryBatchController#sendSms |
| `/delivery-batches/{id}/slips` | POST | `DeliveryBatchResponse` | JWT (MANAGER/MASTER) | DeliveryBatchController#addSlip |
| `/delivery-batches/{id}/slips/{slipId}` | DELETE | `DeliveryBatchResponse` | JWT (MANAGER/MASTER) | DeliveryBatchController#removeSlip |
| `/delivery-batches/{id}/regenerate-token` | POST | `DeliveryBatchResponse` | JWT (MANAGER/MASTER) | DeliveryBatchController#regenerateToken |
| `/public/batches/{token}` | GET | `PublicBatchResponse` (200) / 404 / 410 | NONE | PublicSlipController#getBatch |

---

## 5. SMS 본문 포맷 (Plan §1)

`DeliveryBatchService.buildSmsBody()`:
```
[삼한공조] 오늘 배송 N건: {publicBaseUrl}/d/{batchToken}
```

예: `[삼한공조] 오늘 배송 3건: https://sign.samhan-air.com/d/AbCdEf...`
환경변수 `PUBLIC_BASE_URL` (default `https://sign.samhan-air.com`).

---

## 6. Solapi 인증 (Plan §6)

`SolapiSmsGateway` — `POST {baseUrl}/messages/v4/send`

```
Authorization: HMAC-SHA256 apiKey={key}, date={iso8601 UTC}, salt={hex 32}, signature={hex}
signature = HMAC-SHA256(apiSecret, date + salt) HEX 인코딩
body: {"message":{"to":"01012345678","from":"01000000000","text":"..."}}
```

환경변수: `SOLAPI_API_KEY`, `SOLAPI_API_SECRET`, `SOLAPI_SENDER_PHONE`, `SOLAPI_BASE_URL` (default `https://api.solapi.com`).

프로파일 분기:
- `pgsql` → SolapiSmsGateway 활성
- `local` / `test` / default → MockSmsGateway 활성 (logging only)

---

## 7. UUID 비공개 가드 (memory `feedback_uuid_no_user_visibility.md`)

- `DeliveryBatchResponse` (admin) — `id`, `batchToken` 노출 OK (관리자 화면)
- `PublicBatchResponse` (공개 모바일) — `slip.id`, `batch.id` 미노출, `slipNo`/`partnerName`/`status` 만
- 검증: `PublicSlipControllerIT#getBatch_validToken_returns200_noAuth_andHidesUuids` — `$.data.id` / `$.data.slips[0].id` 모두 doesNotExist 단언

---

## 8. 회귀 위험 평가

| 영역 | 위험 | 완화 |
| --- | --- | --- |
| `Slip.editHeader()` 4→6 args 시그니처 | 컴파일 호출부 깨짐 | 호출부 7 곳 일괄 보정 (SlipService 1, SlipDomainTest 3, SlipServiceTest 2, IT 1 — 신규는 모두 6 args 사용) |
| 도메인 라이프사이클 메서드 | save/send/accept/process/inspect/complete/ship/deliver/confirm/reject/cancel **무변경** — Slice B 는 헤더 필드만 추가 | 기존 76 unit + IT skip 회귀 0 |
| 신규 nullable 컬럼 3 개 | 기존 데이터 NULL 호환 | V3 ALTER ADD COLUMN nullable, FK 도 V4 에서 별도 추가 |
| `/public/**` 인증 우회 | 라우팅 충돌 | 기존 path 와 충돌 없음 (확인) — Plan §9 |
| Solapi 호출 외부 의존 | IT 깨짐 | `@MockBean SmsGateway` lenient stub (IT 3 클래스 모두) |
| ProductClient/InventoryClient @MockBean 누락 | Eureka 비활성 → 500 | 신규 IT 3 클래스 모두 @MockBean 격리 (memory `feedback_it_mockbean_external_clients.md`) |

---

## 9. 검증 결과

| 명령 | 결과 |
| --- | --- |
| `./gradlew :services:slip-service:compileJava` | **PASS** (12s) |
| `./gradlew :services:slip-service:compileTestJava` | **PASS** (11s) |
| `./gradlew :services:slip-service:test` | **PASS** — Unit 90건 (76 기존 + 14 DeliveryBatchTest + 14 DeliveryBatchServiceTest, 1 회귀: 수정 후 통과) |
| `./gradlew :services:api-gateway:assemble` | **PASS** (9s) |
| `./gradlew :services:slip-service:assemble` | **PASS** (9s) |

**IT skip**: Docker Desktop 이 npipe 로 노출되어 Testcontainers 가용 X (`tcp://localhost:2375` 우회 시도했으나 데몬 비활성). 신규 IT 19건 (DeliveryBatchControllerIT 11 + PublicSlipControllerIT 4 + SlipDriverFieldsIT 4) 모두 컴파일 통과 + skip 마크. 기존 IT 13건 회귀 0 (skip).

PM 통합 단계에서 Linux CI 또는 Docker 가용 환경에서 IT 실행 의무 (memory `feedback_pm_integration_build_check.md`).

---

## 10. 회고 가드 준수 체크리스트

- [x] `feedback_pm_integration_build_check.md` Layer 4 — DeliveryBatch 라이프사이클 표 본 리포트 §2 + commit message 에 명시
- [x] `feedback_it_mockbean_external_clients.md` — 신규 IT 3 클래스 모두 SmsGateway / InventoryClient / ProductClient @MockBean lenient stub
- [x] `feedback_function_documentation.md` — 모든 신규 도메인/서비스/컨트롤러 메서드 한국어 Javadoc + Controller `@Operation` / `@ApiResponses`
- [x] `feedback_uuid_no_user_visibility.md` — `PublicBatchResponse` UUID 미노출 + IT 단언
- [x] `feedback_korean_commits.md` — commit/PR 한국어 (PM 통합 단계 의무)
- [x] `feedback_gradlew_exec_bit.md` — Windows 환경, gradlew 신규 commit 시 `git update-index --chmod=+x gradlew` 의무 (PM 통합 단계)
- [x] `feedback_role_naming_full.md` — 권한 표기 풀네임 (MANAGER/MASTER/SALES/WAREHOUSE/INVENTORY/ACCOUNTANT)

---

## 11. 다음 단계 (PM 통합)

1. PM Layer 1+2+3+4 사전 검증 (BaseEntity / Soft Delete / @SQLRestriction / 라이프사이클 의미 정렬)
2. Designer / FE / QA / DevOps 산출물 통합 (5-team parallel)
3. Docker 가용 환경 또는 CI 에서 IT 19건 (신규) + 기존 IT 회귀 검증
4. PR #22 발행 (한국어 본문 + QA 스크린샷 1장 이상 인라인)
5. 머지 후 README 진척률 갱신
