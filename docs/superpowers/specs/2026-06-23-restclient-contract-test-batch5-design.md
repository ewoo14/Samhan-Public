# RestClient 실-HTTP 계약테스트 배치5 — 설계서

> 작성 2026-06-23 (Opus 기획). 연관 이슈: **#531** [CHORE] RestClient 실-HTTP 계약테스트 커버리지 갭 — false-green 방지.
> 표준 워크플로우([[feedback_canonical_workflow]]) 준수: Opus 기획+PR → Codex 개발 → 듀얼 5-agent 리뷰+QA → 0수렴 → PM 머지.

## 1. 배경 / 문제

서비스 간 internal RestClient 의 다운스트림 endpoint 경로·헤더·응답 필드명 변경이 `@MockBean` 우회로 CI green 위장(false-green) — 슬6 categoryKey·MIG-8 cross-DB 계열 회귀. 표준 처방은 `MockRestServiceServer` 로 **경로/헤더/바디 요청 + 응답 필드 파싱 + 200/4xx/5xx 분기**를 실 HTTP 직렬화 경로로 검증하는 client 단위 계약테스트.

배치1~4(#532~537)+#541 로 28개 client 가 이미 계약테스트 보유. 본 배치5는 **잔여 TARGET·NO_TEST 중 H 위험 우선** 3종을 보강한다.

## 2. 스코프 (배치5 = 3 client)

표준 패턴(`MockRestServiceServer.bindTo(builder)`) 호환 + H 위험 우선:

| # | 서비스 | client | 위험 | 핵심 계약 |
|---|---|---|---|---|
| 1 | partner-order | `SlipServiceClient` | **H** | 재무 confirm 흐름 핵심. Idempotency-Key + 200/409 멱등 분기 |
| 2 | partner-order | `mig8/AccountingMig8OrderClient` | **H** | MIG-8 export. BigDecimal 통화 정확성 + 페이징 + 401/403 |
| 3 | arologis | `PartnerClient` | **M** | find-by-codes bulk lookup. UUID 비노출 + fail-soft |

### 2.1 이월 (배치6 — silent cap 금지, 명시 기록)

- **arologis `SlipDispatchTaskClient`** (H, retry/backoff): 생성자가 `builder.requestFactory(SimpleClientHttpRequestFactory)` 로 요청팩토리를 **직접 교체** + retry 가 실 `Thread.sleep(1/2/4s)` → `MockRestServiceServer.bindTo(builder)` 가로채기 무력화·표준 패턴 비호환. 실-HTTP 스텁(JDK `com.sun.net.httpserver.HttpServer` 루프백 또는 테스트 전용 requestFactory 시임) 접근 필요 → 배치6 별도 설계. 기존 `SlipDispatchTaskClientTest` 는 skeleton-mode + missing-token 분기만 검증(실 계약테스트 아님).
- **M/L 잔여**: slip `AuthAccountLookupClient`(×3 동일 계약)·`NotificationChatRoomClient`·`PartnerBlockClient`·`PartnerInternalClient`·`UserInternalClient`, partner-order `PartnerAuthClient`·`mig8/PartnerMig8LookupClient`, dashboard `PartnerClient`/`PartnerOrderClient`/`InventoryClient`(L·skeleton-mode), `NotificationClient`(L·fire-and-forget) → 배치6.
- **이슈 #531 close 는 배치6 완료 후** (H/M TARGET 전부 보강 시). 본 배치5 머지로는 close 하지 않고 진행 코멘트만 게시.

### 2.2 EXCLUDED (대상 아님, 재확인)

vendor/external(GoogleSheets·Aligo·Insung·Sms·vendor Lookup), Noop\*, in-process(DirectDynamicPermissionClient), Phase11 placeholder(Kftc·ETax·ReceiptOcr·EcountRemoteImport — DRY_RUN 스텁), Fixture(FixtureEstimateClient).

## 3. 표준 테스트 패턴 (참조: `accounting/ProductAliasClientTest`, `partner-order/InventoryClientTest`)

```java
class XxxClientTest {
    private MockRestServiceServer server;
    private XxxClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        InternalAuthProperties props = new InternalAuthProperties();
        props.setToken("test-token");
        client = new XxxClient(builder, props /*, objectMapper 등*/);
    }
    // 200 성공 / 4xx·5xx 분기 / 헤더·경로·바디 expect / 응답필드 단언 / server.verify()
}
```

- `MockRestServiceServer.bindTo(RestClient.Builder)` 후 **동일 builder 를 client 생성자에 주입** (client 가 `builder.baseUrl(...).build()` 로 mock 요청팩토리 승계).
- baseUrl 은 client 내부 상수(`http://slip-service` 등) → `requestTo` 는 풀 URL 로 기대.
- ObjectMapper 필요 client(AccountingMig8OrderClient)는 `new ObjectMapper()` 주입.

## 4. 테스트 케이스 (client 별)

### 4.1 partner-order `SlipServiceClient`
생성자 `(RestClient.Builder, InternalAuthProperties)`. baseUrl=`http://slip-service`.
- **publishFromPartnerOrder** (`POST /api/v1/slips/from-partner-order`):
  - 200 → `PublishResult.published(slipNo)` (응답 `{"data":{"slipNo":"..."}}` 파싱). 헤더 `X-Internal-Token=test-token`, `X-User-Id=00000000-0000-0000-0000-000000000000`, `Idempotency-Key=<키>` expect. 바디에 payload 포함 expect.
  - 409 → `PublishResult.duplicate(slipNo)` (body parse 도달 — onStatus no-op 경로 검증).
  - 5xx → `BusinessException(INTERNAL_ERROR)`.
  - 4xx(≠409, 예: 400) → `BusinessException(INVALID_INPUT)`.
  - 200 인데 slipNo 누락 → `BusinessException(INTERNAL_ERROR)`.
  - 입력검증: 빈 payload / blank idempotencyKey → `BusinessException(INVALID_INPUT)` (HTTP 미발생 — `server.verify()` 호출 0건).
- **publishFromOrdersMerge** (`POST /api/v1/slips/from-orders-merge`): 200 published + 409 duplicate 최소 2케이스(경로만 다르고 분기 동일).

### 4.2 partner-order `mig8/AccountingMig8OrderClient`
생성자 `(RestClient.Builder, InternalAuthProperties, ObjectMapper)`. baseUrl=`http://accounting-service`.
- **fetchMig8Orders(page,size)** (`GET /internal/accounting/mig8-orders?page&size`):
  - 200 → `Mig8OrderPage` 파싱. 쿼리파라미터 `page`/`size` expect, `X-Internal-Token` expect. 응답 `{"data":{"content":[{...,"totalSupplyAmount":"1234567.89","totalVatAmount":"...","lines":[{...,"unitPrice":"...","supplyAmount":"..."}]}],"last":true}}`.
    - **BigDecimal 정확성**: `assertThat(order.totalSupplyAmount()).isEqualByComparingTo(new BigDecimal("1234567.89"))` (문자열 직렬화 정확 일치 — float 오차 금지).
    - UUID(`partnerId`/`productId`)·LocalDate(`validUntil`/`itemDueDate`) 파싱, `content`/`last` 매핑, lines 중첩 파싱.
  - 401 → `BusinessException(UNAUTHORIZED)`.
  - 403 → `BusinessException(FORBIDDEN)`.
  - 기타 4xx/5xx → `BusinessException(INTERNAL_ERROR)`.
  - 빈 바디 / content 배열 아님 / malformed JSON → `BusinessException(INTERNAL_ERROR)`.
  - `page<0`/`size<1` 정규화(`Math.max`) 쿼리 expect 1케이스.

### 4.3 arologis `PartnerClient`
생성자 `(RestClient.Builder, ObjectMapper, baseUrl, internalToken, skeletonMode)`. **skeletonMode=false** 로 생성해야 실 호출 경로 검증. baseUrl=테스트 임의(`http://partner-service`) → mock 은 해당 풀 URL 기대.
- **findByCodes** (`POST /internal/partners/find-by-codes`):
  - 200 → `List<PartnerSummary>` (응답 `{"data":[{"partnerCode":"P1","name":"가","partnerId":"<uuid>"}]}`). 헤더 `X-Internal-Token`, 바디=codes 리스트 expect. **UUID 비노출 단언**: `PartnerSummary` 에 partnerId 없음(record 필드=partnerCode,name 만).
  - 4xx/5xx → **빈 리스트**(fail-soft, 예외 전파 안 함).
  - 빈/null 입력 → 빈 리스트(HTTP 0건).
  - skeleton-mode=true 로 별도 생성 시 → 빈 리스트(HTTP 0건) 1케이스.
- **findByCode** (단건): 200 1건 → `Optional` present, 빈 응답 → `Optional.empty()`.

## 5. 비목표 (YAGNI)

- 프로덕션 코드 변경 0 (테스트 전용 추가). SlipDispatchTaskClient 의 testability 리팩터는 배치6 범위.
- 신규 의존성 0 (MockRestServiceServer·AssertJ 기존). 외부 벤더/Phase11 placeholder 테스트 안 함.
- 통합(SpringBootTest) IT 아님 — 순수 client 단위(빠름, 컨텍스트 미로드).

## 6. 검증 / QA

- **단위 실행**: `./gradlew :services:partner-order-service:test --tests "*SlipServiceClientTest" --tests "*AccountingMig8OrderClientTest"` + `:services:arologis-service:test --tests "*PartnerClientTest"` BUILD SUCCESSFUL.
- **라이브 QA 한계 정직 보고**: 본 PR 은 UI 0·계약테스트 전용 → "실사용자 화면 스크린샷" 대상 없음. QA 증빙 = **변경 모듈 전체 test 완주 출력**([[feedback_changed_module_full_test_before_push]]) + CI Linux green fetch. 화면 캡처 N/A 사유 명시(가짜 캡처 금지 [[feedback_no_fake_data_ever]]).
- **회귀 가드**: 기존 client 테스트 무파손(변경 모듈 전체 test). 기존 `SlipDispatchTaskClientTest`(skeleton/token 분기) 유지.

## 7. 산출물

- `services/partner-order-service/src/test/java/.../client/SlipServiceClientTest.java` (신규)
- `services/partner-order-service/src/test/java/.../mig8/client/AccountingMig8OrderClientTest.java` (신규)
- `services/arologis-service/src/test/java/.../client/PartnerClientTest.java` (신규)
- `docs/dev-reports/2026-06-23-restclient-contract-test-batch5.md` (함수 단위 문서화 [[feedback_function_documentation]])
- 이슈 #531 진행 코멘트(배치5 커버 3 + 배치6 잔여 명시), 핸드오프 갱신.
