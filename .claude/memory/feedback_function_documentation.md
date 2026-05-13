---
name: 함수 단위 문서화 의무 — 3-layer 체계 (Javadoc + OpenAPI + Slice Dev Report)
description: 모든 슬라이스에 (1) public 메서드 한국어 Javadoc 의무, (2) springdoc-openapi 자동 yaml 추출, (3) docs/dev-reports/<slice>.md 누적 의무. 함수 단위 의도/제약 영구 보존
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: SamhanLogis 의 모든 슬라이스는 다음 **3-layer 함수 문서화 체계**를 의무 적용한다.

## Layer 1 — Inline 한국어 Javadoc (필수)

**대상**: 모든 public 메서드, 도메인 메서드 (factory + mutator + state transition), service 클래스 메서드, controller endpoint, repository custom query

**형식**:
```java
/**
 * [한국어 1줄 요약 — 무엇을 하는가]
 *
 * [선택: 추가 설명 / 비즈니스 규칙 / 알고리즘]
 *
 * @param  파라미터명 의미 (단위, 제약)
 * @return 반환 의미 (null 가능 여부, 빈 컬렉션 처리 등)
 * @throws BusinessException(NOT_FOUND) 어떤 조건일 때
 * @throws BusinessException(CONFLICT)  어떤 조건일 때
 */
```

**예시** (StockService.deduct):
```java
/**
 * 출고 시 가용 재고를 차감한다. FIFO 알고리즘으로 가장 오래된 lot 부터 소진.
 *
 * @param productId 차감 대상 제품 UUID
 * @param warehouseId 차감 대상 창고 UUID
 * @param quantity 차감 수량 (1 이상)
 * @param fromReservation true 면 reservedQty 에서 차감, false 면 availableQty 직접
 * @return 실제 차감된 lot 들의 (lotId, qty) 리스트 + 차감 후 balance
 * @throws BusinessException(NOT_FOUND) balance 가 없을 때
 * @throws BusinessException(CONFLICT) 가용 수량 부족 또는 version 충돌(1회 재시도 후에도)
 */
```

**예외**: trivial getter/setter, Lombok 생성 메서드, framework callback (`@PostConstruct` 등) 은 생략 가능.

## Layer 2 — OpenAPI 자동 생성 (springdoc-openapi)

**대상**: 모든 마이크로서비스의 Spring Boot module

**의존성** (services/<svc>/build.gradle):
```gradle
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0'
```

**런타임 노출**:
- `http://localhost:<port>/v3/api-docs` (JSON)
- `http://localhost:<port>/swagger-ui.html` (대화형 UI)

**SecurityConfig 갱신**: 위 2 경로를 permitAll (`.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()`)

**CI 자동 yaml 추출**: 빌드 시 `docs/api/<service>-openapi.yaml` 로 commit 가능 (별도 Gradle 태스크 — 본 메모리 본문에는 포함 안 함, 인프라 슬라이스에서 도입). **본 슬라이스부터는 의존성 + UI 노출만 의무**, yaml 추출 자동화는 후속 슬라이스.

**Controller 어노테이션 권장** (springdoc 가 자동 추출하지만 명시적이면 더 풍부):
```java
@Operation(summary = "출고 차감", description = "FIFO 로 가용 lot 차감")
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "차감 성공"),
    @ApiResponse(responseCode = "409", description = "재고 부족 또는 version 충돌")
})
@PostMapping("/deduct")
```
(필수는 아니지만 핵심 endpoint 에 권장)

## Layer 3 — 슬라이스 개발 리포트 (`docs/dev-reports/<slice>.md`)

**위치**: `docs/dev-reports/<slice-slug>.md` (예: `inventory-first-slice.md`, `slip-first-slice.md`)

**작성 책임**: 각 팀 에이전트(BE/FE/QA/DevOps)가 자기 산출물의 핵심 함수/컴포넌트의 **의도 1-2줄씩** 누적. PM 이 통합 시 형식 점검.

**템플릿**:
```markdown
# <Slice 이름> 개발 리포트

> **슬라이스**: <slug> | **base commit**: <hash> | **머지 PR**: #N

## BE (Team-<svc> BE)

### 도메인 메서드
- `Entity.method()`: 의도 / 제약 / 비즈니스 규칙
- ...

### Service 메서드
- `Service.method()`: 핵심 로직 / 트랜잭션 경계 / 예외 매핑
- ...

### Controller endpoint
- `POST /xxx`: 권한 / status / 응답 shape
- ...

### Service-to-Service Client
- `XxxClient.method()`: 호출 대상 / 에러 매핑
- ...

## FE (Team-<svc> FE)

### 신규 컴포넌트
- `<Component>`: 용도 / 핵심 props / Storybook stories 수
- ...

## QA (Team-<svc> QA)

### IT 클래스
- `<XxxIT>`: 검증 대상 / 시나리오 수
- ...

### 시나리오 fixtures
- `fixtures.http`: 시나리오 N건 (간략 목록)

## DevOps

### 인프라 변경
- `<file>`: 변경 의도

### 후속 슬라이스 권고
- ...

## Plan 대비 의도적 변경

- 변경 1: 이유
- ...
```

## 메모리 가드 적용 절차

1. **BE 에이전트 prompt 에** "모든 public 메서드 + 도메인 메서드 한국어 Javadoc 작성 의무" 명시. springdoc 의존성 추가 + SecurityConfig permitAll 추가 의무. dev-reports 자기 섹션 작성 의무
2. **FE 에이전트 prompt 에** "컴포넌트 props + 핵심 함수 JSDoc/한국어 주석 의무". dev-reports 자기 섹션 작성
3. **QA 에이전트 prompt 에** dev-reports 자기 섹션 작성 의무 (IT 가 검증하는 대상 + 시나리오 명시)
4. **DevOps 에이전트 prompt 에** dev-reports 자기 섹션 작성 의무
5. **PM 통합 시 검증**: dev-reports 파일 존재 + 4 팀 섹션 모두 채워졌는지 + springdoc 의존성 추가됐는지 + `/v3/api-docs` 응답 200 (smoke test) 확인

## Why

- 슬라이스 단위 산출물(PR 본문, qa_report.md, devops review.md)은 큰 그림만 잡음. 함수 단위 의도/제약/예외 매핑은 어디에도 누적 안 됨
- 6개월 뒤 "왜 이 메서드가 이렇게 됐지?" 답 못 함 → 코드 + IT 역공학 필요
- BE 의 의도적 plan 변경(예: Product 의 currency CHAR(3) → bpchar mismatch)이 함수별 문서화로 사전 catch 가능
- QA 가 IT 작성 시 OpenAPI yaml 참조 → contract drift 차단 (Product hotfix 4건 회피 가능)
- 신규 개발자/에이전트 온보딩 시 시간순 dev-reports 정독으로 전체 맥락 파악

## 적용 시점 (2026-05-04)

- **Inventory Service 첫 슬라이스부터 의무 적용** (BE retro + FE/QA/DevOps 모두)
- 기존 5 마이크로서비스 (auth/user/product/eureka/api-gateway/logging) 는 별도 retroactive 슬라이스로 보강 (Phase 2 마무리 또는 Phase 3 시작 시점)
- Slip Service 부터는 처음부터 의무 적용

## 관련 메모리

- `feedback_multi_agent_team_pattern.md` — 4-team parallel 디스패치 패턴
- `feedback_pm_integration_build_check.md` — PM 통합 시 풀빌드 검증 (본 메모리는 그 검증에 dev-reports 존재 확인 추가)
- `feedback_korean_commits.md` — 모든 산출물 한국어
