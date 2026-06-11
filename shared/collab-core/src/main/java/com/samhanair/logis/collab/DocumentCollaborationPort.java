package com.samhanair.logis.collab;

import java.util.UUID;

/**
 * 소비 서비스의 도메인 문서를 협업 core 에 연결하는 포트.
 *
 * <p>collab-core 는 문서 저장 구조를 알지 않는다. 각 서비스가 현재 스냅샷 로딩, 변경 제안 적용,
 * 권한 판정을 이 포트로 제공한다.
 */
public interface DocumentCollaborationPort {

    /** 이 포트가 담당하는 협업 문서 유형. */
    CollabDocumentType documentType();

    /** 현재 문서 full snapshot JSON 을 로드한다. */
    String loadSnapshot(UUID documentId);

    /** 제안 수락 시 도메인 문서에 changeSet JSON 을 적용한다. */
    void applyChangeSet(UUID documentId, String changeSetJson);

    /** 특정 revision 의 full snapshot JSON 으로 도메인 문서를 복원한다. */
    void restoreSnapshot(UUID documentId, String snapshotJson);

    /** 사용자가 해당 문서에 변경 제안을 등록할 수 있는지 판정한다. */
    boolean canPropose(UUID userId, UUID documentId);

    /** 사용자가 해당 문서의 변경 제안을 수락/거절할 수 있는지 판정한다. */
    boolean canDecide(UUID userId, UUID documentId);
}
