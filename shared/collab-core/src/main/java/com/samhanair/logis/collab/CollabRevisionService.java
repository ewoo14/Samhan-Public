package com.samhanair.logis.collab;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문서 full-snapshot revision generic service.
 *
 * <p>revisionNo 는 documentType/documentId 별 max+1 로 채번한다. 소비 service 는 unique 제약을
 * 자기 migration 에서 선언하고, 동시 insert 충돌은 같은 transaction 재시도 없이 409 로 변환한다.
 */
public class CollabRevisionService<T extends CollabRevisionRecord> {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public static final String EVENT_REVISION_CAPTURED = "revision.captured";
    public static final String EVENT_REVISION_RESTORED = "revision.restored";

    private final RevisionRepository<T> repository;
    private final RevisionFactory<T> factory;
    private final CollabRealtimePublisher publisher;

    public CollabRevisionService(RevisionRepository<T> repository,
                                 RevisionFactory<T> factory,
                                 CollabRealtimePublisher publisher) {
        this.repository = repository;
        this.factory = factory;
        this.publisher = publisher;
    }

    /** 현재 문서 snapshot 을 캡처한다. */
    @Transactional
    public T capture(DocumentCollaborationPort port, UUID documentId,
                     CollabRevisionType revisionType, Long sourceRevisionNo,
                     UUID sourceSuggestionId, UUID actorId, String actorName) {
        String snapshot = port.loadSnapshot(documentId);
        T saved = captureSnapshot(port.documentType(), documentId, revisionType,
                snapshot, sourceRevisionNo, sourceSuggestionId, actorId, actorName);
        publisher.publish(documentId, EVENT_REVISION_CAPTURED, payload(saved));
        return saved;
    }

    /** 특정 revision snapshot 으로 복원한다. 기본 구현은 full snapshot JSON 을 port.restoreSnapshot 으로 전달한다. */
    @Transactional
    public T restore(DocumentCollaborationPort port, UUID documentId, long targetRevisionNo,
                     UUID actorId, String actorName) {
        return restore(port, documentId, targetRevisionNo, actorId, actorName, port::restoreSnapshot);
    }

    /** 특정 revision snapshot 으로 복원한다. 소비 service 가 snapshot 복원 callback 을 주입할 수 있다. */
    @Transactional
    public T restore(DocumentCollaborationPort port, UUID documentId, long targetRevisionNo,
                     UUID actorId, String actorName, BiConsumer<UUID, String> snapshotRestorer) {
        if (!port.canDecide(actorId, documentId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "회귀 권한 없음");
        }
        T target = repository.findByDocumentTypeAndDocumentIdAndRevisionNo(
                        port.documentType(), documentId, targetRevisionNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "복원 대상 revision 없음 (documentId=" + documentId
                                + ", revisionNo=" + targetRevisionNo + ")"));
        snapshotRestorer.accept(documentId, target.getSnapshot());
        T restored = captureSnapshot(port.documentType(), documentId, CollabRevisionType.RESTORE,
                port.loadSnapshot(documentId), targetRevisionNo, null, actorId, actorName);
        publisher.publish(documentId, EVENT_REVISION_RESTORED, payload(restored));
        return restored;
    }

    /** 최신순 revision 목록과 전체 개수를 함께 반환한다. */
    @Transactional(readOnly = true)
    public RevisionSlice<T> listWithCount(CollabDocumentType documentType, UUID documentId,
                                          int page, int limit) {
        int safePage = Math.max(0, page);
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        Pageable pageable = PageRequest.of(safePage, safeLimit);
        List<T> items = repository.findByDocumentTypeAndDocumentIdOrderByRevisionNoDesc(
                documentType, documentId, pageable);
        long totalCount = repository.countByDocumentTypeAndDocumentId(documentType, documentId);
        return new RevisionSlice<>(items, totalCount);
    }

    private T captureSnapshot(CollabDocumentType documentType, UUID documentId,
                              CollabRevisionType revisionType, String snapshot,
                              Long sourceRevisionNo, UUID sourceSuggestionId,
                              UUID actorId, String actorName) {
        try {
            return saveWithNextRevisionNo(documentType, documentId, revisionType, snapshot,
                    sourceRevisionNo, sourceSuggestionId, actorId, actorName);
        } catch (DataIntegrityViolationException firstConflict) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "동시 수정 충돌 — 재시도", firstConflict);
        }
    }

    private T saveWithNextRevisionNo(CollabDocumentType documentType, UUID documentId,
                                     CollabRevisionType revisionType, String snapshot,
                                     Long sourceRevisionNo, UUID sourceSuggestionId,
                                     UUID actorId, String actorName) {
        Long max = repository.maxRevisionNo(documentType, documentId);
        long next = (max == null ? 0L : max) + 1L;
        return repository.saveAndFlush(factory.create(documentType, documentId, next,
                revisionType, snapshot, actorId, actorName, sourceRevisionNo, sourceSuggestionId));
    }

    private Map<String, Object> payload(T revision) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", revision.getId().toString());
        payload.put("documentType", revision.getDocumentType().name());
        payload.put("documentId", revision.getDocumentId().toString());
        payload.put("revisionNo", revision.getRevisionNo());
        payload.put("revisionType", revision.getRevisionType().name());
        payload.put("actorName", revision.getActorName());
        putIfNotNull(payload, "sourceRevisionNo", revision.getSourceRevisionNo());
        putIfNotNull(payload, "sourceSuggestionId", revision.getSourceSuggestionId() == null
                ? null : revision.getSourceSuggestionId().toString());
        putIfNotNull(payload, "createdAt", revision.getCreatedAt() == null
                ? null : revision.getCreatedAt().toString());
        return payload;
    }

    private void putIfNotNull(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }

    /** revision timeline slice. */
    public record RevisionSlice<T extends CollabRevisionRecord>(List<T> items, long totalCount) {
    }

    /** 소비 service 의 concrete repository adapter. */
    public interface RevisionRepository<T extends CollabRevisionRecord> {
        T saveAndFlush(T revision);

        Optional<T> findByDocumentTypeAndDocumentIdAndRevisionNo(
                CollabDocumentType documentType, UUID documentId, long revisionNo);

        List<T> findByDocumentTypeAndDocumentIdOrderByRevisionNoDesc(
                CollabDocumentType documentType, UUID documentId, Pageable pageable);

        long countByDocumentTypeAndDocumentId(CollabDocumentType documentType, UUID documentId);

        Long maxRevisionNo(CollabDocumentType documentType, UUID documentId);
    }

    /** 소비 service concrete entity 생성 adapter. */
    @FunctionalInterface
    public interface RevisionFactory<T extends CollabRevisionRecord> {
        T create(CollabDocumentType documentType, UUID documentId, long revisionNo,
                 CollabRevisionType revisionType, String snapshot,
                 UUID actorId, String actorName, Long sourceRevisionNo, UUID sourceSuggestionId);
    }
}
