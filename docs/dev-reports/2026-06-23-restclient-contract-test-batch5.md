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
- 200 `data.slipNo` 파싱 후 `PublishResult.published`
- 409 duplicate body 파싱 후 `PublishResult.duplicate`
- 5xx `BusinessException(INTERNAL_ERROR)`
- 409 제외 4xx `BusinessException(INVALID_INPUT)`
- 200 성공 응답의 `slipNo` 누락 시 `INTERNAL_ERROR`
- 빈 payload, blank idempotencyKey 입력검증 시 HTTP 0건

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
- 금액/수량은 `isEqualByComparingTo(new BigDecimal("..."))`로 문자열 기반 정밀도 검증
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

실사용자 화면 변경이 없는 client 단위 계약테스트 작업이므로 UI 스크린샷은 대상이 아니다. QA 증빙은 변경 모듈 테스트 명령 실행 결과로 대체한다.

실행 대상:

```powershell
./gradlew :services:partner-order-service:test --tests "com.samhanair.logis.partnerorder.client.SlipServiceClientTest" --tests "com.samhanair.logis.partnerorder.mig8.client.AccountingMig8OrderClientTest"
./gradlew :services:arologis-service:test --tests "com.samhanair.logis.arologis.client.PartnerClientTest"
```
