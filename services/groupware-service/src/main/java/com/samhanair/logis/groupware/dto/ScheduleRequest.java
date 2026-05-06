package com.samhanair.logis.groupware.dto;

import com.samhanair.logis.groupware.domain.ScheduleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 일정 등록/수정 요청 DTO.
 *
 * <p>등록 시 ownerId 필수. 수정 시 ownerId 는 path/식별자 의미 (변경 불가, 무시).
 *
 * @param ownerId 소유자 user UUID
 * @param title 제목
 * @param description 본문 (선택)
 * @param startsAt 시작 시각
 * @param endsAt 종료 시각 (startsAt 이후)
 * @param status 일정 상태 (null → DRAFT 기본)
 * @param participantIds 참여자 user UUID 목록 (선택)
 */
public record ScheduleRequest(
        @NotNull UUID ownerId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 2000) String description,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        ScheduleStatus status,
        List<UUID> participantIds
) {
}
