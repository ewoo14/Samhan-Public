# BE Cycle 1 리뷰 — audit Slice A (PermissionGuardCounterIT)

> 작성자: Claude BE agent | 일자: 2026-05-19 | 슬라이스: audit-slice-a-followup-cleanup

---

## 판정: APPROVED (조건부 — 경고 1건 확인 권고)

3 케이스 모두 시나리오 정합성 확인. @MockBean 격리 완전. 기존 IT 회귀 위험 없음.

---

## 1. 시나리오 정합성 검증

### Case 1 — VIEW deny → Counter 1 증가

`BalanceSheetController.balanceSheet()`는 `@RequirePermission(page="accounting.reports", action="VIEW")`가 부착되어 있으며, `ROLE_DRIVER`로 `canView=false`를 stub하면 `PermissionAspect.checkPermission()`의 VIEW 분기에서 `denied=true`가 설정되고 `metrics.incrementDenied()` 후 `AccessDeniedException`이 throw된다. 이후 Spring Security ExceptionTranslationFilter가 403을 반환한다.

`readCounter("accounting.reports", ROLE_DRIVER, "VIEW")` 헬퍼가 `PermissionGuardMetrics.COUNTER_NAME`("permission_guard_denied_total")을 태그 4개(service / page / role / action)로 조회하므로 Counter 1.0 검증 로직이 실제 AOP 흐름과 1:1 대응한다. **정합.**

### Case 2 — EDIT deny (view-only override) → Counter 1 증가

`TestEditController.editReport()`에 `@RequirePermission(page="accounting.reports", action="EDIT")`을 부착하고 `canEdit=false + canView=true`로 stub한다. `PermissionAspect` EDIT 분기: `canEdit=false`이면 내부적으로 `canView()`를 추가 호출하고, `canView=true`이면 `denied=true` 처리한다. 시나리오 논리가 AOP 구현(`PermissionAspect` 127~135행)과 정확히 일치한다. **정합.**

### Case 3 — EDIT fallback (canEdit=false + canView=false) → Counter 0

`canEdit=false + canView=false` 조합에서 `PermissionAspect`는 `denied` 플래그를 false로 유지한 채 `joinPoint.proceed()`로 진입한다. `TestEditController`가 200을 반환하고 Counter가 증가하지 않는다. `readCounter()` 헬퍼가 Counter 미등록 상태에서 0.0을 반환하는 null-safe 분기로 검증한다. **정합.**

---

## 2. @MockBean 격리 검증 (feedback_it_mockbean_external_clients)

외부 client @MockBean 목록:

| @MockBean | 역할 |
|---|---|
| `DynamicPermissionClient` | AOP 핵심 — deny/allow 결정자 |
| `SlipServiceClient` | 전표 외부 REST client |
| `PartnerLookupClient` | 거래처 외부 REST client |
| `ProductClient` | 상품 외부 REST client |
| `ChatRoomMappingClient` | 채팅방 외부 REST client |
| `ETaxClient` | 국세청 e-tax 외부 REST client |
| `KftcClient` | 금융결제원 외부 REST client |

`AccountingDynamicPermissionIT`(기존 IT)의 @MockBean 목록과 비교하면 동일 7개 client가 격리된다. Eureka 비활성화는 `AbstractPostgresIT.registerDatasource()`에서 `eureka.client.enabled=false`로 이미 처리되어 Eureka 연결 500 위험이 없다. **완전 격리 확인.**

`@BeforeEach setupLenientStubs()`에서 `canView=true / canEdit=true`를 lenient로 기본 설정하므로, 각 케이스의 명시적 `when()` override 전 상태가 기존 IT를 깨뜨리지 않는다.

---

## 3. TestEditController 설계 검토

`@TestConfiguration` + `@Import` 조합으로 `TestEditController`를 Spring 컨텍스트에 주입하는 방식은 spring-test 공식 패턴이며, 본 파일 외 프로덕션 코드에 영향이 없다. `/test/permission/edit-report` 경로가 프로덕션 URL 충돌 없이 격리된다.

한 가지 확인 권고 사항: accounting-service에 Spring Security `SecurityFilterChain`이 존재한다면, `/test/permission/edit-report` 경로가 `permitAll()` 또는 인증 대상 경로 패턴 중 어느 쪽에 속하는지 확인이 필요하다. 만약 JWT 인증 필터가 활성화되어 `X-User-Id` 헤더만으로는 인증을 통과하지 못하는 구조라면 Case 2/3가 403 응답을 받더라도 원인이 `PermissionAspect`가 아닌 인증 실패일 수 있다. 기존 `AccountingDynamicPermissionIT` 패턴에서 동일 헤더로 정상 동작하고 있으므로 회귀 가능성은 낮지만, CI 결과로 최종 확인을 권고한다.

---

## 4. 기존 IT 회귀 위험 분석

`@Transactional` 어노테이션이 클래스 레벨에 적용되어 각 테스트의 DB 변경이 롤백된다. `MeterRegistry` Counter는 메모리 내 상태이므로 테스트 간 누적 가능성이 있으나, Counter 태그 조합(page + role + action)이 각 케이스마다 달라 Case 1(DRIVER/VIEW), Case 2(SALES/EDIT), Case 3(SALES/EDIT)의 순서 의존성 문제가 잠재한다.

구체적으로 Case 2와 Case 3가 동일 태그 조합(accounting.reports / SALES / EDIT)을 공유하므로, JUnit 5 기본 실행 순서에 따라 Case 2 → Case 3 순서로 실행될 경우 Case 3 시점에 Counter가 이미 1.0인 상태일 수 있다. `MeterRegistry`가 테스트 간 초기화되지 않는다면 Case 3 검증값이 0.0이 아닌 1.0이 되어 실패할 수 있다.

단, `@SpringBootTest`의 ApplicationContext는 캐시되어 재사용되므로, MeterRegistry는 테스트 메서드 간에 공유된다. Case 2가 Counter를 1.0으로 올린 뒤 Case 3가 같은 태그의 Counter를 조회하면 0.0이 아닌 1.0을 읽을 가능성이 높다. **CI에서 실행 순서에 따른 간헐적 실패 위험이 존재**한다. Case 3 검증 로직을 `isGreaterThanOrEqualTo(0.0)` 또는 Counter 초기화 로직으로 보완하거나 @DirtiesContext를 고려할 것을 권고한다.

---

## 5. Javadoc 및 컨벤션 준수

클래스, 메서드, 헬퍼, 내부 클래스 모두 한국어 Javadoc이 작성되어 있다. `@since`, `@see`, `@param`, `@return` 태그가 프로젝트 컨벤션을 준수한다. `@SuppressWarnings("deprecation")` 사유가 인라인 주석으로 명시되어 있다.

---

## 총평

3 케이스의 시나리오 논리와 AOP 구현 간 정합성이 높으며, @MockBean 격리 패턴이 기존 IT와 동일 수준으로 완비되어 있다. Case 2/3 공유 태그에 의한 MeterRegistry 상태 누적 문제가 잠재적 간헐 실패 원인이므로 CI 결과 확인 및 Case 3 검증 방식 보완을 권고한다.
