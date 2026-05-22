# Issue 4 — 통합 알림 센터 설계

> 사용자 보고 (2026-05-22): "우측 상단의 알림은 '안전재고 알림' 외에도 다양한 알림 용도로 사용해야함(종을 누르면 아래로 창을 하나 더 띄워서 여러 알림을 확인할 수 있도록 해야함"
>
> 추가 요구사항: "안전재고뿐 아니라 모든 알림 표시, 단 알림마다 전송 범위가 다르며, 나에게 해당되는 모든 알림은 표시, 알림 확인 후 확인하면 패널에서 사라짐. 알림도 메뉴를 생성하여 나의 지난 알림 내역을 모두 확인할 수 있어야함. 패널 하단에 전체 알림 보기로 이동하거나, 왼쪽 최상단에 알림내역을 넣음"

## 1. 목표

1. AppLayout 우상단 종 → dropdown panel (현재는 안전재고만, 페이지 이동)
2. 다중 알림 채널 통합 (안전재고 + 메신저, 향후 결재/주문/이카운트)
3. 사용자별 필터링 (target_role + target_user_id)
4. read/unread state — acknowledge 시 panel 에서 사라짐
5. 사이드바 "알림 내역" 메뉴 + 전체 알림 history 페이지

## 2. Architecture

### 2.1 도메인 분리

- **notification-service** — 통합 알림 entity + REST API (조회/acknowledge) + 발송 endpoint (single source of truth)
- **알림 source services** (inventory/groupware/accounting/...) — `NotificationPublisher` Spring component 로 event push (fail-soft)
- **FE desktop** — `AppLayout` 의 종 → `NotificationBellDropdown`, 사이드바 "알림 내역" → `NotificationHistoryPage`

### 2.2 데이터 흐름

```
[source services]                     [notification-service]                [FE]
inventory-service                     ┌─────────────────────┐                
  SafetyStockService                  │ notification table  │                
  .checkAndNotify() ─┐                │ (Flyway V12 신규)   │                
                     │                │                     │                
groupware-service    │  POST /internal/notifications        │                
  MessageService     │  (X-Internal-Token + 사용자 헤더)    │                
  .send() ──────────►├───► NotificationPublisher           │                
                     │     → INSERT notification row        │                
accounting-service   │                │                     │                
  (Sprint 7+)        │                └─────────────────────┘                
                     │                          │                            
                     │                          ▼                            
                     │                  GET /notifications/my (unread)       
                     │                  GET /notifications/history (paged)   
                     │                  POST /notifications/{id}/acknowledge ◄── AppLayout
                     │                                                          NotificationBell
                     │                                                            (60s polling)
                     │                                                          NotificationHistoryPage
```

### 2.3 사용자별 필터링

`notification` row 의 (`target_role`, `target_user_id`) 조합:

| target_role | target_user_id | 노출 대상 |
|---|---|---|
| `MASTER` | NULL | 모든 MASTER role 사용자 |
| `MANAGER,INVENTORY` (CSV) | NULL | MANAGER 또는 INVENTORY role 사용자 |
| NULL | `<UUID>` | 특정 사용자만 (메신저 receiver) |
| `*` | NULL | 모든 인증 사용자 |

API 호출 시 X-User-Id + X-User-Role 헤더 기반 자동 필터.

## 3. Components

### 3.1 notification-service (신규 도메인)

```
notification-service/
├─ domain/
│  └─ Notification.java          @Entity (BaseEntity 7 audit + read_at)
├─ repository/
│  └─ NotificationRepository.java
├─ service/
│  ├─ NotificationService.java   조회/acknowledge
│  └─ NotificationPublishService.java   internal POST 처리
├─ web/
│  ├─ NotificationController.java        GET /notifications/my,
│  │                                     /history, POST /{id}/acknowledge
│  ├─ NotificationInternalController.java POST /internal/notifications
│  └─ dto/
│     ├─ NotificationResponse.java
│     ├─ NotificationPublishRequest.java
│     └─ NotificationHistoryPageResponse.java
└─ resources/db/migration/
   └─ V12__create_notification.sql
```

#### Notification entity 컬럼 (Flyway V12)

```sql
CREATE TABLE notification (
    id               UUID PRIMARY KEY,
    channel          VARCHAR(32)  NOT NULL,   -- 'SAFETY_STOCK' | 'MESSENGER' | 'APPROVAL' | ...
    severity         VARCHAR(16)  NOT NULL,   -- 'INFO' | 'WARNING' | 'CRITICAL'
    title            VARCHAR(200) NOT NULL,
    body             TEXT,
    target_role      VARCHAR(200),            -- CSV (e.g. 'MASTER,MANAGER'), NULL = 모든 role
    target_user_id   UUID,                    -- NULL = role-based, else 특정 사용자
    source_service   VARCHAR(32)  NOT NULL,   -- 'inventory-service' | 'groupware-service' | ...
    source_ref_id    VARCHAR(100),            -- 알림 source 식별자 (productId+warehouseId, messageId 등)
    deeplink         VARCHAR(500),            -- FE 가 클릭 시 이동할 라우트 (예: '/inventory/safety-stock-alerts')
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(50) NOT NULL DEFAULT 'system',
    modified_at      TIMESTAMP,
    modified_by      VARCHAR(50),
    read_at          TIMESTAMP,               -- NULL = 미확인, else acknowledge 시점
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(50),
    is_deleted       BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notification_target_role_unread ON notification(target_role, read_at) WHERE is_deleted = FALSE;
CREATE INDEX idx_notification_target_user_unread ON notification(target_user_id, read_at) WHERE is_deleted = FALSE;
CREATE INDEX idx_notification_source_ref ON notification(source_service, source_ref_id, channel);
```

`source_service + source_ref_id + channel` UNIQUE 제약 X — 같은 출처에서 여러 알림 가능 (예: 안전재고 동일 product 가 다시 부족할 때 새 row, 이전은 read 처리되어 history 잔존).

### 3.2 source service 의 `NotificationPublisher`

`shared:common` 또는 신규 `shared:notification-client` 모듈에 공용 컴포넌트:

```java
@Component
@RequiredArgsConstructor
public class NotificationPublisher {
    private final RestClient.Builder lbBuilder;
    private final InternalAuthProperties auth;
    private final String callerServiceName;  // ${spring.application.name}

    public void publish(NotificationPublishRequest req) {
        try {
            restClient.post()
                .uri("/internal/notifications")
                .header("X-Internal-Token", auth.getToken())
                .body(req.withSourceService(callerServiceName))
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException ex) {
            log.warn("NotificationPublisher fail-soft — channel={} ref={}: {}",
                    req.channel(), req.sourceRefId(), ex.getMessage());
            // 트랜잭션 영향 X (alert 누락은 운영 모니터링 책임)
        }
    }
}
```

### 3.3 FE 컴포넌트

```
clients/desktop/src/renderer/
├─ api/
│  └─ notificationApi.ts            (신규)
├─ components/
│  ├─ AppLayout.tsx                 (수정: NotificationBell 통합)
│  └─ NotificationBellDropdown.tsx  (신규)
└─ routes/
   └─ NotificationHistoryPage.tsx   (신규)
```

#### `NotificationBellDropdown` 패턴

채널별 grouping (확장 가능):

```
┌─ 알림 ────────────────────┐
│ ⚠ 안전재고 (3건)          │
│   AJ056RXH4BC1 HQ 부족 -7 │
│   AM100RXMDH HQ 부족 -3   │
│   ... [더보기 →]          │
├──────────────────────────┤
│ 💬 메신저 (5건)           │
│   김미선 — 김종 압축...   │
│   장영구 — 견적 요청...   │
│   ... [더보기 →]          │
├──────────────────────────┤
│ [전체 알림 보기 →]        │
│ [모두 읽음으로 표시]      │
└──────────────────────────┘
```

각 row 클릭 시:
1. `POST /notifications/{id}/acknowledge` 호출
2. `deeplink` 로 navigate (예: 안전재고 → `/inventory/safety-stock-alerts`, 메신저 → `/messenger/rooms/{roomId}`)

### 3.4 사이드바 "알림 내역" 메뉴

`AppLayout.tsx` 사이드바 최상단 (대시보드 다음):

```tsx
<NavLink to="/notifications" data-testid="sidebar-notifications">
  알림 내역
</NavLink>
```

`NotificationHistoryPage` — Page<NotificationResponse> 페이지네이션 50/100/200/500, 채널/severity/read 상태 필터.

## 4. Slice 분할 (Sprint 5/6/7)

본 design 은 1개 Sprint 에 한 번에 들어가지 못함. 분할:

### Slice 1 (Sprint 5) — BE 도메인 신규

- notification-service `Notification` entity + Repository + Service + Controller (admin + internal)
- Flyway V12
- 단위 test 8건 + IT 5건 (publish + my unread + history paged + acknowledge + role filter)
- 기존 source 영향 0 (별도 도메인)

**산출**: notification-service `/notifications/my`, `/notifications/history`, `/notifications/{id}/acknowledge`, `/internal/notifications` REST API 4종.

### Slice 2 (Sprint 6) — FE UI

- `notificationApi.ts` 신규
- `AppLayout` 알림 종 → `NotificationBellDropdown` 통합 (기존 안전재고 chip 제거)
- 사이드바 "알림 내역" 메뉴 추가
- `NotificationHistoryPage` 신규 (Pageable + 필터)
- Playwright fixture 1건

**산출**: FE 가 새 BE endpoint 호출. 단, 알림 source 아직 push 안 함 → panel 은 비어있음.

### Slice 3 (Sprint 7) — 알림 source 통합

- shared 또는 신규 `shared:notification-client` 모듈 + `NotificationPublisher`
- inventory-service `SafetyStockService.checkAndNotify` → `NotificationPublisher.publish` (channel='SAFETY_STOCK', target_role='MASTER,MANAGER,INVENTORY,WAREHOUSE', deeplink='/inventory/safety-stock-alerts')
- groupware-service `MessageService.send` → `NotificationPublisher.publish` (channel='MESSENGER', target_user_id=receiverId, deeplink='/messenger/rooms/{roomId}')
- 기존 안전재고 alert endpoint (`/inventory/alerts/safety-stock`) 은 deprecated 표시 (하위 호환)

**산출**: 실제 알림 발송. AppLayout 종 배지 정상 작동.

## 5. Error handling

- `NotificationPublisher.publish()` fail-soft — source 트랜잭션 영향 0
- `NotificationService.findMy()` — DB 조회 실패 시 빈 list (panel 빈 표시)
- `acknowledge()` — 이미 읽음 처리된 알림 재호출 시 idempotent (read_at 갱신 안 함)
- `target_role` CSV parse 실패 시 해당 row skip + warn log

## 6. Testing

### Slice 1 (BE)
- 단위 test: `NotificationServiceTest` 8건 (findMy role filter + userId filter + read 제외, acknowledge idempotent, publish dedupe)
- IT: `NotificationControllerIT` 5건 (auth + paged history + acknowledge + role 필터 통과)

### Slice 2 (FE)
- TypeScript typecheck pass
- `NotificationBellDropdown` 시각 회귀 Playwright (mock fixture)
- `NotificationHistoryPage` 페이지네이션/필터 e2e

### Slice 3 (source integration)
- `SafetyStockServiceTest` 보강 — `NotificationPublisher` mock + publish 호출 검증
- `MessageServiceTest` 동일
- `feedback_qa_docker_real_test` — 사용자 dev_master 실 검증 (안전재고 충분히 부족 시 종 배지 + panel 표시)

## 7. 권한 매트릭스

| Endpoint | Role |
|---|---|
| `GET /notifications/my` | 모든 인증 사용자 (X-User-Id 기반 자동 필터) |
| `GET /notifications/history` | 모든 인증 사용자 (자기 알림만 조회) |
| `POST /notifications/{id}/acknowledge` | 자기 알림만 |
| `POST /internal/notifications` | X-Internal-Token (service-to-service) |

## 8. 메모리 가드 준수

- `feedback_korean_commits`: 한국어 commit/PR
- `feedback_uuid_no_user_visibility`: FE 화면에 UUID 노출 X (productCode/displayName 등 비즈니스 식별자)
- `feedback_it_mockbean_external_clients`: source service IT 의 NotificationPublisher mock
- `feedback_dual_5agent_review`: 각 Slice 별 5-team review + Codex review + Claude verify

## 9. 잔여 결정 (Sprint 5+ 진행 중)

- shared 모듈 vs 신규 모듈 — `NotificationPublisher` 위치
- 메신저 alert 일괄 발송 패턴 — 1:1 vs 1:N (그룹챗)
- 알림 source 의 multi-target (예: 동일 부족 product 가 MANAGER + INVENTORY 모두 → 1 row CSV vs 2 row 복제)
- Severity 색상 / 아이콘 디자인 (Designer brainstorming, Slice 2 진입 전)
