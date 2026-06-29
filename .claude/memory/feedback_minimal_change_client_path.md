---
name: feedback_minimal_change_client_path
description: client 호출경로 변경 시 .uri() 경로만 바꾸고 생성자/필드/timeout 불변. 다중 생성자 @Component는 production 생성자에 @Autowired 필수. 로컬 Testcontainers npipe skip→IT는 CI 단독검증.
metadata:
  type: feedback
---

2026-06-29 PR #665(auth파손 4건) 회고 — Codex 가 client 호출경로만 바꾸면 될 일에 **생성자까지 over-engineering** 해 CI 5회 반복(로컬 IT skip 이 매번 가림).

**Why**: 작은 변경에 인접 코드를 건드리면 숨은 의존(Eureka LB 빌더·timeout·DI 모호성)이 깨지고, 로컬에서 검증 못 하는 IT 라 CI 왕복으로만 드러난다.

**How to apply**:
- **최소변경**: client 의 다운스트림 경로 교정 = `.uri("/internal/...")` **경로 문자열만** 변경. 생성자/필드/`@Qualifier("loadBalancedRestClientBuilder")`/`requestFactory`(timeout connect2s·read3s)/import 는 **불변**. origin 원형과 diff 가 경로 줄만이어야 한다.
- **다중 생성자 @Component**: 테스트용 생성자(MockRestServiceServer 바인딩된 RestClient 직접 주입, package-private)를 추가하면 **production(Builder) 생성자에 `@Autowired` 명시** 필수 — 안 하면 Spring 이 2 생성자 모호 → `NoSuchMethodException`/BeanInstantiation. 선례 `slip-service ArologisDispatchClient`.
- **로컬 IT 검증 불가 인지**: Windows Docker Desktop npipe 한계로 Testcontainers IT 가 **로컬 skip**(BUILD SUCCESSFUL=false-green), `DOCKER_HOST=tcp://localhost:2375` 도 데몬 미노출이면 무효 → **IT 는 CI(Linux)가 단독 검증**. push 전 (1) MockRestServiceServer client 테스트 로컬 실행 (2) 변경 모듈 코드 정독(생성자 시그니처·@MockBean 누락·matcher 일관·DI 모호성)으로 CI 왕복 최소화. [[feedback_testcontainers_windows_docker]] [[feedback_changed_module_full_test_before_push]]
- **@MockBean 누락 함정**: base IT 가 `@Autowired X` 를 @BeforeEach 에서 `when()` stub 하면, 각 구체 IT 가 `@MockBean(classes=X)` 선언해야 함(누락 시 실 빈에 when()→matcher 누수 InvalidUseOfMatchers). 신규 IT 는 형제 IT 의 @MockBean 세트 답습.
