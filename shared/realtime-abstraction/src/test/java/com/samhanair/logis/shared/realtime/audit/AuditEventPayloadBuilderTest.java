package com.samhanair.logis.shared.realtime.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * PR-H4a — AuditEventPayloadBuilder + ChangeEntry record 단위 (5 case).
 *
 * <ol>
 *   <li>build — 단일 변경 → revisionNo/actorName/changes(1) schema</li>
 *   <li>build — 다중 변경 → 같은 revisionNo, changes(N)</li>
 *   <li>build — actorId null 시 payload 의 actorId 도 null</li>
 *   <li>build — actorColor null 허용</li>
 *   <li>ChangeEntry — record 는 fieldName/oldValue/newValue 컴포넌트 노출</li>
 * </ol>
 */
class AuditEventPayloadBuilderTest {

    @Test
    void build_singleChange_emitsExpectedSchema() {
        UUID actorId = UUID.randomUUID();
        Map<String, Object> payload = AuditEventPayloadBuilder.build(
                3, actorId, "홍길동", "#3B82F6",
                List.of(new ChangeEntry("memo", "old", "new")));

        assertThat(payload.get("revisionNo")).isEqualTo(3);
        assertThat(payload.get("actorId")).isEqualTo(actorId.toString());
        assertThat(payload.get("actorName")).isEqualTo("홍길동");
        assertThat(payload.get("actorColor")).isEqualTo("#3B82F6");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).get("fieldName")).isEqualTo("memo");
        assertThat(changes.get(0).get("oldValue")).isEqualTo("old");
        assertThat(changes.get(0).get("newValue")).isEqualTo("new");
    }

    @Test
    void build_batchChanges_sharesSameRevisionNo() {
        Map<String, Object> payload = AuditEventPayloadBuilder.build(
                7, UUID.randomUUID(), "관리자", null,
                List.of(
                        new ChangeEntry("memo", "a", "b"),
                        new ChangeEntry("shippingAddress", "c", "d")));

        assertThat(payload.get("revisionNo")).isEqualTo(7);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) payload.get("changes");
        assertThat(changes).hasSize(2);
        assertThat(changes.get(0).get("fieldName")).isEqualTo("memo");
        assertThat(changes.get(1).get("fieldName")).isEqualTo("shippingAddress");
    }

    @Test
    void build_actorIdNull_emitsNullInPayload() {
        Map<String, Object> payload = AuditEventPayloadBuilder.build(
                1, null, "system", null,
                List.of(new ChangeEntry("memo", null, "new")));

        assertThat(payload.get("actorId")).isNull();
        assertThat(payload.get("actorName")).isEqualTo("system");
    }

    @Test
    void build_actorColorOptional_keptAsNull() {
        Map<String, Object> payload = AuditEventPayloadBuilder.build(
                1, UUID.randomUUID(), "user", null,
                List.of(new ChangeEntry("memo", "x", "y")));

        assertThat(payload.containsKey("actorColor")).isTrue();
        assertThat(payload.get("actorColor")).isNull();
    }

    @Test
    void changeEntry_recordExposesComponents() {
        ChangeEntry entry = new ChangeEntry("partnerName", "삼한항공", "삼한항공(주)");

        assertThat(entry.fieldName()).isEqualTo("partnerName");
        assertThat(entry.oldValue()).isEqualTo("삼한항공");
        assertThat(entry.newValue()).isEqualTo("삼한항공(주)");
    }
}
