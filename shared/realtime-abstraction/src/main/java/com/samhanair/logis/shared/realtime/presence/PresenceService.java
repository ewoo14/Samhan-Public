package com.samhanair.logis.shared.realtime.presence;

import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * entityId 단위 동시 접속자 presence registry.
 *
 * <p>저장소는 in-memory 이고, 이벤트 fan-out 은 기존 {@link RealtimeBroker} 채널을 그대로
 * 사용한다. Redis broker 모드에서는 broker hook 이 presence event 도 같은 방식으로 cross-node
 * propagate 한다.
 */
public class PresenceService {

    public static final String EVENT_JOIN = "presence:join";
    public static final String EVENT_LEAVE = "presence:leave";
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final String DEFAULT_DISPLAY_NAME = "사용자";
    private static final int MAX_DISPLAY_NAME_LENGTH = 50;
    private static final Pattern UUID_SHAPE = Pattern.compile(
            "(?i)^(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");

    private final RealtimeBroker broker;
    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, PresenceEntry>> entries =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, String>> owners =
            new ConcurrentHashMap<>();

    public PresenceService(RealtimeBroker broker) {
        this(broker, DEFAULT_TTL, Clock.systemUTC());
    }

    public PresenceService(RealtimeBroker broker, Duration ttl, Clock clock) {
        this.broker = Objects.requireNonNull(broker, "broker 는 필수입니다");
        this.ttl = Objects.requireNonNull(ttl, "ttl 은 필수입니다");
        this.clock = Objects.requireNonNull(clock, "clock 은 필수입니다");
    }

    public PresenceEntry join(UUID entityId, String sessionId, String userId, String displayName) {
        Objects.requireNonNull(entityId, "entityId 는 필수입니다");
        String normalizedSessionId = normalizeSessionId(sessionId);
        String normalizedUserId = normalizeUserId(userId);
        Instant now = clock.instant();
        PresenceEntry next = new PresenceEntry(
                normalizedSessionId,
                normalizeDisplayName(displayName),
                PresenceColor.fromUserId(normalizedUserId),
                now);
        AtomicBoolean newSession = new AtomicBoolean(false);

        entries.compute(entityId, (ignored, current) -> {
            ConcurrentHashMap<String, PresenceEntry> entityEntries =
                    current == null ? new ConcurrentHashMap<>() : current;
            PresenceEntry previous = entityEntries.put(normalizedSessionId, next);
            if (previous == null) {
                newSession.set(true);
                owners.compute(entityId, (ignoredOwner, currentOwners) -> {
                    ConcurrentHashMap<String, String> entityOwners =
                            currentOwners == null ? new ConcurrentHashMap<>() : currentOwners;
                    entityOwners.put(normalizedSessionId, normalizedUserId);
                    return entityOwners;
                });
            }
            return entityEntries;
        });
        if (newSession.get()) {
            broker.publish(entityId, EVENT_JOIN, next);
        }
        return next;
    }

    public void leave(UUID entityId, String sessionId, String callerUserId) {
        Objects.requireNonNull(entityId, "entityId 는 필수입니다");
        String normalizedSessionId = normalizeSessionId(sessionId);
        String normalizedCallerUserId = normalizeUserId(callerUserId);
        AtomicReference<PresenceEntry> removed = new AtomicReference<>();
        entries.computeIfPresent(entityId, (ignored, entityEntries) -> {
            ConcurrentHashMap<String, String> entityOwners = owners.get(entityId);
            String ownerUserId = entityOwners == null ? null : entityOwners.get(normalizedSessionId);
            if (!normalizedCallerUserId.equals(ownerUserId)) {
                return entityEntries;
            }
            PresenceEntry removedEntry = entityEntries.remove(normalizedSessionId);
            if (removedEntry != null) {
                removed.set(removedEntry);
                if (entityOwners != null) {
                    entityOwners.remove(normalizedSessionId);
                    if (entityOwners.isEmpty()) {
                        owners.remove(entityId, entityOwners);
                    }
                }
            }
            return entityEntries.isEmpty() ? null : entityEntries;
        });
        if (removed.get() != null) {
            broker.publish(entityId, EVENT_LEAVE, removed.get());
        }
    }

    /**
     * 현재 노드가 보유한 presence snapshot 을 반환한다.
     *
     * <p>presence registry 는 노드-로컬 ConcurrentHashMap 이다. Redis broker 모드(다중 노드)에서는 list()
     * 백필이 노드별 부분집합이고, 노드 다운 시 그 노드 세션의 leave 가 미발행되어 타 노드에 잔존한다.
     * 다중 노드 롤아웃 시 sticky-session 전제 또는 registry 의 Redis 이전이 필요하다.
     */
    public List<PresenceEntry> list(UUID entityId) {
        Objects.requireNonNull(entityId, "entityId 는 필수입니다");
        ConcurrentHashMap<String, PresenceEntry> entityEntries = entries.get(entityId);
        if (entityEntries == null) {
            return List.of();
        }
        return entityEntries.values().stream()
                .sorted(Comparator.comparing(PresenceEntry::displayName)
                        .thenComparing(PresenceEntry::sessionId))
                .toList();
    }

    /**
     * TTL 이 지난 노드-로컬 presence 를 제거하고 leave 이벤트를 발행한다.
     *
     * <p>presence registry 는 노드-로컬 ConcurrentHashMap 이다. Redis broker 모드(다중 노드)에서는 list()
     * 백필이 노드별 부분집합이고, 노드 다운 시 그 노드 세션의 leave 가 미발행되어 타 노드에 잔존한다.
     * 다중 노드 롤아웃 시 sticky-session 전제 또는 registry 의 Redis 이전이 필요하다.
     */
    public List<PresenceEntry> pruneExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        List<PresenceEntry> removed = new ArrayList<>();
        for (Map.Entry<UUID, ConcurrentHashMap<String, PresenceEntry>> entity : entries.entrySet()) {
            UUID entityId = entity.getKey();
            ConcurrentHashMap<String, PresenceEntry> entityEntries = entity.getValue();
            for (PresenceEntry entry : entityEntries.values()) {
                if (entry.lastSeenAt().isBefore(cutoff)) {
                    boolean didRemove = entityEntries.remove(entry.sessionId(), entry);
                    if (didRemove) {
                        removed.add(entry);
                        ConcurrentHashMap<String, String> entityOwners = owners.get(entityId);
                        if (entityOwners != null) {
                            entityOwners.remove(entry.sessionId());
                            if (entityOwners.isEmpty()) {
                                owners.remove(entityId, entityOwners);
                            }
                        }
                        broker.publish(entityId, EVENT_LEAVE, entry);
                    }
                }
            }
            if (entityEntries.isEmpty()) {
                entries.remove(entityId, entityEntries);
                owners.remove(entityId);
            }
        }
        return removed;
    }

    @Scheduled(fixedRateString = "${samhan.realtime.presence.prune-ms:30000}")
    public void scheduledPruneExpired() {
        pruneExpired();
    }

    private String normalizeUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 는 필수입니다");
        }
        return userId.trim();
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 는 필수입니다");
        }
        return sessionId.trim();
    }

    private String normalizeDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return DEFAULT_DISPLAY_NAME;
        }
        String normalized = displayName.trim();
        if (UUID_SHAPE.matcher(normalized).matches()) {
            return DEFAULT_DISPLAY_NAME;
        }
        return normalized.length() <= MAX_DISPLAY_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_DISPLAY_NAME_LENGTH);
    }
}
