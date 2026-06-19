package com.samhanair.logis.shared.realtime.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PresenceServiceTest {

    private RealtimeBroker broker;
    private MutableClock clock;
    private PresenceService service;
    private UUID entityId;

    @BeforeEach
    void setUp() {
        broker = mock(RealtimeBroker.class);
        clock = new MutableClock(Instant.parse("2026-06-19T00:00:00Z"));
        service = new PresenceService(broker, Duration.ofMinutes(5), clock);
        entityId = UUID.randomUUID();
    }

    @Test
    void join_registersDeterministicColorAndPublishesJoinEvent() {
        PresenceEntry entry = service.join(entityId, "session-1", "account-user-1", "홍길동");

        assertThat(entry.sessionId()).isEqualTo("session-1");
        assertThat(entry.displayName()).isEqualTo("홍길동");
        assertThat(entry.color()).isEqualTo(PresenceColor.fromUserId("account-user-1"));
        assertThat(service.list(entityId)).containsExactly(entry);
        verify(broker).publish(eq(entityId), eq(PresenceService.EVENT_JOIN), eq(entry));
    }

    @Test
    void join_sameSessionRefreshesHeartbeatWithoutDuplicateListEntry() {
        PresenceEntry first = service.join(entityId, "session-1", "account-user-1", "홍길동");
        clock.advance(Duration.ofSeconds(30));
        PresenceEntry refreshed = service.join(entityId, "session-1", "account-user-1", "홍길동");

        assertThat(refreshed.lastSeenAt()).isAfter(first.lastSeenAt());
        assertThat(service.list(entityId)).hasSize(1);
    }

    @Test
    void join_sameUserDifferentSessions_keepsEachSession() {
        PresenceEntry first = service.join(entityId, "session-1", "account-user-1", "홍길동");
        PresenceEntry second = service.join(entityId, "session-2", "account-user-1", "홍길동");

        assertThat(service.list(entityId)).containsExactly(first, second);
        assertThat(second.color()).isEqualTo(first.color());
    }

    @Test
    void leave_removesSessionAndPublishesLeaveEvent() {
        PresenceEntry entry = service.join(entityId, "session-1", "account-user-1", "홍길동");

        service.leave(entityId, "session-1");

        assertThat(service.list(entityId)).isEmpty();
        verify(broker).publish(eq(entityId), eq(PresenceService.EVENT_LEAVE), eq(entry));
    }

    @Test
    void leave_unknownUserDoesNotPublishLeaveEvent() {
        service.leave(entityId, "missing");

        verify(broker, never()).publish(eq(entityId), eq(PresenceService.EVENT_LEAVE), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pruneExpired_removesStaleEntriesAndPublishesLeaveEvent() {
        PresenceEntry active = service.join(entityId, "session-active", "active", "김활성");
        PresenceEntry stale = service.join(entityId, "session-stale", "stale", "박만료");
        clock.advance(Duration.ofMinutes(4));
        service.join(entityId, active.sessionId(), "active", active.displayName());
        clock.advance(Duration.ofMinutes(2));

        List<PresenceEntry> removed = service.pruneExpired();

        assertThat(removed).containsExactly(stale);
        assertThat(service.list(entityId)).containsExactly(active.withLastSeenAt(clock.instant().minus(Duration.ofMinutes(2))));
        verify(broker).publish(eq(entityId), eq(PresenceService.EVENT_LEAVE), eq(stale));
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
