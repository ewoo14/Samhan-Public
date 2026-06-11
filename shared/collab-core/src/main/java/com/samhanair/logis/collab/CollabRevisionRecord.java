package com.samhanair.logis.collab;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 문서 full-snapshot 버전 이력 공통 base.
 *
 * <p>소비 service 는 documentId 별 unique(document_type, document_id, revision_no) 같은 제약을
 * 자기 Flyway 에서 정의한다. collab-core 는 채번/재시도 로직만 공통화한다.
 */
@Getter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class CollabRevisionRecord extends BaseEntity {

    /** 하위 entity 의 UUID PK. */
    public abstract UUID getId();

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private CollabDocumentType documentType;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "revision_no", nullable = false)
    private Long revisionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "revision_type", nullable = false, length = 30)
    private CollabRevisionType revisionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false, columnDefinition = "jsonb")
    private String snapshot;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_name", nullable = false, length = 50)
    private String actorName;

    @Column(name = "source_revision_no")
    private Long sourceRevisionNo;

    @Column(name = "source_suggestion_id")
    private UUID sourceSuggestionId;

    /** 하위 entity factory 가 호출하는 공통 초기화. */
    protected void init(CollabDocumentType documentType, UUID documentId, long revisionNo,
                        CollabRevisionType revisionType, String snapshot,
                        UUID actorId, String actorName,
                        Long sourceRevisionNo, UUID sourceSuggestionId) {
        if (documentType == null) {
            throw new IllegalArgumentException("documentType 은 필수입니다");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 는 필수입니다");
        }
        if (revisionNo < 1) {
            throw new IllegalArgumentException("revisionNo 는 1 이상이어야 합니다: " + revisionNo);
        }
        if (revisionType == null) {
            throw new IllegalArgumentException("revisionType 은 필수입니다");
        }
        if (snapshot == null || snapshot.isBlank()) {
            throw new IllegalArgumentException("snapshot 은 필수입니다");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId 는 필수입니다");
        }
        if (actorName == null || actorName.isBlank()) {
            throw new IllegalArgumentException("actorName 은 필수입니다");
        }
        this.documentType = documentType;
        this.documentId = documentId;
        this.revisionNo = revisionNo;
        this.revisionType = revisionType;
        this.snapshot = snapshot;
        this.actorId = actorId;
        this.actorName = actorName;
        this.sourceRevisionNo = sourceRevisionNo;
        this.sourceSuggestionId = sourceSuggestionId;
    }
}
