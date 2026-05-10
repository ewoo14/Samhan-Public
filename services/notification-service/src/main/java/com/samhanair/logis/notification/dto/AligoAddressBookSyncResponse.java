package com.samhanair.logis.notification.dto;

import java.util.List;

/**
 * Phase 10 PR-F1 BE-1 — 알리고 주소록 sync 응답 DTO.
 *
 * <p>4 카테고리 누적 결과:
 * <ul>
 *   <li>{@code added} — 신규 추가된 contact 수</li>
 *   <li>{@code updated} — 기존 contact 갱신 수 (실 알리고 spec 미정 — 현 stub 은 0)</li>
 *   <li>{@code skipped} — 알리고 측에서 중복 / 잘못된 형식 등으로 skip 된 수</li>
 *   <li>{@code failed} — chunk 단위 실패 메시지 리스트
 *       (예: "chunk#3 [first=[P-2026-0050]] HTTP 500")</li>
 * </ul>
 *
 * @param added 신규 추가 contact 수
 * @param updated 기존 갱신 contact 수
 * @param skipped 알리고 skip contact 수
 * @param failed 실패 chunk 메시지 리스트 (sample memo + HTTP status 포함)
 */
public record AligoAddressBookSyncResponse(int added, int updated, int skipped, List<String> failed) {
}
