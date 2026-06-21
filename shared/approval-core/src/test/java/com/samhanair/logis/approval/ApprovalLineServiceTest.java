package com.samhanair.logis.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalLineServiceTest {

    static class FakeStep extends ApprovalStepBase {
        static FakeStep user(UUID a, int seq) { FakeStep s = new FakeStep(); s.initUserStep(a, seq); return s; }
    }
    static class FakeLine extends ApprovalLineBase {
        final List<FakeStep> steps = new ArrayList<>();
        UUID id = UUID.randomUUID();
        static FakeLine open(String no, UUID req, String title, UUID... approvers) {
            FakeLine l = new FakeLine();
            l.initBase(no, req, title);
            for (UUID a : approvers) l.steps.add(FakeStep.user(a, l.steps.size()));
            return l;
        }
        @Override protected List<? extends ApprovalStepBase> stepsView() { return steps; }
    }
    /** in-memory fake port. */
    static class FakePort implements ApprovalRepositoryPort<FakeLine> {
        final Map<UUID, FakeLine> store = new HashMap<>();
        @Override public Optional<FakeLine> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public FakeLine save(FakeLine line) { store.put(line.id, line); return line; }
        @Override public Optional<FakeLine> findByDocument(String t, UUID d) {
            return store.values().stream()
                    .filter(l -> t.equals(l.getDocumentType()) && d.equals(l.getDocumentId())).findFirst();
        }
    }

    @Test
    void approve_는_조회후_도메인_승인하고_저장한다() {
        UUID req = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        FakePort port = new FakePort();
        FakeLine line = FakeLine.open("2026/06/21-1", req, "지출", a1);
        port.save(line);
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(port);

        FakeLine result = service.approve(line.id, a1);
        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
    }

    @Test
    void getOrThrow_는_미존재시_IllegalArgumentException() {
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(new FakePort());
        assertThatThrownBy(() -> service.getOrThrow(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("결재선을 찾을 수 없습니다");
    }

    @Test
    void findByDocument_는_loose_ref_로_조회한다() {
        UUID req = UUID.randomUUID();
        UUID a1 = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        FakePort port = new FakePort();
        FakeLine line = FakeLine.open("2026/06/21-1", req, "출고", a1);
        line.linkDocument("SLIP_OUTBOUND", docId);
        port.save(line);
        ApprovalLineService<FakeLine> service = new ApprovalLineService<>(port);
        assertThat(service.findByDocument("SLIP_OUTBOUND", docId)).isPresent();
    }
}
