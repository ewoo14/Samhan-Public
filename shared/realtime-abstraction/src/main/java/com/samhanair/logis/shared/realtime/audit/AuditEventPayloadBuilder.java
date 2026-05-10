package com.samhanair.logis.shared.realtime.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * audit overlay SSE event payload 빌더 — PR-H4a (Phase 12 Step 4a).
 *
 * <p>14 service 가 동일 schema 로 SSE event 를 발행하기 위한 공용 helper. FE 가 어느 도메인의
 * audit 이벤트든 동일 형식으로 파싱 가능.
 *
 * <p><b>SSE event payload 형식</b>:
 * <pre>
 * {
 *   "revisionNo": 5,
 *   "actorId": "uuid",       // FE 색상 hash 용 (UUID 직접 노출 X — clientside 만)
 *   "actorName": "홍길동",    // 사용자 화면 노출
 *   "actorColor": "#3B82F6", // optional
 *   "changes": [
 *     {"fieldName":"memo","oldValue":"old","newValue":"new"},
 *     ...
 *   ]
 * }
 * </pre>
 */
public final class AuditEventPayloadBuilder {

    private AuditEventPayloadBuilder() {
        // utility class
    }

    /**
     * audit overlay event payload 빌드 — 일관 schema 보장.
     *
     * @param revisionNo 단조 증가 수정 번호
     * @param actorId 수정자 UUID (FE 색상 hash 결정성용)
     * @param actorName 수정자 표시명
     * @param actorColor FE 색상 hex (선택)
     * @param changes 변경된 필드 리스트 (1건 이상)
     * @return 직렬화 가능한 LinkedHashMap (SSE event data)
     */
    public static Map<String, Object> build(int revisionNo, UUID actorId, String actorName,
                                            String actorColor, List<ChangeEntry> changes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("revisionNo", revisionNo);
        payload.put("actorId", actorId == null ? null : actorId.toString());
        payload.put("actorName", actorName);
        payload.put("actorColor", actorColor);
        List<Map<String, Object>> changeMaps = new ArrayList<>(changes.size());
        for (ChangeEntry c : changes) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fieldName", c.fieldName());
            m.put("oldValue", c.oldValue());
            m.put("newValue", c.newValue());
            changeMaps.add(m);
        }
        payload.put("changes", changeMaps);
        return payload;
    }
}
