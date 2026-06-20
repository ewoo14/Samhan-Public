> **슬라이스 플랜** — 에픽 인덱스: [2026-06-21-employee-signature-stamp-plan.md](2026-06-21-employee-signature-stamp-plan.md) (Global Constraints · 공유 계약 · 실행 방식 · 구현시점 확인항목). 본 파일 = 단일 슬라이스 = 1 PR. Step 은 `- [ ]` 체크박스로 추적.

## Slice C3: enrichment 구축 + 결재란 스탬프 (slip-service BE + desktop FE)

**PR boundary:** 이 슬라이스 = PR 1개. slip-service `getOne` enrichment(이름 3자 + 서명 3자) 신규 구축 + `SlipDetailResponse` additive reshape + desktop 결재란(작성자/출고인/검수인) 서명 스탬프 주입. user-service `POST /internal/users/signatures`(C1a) 가 **머지·배포된 뒤** 착수하며, 미배포 구간은 빈 맵 graceful fallback(빈 공간, 500 금지)로 안전하다. 거래명세서·arologis 제외, 신규 권한/시드/Flyway 0.

> 사전 확인된 ground truth (실코드):
> - `SlipService.getOne()` 은 오늘 owner(`createdBy`) fullName 만 `resolveOwnerFullName → UserInternalClient.resolveFullName → GET /internal/users/{id}` 단건으로 resolve. dispatcher/inspector 는 resolve 안 함(raw UUID 문자열만 노출, `SlipDetailResponse.dispatcherUserId/inspectorUserId`).
> - desktop `SlipApprovalActor{userId, fullName, signedAt}` 는 **FE 선정의·BE 미생산**. `DispatchView` 의 `slip.dispatcher?.fullName` / `slip.inspector?.fullName` 은 현재 항상 undefined → 빈칸.
> - `RoleCell.signaturePng` stub 은 이미 존재(`DispatchView.tsx:63,74`). `OutboundView` 출고인 stamp slot 은 `[인]` 텍스트 마크(`OutboundView.tsx:177-182`).
> - desktop vitest `include = src/**/*.test.ts`(NOT .tsx), `environment: 'node'`. 컴포넌트 렌더 테스트 불가 → 계약은 순수 함수 `roleStamps.ts` 로 추출해 박제.
> - print 경로 2개(DispatchView/OutboundView) 모두 `useQuery({ queryFn: () => getSlip(id) })` = `GET /slips/{id}` 사용 → enrichment-on-GET-only 와 정합(확인 완료).

---

### Task C3.1: slip-service `EmployeeSignatureDto` + `UserInternalClient.resolveSignatures(List<UUID>)` 배치 조회 + 계약테스트

**Files:**
- create `services/slip-service/src/main/java/com/samhanair/logis/slip/client/EmployeeSignatureDto.java`
- modify `services/slip-service/src/main/java/com/samhanair/logis/slip/client/UserInternalClient.java`
- create `services/slip-service/src/test/java/com/samhanair/logis/slip/client/UserInternalClientSignatureTest.java`

**Interfaces:**
- Consumes (C1a): `POST http://user-service/internal/users/signatures` body `{"userIds":[...]}` → `ApiResponse<Map<UUID, EmployeeSignatureDto>>`, `EmployeeSignatureDto{ String signaturePngBase64; String signedAt }`. X-Internal-Token + `@PreAuthorize hasRole('MASTER')`. 미등록 사원은 맵에서 생략.
- Produces: `Map<UUID, EmployeeSignatureDto> UserInternalClient.resolveSignatures(List<UUID> userIds)` — 404/5xx/연결실패/토큰미설정/null·빈 입력 시 빈 맵, 절대 throw 안 함.
- 기존 `resolveFullName(UUID)` 의 graceful-fallback·X-Internal-Token·timeout(2s/3s) 정책을 그대로 미러.

- [ ] **Step 1: `EmployeeSignatureDto` 계약 record 작성 (구현 먼저 — DTO 는 테스트 대상이 아니라 계약 고정용).**
  `services/slip-service/.../client/EmployeeSignatureDto.java`:
  ```java
  package com.samhanair.logis.slip.client;

  import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

  /**
   * user-service {@code POST /internal/users/signatures} 응답 맵의 값 — 사원 서명 1건.
   *
   * <p>C1a 계약(공유): {@code { signaturePngBase64, signedAt }}. 미등록 사원은 응답 맵에서
   * 생략되므로 본 record 의 필드는 모두 비어있지 않은 등록 사원에만 존재한다.
   *
   * @param signaturePngBase64 PNG base64 (dataURL prefix 미포함 — 순수 base64 본문). null 불가(등록자만 응답)
   * @param signedAt 최종 등록 시각 ISO-8601. 결재란에는 표시하지 않음(인감 모델).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmployeeSignatureDto(String signaturePngBase64, String signedAt) {}
  ```

- [ ] **Step 2: 실패 테스트 작성 — 200 성공/404 fallback/5xx fallback/토큰미설정 fallback/빈입력 (REAL).**
  `services/slip-service/src/test/java/.../client/UserInternalClientSignatureTest.java`:
  ```java
  package com.samhanair.logis.slip.client;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.samhanair.logis.security.InternalAuthProperties;
  import java.util.List;
  import java.util.Map;
  import java.util.UUID;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.http.HttpMethod;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.client.MockRestServiceServer;
  import org.springframework.web.client.RestClient;

  /**
   * slip-service → user-service {@code POST /internal/users/signatures} 계약테스트.
   *
   * <p>다운스트림 선검증 가드(memory feedback_restclient_contract_test_false_green):
   * 실 endpoint URL·X-Internal-Token 헤더·ApiResponse data-map 매핑·404/5xx graceful fallback 을
   * MockRestServiceServer 로 박제한다. @MockBean 우회/fabricated stub 금지.
   */
  class UserInternalClientSignatureTest {

      private static final String TOKEN = "test-token-xyz";
      private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-000000000a01");
      private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-000000000a02");

      private MockRestServiceServer server;
      private UserInternalClient client;
      private final ObjectMapper objectMapper = new ObjectMapper();

      @BeforeEach
      void setUp() {
          RestClient.Builder builder = RestClient.builder();
          server = MockRestServiceServer.bindTo(builder).build();
          InternalAuthProperties props = new InternalAuthProperties();
          props.setToken(TOKEN);
          client = new UserInternalClient(builder, props, objectMapper);
      }

      @Test
      void resolveSignatures_success_mapsRegisteredEmployeesOnly() {
          // U1 등록 / U2 미등록 → 응답 맵에 U1 만 존재.
          String body = "{\"data\":{\""
                  + U1 + "\":{\"signaturePngBase64\":\"iVBORw0KGgo=\","
                  + "\"signedAt\":\"2026-06-21T09:30:00\"}}}";
          server.expect(requestTo("http://user-service/internal/users/signatures"))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(header("X-Internal-Token", TOKEN))
                  .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

          Map<UUID, EmployeeSignatureDto> result = client.resolveSignatures(List.of(U1, U2));

          assertThat(result).containsKey(U1);
          assertThat(result).doesNotContainKey(U2);
          assertThat(result.get(U1).signaturePngBase64()).isEqualTo("iVBORw0KGgo=");
          assertThat(result.get(U1).signedAt()).isEqualTo("2026-06-21T09:30:00");
          server.verify();
      }

      @Test
      void resolveSignatures_404_returnsEmptyMap_noThrow() {
          server.expect(requestTo("http://user-service/internal/users/signatures"))
                  .andRespond(withStatus(HttpStatus.NOT_FOUND));
          assertThat(client.resolveSignatures(List.of(U1))).isEmpty();
          server.verify();
      }

      @Test
      void resolveSignatures_5xx_returnsEmptyMap_noThrow() {
          server.expect(requestTo("http://user-service/internal/users/signatures"))
                  .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
          assertThat(client.resolveSignatures(List.of(U1))).isEmpty();
          server.verify();
      }

      @Test
      void resolveSignatures_blankInput_skipsCall_returnsEmptyMap() {
          // 입력이 비면 RPC 자체를 호출하지 않는다(서버 기대 미등록 → verify 시 미호출 정상).
          assertThat(client.resolveSignatures(List.of())).isEmpty();
          assertThat(client.resolveSignatures(null)).isEmpty();
          server.verify();
      }

      @Test
      void resolveSignatures_missingToken_returnsEmptyMap_noCall() {
          InternalAuthProperties empty = new InternalAuthProperties();
          empty.setToken("");
          UserInternalClient bare = new UserInternalClient(RestClient.builder(), empty, objectMapper);
          assertThat(bare.resolveSignatures(List.of(U1))).isEmpty();
      }
  }
  ```

- [ ] **Step 3: 테스트 실행 — 컴파일 실패(메서드 부재) 확인.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.client.UserInternalClientSignatureTest"`
  기대: **FAIL** (`resolveSignatures` 심볼 부재 → compileTestJava 실패).

- [ ] **Step 4: `UserInternalClient.resolveSignatures` 최소 구현 (REAL).**
  `UserInternalClient.java` import 추가:
  ```java
  import java.util.ArrayList;
  import java.util.Iterator;
  import java.util.LinkedHashMap;
  import java.util.List;
  import java.util.Map;
  ```
  클래스 끝(닫는 `}` 직전)에 메서드 추가:
  ```java
      /**
       * userId 목록 → 사원 서명 배치 resolve.
       *
       * <p>user-service {@code POST /internal/users/signatures} 를 1회 호출하여
       * 등록 사원의 {@code {userId: {signaturePngBase64, signedAt}}} 맵을 반환한다.
       * display-names/verify-bulk 배치 패턴을 미러한다.
       *
       * <p>오류 처리(graceful fallback) — resolveFullName 과 동일 정책:
       * <ul>
       *   <li>입력 null/빈 목록 → 빈 맵(RPC 미호출).</li>
       *   <li>internal token 미설정 → 빈 맵 + warn.</li>
       *   <li>404 / 5xx / 연결 실패 → 빈 맵 + warn/debug.</li>
       * </ul>
       * 미등록 사원은 user-service 응답에서 생략되므로 맵에도 부재한다.
       *
       * @param userIds 직원 UUID 목록(owner/dispatcher/inspector 등)
       * @return 등록 사원의 서명 DTO 맵. 실패 시 빈 맵(절대 throw 안 함).
       */
      public Map<UUID, EmployeeSignatureDto> resolveSignatures(List<UUID> userIds) {
          if (userIds == null || userIds.isEmpty()) {
              return Map.of();
          }
          List<UUID> distinct = new ArrayList<>(new LinkedHashSet<>(userIds));
          distinct.removeIf(java.util.Objects::isNull);
          if (distinct.isEmpty()) {
              return Map.of();
          }
          String token = internalAuthProperties.getToken();
          if (token == null || token.isBlank()) {
              log.warn("UserInternalClient.resolveSignatures — internal.token 미설정, skipped (count={})",
                      distinct.size());
              return Map.of();
          }
          try {
              Map<String, Object> requestBody = Map.of("userIds", distinct);
              String body = restClient.post()
                      .uri("/internal/users/signatures")
                      .header(INTERNAL_TOKEN_HEADER, token)
                      .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                      .body(requestBody)
                      .retrieve()
                      .body(String.class);
              if (body == null || body.isBlank()) {
                  return Map.of();
              }
              com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(body);
              com.fasterxml.jackson.databind.JsonNode data = root.has("data") ? root.get("data") : root;
              if (data == null || data.isNull() || !data.isObject()) {
                  return Map.of();
              }
              Map<UUID, EmployeeSignatureDto> result = new LinkedHashMap<>();
              Iterator<Map.Entry<String, com.fasterxml.jackson.databind.JsonNode>> fields = data.fields();
              while (fields.hasNext()) {
                  Map.Entry<String, com.fasterxml.jackson.databind.JsonNode> e = fields.next();
                  UUID key;
                  try {
                      key = UUID.fromString(e.getKey());
                  } catch (IllegalArgumentException ignore) {
                      continue;
                  }
                  com.fasterxml.jackson.databind.JsonNode v = e.getValue();
                  if (v == null || v.isNull()) {
                      continue;
                  }
                  com.fasterxml.jackson.databind.JsonNode png = v.get("signaturePngBase64");
                  if (png == null || png.isNull() || png.asText().isBlank()) {
                      continue;
                  }
                  com.fasterxml.jackson.databind.JsonNode at = v.get("signedAt");
                  result.put(key, new EmployeeSignatureDto(
                          png.asText(),
                          at == null || at.isNull() ? null : at.asText()));
              }
              return result;
          } catch (RestClientResponseException ex) {
              if (ex.getStatusCode().is5xxServerError()) {
                  log.warn("UserInternalClient.resolveSignatures 5xx — count={}, status={}",
                          distinct.size(), ex.getStatusCode());
              } else {
                  log.debug("UserInternalClient.resolveSignatures 4xx — count={}, status={}",
                          distinct.size(), ex.getStatusCode());
              }
              return Map.of();
          } catch (Exception ex) {
              log.warn("UserInternalClient.resolveSignatures 호출 실패 — count={}, msg={}",
                      distinct.size(), ex.getMessage());
              return Map.of();
          }
      }
  ```
  (기존 import 에 `java.util.LinkedHashSet` 가 없으면 추가.)

- [ ] **Step 5: 테스트 재실행 — PASS 확인.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.client.UserInternalClientSignatureTest"`
  기대: **PASS** (5 테스트). (한글경로 JDK 트랩으로 로컬 skip 시 Linux CI 결과로 확증.)

- [ ] **Step 6: 커밋.**
  ```
  git add services/slip-service/src/main/java/com/samhanair/logis/slip/client/EmployeeSignatureDto.java \
          services/slip-service/src/main/java/com/samhanair/logis/slip/client/UserInternalClient.java \
          services/slip-service/src/test/java/com/samhanair/logis/slip/client/UserInternalClientSignatureTest.java
  git commit -F (커밋메시지 파일)
  ```
  커밋 메시지(Write→`git commit -F`):
  ```
  feat(slip): 사원 서명 배치 조회 UserInternalClient.resolveSignatures 추가

  user-service POST /internal/users/signatures 배치 endpoint 소비 메서드 신규.
  EmployeeSignatureDto(signaturePngBase64, signedAt) 계약 record + 404/5xx/토큰미설정
  graceful fallback(빈 맵, throw 금지). MockRestServiceServer 다운스트림 선검증 계약테스트.
  결재란 인감 스탬프 enrichment(C3) 토대.

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B9aoimz6wNow8HA2nodZZr
  ```

---

### Task C3.2: `SlipApprovalActorResponse` nested DTO + `SlipDetailResponse.fromEnriched` additive reshape

**Files:**
- create `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipApprovalActorResponse.java`
- modify `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipDetailResponse.java`

**Interfaces:**
- Produces: `record SlipApprovalActorResponse(String fullName, String signaturePngBase64)`. JSON 키 = `dispatcher`, `inspector`(nested). 추가 top-level: `String ownerSignaturePngBase64`(기존 `ownerFullName` 옆).
- `SlipDetailResponse.fromEnriched(Slip slip, String ownerFullName, String ownerSignaturePngBase64, SlipApprovalActorResponse dispatcher, SlipApprovalActorResponse inspector)` — getOne 전용. 기존 `from(slip)`/`from(slip, ownerFullName)` 은 nested actor·ownerSignature 를 null 로(= mutation 응답 null 보장).
- 기존 `dispatcherUserId/dispatcherSignedAt/inspectorUserId/inspectorSignedAt`(Slice A progress-bar 소비) 필드는 **보존**(additive). `ownerUserId` 는 신규 노출 안 함.

- [ ] **Step 1: `SlipApprovalActorResponse` record 작성 (구현 먼저 — 계약 고정).**
  ```java
  package com.samhanair.logis.slip.web.dto;

  /**
   * 결재란 출고인/검수인 actor — getOne enrichment 전용(signature-slice-C3).
   *
   * <p>desktop {@code SlipApprovalActor{userId, fullName, signedAt, signaturePng}} 와 매핑되되
   * BE 응답에는 userId/signedAt 을 담지 않는다(UUID 비공개 + 인감 모델 등록시각 미표시).
   * FE 는 본 응답의 {@code fullName} 을 셀 이름으로, {@code signaturePngBase64} 를 스탬프 이미지로 사용.
   *
   * @param fullName 사원 성명. user-service lookup 실패 시 null.
   * @param signaturePngBase64 PNG base64 본문(dataURL prefix 미포함). 미등록 시 null.
   */
  public record SlipApprovalActorResponse(String fullName, String signaturePngBase64) {}
  ```

- [ ] **Step 2: 실패 테스트 작성 — record 컴포넌트 존재 + mutation 응답 null 박제 (REAL, 순수 record 단위테스트).**
  create `services/slip-service/src/test/java/com/samhanair/logis/slip/web/dto/SlipDetailResponseEnrichmentTest.java`:
  ```java
  package com.samhanair.logis.slip.web.dto;

  import static org.assertj.core.api.Assertions.assertThat;

  import com.samhanair.logis.slip.domain.Slip;
  import com.samhanair.logis.slip.domain.SlipType;
  import org.junit.jupiter.api.Test;

  /**
   * SlipDetailResponse enrichment reshape 단위테스트(C3).
   *
   * <p>핵심 계약: mutation 변환 from(slip) 은 dispatcher/inspector/ownerSignature 가 null
   * (enrichment-on-GET-only) ; fromEnriched 만 채워진다. nested actor 는 additive(기존
   * dispatcherUserId 등 raw 필드는 보존).
   */
  class SlipDetailResponseEnrichmentTest {

      private Slip newOutbound() {
          return Slip.draft(SlipType.OUTBOUND, java.time.LocalDate.of(2026, 6, 21), 1,
                  null, null, null, null, null);
      }

      @Test
      void from_mutation_leavesEnrichmentNull() {
          SlipDetailResponse r = SlipDetailResponse.from(newOutbound());
          assertThat(r.dispatcher()).isNull();
          assertThat(r.inspector()).isNull();
          assertThat(r.ownerSignaturePngBase64()).isNull();
          assertThat(r.ownerFullName()).isNull();
      }

      @Test
      void fromEnriched_populatesActorsAndOwnerSignature() {
          SlipApprovalActorResponse disp = new SlipApprovalActorResponse("홍출고", "iVBORw0KGgo=");
          SlipApprovalActorResponse insp = new SlipApprovalActorResponse("김검수", "iVBORw0AAAA=");
          SlipDetailResponse r = SlipDetailResponse.fromEnriched(
                  newOutbound(), "이작성", "iVBORw0OWNER=", disp, insp);
          assertThat(r.ownerFullName()).isEqualTo("이작성");
          assertThat(r.ownerSignaturePngBase64()).isEqualTo("iVBORw0OWNER=");
          assertThat(r.dispatcher().fullName()).isEqualTo("홍출고");
          assertThat(r.dispatcher().signaturePngBase64()).isEqualTo("iVBORw0KGgo=");
          assertThat(r.inspector().fullName()).isEqualTo("김검수");
      }
  }
  ```
  > NOTE for implementer: `Slip.draft(...)` 시그니처는 실제 도메인 factory 와 정확 일치시킬 것(`Slip.java` 의 정적 factory 인자 순서 그대로). 인자가 다르면 컴파일 단계에서 즉시 드러난다 — 거기서만 맞추고 단언 로직은 변경 금지. (도메인 직접 set 금지 규칙 준수: factory/도메인 메서드만 사용.)

- [ ] **Step 3: 테스트 실행 — 컴파일 실패(메서드/컴포넌트 부재) 확인.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.web.dto.SlipDetailResponseEnrichmentTest"`
  기대: **FAIL** (`dispatcher()`/`inspector()`/`ownerSignaturePngBase64()`/`fromEnriched` 심볼 부재).

- [ ] **Step 4: `SlipDetailResponse` additive reshape (REAL).**
  4-1. record 헤더에 필드 추가 — 기존 `String ownerFullName,` 바로 뒤, `String destinationWarehouseName,` 앞에 삽입:
  ```java
          String ownerFullName,
          /**
           * 담당자 등록 서명 PNG base64 본문(dataURL prefix 미포함) — signature-slice-C3.
           * getOne enrichment 전용. mutation 응답/미등록/lookup 실패 시 null.
           */
          String ownerSignaturePngBase64,
          /**
           * 출고인 actor(이름+서명) — signature-slice-C3 신규. getOne 전용, 그 외 null.
           * 기존 {@code dispatcherUserId} raw 필드는 progress-bar 호환 위해 보존.
           */
          SlipApprovalActorResponse dispatcher,
          /**
           * 검수인 actor(이름+서명) — signature-slice-C3 신규. getOne 전용, 그 외 null.
           */
          SlipApprovalActorResponse inspector,
  ```
  4-2. 기존 `from(Slip slip)` 와 `from(Slip slip, String ownerFullName)` 의 생성자 인자에 신규 3필드를 **null 로** 전달. `from(slip, ownerFullName)` 본문의 `ownerFullName,` 다음 줄(현 `// SP-08-FU2 P2-2 — 도착지 창고명 snapshot` 바로 위)에 삽입:
  ```java
                  ownerFullName,
                  null, // ownerSignaturePngBase64 — mutation/하위호환 변환은 enrichment 없음
                  null, // dispatcher actor — getOne 전용
                  null, // inspector actor  — getOne 전용
                  // SP-08-FU2 P2-2 — 도착지 창고명 snapshot
                  slip.getDestinationWarehouseName(),
  ```
  4-3. 신규 factory 추가(`from(slip, ownerFullName)` 메서드 닫는 `}` 직후):
  ```java
      /**
       * enrichment 포함 변환 — getOne 전용(signature-slice-C3).
       *
       * <p>owner/dispatcher/inspector 이름 + 서명을 user-service lookup 결과로 채운다.
       * mutation 경로는 본 메서드를 호출하지 않으므로 nested actor 는 항상 null(enrichment-on-GET-only).
       *
       * @param slip 전표 도메인
       * @param ownerFullName 담당자 성명(null 허용)
       * @param ownerSignaturePngBase64 담당자 서명 base64(null 허용)
       * @param dispatcher 출고인 actor(null 허용)
       * @param inspector 검수인 actor(null 허용)
       * @return enrichment 채워진 SlipDetailResponse
       */
      public static SlipDetailResponse fromEnriched(
              Slip slip,
              String ownerFullName,
              String ownerSignaturePngBase64,
              SlipApprovalActorResponse dispatcher,
              SlipApprovalActorResponse inspector) {
          SlipDetailResponse base = from(slip, ownerFullName);
          return new SlipDetailResponse(
                  base.id(), base.slipType(), base.slipNo(), base.slipDate(), base.seqNo(),
                  base.status(), base.partnerId(), base.partnerName(), base.partnerCode(),
                  base.sourceWarehouseId(), base.destinationWarehouseId(), base.deliveryTag(),
                  base.memo(), base.requesterId(), base.acceptedBy(), base.acceptedAt(),
                  base.completedAt(), base.confirmedAt(), base.updatedAt(),
                  base.shippingAddress(), base.inspectionAddress(), base.receiverPhone(),
                  base.customerTel(), base.customerAddress(), base.customerRepresentative(),
                  base.paymentDueLabel(), base.discountInfo(), base.collectTerm(), base.agreeTerm(),
                  base.dispatcherUserId(), base.dispatcherSignedAt(),
                  base.inspectorUserId(), base.inspectorSignedAt(),
                  base.driverName(), base.driverPhone(), base.deliveryBatchId(), base.version(),
                  base.businessNumber(), base.deliveryAddress(), base.supervisionAddress(),
                  base.projectName(), base.recipientPhone(), base.paymentDueDate(), base.printed(),
                  base.inspectionStatus(),
                  ownerFullName,
                  ownerSignaturePngBase64,
                  dispatcher,
                  inspector,
                  base.destinationWarehouseName(),
                  base.lines());
      }
  ```
  > NOTE: 4-3 의 인자 순서는 record 컴포넌트 선언 순서와 **정확히** 일치해야 한다(4-1 삽입 위치 기준). 순서 불일치는 컴파일 또는 타입 불일치로 즉시 드러난다.

- [ ] **Step 5: 테스트 재실행 — PASS.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.web.dto.SlipDetailResponseEnrichmentTest"`
  기대: **PASS** (2 테스트).

- [ ] **Step 6: 커밋.**
  ```
  git add services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipApprovalActorResponse.java \
          services/slip-service/src/main/java/com/samhanair/logis/slip/web/dto/SlipDetailResponse.java \
          services/slip-service/src/test/java/com/samhanair/logis/slip/web/dto/SlipDetailResponseEnrichmentTest.java
  git commit -F (파일)
  ```
  메시지:
  ```
  feat(slip): SlipDetailResponse 결재란 actor/서명 enrichment 필드 additive 추가

  dispatcher/inspector nested actor(fullName+signaturePngBase64) + ownerSignaturePngBase64
  신규. fromEnriched(getOne 전용) 추가, from(mutation)은 null 유지(enrichment-on-GET-only).
  기존 dispatcherUserId/SignedAt raw 필드 보존(Slice A progress-bar 호환), ownerUserId 미노출.

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B9aoimz6wNow8HA2nodZZr
  ```

---

### Task C3.3: `SlipService.getOne()` 3자 이름 resolve + 서명 배치 조회 배선 + Testcontainers IT

**Files:**
- modify `services/slip-service/src/main/java/com/samhanair/logis/slip/service/SlipService.java`
- create `services/slip-service/src/test/java/com/samhanair/logis/slip/it/SlipGetOneSignatureEnrichmentIT.java`

**Interfaces:**
- Consumes: `UserInternalClient.resolveFullName(UUID)`(기존, 단건) + `resolveSignatures(List<UUID>)`(C3.1) + `SlipDetailResponse.fromEnriched(...)`(C3.2).
- Produces: `getOne()` 응답이 owner/dispatcher/inspector fullName + 3자 서명(base64) 을 포함. join key = `Slip.createdBy`=`dispatcherUserId`=`inspectorUserId`=Employee.id. fallback 시 null(빈 공간).

- [ ] **Step 1: 실패 IT 작성 — getOne 이 dispatcher/inspector/owner 이름+서명을 enrich + join-key 회귀 + fallback null (REAL).**
  create `services/slip-service/src/test/java/.../it/SlipGetOneSignatureEnrichmentIT.java`. 기존 IT(`SlipDriverFieldsIT`)의 부트스트랩(Testcontainers Postgres, `@SpringBootTest`, `@MockBean UserInternalClient`, lenient stub, X-User-* 헤더, MockMvc)을 미러. 핵심 단언만 (전문 스캐폴딩은 형제 IT 복제):
  ```java
  package com.samhanair.logis.slip.it;

  import static org.mockito.ArgumentMatchers.anyList;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  // ... @SpringBootTest + @Testcontainers + @AutoConfigureMockMvc 스캐폴딩은 SlipDriverFieldsIT 미러 ...
  // @MockBean private UserInternalClient userInternalClient;  (외부 client 격리)

  /**
   * getOne 결재란 enrichment IT(C3) — owner/dispatcher/inspector 이름+서명 join-key 회귀.
   *
   * <p>Testcontainers Postgres 실 DB. UserInternalClient 만 @MockBean(다운스트림 user-service 부재).
   * join key = createdBy = dispatcherUserId = inspectorUserId = Employee.id 가 서명을 정확히 반환함을
   * 박제(silent no-op 회귀 가드). fallback 시 빈 서명(null) → 500 금지.
   */
  // @Test
  void getOne_resolvesThreeActorsNamesAndSignatures() throws Exception {
      // given: OUTBOUND 전표를 ACCEPTED+INSPECTING 까지 전이시켜 dispatcherUserId/inspectorUserId 채움.
      //   (도메인 메서드 accept(dispatcherId)/inspect(inspectorId) 사용 — 직접 set 금지)
      java.util.UUID owner = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c1");
      java.util.UUID disp  = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c2");
      java.util.UUID insp  = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c3");
      java.util.UUID slipId = persistAcceptedInspectedSlip(owner, disp, insp); // helper

      org.mockito.Mockito.when(userInternalClient.resolveFullName(owner))
              .thenReturn(java.util.Optional.of("이작성"));
      org.mockito.Mockito.when(userInternalClient.resolveFullName(disp))
              .thenReturn(java.util.Optional.of("홍출고"));
      org.mockito.Mockito.when(userInternalClient.resolveFullName(insp))
              .thenReturn(java.util.Optional.of("김검수"));
      org.mockito.Mockito.when(userInternalClient.resolveSignatures(anyList()))
              .thenReturn(java.util.Map.of(
                      owner, new com.samhanair.logis.slip.client.EmployeeSignatureDto("iVOWNER", "2026-06-21T09:00:00"),
                      disp,  new com.samhanair.logis.slip.client.EmployeeSignatureDto("iVDISP",  "2026-06-21T09:01:00"),
                      insp,  new com.samhanair.logis.slip.client.EmployeeSignatureDto("iVINSP",  "2026-06-21T09:02:00")));

      mockMvc.perform(get("/slips/{id}", slipId).header("X-User-Role", "MASTER"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.data.ownerFullName").value("이작성"))
              .andExpect(jsonPath("$.data.ownerSignaturePngBase64").value("iVOWNER"))
              .andExpect(jsonPath("$.data.dispatcher.fullName").value("홍출고"))
              .andExpect(jsonPath("$.data.dispatcher.signaturePngBase64").value("iVDISP"))
              .andExpect(jsonPath("$.data.inspector.fullName").value("김검수"))
              .andExpect(jsonPath("$.data.inspector.signaturePngBase64").value("iVINSP"))
              // 인감 모델: 등록시각 미노출 — actor 에 signedAt 키 자체가 없어야 함.
              .andExpect(jsonPath("$.data.dispatcher.signedAt").doesNotExist())
              .andExpect(jsonPath("$.data.inspector.signedAt").doesNotExist());
  }

  // @Test
  void getOne_userServiceDown_returnsNullEnrichment_not500() throws Exception {
      java.util.UUID owner = java.util.UUID.fromString("00000000-0000-0000-0000-0000000000d1");
      java.util.UUID slipId = persistAcceptedInspectedSlip(owner, null, null);
      org.mockito.Mockito.when(userInternalClient.resolveFullName(org.mockito.ArgumentMatchers.any()))
              .thenReturn(java.util.Optional.empty());
      org.mockito.Mockito.when(userInternalClient.resolveSignatures(anyList()))
              .thenReturn(java.util.Map.of()); // 빈 맵 fallback

      mockMvc.perform(get("/slips/{id}", slipId).header("X-User-Role", "MASTER"))
              .andExpect(status().isOk()) // 500 금지
              .andExpect(jsonPath("$.data.ownerSignaturePngBase64").doesNotExist())
              .andExpect(jsonPath("$.data.dispatcher").doesNotExist());
  }
  ```
  > NOTE: `persistAcceptedInspectedSlip(...)` 헬퍼는 본 IT 안에서 도메인 factory + lifecycle 메서드(`accept`/`process`/`inspect`)로 작성하라(직접 필드 set 금지). dispatcher/inspector 미전이 케이스(두 번째 테스트)는 null userId → resolve 대상에서 빠지고 actor 미노출. 스캐폴딩(컨테이너·MockMvc·repository 주입)은 `SlipControllerIT`/`SlipDriverFieldsIT` 를 1:1 복제.

- [ ] **Step 2: IT 실행 — 실패(enrichment 미배선) 확인.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.it.SlipGetOneSignatureEnrichmentIT"`
  기대: **FAIL** (`dispatcher.fullName` 등 jsonPath null/부재 — getOne 이 아직 fromEnriched 호출 안 함). Windows 로컬 Docker 미가용 시 skip → Linux CI 로 확증.

- [ ] **Step 3: `SlipService.getOne()` 배선 + 헬퍼 (REAL).**
  3-1. `getOne` 본문 교체:
  ```java
      @Transactional(readOnly = true)
      public SlipDetailResponse getOne(UUID id) {
          Slip slip = loadOrThrow(id);
          UUID ownerId = parseUuidOrNull(slip.getCreatedBy());
          UUID dispatcherId = parseUuidOrNull(slip.getDispatcherUserId());
          UUID inspectorId = parseUuidOrNull(slip.getInspectorUserId());

          String ownerFullName = ownerId == null ? null
                  : userInternalClient.resolveFullName(ownerId).orElse(null);
          String dispatcherFullName = dispatcherId == null ? null
                  : userInternalClient.resolveFullName(dispatcherId).orElse(null);
          String inspectorFullName = inspectorId == null ? null
                  : userInternalClient.resolveFullName(inspectorId).orElse(null);

          java.util.List<UUID> sigKeys = java.util.stream.Stream.of(ownerId, dispatcherId, inspectorId)
                  .filter(java.util.Objects::nonNull)
                  .distinct()
                  .toList();
          java.util.Map<UUID, EmployeeSignatureDto> signatures =
                  userInternalClient.resolveSignatures(sigKeys);

          String ownerSig = signatureOf(signatures, ownerId);
          SlipApprovalActorResponse dispatcher =
                  toActor(dispatcherFullName, signatureOf(signatures, dispatcherId), dispatcherId);
          SlipApprovalActorResponse inspector =
                  toActor(inspectorFullName, signatureOf(signatures, inspectorId), inspectorId);

          return SlipDetailResponse.fromEnriched(slip, ownerFullName, ownerSig, dispatcher, inspector);
      }
  ```
  3-2. 클래스에 헬퍼 추가(`resolveOwnerFullName` 메서드 옆):
  ```java
      private static UUID parseUuidOrNull(String raw) {
          if (raw == null || raw.isBlank()) {
              return null;
          }
          try {
              return UUID.fromString(raw);
          } catch (IllegalArgumentException ex) {
              return null;
          }
      }

      private static String signatureOf(java.util.Map<UUID, EmployeeSignatureDto> map, UUID key) {
          if (key == null) {
              return null;
          }
          EmployeeSignatureDto dto = map.get(key);
          return dto == null ? null : dto.signaturePngBase64();
      }

      /**
       * actor 응답 생성 — 식별자가 없으면(null) actor 자체를 null 로(미노출).
       * 이름·서명 둘 다 없어도 식별자만 있으면 빈 actor(null/null) 반환(전이 발생 사실 보존).
       */
      private static SlipApprovalActorResponse toActor(String fullName, String signature, UUID id) {
          if (id == null) {
              return null;
          }
          return new SlipApprovalActorResponse(fullName, signature);
      }
  ```
  3-3. import 추가: `import com.samhanair.logis.slip.client.EmployeeSignatureDto;` `import com.samhanair.logis.slip.web.dto.SlipApprovalActorResponse;` (없으면). 기존 `resolveOwnerFullName` 은 다른 호출자 없으면 제거 가능하나, 회귀 최소화를 위해 **유지**(deprecated 주석 불요, 호출만 getOne 에서 제거됨 — 미사용 private 경고는 무시하거나 제거. 제거 시 동일 커밋).

- [ ] **Step 4: IT 재실행 — PASS.**
  `cd services/slip-service && ./gradlew test --tests "com.samhanair.logis.slip.it.SlipGetOneSignatureEnrichmentIT"`
  기대: **PASS** (2 테스트). 추가로 변경 모듈 전체 회귀:
  `cd services/slip-service && ./gradlew test`
  기대: 기존 `getOne`/`ownerFullName` IT(`SlipControllerIT`, publish IT 등) **그대로 PASS** (additive reshape 라 회귀 0). (한글경로 skip 시 CI 확증.)

- [ ] **Step 5: 커밋.**
  ```
  git add services/slip-service/src/main/java/com/samhanair/logis/slip/service/SlipService.java \
          services/slip-service/src/test/java/com/samhanair/logis/slip/it/SlipGetOneSignatureEnrichmentIT.java
  git commit -F (파일)
  ```
  메시지:
  ```
  feat(slip): getOne 결재란 3자 이름+서명 enrichment 배선

  owner/dispatcher/inspector fullName(resolveFullName 단건) + 서명 배치(resolveSignatures)
  조회하여 fromEnriched 로 응답. join key=createdBy=dispatcherUserId=inspectorUserId=Employee.id.
  user-service 다운/미등록 시 null fallback(500 금지). Testcontainers IT 로 join-key 회귀+fallback 박제.

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B9aoimz6wNow8HA2nodZZr
  ```

---

### Task C3.4: desktop `slip.ts` 타입 확장 + `roleStamps.ts` 순수 helper + signedAt 미표시 계약테스트

**Files:**
- modify `clients/desktop/src/renderer/api/slip.ts`
- create `clients/desktop/src/renderer/print/roleStamps.ts`
- create `clients/desktop/src/renderer/print/roleStamps.test.ts`

**Interfaces:**
- Produces: `SlipApprovalActor` += `signaturePng?: string | null`; `SlipDetail` += `ownerSignaturePng?: string | null`. `roleStamps.ts`: `roleStampProps(slip): { owner: RoleStamp; dispatcher: RoleStamp; inspector: RoleStamp }`, `RoleStamp = { value: string | null; signaturePng: string | null }`. **signedAt 미포함**(타입에 time 필드 부재 = 미표시 계약).
- Consumes: BE `SlipDetailResponse.dispatcher.signaturePngBase64` 등(C3.2). FE 는 `data:image/png;base64,` prefix 를 helper 에서 부착.

- [ ] **Step 1: 실패 테스트 작성 — roleStampProps 가 3자 value+signaturePng 만 노출, signedAt 키 부재 (REAL, node vitest).**
  create `clients/desktop/src/renderer/print/roleStamps.test.ts`:
  ```ts
  import { describe, it, expect } from 'vitest'

  import type { SlipDetail } from '../api/slip'
  import { roleStampProps, type RoleStamp } from './roleStamps'

  function slip(input: Partial<SlipDetail> = {}): SlipDetail {
    return {
      id: 'id', slipType: 'OUTBOUND', slipNo: '2026/06/21-1', slipDate: '2026-06-21',
      seqNo: 1, status: 'ACCEPTED', partnerId: null, partnerName: null,
      sourceWarehouseId: null, destinationWarehouseId: null, deliveryTag: null,
      requesterId: null, acceptedBy: null, acceptedAt: null, completedAt: null,
      confirmedAt: null, updatedAt: '2026-06-21T00:00:00', version: 0,
      memo: null, lines: [],
      ...input,
    } as SlipDetail
  }

  describe('roleStampProps', () => {
    it('3자 이름+서명을 매핑하고 base64 에 dataURL prefix 를 부착한다', () => {
      const s = slip({
        ownerFullName: '이작성',
        ownerSignaturePng: 'iVOWNER',
        dispatcher: { userId: 'u2', fullName: '홍출고', signedAt: '2026-06-21T09:01:00', signaturePng: 'iVDISP' },
        inspector: { userId: 'u3', fullName: '김검수', signedAt: '2026-06-21T09:02:00', signaturePng: 'iVINSP' },
      })
      const r = roleStampProps(s)
      expect(r.owner.value).toBe('이작성')
      expect(r.owner.signaturePng).toBe('data:image/png;base64,iVOWNER')
      expect(r.dispatcher.value).toBe('홍출고')
      expect(r.dispatcher.signaturePng).toBe('data:image/png;base64,iVDISP')
      expect(r.inspector.signaturePng).toBe('data:image/png;base64,iVINSP')
    })

    it('서명 미등록 시 signaturePng=null (빈 공간 fallback)', () => {
      const s = slip({ ownerFullName: '이작성', dispatcher: null, inspector: null })
      const r = roleStampProps(s)
      expect(r.owner.signaturePng).toBeNull()
      expect(r.dispatcher.value).toBeNull()
      expect(r.dispatcher.signaturePng).toBeNull()
    })

    it('signedAt 을 절대 노출하지 않는다(인감 모델 — RoleStamp 에 time 필드 부재)', () => {
      const s = slip({
        dispatcher: { userId: 'u2', fullName: '홍출고', signedAt: '2026-06-21T09:01:00', signaturePng: 'iVDISP' },
      })
      const r: RoleStamp = roleStampProps(s).dispatcher
      expect(Object.keys(r).sort()).toEqual(['signaturePng', 'value'])
      expect((r as Record<string, unknown>).signedAt).toBeUndefined()
    })

    it('이미 dataURL prefix 가 있으면 중복 부착하지 않는다', () => {
      const s = slip({ ownerSignaturePng: 'data:image/png;base64,iVOWNER' })
      expect(roleStampProps(s).owner.signaturePng).toBe('data:image/png;base64,iVOWNER')
    })
  })
  ```

- [ ] **Step 2: 테스트 실행 — 실패(파일/심볼 부재) 확인.**
  `cd clients/desktop && node_modules/.bin/vitest run src/renderer/print/roleStamps.test.ts`
  기대: **FAIL** (`./roleStamps` 모듈 없음).

- [ ] **Step 3: `slip.ts` 타입 확장 (REAL).**
  3-1. `SlipApprovalActor` 에 필드 추가(`signedAt` 뒤):
  ```ts
  export interface SlipApprovalActor {
    /** 사용자 UUID — 화면 미노출 (UUID 비공개 가드). */
    userId: string
    /** 사용자 이름 — 결재란 셀에 표시. */
    fullName: string
    /** ISO 8601 timestamp — Slice A progress-bar 용. 결재란 인감에는 미표시(signature-slice-C3). */
    signedAt: string
    /**
     * 사원 등록 서명 base64 dataURL — signature-slice-C3. 미등록 시 null/undefined.
     * BE SlipApprovalActorResponse.signaturePngBase64(순수 base64) 를 FE 에서 dataURL 로 변환.
     */
    signaturePng?: string | null
  }
  ```
  3-2. `SlipDetail` 의 `ownerFullName` 옆에 추가:
  ```ts
    /** 담당자 (slip.createdBy 의 fullName). */
    ownerFullName?: string | null
    /** 담당자 등록 서명 base64 dataURL — signature-slice-C3. ownerUserId 는 노출하지 않음. */
    ownerSignaturePng?: string | null
  ```
  > NOTE: BE 응답 키는 `signaturePngBase64`/`ownerSignaturePngBase64`(순수 base64). FE 타입은 `signaturePng`/`ownerSignaturePng`(dataURL). 매핑은 `getSlip` 응답을 그대로 두고 `roleStamps.ts` helper 에서 변환(컴포넌트는 helper 만 사용). 단, `slip.ts` `getSlip` 은 base64 를 그대로 `signaturePng`/`ownerSignaturePng` 에 받도록 키 정렬이 필요 → axios 응답 매핑: getSlip 내부에서 actor.signaturePngBase64 → signaturePng 재배치. 아래 3-3 참조.
  3-3. `getSlip` 을 응답 키 정렬하도록 수정(BE `*Base64` → FE `*Png` 1점 변환):
  ```ts
  export async function getSlip(id: string): Promise<SlipDetail> {
    const res = await apiClient.get<ApiEnvelope<SlipDetail & {
      ownerSignaturePngBase64?: string | null
      dispatcher?: (SlipApprovalActor & { signaturePngBase64?: string | null }) | null
      inspector?: (SlipApprovalActor & { signaturePngBase64?: string | null }) | null
    }>>(`/slips/${id}`)
    const d = res.data.data
    const mapActor = (
      a: (SlipApprovalActor & { signaturePngBase64?: string | null }) | null | undefined,
    ): SlipApprovalActor | null =>
      a ? { ...a, signaturePng: a.signaturePngBase64 ?? null } : null
    return {
      ...d,
      ownerSignaturePng: d.ownerSignaturePngBase64 ?? null,
      dispatcher: mapActor(d.dispatcher),
      inspector: mapActor(d.inspector),
    }
  }
  ```

- [ ] **Step 4: `roleStamps.ts` 순수 helper 구현 (REAL).**
  create `clients/desktop/src/renderer/print/roleStamps.ts`:
  ```ts
  /**
   * 결재란 작성자/출고인/검수인 스탬프 props 매핑 — signature-slice-C3.
   *
   * 인감(도장) 모델: 이름(value) + 서명 이미지(signaturePng) 만 노출하고 등록시각(signedAt)은
   * 의도적으로 제외한다(RoleStamp 타입에 time 필드 부재 = 미표시 계약). DispatchView/OutboundView
   * 의 결재란 셀은 본 helper 결과만 사용해야 한다(컴포넌트가 직접 signedAt 을 읽지 못하게 단일화).
   */
  import type { SlipDetail, SlipApprovalActor } from '../api/slip'

  /** 결재란 셀 1칸 props — 이름 + 서명 dataURL(없으면 null). 시간 필드 없음(인감 모델). */
  export interface RoleStamp {
    value: string | null
    signaturePng: string | null
  }

  const DATA_URL_PREFIX = 'data:image/png;base64,'

  /** 순수 base64 → PNG dataURL. 이미 dataURL 이거나 null/빈값이면 그대로/null 반환. */
  function toDataUrl(raw: string | null | undefined): string | null {
    if (!raw) return null
    return raw.startsWith('data:') ? raw : `${DATA_URL_PREFIX}${raw}`
  }

  function actorStamp(actor: SlipApprovalActor | null | undefined): RoleStamp {
    return {
      value: actor?.fullName ?? null,
      signaturePng: toDataUrl(actor?.signaturePng),
    }
  }

  /**
   * 전표 상세 → 결재란 3자 스탬프 props.
   *
   * @param slip 전표 상세(getSlip 결과 — BE base64 가 dataURL 친화 키로 정렬되어 들어옴)
   * @returns owner/dispatcher/inspector 각각 {value, signaturePng}. 미등록 서명은 null.
   */
  export function roleStampProps(slip: SlipDetail): {
    owner: RoleStamp
    dispatcher: RoleStamp
    inspector: RoleStamp
  } {
    return {
      owner: {
        value: slip.ownerFullName ?? null,
        signaturePng: toDataUrl(slip.ownerSignaturePng),
      },
      dispatcher: actorStamp(slip.dispatcher),
      inspector: actorStamp(slip.inspector),
    }
  }
  ```

- [ ] **Step 5: 테스트 재실행 — PASS + 타입체크.**
  `cd clients/desktop && node_modules/.bin/vitest run src/renderer/print/roleStamps.test.ts` → **PASS** (4 테스트).
  `cd clients/desktop && npm run typecheck` → **PASS** (tsconfig.node+web; raw tsc 아님, 메모리 feedback_desktop_typecheck_command).

- [ ] **Step 6: 커밋.**
  ```
  git add clients/desktop/src/renderer/api/slip.ts \
          clients/desktop/src/renderer/print/roleStamps.ts \
          clients/desktop/src/renderer/print/roleStamps.test.ts
  git commit -F (파일)
  ```
  메시지:
  ```
  feat(desktop): 결재란 서명 스탬프 타입 확장 + roleStamps 순수 helper

  SlipApprovalActor.signaturePng / SlipDetail.ownerSignaturePng 추가. getSlip 에서 BE
  *Base64 → FE *Png dataURL 1점 변환. roleStampProps helper(작성자/출고인/검수인)는 이름+서명만
  노출하고 signedAt 미표시(인감 모델) 계약 박제(node vitest).

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B9aoimz6wNow8HA2nodZZr
  ```

---

### Task C3.5: `DispatchView` + `OutboundView` 작성자/출고인/검수인 RoleCell 에 서명 주입 (결함 family sweep)

**Files:**
- modify `clients/desktop/src/renderer/print/DispatchView.tsx`
- modify `clients/desktop/src/renderer/print/OutboundView.tsx`

**Interfaces:**
- Consumes: `roleStampProps(slip)`(C3.4). 담당부서·결제예정일 셀은 stamp 미적용(이름/값만). signedAt 미표시.
- Produces: 결재란 작성자/출고인/검수인 셀에 등록 서명 인감 렌더. PNG 부재 시 기존 빈 공간 fallback(CSS·grid 무변경).

- [ ] **Step 1: 회귀 가드 테스트 갱신 — roleStamps 계약 일치만 확인(렌더 테스트 불가 → C3.4 helper 테스트가 계약 박제).**
  추가 테스트 불필요(C3.4 의 `roleStamps.test.ts` 가 두 뷰의 데이터 계약을 단일점에서 박제). 본 Task 는 뷰가 그 helper 를 **실제로 사용**하도록 배선하는 것이며, 사용 여부는 typecheck + 라이브 캡처(아래 Step 4)로 검증. (vitest 가 .tsx 미포함이라 jsdom 렌더 단언 불가 — 이는 인프라 제약, P2 메모로 PR 본문 명시.)

- [ ] **Step 2: `DispatchView.tsx` 배선 (REAL).**
  2-1. import 추가:
  ```ts
  import { roleStampProps } from './roleStamps'
  ```
  2-2. `const slip: SlipDetail = detailQuery.data` 다음 줄에:
  ```ts
  const stamps = roleStampProps(slip)
  ```
  2-3. 결재란 5칸(`dispatch-roles`)의 RoleCell 3개에 `signaturePng` + value 를 helper 로 교체:
  ```tsx
            <RoleCell label="담당부서" value={slip.ownerDepartment ?? null} />
            <RoleCell label="작성자" value={stamps.owner.value} signaturePng={stamps.owner.signaturePng} />
            <RoleCell label="출고인" value={stamps.dispatcher.value} signaturePng={stamps.dispatcher.signaturePng} />
            <RoleCell label="검수인" value={stamps.inspector.value} signaturePng={stamps.inspector.signaturePng} />
            <RoleCell label="결제예정일" value={paymentDueMmdd} />
  ```
  (담당부서·결제예정일 RoleCell 은 `signaturePng` 미전달 = stamp 미적용 유지.) RoleCell 정의 자체는 변경 불필요(이미 `signaturePng` prop 지원).

- [ ] **Step 3: `OutboundView.tsx` 배선 (REAL) — 출고인 stamp slot.**
  3-1. import 추가:
  ```ts
  import { roleStampProps } from './roleStamps'
  ```
  3-2. `const slip: SlipDetail = detailQuery.data` 다음에:
  ```ts
  const stamps = roleStampProps(slip)
  ```
  3-3. footer 의 출고인 stamp-cell(현재 `[인]` 텍스트 마크)을 서명 우선 렌더로 교체:
  ```tsx
            <div className="stamp-cell">
              <div className="stamp-label">출고인</div>
              <div className="stamp-value">
                {stamps.dispatcher.value ?? ''}
                {stamps.dispatcher.signaturePng ? (
                  <img className="stamp-png" src={stamps.dispatcher.signaturePng} alt="출고인 서명" />
                ) : (
                  <span className="stamp-mark">[인]</span>
                )}
              </div>
            </div>
  ```
  (인수자 stamp-cell 은 기존 `slip.signaturePng`(인수자 전자서명, 슬라이스 B) 유지 — 사원 서명 아님, 변경 금지.)

- [ ] **Step 4: 검증 — typecheck + 라이브 Docker 실QA 캡처.**
  4-1. `cd clients/desktop && npm run typecheck` → **PASS**.
  4-2. `cd clients/desktop && node_modules/.bin/vitest run` → 기존 + roleStamps **전부 PASS** (회귀 0).
  4-3. 라이브 실QA(머지 전 의무, 메모리 feedback_overnight_live_capture / feedback_no_fake_data_ever): Docker 스택 기동(`docker compose up --build user-service slip-service api-gateway`), 실 로그인 `dev_master`, VITE_MOCK_MODE off, 실 사원에 서명 등록(C1/C2 산출) 후 ACCEPTED+INSPECTING 전표를 `/sales/:id/print/dispatch` 와 `/sales/:id/print/outbound` 로 열어 **작성자/출고인/검수인 인감 스탬프** + **UUID 비노출** + **signedAt 미표시** 데스크톱 화면 캡처. PR 본문 인라인 첨부.

- [ ] **Step 5: 커밋.**
  ```
  git add clients/desktop/src/renderer/print/DispatchView.tsx \
          clients/desktop/src/renderer/print/OutboundView.tsx
  git commit -F (파일)
  ```
  메시지:
  ```
  feat(desktop): 출고전표 결재란 작성자/출고인/검수인 서명 인감 스탬프 주입

  DispatchView 결재란 5칸 중 작성자/출고인/검수인 RoleCell + OutboundView 출고인 stamp-cell 에
  roleStampProps 서명 주입(담당부서·결제예정일·인수자 셀 제외). PNG 부재 시 기존 빈 공간/[인] fallback,
  signedAt 미표시(인감 모델). 결함 family sweep — stamp slot 보유 print 뷰 2종 동시 배선.

  Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01B9aoimz6wNow8HA2nodZZr
  ```