---
name: restclient-contract-test-false-green
description: 서비스간 RestClient 계약테스트는 다운스트림 엔드포인트 선검증 필수 — @MockBean 우회/fabricated stub 은 계약변경을 CI green 으로 위장(false-green), 미존재 엔드포인트 호출은 실 런타임 버그
metadata:
  type: feedback
---
2026-06-20 RestClient 계약테스트 커버리지 감사(#531) + #533 회고. 전 11서비스 internal RestClient ~41개 중 MockRestServiceServer 실-HTTP 계약테스트가 11개뿐 — 나머지는 소비처 IT 가 `@MockBean` 으로 client 자체를 mock 하거나 무테스트 → **다운스트림 endpoint 경로/헤더/응답필드 변경이 CI green 으로 위장**(false-green). [[enforcement-real-http-test]] 의 계약테스트 구체화.

## 핵심 위험 3종
1. **fabricated stub = false-contract**: MockRestServiceServer 테스트의 stub 이 실 다운스트림 계약과 다르면 "통과"해도 무의미(회귀 미가드). #532/#534 에서 Codex 교차가 미존재 경로 stub·passthrough body 불일치·409 의미반전을 적발·제거.
2. **미존재 엔드포인트 호출 = 실 버그**: client 가 다운스트림에 없는 경로를 "가정"(javadoc '가정한다'/'BE-A0 가 추가')하면 런타임 4xx. #533: inventory `SlipServiceClient`→slip 미존재 `/slips/outbound`→`/{id}` 400→DPS 입고비교 상시 실패. fix=slip-service `/internal/slips/outbound-lines` 신설(PR #535).
3. **🆕 @MockBean 이 client 빈 생성 자체를 우회 → DI/생성자/LB 와이어링 버그가 실 컨테이너 부팅에서만 발현** (2026-06-22 A2-2 #556 야간): 소비처 IT 전부가 client 를 `@MockBean` 하면 **실 RestClient build + LB resolution + 생성자 와이어링이 CI 어디서도 실행 안 됨**. 사례 ①`ApprovalLineAuthorizeClient` 운영+테스트 생성자 2개 + `@Autowired` 미표기 → Spring "No default constructor" — compile·unit·IT 전부 green 인데 slip-service 기동만 폭발. ②`parse()` data 없으면 root 직파싱 fail-open → 계약 드리프트 silent skip. **둘 다 라이브 Docker 부팅/실QA 가 단독 적발**. → **(a)** 신규 internal client 는 실 빈을 생성하는 context-load/ApplicationContextRunner 테스트로 DI 가드, **(b)** auth 측 IT 에 `$.data.*`·`$.success` jsonPath 로 실 envelope 단언(소비처 client 파싱과 동일 shape 강제), **(c)** 매 라운드 라이브 부팅 의무([[overnight-live-capture]] — 부팅만으로 DI 폭발 적발).

## 작성 전 4-체크 (추측 stub 금지)
①다운스트림 controller `@GetMapping`/`@PostMapping` **실재**(grep) ②요청 **DTO 실 필드**(client 가 passthrough Map 이면 body 단언은 caller 소관) ③**passthrough 여부**(client vs caller) ④**상태코드 의미**(409=중복replay vs conflict, 404=miss 등). 표준 패턴=`accounting/.../client/ProductAliasClientTest.java`(경로/헤더/바디 + 응답파싱 + 200/4xx/5xx/409 + `server.verify()`).

## How to apply
신규 internal RestClient 추가 시 MockRestServiceServer 계약테스트 동반. 기존 client 계약테스트 보강 시 **다운스트림 전수검증 선행**(workflow agent 로 fan-out — endpoint 실재+shape 정합 판정 → BUG/TESTABLE/SKELETON). 외부벤더(Google/Aligo/SMS)·Phase11 placeholder(ETax/Kftc/OCR)·in-process 위임은 대상 아님. 관련: [[enforcement-real-http-test]] [[inprocess-mock-principles]] [[ci-test-filter-false-green]] [[identity-header-authz-antipattern]].
