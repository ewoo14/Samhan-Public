package com.samhanair.logis.groupware.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.groupware.domain.Schedule;
import com.samhanair.logis.groupware.domain.ScheduleStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 일정 도메인 단위 테스트 — 4 case:
 * <ol>
 *   <li>create 정상 흐름 (status default=DRAFT)</li>
 *   <li>update 시간 검증 (endsAt &lt;= startsAt 거부)</li>
 *   <li>참여자 추가 — 중복 idempotent</li>
 *   <li>cancel 호출 → status=CANCELLED</li>
 * </ol>
 */
class ScheduleServiceTest {

    @Test
    void create_defaults_to_draft_status_when_status_missing() {
        UUID owner = UUID.randomUUID();
        LocalDateTime starts = LocalDateTime.now().plusDays(1);
        LocalDateTime ends = starts.plusHours(1);

        Schedule s = Schedule.create(owner, "회의", "본문", starts, ends, null);

        assertThat(s.getStatus()).isEqualTo(ScheduleStatus.DRAFT);
        assertThat(s.getStartsAt()).isEqualTo(starts);
        assertThat(s.getEndsAt()).isEqualTo(ends);
    }

    @Test
    void update_rejects_invalid_time_range() {
        UUID owner = UUID.randomUUID();
        LocalDateTime starts = LocalDateTime.now().plusDays(1);
        LocalDateTime ends = starts.plusHours(1);
        Schedule s = Schedule.create(owner, "회의", null, starts, ends, ScheduleStatus.CONFIRMED);

        // endsAt == startsAt → invalid
        assertThatThrownBy(() -> s.update("수정", null, starts, starts, ScheduleStatus.CONFIRMED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addParticipant_is_idempotent_for_same_id() {
        UUID owner = UUID.randomUUID();
        UUID p1 = UUID.randomUUID();
        Schedule s = Schedule.create(owner, "회의", null,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                ScheduleStatus.CONFIRMED);

        s.addParticipant(p1);
        s.addParticipant(p1); // idempotent

        assertThat(s.getParticipantsView()).hasSize(1);
        assertThat(s.getParticipantsView().get(0).getParticipantId()).isEqualTo(p1);
    }

    @Test
    void cancel_transitions_status_to_cancelled() {
        UUID owner = UUID.randomUUID();
        Schedule s = Schedule.create(owner, "회의", null,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1),
                ScheduleStatus.CONFIRMED);

        s.cancel();

        assertThat(s.getStatus()).isEqualTo(ScheduleStatus.CANCELLED);
    }
}
