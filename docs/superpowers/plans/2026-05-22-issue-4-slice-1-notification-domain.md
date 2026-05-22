# Issue 4 Slice 1 — 통합 알림 도메인 BE Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** notification-service 에 통합 알림 도메인 (Notification entity + REST API 4종 + IT) 신규 추가. 알림 source services 변경 없음.

**Architecture:** Spring Boot 3 / JPA + Flyway. notification-service 내부에 도메인 추가. `Notification` entity (BaseEntity 7 audit + read_at). 4 REST endpoint (조회/acknowledge/internal publish). `target_role` CSV + `target_user_id` UUID 로 사용자별 필터.

**Tech Stack:** Spring Boot 3.3.5, Spring Data JPA, Flyway 10, PostgreSQL, JUnit 5, Mockito, MockMvc + Testcontainers (IT)

**Spec:** [`docs/superpowers/specs/2026-05-22-issue-4-unified-notification-center-design.md`](../specs/2026-05-22-issue-4-unified-notification-center-design.md) Slice 1

---

## File Structure

### Create
- `services/notification-service/src/main/resources/db/migration/V5__create_notification.sql` — Flyway V5 (V4 다음)
- `services/notification-service/src/main/java/com/samhanair/logis/notification/domain/Notification.java` — Entity
- `services/notification-service/src/main/java/com/samhanair/logis/notification/domain/NotificationSeverity.java` — enum (INFO/WARNING/CRITICAL)
- `services/notification-service/src/main/java/com/samhanair/logis/notification/repository/NotificationCenterRepository.java` — Spring Data
- `services/notification-service/src/main/java/com/samhanair/logis/notification/service/NotificationCenterService.java` — 비즈니스
- `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationCenterResponse.java`
- `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationPublishRequest.java`
- `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationCenterPage.java`
- `services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterController.java` — `/notifications/my`, `/history`, `/{id}/acknowledge`
- `services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterInternalController.java` — `/internal/notifications`
- `services/notification-service/src/test/java/com/samhanair/logis/notification/service/NotificationCenterServiceTest.java` — 단위 8건
- `services/notification-service/src/test/java/com/samhanair/logis/notification/it/NotificationCenterControllerIT.java` — IT 5건

### Modify
- `services/notification-service/src/main/java/com/samhanair/logis/notification/config/SecurityConfig.java` — `/notifications/**` + `/internal/notifications/**` matcher
- `services/api-gateway/src/main/resources/application.yml` — route `/api/notifications/**` + `/api/v1/notifications/**` 추가

### Naming 주의
기존 `NotificationLog`, `NotificationRequest` 등이 이미 존재 (전송 이력 도메인). 본 신규는 사용자 알림 센터이므로 클래스명에 `Center` 접두/접미사 사용 → `Notification` 이라는 단순명을 피하고 `NotificationCenter*` 사용.

---

## Task 1: Flyway V5 — notification 테이블 + index

**Files:**
- Create: `services/notification-service/src/main/resources/db/migration/V5__create_notification.sql`

- [ ] **Step 1: SQL 작성**

```sql
-- V5__create_notification.sql
-- Issue 4 통합 알림 센터 — Slice 1 (2026-05-22)
-- 사용자 알림 (multi-channel) 단일 entity. NotificationLog (게이트웨이 발송 이력) 와 별개.

CREATE TABLE notification_center (
    id               UUID         PRIMARY KEY,
    channel          VARCHAR(32)  NOT NULL,
    severity         VARCHAR(16)  NOT NULL,
    title            VARCHAR(200) NOT NULL,
    body             TEXT,
    target_role      VARCHAR(200),
    target_user_id   UUID,
    source_service   VARCHAR(64)  NOT NULL,
    source_ref_id    VARCHAR(200),
    deeplink         VARCHAR(500),
    read_at          TIMESTAMP,

    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(50)  NOT NULL DEFAULT 'system',
    modified_at      TIMESTAMP,
    modified_by      VARCHAR(50),
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(50),
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_notification_center_target_role_unread
    ON notification_center(target_role, read_at)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_notification_center_target_user_unread
    ON notification_center(target_user_id, read_at)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_notification_center_source_ref
    ON notification_center(source_service, source_ref_id, channel);

CREATE INDEX idx_notification_center_created_at
    ON notification_center(created_at DESC)
    WHERE is_deleted = FALSE;
```

- [ ] **Step 2: 컴파일 검증 (Flyway dry-run X — 실 DB 마이그레이션은 Task 8 IT 에서 검증)**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL (SQL 파일은 compileJava 영향 없으므로 이전 build cache 영향만)

- [ ] **Step 3: Commit**

```bash
git add services/notification-service/src/main/resources/db/migration/V5__create_notification.sql
git commit -m "feat(notification): Slice 1 Task 1 — Flyway V5 notification_center 테이블 + 4 index"
```

---

## Task 2: NotificationSeverity enum + Notification entity

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/domain/NotificationSeverity.java`
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/domain/NotificationCenter.java`

- [ ] **Step 1: NotificationSeverity enum 작성**

```java
package com.samhanair.logis.notification.domain;

/** 알림 심각도 — UI 아이콘/색상 매핑 키. */
public enum NotificationSeverity {
    INFO,
    WARNING,
    CRITICAL
}
```

- [ ] **Step 2: NotificationCenter entity 작성**

```java
package com.samhanair.logis.notification.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 사용자 통합 알림 (Issue 4 Slice 1).
 *
 * <p>NotificationLog (게이트웨이 발송 이력) 와 별개의 도메인 — 사용자 화면 알림.
 * target_role CSV (e.g. "MASTER,MANAGER") + target_user_id UUID 조합으로 노출 대상 결정.
 * read_at NULL = 미확인, NOT NULL = acknowledge 시점.
 */
@Entity
@Getter
@Table(name = "notification_center")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class NotificationCenter extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "channel", nullable = false, length = 32, updatable = false)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16, updatable = false)
    private NotificationSeverity severity;

    @Column(name = "title", nullable = false, length = 200, updatable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT", updatable = false)
    private String body;

    @Column(name = "target_role", length = 200, updatable = false)
    private String targetRole;

    @Column(name = "target_user_id", updatable = false)
    private UUID targetUserId;

    @Column(name = "source_service", nullable = false, length = 64, updatable = false)
    private String sourceService;

    @Column(name = "source_ref_id", length = 200, updatable = false)
    private String sourceRefId;

    @Column(name = "deeplink", length = 500, updatable = false)
    private String deeplink;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static NotificationCenter publish(String channel, NotificationSeverity severity,
                                             String title, String body,
                                             String targetRole, UUID targetUserId,
                                             String sourceService, String sourceRefId,
                                             String deeplink) {
        NotificationCenter n = new NotificationCenter();
        n.channel = channel;
        n.severity = severity;
        n.title = title;
        n.body = body;
        n.targetRole = targetRole;
        n.targetUserId = targetUserId;
        n.sourceService = sourceService;
        n.sourceRefId = sourceRefId;
        n.deeplink = deeplink;
        return n;
    }

    public void acknowledge(LocalDateTime when) {
        if (this.readAt == null) {
            this.readAt = when;
        }
    }
}
```

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/domain/NotificationSeverity.java services/notification-service/src/main/java/com/samhanair/logis/notification/domain/NotificationCenter.java
git commit -m "feat(notification): Slice 1 Task 2 — NotificationCenter entity + NotificationSeverity enum"
```

---

## Task 3: NotificationCenterRepository

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/repository/NotificationCenterRepository.java`

- [ ] **Step 1: Repository 작성**

```java
package com.samhanair.logis.notification.repository;

import com.samhanair.logis.notification.domain.NotificationCenter;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * NotificationCenter 조회.
 *
 * <p>target_role CSV / target_user_id UUID 조합 필터. role 매칭은 PostgreSQL 의 `string_to_array`
 * + ANY 패턴으로 처리한다.
 */
public interface NotificationCenterRepository extends JpaRepository<NotificationCenter, UUID> {

    /**
     * 사용자 미확인 알림 (read_at IS NULL) 조회. 최신순.
     * (target_role 에 role 이 포함되거나, target_user_id = userId) 조합.
     */
    @Query(value = """
            SELECT n.* FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND n.read_at IS NULL
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            ORDER BY n.created_at DESC
            """, nativeQuery = true)
    List<NotificationCenter> findMyUnread(@Param("userId") UUID userId, @Param("role") String role);

    /**
     * 사용자 전체 알림 history (read_at 무관). 페이지네이션.
     */
    @Query(value = """
            SELECT n.* FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            ORDER BY n.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM notification_center n
            WHERE n.is_deleted = FALSE
              AND (
                   n.target_user_id = :userId
                OR (n.target_role IS NOT NULL
                    AND :role = ANY(string_to_array(n.target_role, ',')))
              )
            """,
            nativeQuery = true)
    Page<NotificationCenter> findMyHistory(@Param("userId") UUID userId, @Param("role") String role, Pageable pageable);
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/repository/NotificationCenterRepository.java
git commit -m "feat(notification): Slice 1 Task 3 — NotificationCenterRepository (native query role CSV filter)"
```

---

## Task 4: NotificationPublishRequest + NotificationCenterResponse + NotificationCenterPage DTO

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationPublishRequest.java`
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationCenterResponse.java`
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/NotificationCenterPage.java`

- [ ] **Step 1: NotificationPublishRequest 작성**

```java
package com.samhanair.logis.notification.web.dto;

import com.samhanair.logis.notification.domain.NotificationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * `/internal/notifications` 발송 요청 body — source service 가 호출.
 *
 * @param channel        알림 채널 키 ('SAFETY_STOCK' / 'MESSENGER' / 'APPROVAL' 등)
 * @param severity       심각도 (INFO/WARNING/CRITICAL)
 * @param title          알림 제목 (200자 이내)
 * @param body           본문 (TEXT)
 * @param targetRole     role CSV (예: "MASTER,MANAGER"), null/blank 면 role 필터 미적용
 * @param targetUserId   특정 사용자 UUID, null 면 role 기반
 * @param sourceService  발송 service 명 (기록용)
 * @param sourceRefId    source 식별자 (예: productId+warehouseId, messageId)
 * @param deeplink       FE 가 클릭 시 이동할 라우트
 */
public record NotificationPublishRequest(
        @NotBlank String channel,
        @NotNull NotificationSeverity severity,
        @NotBlank String title,
        String body,
        String targetRole,
        UUID targetUserId,
        @NotBlank String sourceService,
        String sourceRefId,
        String deeplink
) {
}
```

- [ ] **Step 2: NotificationCenterResponse 작성**

```java
package com.samhanair.logis.notification.web.dto;

import com.samhanair.logis.notification.domain.NotificationCenter;
import com.samhanair.logis.notification.domain.NotificationSeverity;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationCenterResponse(
        UUID id,
        String channel,
        NotificationSeverity severity,
        String title,
        String body,
        String deeplink,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {
    public static NotificationCenterResponse from(NotificationCenter n) {
        return new NotificationCenterResponse(
                n.getId(),
                n.getChannel(),
                n.getSeverity(),
                n.getTitle(),
                n.getBody(),
                n.getDeeplink(),
                n.getCreatedAt(),
                n.getReadAt()
        );
    }
}
```

- [ ] **Step 3: NotificationCenterPage 작성**

```java
package com.samhanair.logis.notification.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지네이션 응답 — number 0-base, totalElements/totalPages 표준.
 */
public record NotificationCenterPage(
        List<NotificationCenterResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages
) {
    public static NotificationCenterPage from(Page<NotificationCenterResponse> page) {
        return new NotificationCenterPage(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
```

- [ ] **Step 4: 컴파일 검증**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/web/dto/
git commit -m "feat(notification): Slice 1 Task 4 — NotificationCenter DTO 3종 (PublishRequest, Response, Page)"
```

---

## Task 5: NotificationCenterService — 단위 test 8건 RED → GREEN

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/service/NotificationCenterService.java`
- Create: `services/notification-service/src/test/java/com/samhanair/logis/notification/service/NotificationCenterServiceTest.java`

- [ ] **Step 1: 단위 test 작성 (RED)**

```java
package com.samhanair.logis.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.notification.domain.NotificationCenter;
import com.samhanair.logis.notification.domain.NotificationSeverity;
import com.samhanair.logis.notification.repository.NotificationCenterRepository;
import com.samhanair.logis.notification.web.dto.NotificationCenterPage;
import com.samhanair.logis.notification.web.dto.NotificationCenterResponse;
import com.samhanair.logis.notification.web.dto.NotificationPublishRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NotificationCenterServiceTest {

    @Mock private NotificationCenterRepository repository;

    @InjectMocks private NotificationCenterService service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("publish: 신규 알림 row INSERT 후 ID 반환")
    void publish_createsNotification() {
        NotificationPublishRequest req = new NotificationPublishRequest(
                "SAFETY_STOCK", NotificationSeverity.WARNING,
                "AJ056 부족", "현재 30 / 임계 50",
                "MASTER,MANAGER", null,
                "inventory-service", "product-1+warehouse-A",
                "/inventory/safety-stock-alerts");

        NotificationCenter saved = NotificationCenter.publish(
                req.channel(), req.severity(), req.title(), req.body(),
                req.targetRole(), req.targetUserId(), req.sourceService(),
                req.sourceRefId(), req.deeplink());
        when(repository.save(any(NotificationCenter.class))).thenReturn(saved);

        UUID id = service.publish(req);

        assertThat(id).isNotNull();
        verify(repository).save(any(NotificationCenter.class));
    }

    @Test
    @DisplayName("findMyUnread: role + userId 조합으로 조회")
    void findMyUnread_callsRepositoryWithRoleAndUserId() {
        when(repository.findMyUnread(userId, "MASTER"))
                .thenReturn(List.of(stubNotification()));

        List<NotificationCenterResponse> result = service.findMyUnread(userId, "MASTER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).channel()).isEqualTo("SAFETY_STOCK");
    }

    @Test
    @DisplayName("findMyUnread: 조회 결과 0건 시 빈 list")
    void findMyUnread_emptyResult_returnsEmptyList() {
        when(repository.findMyUnread(userId, "SALES")).thenReturn(List.of());

        List<NotificationCenterResponse> result = service.findMyUnread(userId, "SALES");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findMyHistory: pageable 전달 + page response 매핑")
    void findMyHistory_returnsPageResponse() {
        NotificationCenter n = stubNotification();
        Page<NotificationCenter> page = new PageImpl<>(List.of(n), PageRequest.of(0, 50), 1);
        when(repository.findMyHistory(eq(userId), eq("MASTER"), any())).thenReturn(page);

        NotificationCenterPage response = service.findMyHistory(userId, "MASTER", PageRequest.of(0, 50));

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).id()).isEqualTo(n.getId());
    }

    @Test
    @DisplayName("acknowledge: 미확인 알림 → read_at 설정 + save")
    void acknowledge_unreadNotification_setsReadAt() {
        NotificationCenter n = stubNotification();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));
        when(repository.save(n)).thenReturn(n);

        service.acknowledge(n.getId(), userId, "MASTER");

        assertThat(n.getReadAt()).isNotNull();
        verify(repository).save(n);
    }

    @Test
    @DisplayName("acknowledge: 이미 확인된 알림 → idempotent (save 호출 X)")
    void acknowledge_alreadyRead_isIdempotent() {
        NotificationCenter n = stubNotification();
        n.acknowledge(LocalDateTime.now().minusHours(1));
        LocalDateTime originalReadAt = n.getReadAt();
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.acknowledge(n.getId(), userId, "MASTER");

        assertThat(n.getReadAt()).isEqualTo(originalReadAt);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("acknowledge: 권한 없는 알림 → FORBIDDEN")
    void acknowledge_notMyNotification_throwsForbidden() {
        NotificationCenter n = NotificationCenter.publish(
                "MESSENGER", NotificationSeverity.INFO,
                "메시지", "내용",
                null, UUID.randomUUID(),  // 다른 사용자에게만 노출
                "groupware-service", "msg-1", "/messenger");
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.acknowledge(n.getId(), userId, "SALES"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("acknowledge: 존재하지 않는 ID → NOT_FOUND")
    void acknowledge_unknownId_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(unknown, userId, "MASTER"))
                .isInstanceOf(BusinessException.class);
    }

    private NotificationCenter stubNotification() {
        return NotificationCenter.publish(
                "SAFETY_STOCK", NotificationSeverity.WARNING,
                "AJ056 부족", "현재 30 / 임계 50",
                "MASTER,MANAGER", null,
                "inventory-service", "product-1", "/inventory/safety-stock-alerts");
    }
}
```

- [ ] **Step 2: test RED 확인 — Service 미존재로 컴파일 fail**

Run: `./gradlew :services:notification-service:compileTestJava --no-daemon`
Expected: BUILD FAILED — `NotificationCenterService` 클래스 없음

- [ ] **Step 3: NotificationCenterService 구현 (GREEN)**

```java
package com.samhanair.logis.notification.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.notification.domain.NotificationCenter;
import com.samhanair.logis.notification.repository.NotificationCenterRepository;
import com.samhanair.logis.notification.web.dto.NotificationCenterPage;
import com.samhanair.logis.notification.web.dto.NotificationCenterResponse;
import com.samhanair.logis.notification.web.dto.NotificationPublishRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCenterService {

    private final NotificationCenterRepository repository;

    /**
     * 알림 발송 — source service 가 /internal/notifications 호출 시 진입.
     */
    @Transactional
    public UUID publish(NotificationPublishRequest req) {
        NotificationCenter n = NotificationCenter.publish(
                req.channel(), req.severity(), req.title(), req.body(),
                req.targetRole(), req.targetUserId(),
                req.sourceService(), req.sourceRefId(), req.deeplink());
        NotificationCenter saved = repository.save(n);
        return saved.getId();
    }

    /**
     * 사용자 미확인 알림 (read_at IS NULL) 최신순.
     */
    @Transactional(readOnly = true)
    public List<NotificationCenterResponse> findMyUnread(UUID userId, String role) {
        return repository.findMyUnread(userId, role).stream()
                .map(NotificationCenterResponse::from)
                .toList();
    }

    /**
     * 사용자 전체 history (read 무관) — pageable.
     */
    @Transactional(readOnly = true)
    public NotificationCenterPage findMyHistory(UUID userId, String role, Pageable pageable) {
        Page<NotificationCenter> page = repository.findMyHistory(userId, role, pageable);
        return NotificationCenterPage.from(page.map(NotificationCenterResponse::from));
    }

    /**
     * 알림 acknowledge — read_at 설정. 이미 read 면 idempotent. 권한 미보유 시 FORBIDDEN.
     */
    @Transactional
    public void acknowledge(UUID notificationId, UUID userId, String role) {
        NotificationCenter n = repository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다"));

        if (!canAccess(n, userId, role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 알림이 아닙니다");
        }

        if (n.getReadAt() == null) {
            n.acknowledge(LocalDateTime.now());
            repository.save(n);
        }
    }

    private boolean canAccess(NotificationCenter n, UUID userId, String role) {
        if (userId != null && userId.equals(n.getTargetUserId())) {
            return true;
        }
        if (n.getTargetRole() != null && !n.getTargetRole().isBlank()) {
            List<String> roles = Arrays.stream(n.getTargetRole().split(","))
                    .map(String::trim)
                    .toList();
            return roles.contains(role);
        }
        return false;
    }
}
```

- [ ] **Step 4: test GREEN 확인**

Run: `./gradlew :services:notification-service:test --tests NotificationCenterServiceTest --no-daemon`
Expected: 8 tests PASS

- [ ] **Step 5: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/service/NotificationCenterService.java services/notification-service/src/test/java/com/samhanair/logis/notification/service/NotificationCenterServiceTest.java
git commit -m "feat(notification): Slice 1 Task 5 — NotificationCenterService + 단위 test 8건 (TDD GREEN)"
```

---

## Task 6: NotificationCenterController (admin REST 3종)

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterController.java`

- [ ] **Step 1: Controller 작성**

```java
package com.samhanair.logis.notification.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.notification.service.NotificationCenterService;
import com.samhanair.logis.notification.web.dto.NotificationCenterPage;
import com.samhanair.logis.notification.web.dto.NotificationCenterResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 통합 알림 센터 — Issue 4 Slice 1.
 *
 * <p>X-User-Id + X-User-Role 헤더 기반 자동 필터.
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Issue 4 — 통합 알림 센터")
public class NotificationCenterController {

    private final NotificationCenterService service;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "내 미확인 알림 목록")
    public ApiResponse<List<NotificationCenterResponse>> findMyUnread(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        return ApiResponse.ok(service.findMyUnread(userId, role));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "내 전체 알림 history (paged)")
    public ApiResponse<NotificationCenterPage> findMyHistory(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiResponse.ok(service.findMyHistory(userId, role, pageable));
    }

    @PostMapping("/{id}/acknowledge")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "알림 확인 처리 (read_at 설정)")
    public ApiResponse<Void> acknowledge(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role) {
        service.acknowledge(id, userId, role);
        return ApiResponse.ok(null);
    }
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterController.java
git commit -m "feat(notification): Slice 1 Task 6 — NotificationCenterController (GET /my, /history, POST /{id}/acknowledge)"
```

---

## Task 7: NotificationCenterInternalController + Security Config

**Files:**
- Create: `services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterInternalController.java`
- Modify: `services/notification-service/src/main/java/com/samhanair/logis/notification/config/SecurityConfig.java`

- [ ] **Step 1: NotificationCenterInternalController 작성**

```java
package com.samhanair.logis.notification.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.notification.service.NotificationCenterService;
import com.samhanair.logis.notification.web.dto.NotificationPublishRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal 알림 발송 endpoint — source service 가 X-Internal-Token 헤더로 호출.
 *
 * <p>InternalTokenFilter (path-prefix=/internal/) 가 X-Internal-Token 검증 + ROLE_MASTER 부여.
 */
@RestController
@RequestMapping("/internal/notifications")
@RequiredArgsConstructor
@Tag(name = "Issue 4 — 통합 알림 센터 (Internal)")
public class NotificationCenterInternalController {

    private final NotificationCenterService service;

    @PostMapping
    @Operation(summary = "알림 발송 (source service 호출용)")
    public ApiResponse<UUID> publish(@Valid @RequestBody NotificationPublishRequest req) {
        UUID id = service.publish(req);
        return ApiResponse.ok(id);
    }
}
```

- [ ] **Step 2: SecurityConfig 의 requestMatchers 확인 + 필요 시 수정**

기존 notification-service `SecurityConfig` 가 `/internal/**` 와 `/notifications/**` 둘 다 인증 적용하는지 확인:

Run: `cat services/notification-service/src/main/java/com/samhanair/logis/notification/config/SecurityConfig.java`

Expected: `.anyRequest().authenticated()` 패턴 — 별도 수정 불요. `app.security.internal.path-prefix=/internal/` 가 application.yml 에 설정되어 있어야 InternalTokenFilter 자동 적용.

- [ ] **Step 3: 컴파일 검증**

Run: `./gradlew :services:notification-service:compileJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add services/notification-service/src/main/java/com/samhanair/logis/notification/web/NotificationCenterInternalController.java
git commit -m "feat(notification): Slice 1 Task 7 — NotificationCenterInternalController (POST /internal/notifications)"
```

---

## Task 8: NotificationCenterControllerIT (IT 5건)

**Files:**
- Create: `services/notification-service/src/test/java/com/samhanair/logis/notification/it/NotificationCenterControllerIT.java`

- [ ] **Step 1: IT 작성**

```java
package com.samhanair.logis.notification.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.notification.domain.NotificationCenter;
import com.samhanair.logis.notification.domain.NotificationSeverity;
import com.samhanair.logis.notification.repository.NotificationCenterRepository;
import com.samhanair.logis.notification.web.dto.NotificationPublishRequest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationCenterControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationCenterRepository repository;
    @Autowired private ObjectMapper objectMapper;

    private UUID masterUserId;

    @BeforeEach
    void setUp() {
        masterUserId = UUID.randomUUID();
        repository.deleteAll();
    }

    @Test
    @DisplayName("POST /internal/notifications — X-Internal-Token 으로 알림 INSERT + ID 반환")
    void publish_internalToken_inserts() throws Exception {
        NotificationPublishRequest req = new NotificationPublishRequest(
                "SAFETY_STOCK", NotificationSeverity.WARNING,
                "AJ056 부족", null,
                "MASTER,MANAGER", null,
                "inventory-service", "product-1+wh-A",
                "/inventory/safety-stock-alerts");

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", "dev-internal-token-change-me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }

    @Test
    @DisplayName("GET /notifications/my — MASTER role 알림만 노출 (다른 role row 제외)")
    void findMyUnread_filtersByRole() throws Exception {
        repository.save(NotificationCenter.publish(
                "SAFETY_STOCK", NotificationSeverity.WARNING,
                "MASTER 대상", null,
                "MASTER", null,
                "inventory-service", "ref-1", null));
        repository.save(NotificationCenter.publish(
                "MESSENGER", NotificationSeverity.INFO,
                "SALES 대상", null,
                "SALES", null,
                "groupware-service", "ref-2", null));

        mockMvc.perform(get("/notifications/my")
                        .header("X-User-Id", masterUserId.toString())
                        .header("X-User-Role", "MASTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[0].title").value("MASTER 대상"));
    }

    @Test
    @DisplayName("GET /notifications/my — target_user_id 매칭 row 도 포함")
    void findMyUnread_includesUserIdMatch() throws Exception {
        repository.save(NotificationCenter.publish(
                "MESSENGER", NotificationSeverity.INFO,
                "메시지", null,
                null, masterUserId,
                "groupware-service", "msg-1", null));

        mockMvc.perform(get("/notifications/my")
                        .header("X-User-Id", masterUserId.toString())
                        .header("X-User-Role", "SALES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("POST /notifications/{id}/acknowledge — read_at 설정 + 두 번째 호출 idempotent")
    void acknowledge_idempotent() throws Exception {
        NotificationCenter saved = repository.save(NotificationCenter.publish(
                "SAFETY_STOCK", NotificationSeverity.WARNING,
                "test", null,
                "MASTER", null,
                "inventory-service", "ref-1", null));

        mockMvc.perform(post("/notifications/{id}/acknowledge", saved.getId())
                        .header("X-User-Id", masterUserId.toString())
                        .header("X-User-Role", "MASTER"))
                .andExpect(status().isOk());

        // 두 번째 호출도 200 (idempotent)
        mockMvc.perform(post("/notifications/{id}/acknowledge", saved.getId())
                        .header("X-User-Id", masterUserId.toString())
                        .header("X-User-Role", "MASTER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /notifications/history — Pageable 적용 + page response")
    void findMyHistory_pagedResponse() throws Exception {
        for (int i = 0; i < 5; i++) {
            repository.save(NotificationCenter.publish(
                    "SAFETY_STOCK", NotificationSeverity.WARNING,
                    "title " + i, null,
                    "MASTER", null,
                    "inventory-service", "ref-" + i, null));
        }

        mockMvc.perform(get("/notifications/history")
                        .header("X-User-Id", masterUserId.toString())
                        .header("X-User-Role", "MASTER")
                        .param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(3)));
    }
}
```

- [ ] **Step 2: IT 컴파일 검증**

Run: `./gradlew :services:notification-service:compileTestJava --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: IT 실행 (Docker 가용 환경)**

Run: `./gradlew :services:notification-service:test --tests NotificationCenterControllerIT --no-daemon`
Expected: 5 tests PASS (Docker 미가용 시 skip — `AbstractPostgresIT` 패턴 적용 검토 필요)

> **참고**: notification-service 의 기존 IT 가 Testcontainers 패턴 사용 시 `AbstractPostgresIT` extend 추가 + DockerAvailableCondition 적용. 본 plan 의 IT 는 `@SpringBootTest + @ActiveProfiles("test")` 기본 패턴. 실 IT 실행 환경에서 검증.

- [ ] **Step 4: Commit**

```bash
git add services/notification-service/src/test/java/com/samhanair/logis/notification/it/NotificationCenterControllerIT.java
git commit -m "test(notification): Slice 1 Task 8 — NotificationCenterControllerIT 5건 (publish, role filter, userId filter, acknowledge idempotent, paged history)"
```

---

## Task 9: api-gateway route + PR

**Files:**
- Modify: `services/api-gateway/src/main/resources/application.yml`

- [ ] **Step 1: gateway route 추가**

`application.yml` 의 `routes:` list 에 notification-service 라우트가 이미 있는지 확인.

Run: `grep -A 5 "notification-service" services/api-gateway/src/main/resources/application.yml | head -20`

Expected: 기존 라우트가 있으면 path 확장만, 없으면 신규 라우트 추가:

```yaml
        - id: notification-service-center
          uri: lb://notification-service
          predicates:
            - Path=/api/notifications/**
          filters:
            - StripPrefix=1
            - JwtAuthentication
```

- [ ] **Step 2: 전체 컴파일 검증**

Run: `./gradlew compileJava compileTestJava --no-daemon`
Expected: BUILD SUCCESSFUL (66 task)

- [ ] **Step 3: 단위 test 일괄 실행**

Run: `./gradlew :services:notification-service:test --tests "NotificationCenterServiceTest" --tests "NotificationCenterControllerIT" --no-daemon`
Expected: 13 tests PASS (단위 8 + IT 5)

- [ ] **Step 4: Commit + PR 발행**

```bash
git add services/api-gateway/src/main/resources/application.yml
git commit -m "feat(gateway): Slice 1 Task 9 — /api/notifications/** route 추가"

git push -u origin spec/issue-4-unified-notification-center

gh pr create --title "[FEAT] Issue 4 Slice 1 — 통합 알림 센터 BE 도메인 (notification-service)" \
  --body "$(cat <<'EOF'
## 요약

Issue 4 통합 알림 센터의 BE 도메인 신규 (Slice 1).

## 변경 (9 task)

### BE (notification-service)
- Flyway V5 \`notification_center\` 테이블 + 4 index
- \`NotificationCenter\` entity (BaseEntity 7 audit + read_at)
- \`NotificationSeverity\` enum (INFO/WARNING/CRITICAL)
- \`NotificationCenterRepository\` (native query — role CSV string_to_array 필터)
- DTO 3종 (PublishRequest, Response, Page)
- \`NotificationCenterService\` — publish/findMyUnread/findMyHistory/acknowledge (idempotent)
- \`NotificationCenterController\` — GET /my, /history, POST /{id}/acknowledge
- \`NotificationCenterInternalController\` — POST /internal/notifications

### Gateway
- \`/api/notifications/**\` route + StripPrefix=1 + JwtAuthentication

### Test
- 단위 8건 (TDD GREEN)
- IT 5건 (publish, role filter, userId filter, acknowledge idempotent, paged history)

## Spec
[\`docs/superpowers/specs/2026-05-22-issue-4-unified-notification-center-design.md\`](../docs/superpowers/specs/2026-05-22-issue-4-unified-notification-center-design.md)

## 다음 Slice
- Slice 2: FE UI (NotificationBellDropdown + NotificationHistoryPage)
- Slice 3: source services 통합 (SafetyStockService + MessageService → publish)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

### Spec coverage
- [x] notification entity Flyway V5 → Task 1
- [x] target_role CSV + target_user_id 필터 → Task 3 (native query)
- [x] read/unread state (read_at) → Task 2 entity + Task 5 service.acknowledge
- [x] REST API 4종 → Task 6 + Task 7
- [x] target_role/userId 권한 가드 → Task 5 canAccess
- [x] paged history → Task 4 NotificationCenterPage + Task 5/6
- [x] internal token 가드 → Task 7 (path-prefix 자동) + Task 8 IT 첫번째
- [x] gateway route → Task 9

### Placeholder scan
- 0건 — 모든 step 에 실제 code/명령 포함

### Type consistency
- `NotificationCenter` entity 명 일관 (NotificationLog 충돌 회피)
- `NotificationCenterResponse`, `NotificationCenterPage`, `NotificationPublishRequest` DTO 명 일관
- `NotificationCenterService.publish/findMyUnread/findMyHistory/acknowledge` 시그니처 Task 5 → Task 6/7/8 일관

### Scope
단일 Slice 1 (BE 도메인) — source service 영향 0, FE 영향 0. Slice 2/3 는 별도 plan.
