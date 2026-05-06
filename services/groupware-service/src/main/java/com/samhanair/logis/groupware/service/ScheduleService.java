package com.samhanair.logis.groupware.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.groupware.client.UserClient;
import com.samhanair.logis.groupware.domain.Schedule;
import com.samhanair.logis.groupware.dto.ScheduleRequest;
import com.samhanair.logis.groupware.repository.ScheduleRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 일정 service — 등록 / 수정 / 조회 / 삭제 (soft) / 참여자 관리.
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository repository;
    private final UserClient userClient;

    /** 일정 등록 + 참여자 초기 등록. owner / participant 모두 사용자 존재 검증. */
    @Transactional
    public Schedule create(ScheduleRequest req) {
        if (!userClient.exists(req.ownerId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "소유자 미존재: " + req.ownerId());
        }
        try {
            Schedule schedule = Schedule.create(req.ownerId(), req.title(), req.description(),
                    req.startsAt(), req.endsAt(), req.status());
            if (req.participantIds() != null) {
                for (UUID participantId : req.participantIds()) {
                    if (!userClient.exists(participantId)) {
                        throw new BusinessException(ErrorCode.NOT_FOUND,
                                "참여자 미존재: " + participantId);
                    }
                    schedule.addParticipant(participantId);
                }
            }
            return repository.save(schedule);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
    }

    /** 단건 조회. */
    @Transactional(readOnly = true)
    public Schedule findById(UUID scheduleId) {
        return repository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "일정을 찾을 수 없습니다: " + scheduleId));
    }

    /** 소유자 + 기간 조회. */
    @Transactional(readOnly = true)
    public List<Schedule> findInRange(UUID ownerId, LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !to.isAfter(from)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from < to 필수");
        }
        return repository.findOwnedInRange(ownerId, from, to);
    }

    /** 일정 수정 + 참여자 재정의 (전체 교체 패턴). */
    @Transactional
    public Schedule update(UUID scheduleId, ScheduleRequest req) {
        Schedule schedule = findById(scheduleId);
        try {
            schedule.update(req.title(), req.description(), req.startsAt(), req.endsAt(), req.status());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
        if (req.participantIds() != null) {
            // 전체 교체 — 기존 제거 후 신규 추가
            List<UUID> existing = schedule.getParticipantsView().stream()
                    .map(p -> p.getParticipantId())
                    .toList();
            for (UUID id : existing) {
                if (!req.participantIds().contains(id)) {
                    schedule.removeParticipant(id);
                }
            }
            for (UUID id : req.participantIds()) {
                if (!userClient.exists(id)) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "참여자 미존재: " + id);
                }
                schedule.addParticipant(id);
            }
        }
        return schedule;
    }

    /** 참여자 단건 추가 — 명시 endpoint. */
    @Transactional
    public Schedule addParticipant(UUID scheduleId, UUID participantId) {
        if (!userClient.exists(participantId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "참여자 미존재: " + participantId);
        }
        Schedule schedule = findById(scheduleId);
        schedule.addParticipant(participantId);
        return schedule;
    }

    /** soft-delete. */
    @Transactional
    public void delete(UUID scheduleId, String actorUserId) {
        Schedule schedule = findById(scheduleId);
        schedule.markDeleted(actorUserId);
    }
}
