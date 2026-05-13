---
name: IT 외부 RestClient/HttpClient 모두 @MockBean 의무
description: SpringBootTest IT 가 service-to-service 호출하는 모든 외부 RestClient (ProductClient/InventoryClient/AuthClient/UserClient 등) 를 @MockBean 으로 격리. 누락 시 실제 호출 → DiscoveryClient/Eureka 비활성 → 500
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: `@SpringBootTest` 기반 IT (특히 `*ControllerIT`, `*LifecycleControllerIT`) 가 호출하는 service 안에 외부 마이크로서비스 RestClient (예: `ProductClient`, `InventoryClient`, `AuthClient`, `UserClient`) 가 있다면 **모두 `@MockBean` 으로 격리 + `@BeforeEach` 에서 lenient mock setup 의무**.

**Why** (PR #17 Slip 첫 슬라이스 회고, 2026-05-04):
- SlipControllerIT 와 SlipLifecycleControllerIT 가 `InventoryClient` 만 `@MockBean` 등록하고 `ProductClient` 누락
- BE 의 `SlipService.create()` 가 라인 productId 검증을 위해 `productClient.lookup()` 호출
- IT 환경에서는 `ProductClient` 가 실제 빈으로 주입 → RestClient 가 `lb://product-service` 호출 시도 → Eureka DiscoveryClient 비활성 (test profile 에서 disable) → IllegalStateException → BE 의 try/catch 가 INTERNAL_ERROR 로 매핑 → 500 반환
- **Status 201 expected, was 500** 으로 IT 12건 중 10건 fail

**적용 절차** (모든 SpringBootTest IT):
```java
@SpringBootTest(classes = MyServiceApplication.class)
@AutoConfigureMockMvc
@Transactional
class MyControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductClient productClient;     // 외부 호출 1

    @MockBean
    private InventoryClient inventoryClient; // 외부 호출 2

    @MockBean
    private AuthClient authClient;           // 외부 호출 3 (있다면)

    @BeforeEach
    void mockExternalClients() {
        // void 메서드는 default no-op (mock 기본 동작) — 추가 setup 불필요
        // 반환값 있는 메서드만 lenient stub
        Mockito.lenient().when(productClient.lookup(ArgumentMatchers.anyList()))
                .thenAnswer(inv -> {
                    List<UUID> ids = inv.getArgument(0);
                    return ids.stream()
                            .map(id -> new ProductSummary(id, "테스트 제품", "MOD-001",
                                    UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"))
                            .toList();
                });
        Mockito.lenient().when(productClient.requireExists(ArgumentMatchers.any()))
                .thenAnswer(inv -> new ProductSummary(
                        inv.getArgument(0), "테스트 제품", "MOD-001",
                        UUID.randomUUID(), new BigDecimal("100000"), "ACTIVE"));
    }
    // ... tests
}
```

## 체크리스트 (QA agent prompt 에 명시)

- [ ] BE service 클래스의 모든 외부 RestClient 의존 (생성자/필드 inject) 식별
- [ ] 각 외부 client 마다 `@MockBean` 추가
- [ ] 각 client 의 메서드 반환 타입 확인:
  - **void 반환** → mock 기본 동작 = no-op, 추가 setup 불필요. **`Mockito.doNothing()` 사용 금지** (void 메서드 한정 동작이고 default 와 동일)
  - **객체 반환** → `Mockito.lenient().when(...).thenReturn(...)` 또는 `thenAnswer(...)`
- [ ] `lenient()` 적용 — 일부 IT 가 mock 호출 안 해도 strict mode 경고 회피
- [ ] `@BeforeEach` 에서 setup (각 test 시작 전 일관 동작 보장)

## QA agent prompt 에 inline 의무 (memory 표준)

QA agent prompt 작성 시 다음 문구 의무 포함:
> **외부 RestClient @MockBean 의무**: BE service 가 호출하는 모든 외부 마이크로서비스 client (예: `ProductClient`, `InventoryClient`, `AuthClient`, `UserClient`) 를 `@MockBean` 으로 격리하고 `@BeforeEach` 에서 lenient mock setup 추가. 누락 시 실제 RestClient 호출 → Eureka 비활성 → 500. 반환값 있는 메서드만 stub, void 메서드는 mock 기본 동작 (no-op) 활용.

## 관련 메모리
- `feedback_pm_integration_build_check.md` — Docker 가용 IT 사전 실행 의무 (본 메모리는 그 가드 안에서 작동)
- `feedback_function_documentation.md` — 외부 client 메서드도 한국어 Javadoc 의무 (반환 타입 명시 → IT mock 시 참고)
