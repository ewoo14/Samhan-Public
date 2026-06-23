# RestClient 실-HTTP 계약테스트 배치5

> 작성일: 2026-06-23  
> 연관 이슈: #531  
> 권위 스펙: `docs/superpowers/specs/2026-06-23-restclient-contract-test-batch5-design.md`

## 범위

배치5는 H/M 위험 RestClient 3종에 대해 `MockRestServiceServer.bindTo(builder)` 기반 실 HTTP 직렬화 경로를 검증한다. 프로덕션 코드와 기존 테스트는 수정하지 않고 신규 테스트 파일만 추가했다.

## SlipServiceClient

- 대상: `services/partner-order-service/src/main/java/com/samhanair/logis/partnerorder/client/SlipServiceClient.java`
- 생성자 계약: `RestClient.Builder`, `InternalAuthProperties`
- baseUrl: `http://slip-service`
- 검증 endpoint:
  - `POST /api/v1/slips/from-partner-order`
  - `POST /api/v1/slips/from-orders-merge`

검증 내용:

- `X-Internal-Token`, `X-User-Id`, `Idempotency-Key` 헤더 계약
- partner-order payload JSON 직렬화
- **201 Created(신규) / 200 OK(멱등 replay)** `data.slipNo` 파싱 후 `PublishResult.published`
- **409 Conflict(동일 키 다른 본문/race, 실 응답 `data=null`) → `BusinessException(CONFLICT)`**
- **401 → `UNAUTHORIZED` / 403 → `FORBIDDEN`** (의미 구분)
- 그 외 4xx → `BusinessException(INVALID_INPUT)`, 5xx → `BusinessException(INTERNAL_ERROR)`
- 성공(200/201) 응답의 `slipNo` 누락 시 `INTERNAL_ERROR`
- 빈 payload, blank idempotencyKey 입력검증 시 HTTP 0건

### 🔴 듀얼리뷰 BLOCKING fix (Opus BE 라운드)

초안 테스트는 `409 → {"data":{"slipNo":...}} → duplicate(slipNo)` 를 박제했으나 **발생 불가한 허구 계약(false-green)**이었다. 실 다운스트림(`SlipPublishController` Javadoc L44-46):

- 신규 발행 = **201 Created** + `data.slipNo`
- 멱등 재시도(같은 키 + 같은 본문) = **200 OK** + 기존 `data.slipNo`
- 동일 키 + **다른 본문**/동시 race = **409 Conflict** → `GlobalExceptionHandler` → `ApiResponse.fail` → **`data=null`** (slipNo 는 message 텍스트만)

클라 `extractSlipNo` 는 `data=null` 에서 null 반환 → `INTERNAL_ERROR` 로 떨어지므로 `PublishResult.duplicate` 분기는 **도달 불가 dead code** 였다. **Opus 라운드 fix(직접 Edit)**:

1. `SlipServiceClient`(prod): 409 → `BusinessException(CONFLICT)` throw, dead `duplicate` 분기 제거, 401→UNAUTHORIZED·403→FORBIDDEN 매핑 추가, 클래스/메서드 Javadoc 정정. `PublishResult.duplicate` 팩토리는 호환 위해 잔존(현재 미사용).
2. 테스트: 허구 409 케이스 → 실 계약(201 신규/200 멱등 replay/409 CONFLICT/401/403) 반영(9→13 케이스).
3. 호출자 영향 0: `PartnerOrderConvertService`/`MergeConvertService`/`SlipPublishOutboxScheduler` 모두 `result.duplicate()` 미사용 + `BusinessException` 동일 처리(보상/outbox retry). 모듈 전체 회귀 그린.
4. **잔여 관찰(본 PR 범위 외)**: 실 409(genuine conflict)는 호출자 outbox 가 `BusinessException` 을 일괄 retry → maxRetryHours 까지 재시도 후 영구실패. genuine conflict 의 비-재시도 분기는 후속 검토 대상.

## AccountingMig8OrderClient

- 대상: `services/partner-order-service/src/main/java/com/samhanair/logis/partnerorder/mig8/client/AccountingMig8OrderClient.java`
- 생성자 계약: `RestClient.Builder`, `InternalAuthProperties`, `ObjectMapper`
- baseUrl: `http://accounting-service`
- 검증 endpoint: `GET /internal/accounting/mig8-orders?page&size`

검증 내용:

- `X-Internal-Token` 헤더와 page/size 쿼리 계약
- `Mig8OrderPage.content`, `last` 파싱
- `Mig8OrderExport`의 UUID, LocalDate, BigDecimal, 문자열 필드 파싱
- `Mig8OrderLineExport`의 중첩 lines 파싱
- 금액/수량은 `isEqualByComparingTo(new BigDecimal("..."))`로 정밀도 검증. 픽스처는 문자열 형태(클라 `decimal()`=`asText()→new BigDecimal` 정밀 라운드트립). 실 wire 는 Jackson 기본 numeric 직렬화이나 클라 parse 가 numeric/string 양립이라 계약상 무해 — numeric 픽스처는 `readTree` DoubleNode→asText 로 고정밀에서 flaky 위험이라 문자열 채택(코드 주석 명시)
- 401 `BusinessException(UNAUTHORIZED)`
- 403 `BusinessException(FORBIDDEN)`
- 500, 빈 body, content 비배열, malformed JSON `BusinessException(INTERNAL_ERROR)`
- `page < 0`, `size < 1` 입력의 `Math.max` 정규화 쿼리 검증

## PartnerClient

- 대상: `services/arologis-service/src/main/java/com/samhanair/logis/arologis/client/PartnerClient.java`
- 생성자 계약: `RestClient.Builder`, `ObjectMapper`, `baseUrl`, `internalToken`, `skeletonMode`
- 테스트 설정: `baseUrl=http://partner-service`, `internalToken=test-token`, `skeletonMode=false`
- 검증 endpoint: `POST /internal/partners/find-by-codes`

검증 내용:

- `X-Internal-Token` 헤더와 codes 리스트 JSON 바디 계약
- 200 `data[]` 응답에서 `partnerCode`, `name`만 파싱
- `PartnerSummary` record component가 `partnerCode`, `name`뿐임을 검증해 UUID 비노출 계약 고정
- 4xx/5xx fail-soft 빈 리스트
- null/empty 입력 HTTP 0건
- skeleton-mode=true HTTP 0건
- `findByCode` 단건 present/empty Optional 분기

## QA

실사용자 화면 변경이 없는 client 단위 계약테스트 작업이므로 UI 스크린샷은 대상이 아니다(가짜 캡처 금지 원칙 준수, N/A 정직 보고). QA 증빙은 변경 모듈 **전체** 테스트 실 실행 결과로 대체한다(코드리딩 PASS 아님).

실행 결과(Opus 라운드 fix 후):

```
./gradlew :services:partner-order-service:test  → BUILD SUCCESSFUL (모듈 전체, 회귀 0)
  · SlipServiceClientTest      tests=13 failures=0 errors=0 skipped=0
  · AccountingMig8OrderClientTest tests=8 failures=0 errors=0 skipped=0
./gradlew :services:arologis-service:test --tests "*PartnerClientTest"  → BUILD SUCCESSFUL (tests=7)
```

prod 클라이언트(SlipServiceClient 409→CONFLICT) 변경에도 partner-order 모듈 전체(호출자/IT 포함) 회귀 그린으로 무파손 확인.
