package com.samhanair.logis.slip.revision.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SlipRevision} factory 검증 + {@link SlipSnapshot} Jackson round-trip 단위 테스트
 * (권한 재편 Phase 2.1 Task 1).
 *
 * <p>JSONB 컬럼에 저장될 스냅샷 DTO 가 헤더(LocalDate/UUID 포함)+라인 배열을 무손실 직렬화/
 * 역직렬화하는지, factory 가 필수 인자를 강제하는지 확인한다.
 */
class SlipRevisionSnapshotTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * [R8-BE-5] 기사/하차 3필드 round-trip — {@code SlipService.editDriver} 는 기사 변경을 EDIT
     * 스냅샷으로 캡처하며 그 주석이 <i>"driverName/driverPhone 은 toSnapshot 필드"</i> 라고 명시했으나
     * record 에 실제로는 없어, 기사 변경이 스냅샷에 담기지 않고 복원이 현재 값을 남겼다.
     */
    @Test
    @DisplayName("SlipSnapshot 은 기사명/기사연락처/하차일을 무손실 직렬화한다 (R8-BE-5)")
    void snapshotRoundTripsDriverAndUnloadDate() throws Exception {
        SlipSnapshot original = new SlipSnapshot(
                "2026/07/16-1", LocalDate.of(2026, 7, 16), UUID.randomUUID(), "삼한물산",
                "P-2026-0001", "123-45-67890", "메모", "REGION",
                null, null, null, null, null, UUID.randomUUID(), "본사창고",
                // 기사/하차 3필드
                "김기사", "010-5555-6666", LocalDate.of(2026, 7, 18),
                null, null, null, null, null, null, null, null, null, null,
                List.of());

        SlipSnapshot restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), SlipSnapshot.class);

        assertThat(restored.driverName()).isEqualTo("김기사");
        assertThat(restored.driverPhone()).isEqualTo("010-5555-6666");
        assertThat(restored.unloadDate()).isEqualTo(LocalDate.of(2026, 7, 18));
    }

    /**
     * [R8-BE-5 하위호환] 기사/하차 키가 <b>없는</b> 구 JSONB 스냅샷도 깨지지 않고 null 로
     * 역직렬화되어야 한다 — 기존 revision 행은 그 키를 갖고 있지 않다.
     */
    @Test
    @DisplayName("기사/하차 키가 없는 구 스냅샷 JSON 도 null 로 안전하게 역직렬화된다 (R8-BE-5 하위호환)")
    void legacySnapshotJsonWithoutDriverKeys_deserializesWithNulls() throws Exception {
        String legacyJson = """
                {"slipNo":"2026/05/29-3","slipDate":"2026-05-29","partnerName":"삼한물산",
                 "partnerCode":"P-2026-0001","memo":"긴급 출고","deliveryTag":"OUTBOUND_DELIVERY",
                 "lines":[]}
                """;

        SlipSnapshot snapshot = objectMapper.readValue(legacyJson, SlipSnapshot.class);

        assertThat(snapshot.slipNo()).isEqualTo("2026/05/29-3");
        assertThat(snapshot.driverName()).isNull();
        assertThat(snapshot.driverPhone()).isNull();
        assertThat(snapshot.unloadDate()).isNull();
    }

    @Test
    @DisplayName("SlipSnapshot 은 헤더+라인 배열을 Jackson round-trip 무손실 직렬화한다")
    void snapshotJacksonRoundTrip() throws Exception {
        UUID partnerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        SlipSnapshot original = new SlipSnapshot(
                "2026/05/29-3",
                LocalDate.of(2026, 5, 29),
                partnerId,
                "삼한물산",
                "P-2026-0001",
                "123-45-67890",
                "긴급 출고",
                "OUTBOUND_DELIVERY",
                "서울시 강남구 1",
                "서울시 강남구 2",
                "강남 신축 프로젝트",
                "010-1234-5678",
                LocalDate.of(2026, 6, 30),
                warehouseId,
                "본사창고",
                // audit overlay 필드 10개 (PR #318 cycle1 P1-1)
                "배송지 주소", "검수지 주소", "010-9999-0000", "010-1111-2222",
                "거래처 사업장 주소", "김대표", "익월말", "5% 할인", "월말", "운송비 별도",
                List.of(
                        new SlipSnapshot.Line(productId, "펌프", "MX-100", "220V", 2,
                                new BigDecimal("15000.00"), new BigDecimal("30000.00"), "라인메모",
                                new BigDecimal("16500.00"), new BigDecimal("3000.00"),
                                new BigDecimal("30000.00")),
                        new SlipSnapshot.Line(UUID.randomUUID(), "밸브", null, null, 5,
                                new BigDecimal("3000.00"), new BigDecimal("15000.00"), null,
                                null, null, null)));

        String json = objectMapper.writeValueAsString(original);
        SlipSnapshot restored = objectMapper.readValue(json, SlipSnapshot.class);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.lines()).hasSize(2);
        assertThat(restored.slipDate()).isEqualTo(LocalDate.of(2026, 5, 29));
        assertThat(restored.partnerId()).isEqualTo(partnerId);
        assertThat(restored.lines().get(0).lineTotal()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("[R6-H3] 세트 계보 필드는 round-trip 보존되고, 계보 없는 구 JSON 은 null 로 역직렬화된다")
    void snapshotLineLineageRoundTripAndLegacyJsonBackwardCompat() throws Exception {
        UUID productId = UUID.randomUUID();
        SlipSnapshot.Line headLine = new SlipSnapshot.Line(productId, "실내기", "COMP-1", "220V", 1,
                new BigDecimal("300000.00"), new BigDecimal("300000.00"), null,
                new BigDecimal("330000.00"), new BigDecimal("30000.00"), new BigDecimal("300000.00"),
                Boolean.TRUE, "SET-809");

        String json = objectMapper.writeValueAsString(headLine);
        SlipSnapshot.Line restored = objectMapper.readValue(json, SlipSnapshot.Line.class);
        assertThat(restored.setHead()).isTrue();
        assertThat(restored.parentSetModel()).isEqualTo("SET-809");

        // 구 스냅샷 JSON(계보 필드 자체가 없음) — V58 이전 slip_revisions 행 하위호환
        String legacyJson = """
                {"productId":"%s","productName":"펌프","quantity":2,"unitPrice":15000.00}
                """.formatted(productId);
        SlipSnapshot.Line legacy = objectMapper.readValue(legacyJson, SlipSnapshot.Line.class);
        assertThat(legacy.setHead()).isNull();
        assertThat(legacy.parentSetModel()).isNull();
    }

    @Test
    @DisplayName("SlipRevision.of 는 RESTORE 스냅샷을 생성하고 source revision 을 보존한다")
    void factoryCreatesRestoreRevision() {
        UUID slipId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        SlipSnapshot snapshot = new SlipSnapshot("2026/05/29-3", LocalDate.of(2026, 5, 29),
                null, "삼한물산", null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                List.of());

        SlipRevision revision = SlipRevision.of(slipId, 4, SlipRevisionType.RESTORE, 2,
                "2026/05/29-3", LocalDate.of(2026, 5, 29), snapshot, actorId, "홍길동", "#3366FF");

        assertThat(revision.getSlipId()).isEqualTo(slipId);
        assertThat(revision.getRevisionNo()).isEqualTo(4);
        assertThat(revision.getRevisionType()).isEqualTo(SlipRevisionType.RESTORE);
        assertThat(revision.getSourceRevisionNo()).isEqualTo(2);
        assertThat(revision.getActorName()).isEqualTo("홍길동");
        assertThat(revision.getSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("SlipRevision.of 는 필수 인자(slipId/revisionNo/revisionType/snapshot) 누락 시 거부한다")
    void factoryRejectsMissingRequiredArgs() {
        SlipSnapshot snapshot = new SlipSnapshot(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, List.of());
        UUID slipId = UUID.randomUUID();

        assertThatThrownBy(() -> SlipRevision.of(null, 1, SlipRevisionType.CREATE, null,
                null, null, snapshot, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlipRevision.of(slipId, null, SlipRevisionType.CREATE, null,
                null, null, snapshot, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlipRevision.of(slipId, 1, null, null,
                null, null, snapshot, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SlipRevision.of(slipId, 1, SlipRevisionType.CREATE, null,
                null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
