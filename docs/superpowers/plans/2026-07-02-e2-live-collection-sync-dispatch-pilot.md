# E2 라이브 컬렉션 동기화 — 배차 파일럿 (Plan A / 기둥1) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 공유 `CollectionRealtimePublisher`(afterCommit) 를 만들고, 배차현황 목록(`GET /admin/dispatch-tasks`)에 생성/수정/삭제/상태변경이 다른 사용자 화면에 실시간 반영되도록 배선한다(기둥1).

**Architecture:** product 카탈로그 3종(afterCommit publisher + 컬렉션 SSE 컨트롤러 + FE invalidate)을 `shared/realtime-abstraction` 헬퍼로 일반화하고, 배차(slip-service dispatch 도메인)에 복제. 서버는 opaque 최소 페이로드만 broadcast, FE 는 수신 시 react-query `invalidateQueries` 로 refetch.

**Tech Stack:** Java 17 / Spring Boot 3 (slip-service, shared/realtime-abstraction), SseEmitter, React + TanStack Query (clients/desktop), vitest / JUnit(Testcontainers).

## Global Constraints
- BaseEntity 7 audit + Soft Delete only (하드삭제 금지).
- UUID/식별자 사용자 비노출 — SSE payload 는 opaque, 화면 노출 식별자는 taskCode.
- 게이트웨이 단일 신원 권위(X-User-Role 미주입) — SSE 구독도 목록 VIEW 권한 재사용.
- 한국어 Javadoc/커밋/PR. `@RequirePermission(page,action)` 규약. Flyway 신규 V만(본 Plan A = Flyway 0).
- afterCommit 발화(롤백 시 미발화). react-query invalidate 방식(캐시 패치 아님).
- 본 Plan A 범위 = **기둥1(라이브 동기화)만**. 기둥2(취소선 삭제+복원)=Plan B, 배차 전표확인 미리보기=Plan C(별도).

---

### Task 1: 공유 `CollectionRealtimePublisher` (afterCommit 컬렉션 변경 발화)

**Files:**
- Create: `shared/realtime-abstraction/src/main/java/com/samhanair/logis/shared/realtime/collection/CollectionRealtimePublisher.java`
- Modify: `shared/realtime-abstraction/src/main/java/com/samhanair/logis/shared/realtime/RealtimeAutoConfiguration.java` (bean 등록)
- Test: `shared/realtime-abstraction/src/test/java/com/samhanair/logis/shared/realtime/collection/CollectionRealtimePublisherTest.java`

**Interfaces:**
- Consumes: `RealtimeBroker.publish(UUID entityId, String eventName, Object payload)` (기존).
- Produces: `CollectionRealtimePublisher.publishChange(UUID channelId, String eventName, Map<String,Object> payload)` — 활성 트랜잭션이면 afterCommit 지연, 없으면 즉시. Task 3 이 사용.

- [ ] **Step 1: 실패 테스트 작성** — `CollectionRealtimePublisherTest.java`

```java
package com.samhanair.logis.shared.realtime.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.shared.realtime.broker.InMemoryRealtimeBroker;
import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CollectionRealtimePublisherTest {

    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-0000000000AA");

    @Test
    void 트랜잭션_없으면_즉시_발화() {
        RealtimeBroker broker = new InMemoryRealtimeBroker();
        CollectionRealtimePublisher publisher = new CollectionRealtimePublisher(broker);

        publisher.publishChange(CHANNEL, "dispatch:board:changed", Map.of("changeType", "CREATED"));

        assertThat(broker.publishCount()).isEqualTo(1L);
    }

    @Test
    void 활성_트랜잭션이면_afterCommit_등록_커밋전_미발화() {
        RealtimeBroker broker = new InMemoryRealtimeBroker();
        CollectionRealtimePublisher publisher = new CollectionRealtimePublisher(broker);

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishChange(CHANNEL, "dispatch:board:changed", Map.of("changeType", "UPDATED"));
            // 커밋 전 — 아직 미발화
            assertThat(broker.publishCount()).isZero();
            // afterCommit 콜백 수동 트리거 (커밋 시뮬레이션)
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertThat(broker.publishCount()).isEqualTo(1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :shared:realtime-abstraction:test --tests "*CollectionRealtimePublisherTest*"`
Expected: FAIL — `CollectionRealtimePublisher` 클래스 없음(compile error).

- [ ] **Step 3: 구현 작성** — `CollectionRealtimePublisher.java`

```java
package com.samhanair.logis.shared.realtime.collection;

import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 컬렉션(목록) 레벨 변경 SSE 발화 통일 게이트웨이 (E2 기둥1 공유 헬퍼).
 *
 * <p>product 카탈로그의 {@code ProductCatalogChangePublisher} afterCommit 패턴을 도메인 무관
 * 재사용 형태로 일반화. 각 도메인은 well-known 합성 채널 UUID + 이벤트명을 넘겨 목록 변경을
 * 브로드캐스트한다. 페이로드는 opaque 최소값(변경 종류/식별자) — FE 는 상세를 refetch 한다.
 *
 * <p><b>발화 시점</b>: 활성 트랜잭션이면 {@link TransactionSynchronization#afterCommit()} 지연
 * (롤백 시 미발화), 없으면 즉시(fallback). "생성/수정/삭제가 커밋된 뒤에만 타 화면 반영".
 */
public class CollectionRealtimePublisher {

    private final RealtimeBroker broker;

    public CollectionRealtimePublisher(RealtimeBroker broker) {
        this.broker = broker;
    }

    /**
     * 컬렉션 변경 publish (커밋 후 발화).
     *
     * @param channelId 도메인 컬렉션 채널 (well-known 합성 UUID)
     * @param eventName SSE event name (예: "dispatch:board:changed")
     * @param payload   opaque 최소 페이로드 (예: {@code {"changeType":"CREATED"}})
     */
    public void publishChange(UUID channelId, String eventName, Map<String, Object> payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broker.publish(channelId, eventName, payload);
                }
            });
        } else {
            broker.publish(channelId, eventName, payload);
        }
    }
}
```

- [ ] **Step 4: auto-config bean 등록** — `RealtimeAutoConfiguration.java` 에 추가 (import + @Bean)

import 추가:
```java
import com.samhanair.logis.shared.realtime.collection.CollectionRealtimePublisher;
```
클래스 본문에 bean 메서드 추가(기존 `presenceService` bean 아래):
```java
    /** 컬렉션(목록) 레벨 변경 발화 헬퍼 — consumer service override 가능. */
    @Bean
    @ConditionalOnMissingBean(CollectionRealtimePublisher.class)
    public CollectionRealtimePublisher collectionRealtimePublisher(RealtimeBroker broker) {
        return new CollectionRealtimePublisher(broker);
    }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :shared:realtime-abstraction:test --tests "*CollectionRealtimePublisherTest*"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add shared/realtime-abstraction/src/main/java/com/samhanair/logis/shared/realtime/collection/CollectionRealtimePublisher.java shared/realtime-abstraction/src/main/java/com/samhanair/logis/shared/realtime/RealtimeAutoConfiguration.java shared/realtime-abstraction/src/test/java/com/samhanair/logis/shared/realtime/collection/CollectionRealtimePublisherTest.java
git commit -m "feat(realtime): 공유 CollectionRealtimePublisher(afterCommit 컬렉션 발화) — E2 기둥1 헬퍼"
```

---

### Task 2: 배차 컬렉션 채널 상수 + SSE 구독 컨트롤러

**Files:**
- Create: `services/slip-service/src/main/java/com/samhanair/logis/slip/realtime/DispatchBoardRealtime.java` (채널 상수)
- Create: `services/slip-service/src/main/java/com/samhanair/logis/slip/web/dispatch/DispatchBoardRealtimeController.java`
- Test: `services/slip-service/src/test/java/com/samhanair/logis/slip/it/dispatch/DispatchBoardRealtimeControllerIT.java`

**Interfaces:**
- Consumes: `SlipRealtimeBroker`(기존 bean, `RealtimeBroker` 구현), `@RequirePermission`.
- Produces: `DispatchBoardRealtime.CHANNEL_ID` (UUID), `DispatchBoardRealtime.EVENT_CHANGED` (String) — Task 3 이 발화에 사용. SSE endpoint `GET /admin/dispatch-tasks/board-realtime`.

- [ ] **Step 1: 채널 상수 작성** — `DispatchBoardRealtime.java`

```java
package com.samhanair.logis.slip.realtime;

import java.util.UUID;

/**
 * 배차현황 목록 레벨 실시간 동기화 채널 상수 (E2 기둥1).
 *
 * <p>개별 dispatchTask UUID 가 아닌 목록 전체 invalidate 용 well-known 합성 채널.
 * FE 는 이 채널 하나만 구독하여 배차 생성/수정/삭제/상태변경 시 목록을 refetch 한다.
 */
public final class DispatchBoardRealtime {

    private DispatchBoardRealtime() {}

    /** 배차현황 목록 브로드캐스트 채널 (합성 UUID). */
    public static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-0000d15ba7c40001");

    /** 배차 목록 변경 이벤트명. */
    public static final String EVENT_CHANGED = "dispatch:board:changed";
}
```
> NOTE: UUID 는 well-known 상수면 충분(실 엔티티 아님). 위 값은 유효한 UUID 문자열이어야 함 — 실제 사용 값 `00000000-0000-0000-0000-00000d15ba70` 처럼 16진수 16바이트로 맞춰 작성(구현 시 `UUID.fromString` 이 파싱 가능한 값 확인, 필요 시 `UUID.nameUUIDFromBytes("dispatch:board".getBytes())` 사용).

- [ ] **Step 2: 실패 IT 작성** — `DispatchBoardRealtimeControllerIT.java`

```java
package com.samhanair.logis.slip.it.dispatch;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class DispatchBoardRealtimeControllerIT {

    @Autowired WebApplicationContext ctx;
    MockMvc mvc;

    // 권한 헤더/설정은 기존 DispatchTaskAdminController IT 의 셋업 헬퍼 재사용 (같은 page="dispatch.board").
    // 여기서는 SSE endpoint 가 200 text/event-stream 으로 응답하고 권한 가드가 걸림을 검증.

    @Test
    void 권한없으면_403() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
        mvc.perform(get("/admin/dispatch-tasks/board-realtime")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isForbidden());
    }
}
```
> NOTE: 실제 프로젝트의 IT 권한 셋업(X-User-* 헤더 주입, dispatch.board VIEW 부여)은 기존 `DispatchTaskAdminControllerIT` 패턴을 그대로 복제. 200 케이스는 SSE 특성상 emitter 반환 즉시 열림 — 권한 부여 후 `status().isOk()` + `content().contentTypeCompatibleWith(TEXT_EVENT_STREAM)` 로 검증.

- [ ] **Step 3: 실패 확인**

Run: `./gradlew :services:slip-service:test --tests "*DispatchBoardRealtimeControllerIT*"`
Expected: FAIL — 컨트롤러/엔드포인트 없음(404 또는 compile error).

- [ ] **Step 4: 컨트롤러 구현** — `DispatchBoardRealtimeController.java`

```java
package com.samhanair.logis.slip.web.dispatch;

import com.samhanair.logis.security.permission.PermissionAction;
import com.samhanair.logis.security.permission.RequirePermission;
import com.samhanair.logis.slip.realtime.DispatchBoardRealtime;
import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 배차현황 목록 레벨 실시간 SSE 구독 endpoint (E2 기둥1).
 *
 * <p>{@code GET /admin/dispatch-tasks/board-realtime} — text/event-stream.
 * {@link DispatchBoardRealtime#CHANNEL_ID} 채널 구독자 전원에게
 * {@code dispatch:board:changed} 이벤트가 broadcast 되어 동시 시청자 목록이 실시간 갱신된다.
 *
 * <p>권한: dispatch.board VIEW (배차현황 조회와 동일).
 */
@RestController
@RequestMapping("/admin/dispatch-tasks/board-realtime")
public class DispatchBoardRealtimeController {

    private final SlipRealtimeBroker broker;

    public DispatchBoardRealtimeController(SlipRealtimeBroker broker) {
        this.broker = broker;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequirePermission(page = "dispatch.board", action = PermissionAction.VIEW)
    public SseEmitter subscribe() {
        return broker.subscribe(DispatchBoardRealtime.CHANNEL_ID);
    }
}
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :services:slip-service:test --tests "*DispatchBoardRealtimeControllerIT*"`
Expected: PASS.

- [ ] **Step 6: 커밋**

```bash
git add services/slip-service/src/main/java/com/samhanair/logis/slip/realtime/DispatchBoardRealtime.java services/slip-service/src/main/java/com/samhanair/logis/slip/web/dispatch/DispatchBoardRealtimeController.java services/slip-service/src/test/java/com/samhanair/logis/slip/it/dispatch/DispatchBoardRealtimeControllerIT.java
git commit -m "feat(dispatch): 배차현황 목록 SSE 구독 endpoint + 채널 상수 (E2 기둥1)"
```

---

### Task 3: DispatchTaskService mutation 에 컬렉션 변경 발화 배선

**Files:**
- Modify: `services/slip-service/src/main/java/com/samhanair/logis/slip/service/dispatch/DispatchTaskService.java` (생성자 필드 + 각 mutation 뒤 publish)
- Test: `services/slip-service/src/test/java/com/samhanair/logis/slip/it/dispatch/DispatchTaskServicePublishIT.java`

**Interfaces:**
- Consumes: `CollectionRealtimePublisher.publishChange(...)` (Task 1), `DispatchBoardRealtime.CHANNEL_ID/EVENT_CHANGED` (Task 2).
- Produces: 없음(내부 발화).

- [ ] **Step 1: 실패 테스트 작성** — `DispatchTaskServicePublishIT.java`

```java
package com.samhanair.logis.slip.it.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import com.samhanair.logis.slip.service.dispatch.DispatchTaskService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** createTask 커밋 후 배차 목록 채널로 이벤트가 발화되는지 검증. */
@SpringBootTest
class DispatchTaskServicePublishIT {

    @Autowired DispatchTaskService service;
    @Autowired SlipRealtimeBroker broker;

    @Test
    void createTask_커밋후_목록채널_발화() {
        long before = broker.publishCount();
        service.createTask(LocalDate.now());
        // @Transactional 테스트라도 afterCommit 은 실제 커밋 시 실행 — @Commit 또는 TestTransaction 사용.
        // 여기서는 커밋 경계 밖에서 호출되도록 서비스가 자체 트랜잭션을 커밋한 뒤 count 증가 확인.
        assertThat(broker.publishCount()).isGreaterThan(before);
    }
}
```
> NOTE: afterCommit 은 실제 커밋에서만 실행되므로 IT 는 `@Commit` 또는 별도 트랜잭션 경계 필요. 기존 slip IT 의 커밋 검증 패턴(`TestTransaction.flagForCommit`/`end`) 을 따른다. 대안: publisher 를 spy 로 주입해 `publishChange` 호출 자체를 verify(단위 레벨). 구현자는 기존 slip IT 관례에 맞춰 선택.

- [ ] **Step 2: 실패 확인**

Run: `./gradlew :services:slip-service:test --tests "*DispatchTaskServicePublishIT*"`
Expected: FAIL — 발화 없음(publishCount 불변).

- [ ] **Step 3: 서비스 배선** — `DispatchTaskService.java`

생성자 필드 추가 (`@RequiredArgsConstructor` 라 `private final` 만 추가):
```java
    private final com.samhanair.logis.shared.realtime.collection.CollectionRealtimePublisher collectionPublisher;
```
헬퍼 메서드 추가(클래스 하단):
```java
    /** 배차 목록 변경 발화 (커밋 후). changeType = CREATED/UPDATED/DELETED/STATUS_CHANGED. */
    private void publishBoardChanged(String changeType) {
        collectionPublisher.publishChange(
                com.samhanair.logis.slip.realtime.DispatchBoardRealtime.CHANNEL_ID,
                com.samhanair.logis.slip.realtime.DispatchBoardRealtime.EVENT_CHANGED,
                java.util.Map.of("changeType", changeType));
    }
```
각 mutation 반환 직전 호출 추가:
- `createTask` 반환 전 → `publishBoardChanged("CREATED");`
- `addVehicleGroup`(두 오버로드) / `assignSlip` / `reorderSlips` 반환 전 → `publishBoardChanged("UPDATED");`
- `removeVehicleGroup` / `removeSlipFromGroup` 끝 → `publishBoardChanged("DELETED");`
> STATUS_CHANGED(DISPATCHING/CANCELLED 등)는 `DispatchTaskCompletionService`/`...ConfirmService`/`...UnavailableService` 에도 동일 `collectionPublisher.publishChange(..., "STATUS_CHANGED")` 배선(해당 서비스도 `@Transactional`). 본 Task 범위에 포함(각 서비스 생성자 필드 + 전이 성공 지점 1줄).

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :services:slip-service:test --tests "*DispatchTaskServicePublishIT*"`
Expected: PASS.

- [ ] **Step 5: 변경 모듈 전체 test 완주** ([[feedback_changed_module_full_test_before_push]])

Run: `./gradlew :services:slip-service:test`
Expected: 기존 배차 IT/단위 전부 그린(발화 추가가 기존 계약 무변경).

- [ ] **Step 6: 커밋**

```bash
git add services/slip-service/src/main/java/com/samhanair/logis/slip/service/dispatch/
git commit -m "feat(dispatch): DispatchTask mutation/상태전이에 목록 변경 SSE 발화 배선 (E2 기둥1)"
```

---

### Task 4: 게이트웨이 SSE 라우트 확인/추가

**Files:**
- Modify: `services/gateway/src/main/resources/application.yml`(또는 라우트 정의 파일) — `/admin/dispatch-tasks/board-realtime` SSE 라우트.
- Test: 라우트 로드 확인(기존 gateway route 테스트 패턴).

**Interfaces:** Consumes: Task 2 endpoint. Produces: 없음.

- [ ] **Step 1: 기존 dispatch 라우트 확인**

Run: `grep -rn "dispatch-tasks" services/gateway/src/main/resources/`
Expected: `/admin/dispatch-tasks/**` 라우트가 이미 slip-service 로 감. SSE 는 buffering 회피 필요.

- [ ] **Step 2: SSE 라우트 배선(필요 시)**

기존 `/admin/dispatch-tasks/**` 라우트가 prefix 로 SSE endpoint 를 커버하면 별도 라우트 불요(대부분 커버). SSE 응답 버퍼링 이슈가 있으면 product-catalog-realtime 라우트(no-strip, JwtAuthentication) 패턴을 복제한 전용 라우트 추가:
```yaml
# (product catalog-realtime 라우트 블록을 참조해 dispatch board-realtime 전용 라우트 추가 — 필요 시)
```
> NOTE: 구현자는 먼저 기존 라우트가 SSE 를 통과시키는지 로컬 실 게이트웨이(:8080)에서 확인. 통과하면 이 Task 는 "확인만" 으로 종료(코드 무변경). product 카탈로그 SSE 가 별도 라우트를 쓴 이유(no-strip)가 있으면 동일 적용.

- [ ] **Step 3: 커밋(변경 있을 때만)**

```bash
git add services/gateway/src/main/resources/
git commit -m "feat(gateway): 배차 목록 SSE 라우트 (E2 기둥1)"
```

---

### Task 5: FE — DispatchTaskRealtimeClient + useCollectionRealtime 훅 + 목록 배선

**Files:**
- Create: `clients/desktop/src/renderer/realtime/DispatchTaskRealtimeClient.ts`
- Create: `clients/desktop/src/renderer/realtime/useCollectionRealtime.ts`
- Modify: `clients/desktop/src/renderer/routes/dispatch-board/DispatchHistoryPage.tsx`
- Test: `clients/desktop/src/renderer/realtime/useCollectionRealtime.test.ts` (환경 제약: vitest node — 순수 로직만) + FE 목록 구독 vitest(jsdom 필요 시 별도)

**Interfaces:**
- Consumes: `createRealtimeClient({name, endpointPath})` → `{subscribe(entityId, onEvent): AbortController}` (기존).
- Produces: `DispatchTaskRealtimeClient` (module singleton), `useCollectionRealtime(client, entityIdSentinel, queryKey)` 훅.

- [ ] **Step 1: realtime client 작성** — `DispatchTaskRealtimeClient.ts`

```ts
/**
 * 배차현황 목록 레벨 실시간 SSE 클라이언트 (E2 기둥1).
 *
 * BE endpoint: GET /admin/dispatch-tasks/board-realtime (DispatchBoardRealtimeController).
 * 목록 전체 브로드캐스트 채널을 구독 — 동시 시청자 목록이 실시간 갱신된다.
 * entityId 는 고정 경로라 sentinel('board') 을 넘긴다. mock 모드는 구독 skip(호출부 가드).
 */
import { createRealtimeClient } from './createRealtimeClient'

export const DispatchTaskRealtimeClient = createRealtimeClient({
  name: 'DispatchTaskRealtimeClient',
  endpointPath: (_entityId) => `/admin/dispatch-tasks/board-realtime`,
})
```

- [ ] **Step 2: 공통 훅 작성** — `useCollectionRealtime.ts`

```ts
/**
 * 컬렉션(목록) 라이브 동기화 공통 훅 (E2 기둥1).
 *
 * 도메인 realtime client 를 구독하여 변경 이벤트 수신 시 지정 queryKey 를 invalidate(refetch).
 * mock 모드에서는 구독하지 않는다. 언마운트 시 abort.
 */
import { useEffect } from 'react'
import { useQueryClient, type QueryKey } from '@tanstack/react-query'
import type { RealtimeClient } from './createRealtimeClient'
import { isMockMode } from '../api/mockMode' // 기존 mock 판별 유틸 경로 (프로젝트 관례 확인)

export function useCollectionRealtime(
  client: RealtimeClient,
  entityIdSentinel: string,
  queryKey: QueryKey,
): void {
  const qc = useQueryClient()
  useEffect(() => {
    if (isMockMode()) return
    const ctrl = client.subscribe(entityIdSentinel, () => {
      void qc.invalidateQueries({ queryKey })
    })
    return () => ctrl.abort()
    // queryKey 는 배열 참조 안정성 위해 JSON 직렬화로 비교하거나 상위에서 useMemo 고정.
  }, [client, entityIdSentinel, qc, JSON.stringify(queryKey)])
}
```
> NOTE: `isMockMode` 실제 경로/이름은 `ProductCatalogPage` 의 mock 가드 구현을 그대로 참조(정찰: mock 모드 skip). queryKey 의존성은 `JSON.stringify` 로 안정화.

- [ ] **Step 3: 목록 페이지 배선** — `DispatchHistoryPage.tsx`

`useDispatchTasksQuery` 사용부 근처에 훅 1줄 추가(list params 와 동일 queryKey `['dispatchTasks', params]` invalidate):
```tsx
import { DispatchTaskRealtimeClient } from '../../realtime/DispatchTaskRealtimeClient'
import { useCollectionRealtime } from '../../realtime/useCollectionRealtime'
// ... 컴포넌트 본문, listQuery 선언 아래:
useCollectionRealtime(DispatchTaskRealtimeClient, 'board', ['dispatchTasks'])
```
> `['dispatchTasks']` prefix invalidate 로 모든 params 변형 목록을 갱신(정찰: mutation 들도 `['dispatchTasks']` prefix invalidate 사용).

- [ ] **Step 4: 훅 단위 테스트** — `useCollectionRealtime.test.ts`

```ts
import { describe, it, expect, vi } from 'vitest'
import type { RealtimeClient } from './createRealtimeClient'

// 순수 로직 검증: mock 모드면 subscribe 미호출 / 아니면 subscribe 호출 후 이벤트 시 invalidate.
// jsdom 미설정 환경이면 훅 렌더 대신 subscribe/abort 계약을 최소 검증(react 훅 런타임 필요 시
// vitest.config environment 를 'jsdom' 로 요구 → 프로젝트가 node 전용이므로, 훅은 통합 QA 에서 실검증하고
// 여기서는 client.subscribe 반환 AbortController.abort 계약만 단위 검증).
describe('useCollectionRealtime 계약', () => {
  it('client.subscribe 는 AbortController 를 반환하고 abort 가능', () => {
    const ctrl = new AbortController()
    const client: RealtimeClient = { subscribe: vi.fn().mockReturnValue(ctrl) }
    const c = client.subscribe('board', () => {})
    expect(c).toBe(ctrl)
    c.abort()
    expect(c.signal.aborted).toBe(true)
  })
})
```
> NOTE: react 훅 자체 렌더 테스트는 jsdom 필요 → 프로젝트 vitest 는 node 환경(정찰). 훅 동작(구독→invalidate)은 **라이브 QA(2세션)** 에서 실검증하고, 단위는 client 계약만. (또는 desktop 에 jsdom 테스트 경로가 있으면 @testing-library 로 훅 렌더 테스트 추가.)

- [ ] **Step 5: FE 검증**

Run: `cd clients/desktop && npm run typecheck && npx vitest run src/renderer/realtime/useCollectionRealtime.test.ts`
Expected: typecheck EXIT 0, 테스트 PASS.

- [ ] **Step 6: 커밋**

```bash
git add clients/desktop/src/renderer/realtime/DispatchTaskRealtimeClient.ts clients/desktop/src/renderer/realtime/useCollectionRealtime.ts clients/desktop/src/renderer/realtime/useCollectionRealtime.test.ts clients/desktop/src/renderer/routes/dispatch-board/DispatchHistoryPage.tsx
git commit -m "feat(dispatch-fe): 배차현황 목록 라이브 동기화 SSE 구독 → invalidate (E2 기둥1)"
```

---

### Task 6: 라이브 QA (2세션 실 SSE round-trip) + 모바일 WebView 검증

**Files:** Create: `docs/qa/e2-live-sync-dispatch/`(실 캡처)

- [ ] **Step 1: Docker 스택 기동** — slip-service 재빌드 포함
```bash
docker compose up -d --build slip-service gateway
```
- [ ] **Step 2: 2세션 데스크탑 라이브 QA**
  - A/B 두 세션 배차현황 목록 오픈(mock OFF, 실 게이트웨이 :8080).
  - A: 배차 생성 → **B 목록에 즉시 신규 행** 실캡처.
  - A: 그룹/전표 추가·재정렬(수정) → **B 반영** 실캡처.
  - A: 상태 전이(DISPATCHING) → **B 반영** 실캡처.
  - 캡처 저장 `docs/qa/e2-live-sync-dispatch/`.
- [ ] **Step 3: 모바일 WebView 반영 확인** — WebView 안 웹 번들 SSE 연결/수신 1컷(미연결 시 P2 명시 + 폴백 후속).
- [ ] **Step 4: dev-report 작성** — `docs/dev-reports/2026-07-02-e2-live-sync-dispatch-pilot.md` (함수 문서화 3-layer).

---

## Self-Review (작성자 체크)
- **Spec 커버리지**: 기둥1(라이브 컬렉션 동기화) = Task 1~6 커버. 기둥2(취소선 삭제+복원)=Plan B, task5(전표확인 미리보기)=Plan C 로 분리 명시(스코프 축소 아님 — 별도 plan). ✅
- **Placeholder**: 게이트웨이 라우트(Task 4)·isMockMode 경로·IT 커밋 검증 방식은 "구현자가 기존 패턴 확인" NOTE 로 명시(코드 원문 제공, 프로젝트-특정 헬퍼만 참조). 순수 신규 코드는 완전 코드 제공. ✅
- **타입 일관성**: `CollectionRealtimePublisher.publishChange(UUID,String,Map)` / `DispatchBoardRealtime.CHANNEL_ID·EVENT_CHANGED` / `createRealtimeClient({name,endpointPath})→{subscribe(entityId,onEvent):AbortController}` — Task 간 일관. ✅
- **주의**: DispatchBoardRealtime.CHANNEL_ID UUID 리터럴은 유효 UUID 여야 함(구현 Step 1 에서 `UUID.fromString` 파싱 확인, 실패 시 `UUID.nameUUIDFromBytes`). 

## 캐논 워크플로우 (각 Task 아님, plan 전체 = 1 PR)
조기 PR(base=main) → Codex 개발 → Opus 5-agent(FE/BE/Design/DevOps/QA) + fix + 게시 → Codex 5-agent + fix + 게시 → 0수렴 → PM 종합 → CI green(slip-service IT + desktop typecheck/vitest + Playwright) → squash 머지. 라이브 QA(Task 6) 필수.
