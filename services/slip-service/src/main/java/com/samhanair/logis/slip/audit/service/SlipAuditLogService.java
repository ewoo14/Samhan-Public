package com.samhanair.logis.slip.audit.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.audit.domain.SlipAuditLog;
import com.samhanair.logis.slip.audit.repository.SlipAuditLogRepository;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.realtime.SlipRealtimeBroker;
import com.samhanair.logis.slip.repository.SlipRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 슬립 audit overlay 라이프사이클 — PR-H2 (Phase 12 Step 2).
 *
 * <p>책임 경계:
 * <ul>
 *   <li>{@link #recordOverlayPatch} — 단일 필드 변경 audit 1행 + SSE broadcast
 *       ({@code slip:edit}). diff 계산은 호출자가 수행 (service 가 entity old 값 snapshot).</li>
 *   <li>{@link #recordBatch} — 다중 필드 같은 revision_no 로 일괄 기록 (editHeader 등).
 *       slip.incrementRevision 1회 호출 후 모든 changes 에 동일 revisionNo 적용.</li>
 *   <li>{@link #listBySlip} — FE timeline 표시 (최신 revision 우선).</li>
 *   <li>{@link #revertToRevision} — 특정 revision 으로 복원. 복원 자체도 신규 audit row 1행
 *       (action="REVERT", new_value=과거 값) 으로 영원 추적.</li>
 * </ul>
 *
 * <p><b>SSE event 형식</b> ({@code "slip:edit"}):
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
 *
 * <p><b>UUID 비공개</b>: payload 에 actorId 포함은 FE 색상 hash 의 결정성을 위해 (한 사용자 =
 * 항상 같은 색). 사용자 화면 표시는 actorName 만 사용. UUID 자체는 화면에 출력 금지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlipAuditLogService {

    /** SSE event name — 슬립 본문 수정. */
    public static final String EVENT_SLIP_EDIT = "slip:edit";

    /** SSE event name — audit revert (과거 값으로 되돌림). */
    public static final String EVENT_SLIP_REVERTED = "slip:reverted";

    private final SlipAuditLogRepository auditLogRepository;
    private final SlipRepository slipRepository;
    private final SlipRealtimeBroker broker;

    /**
     * 단일 필드 변경 audit 기록 + SSE broadcast.
     *
     * <p>호출 순서: service 레이어에서 (1) slip.readOverlayField(name) 으로 oldValue snapshot,
     * (2) slip.applyOverlayPatch(name, newValue) 로 mutation, (3) 본 메서드 호출.
     *
     * @param slipId 대상 슬립
     * @param actorId 수정자 UUID (audit/감사용)
     * @param actorName 수정자 표시명 (UUID 비공개 가드)
     * @param actorColor FE 색상 hex (선택)
     * @param fieldName 변경된 필드 식별자
     * @param oldValue 이전 값 (선택)
     * @param newValue 새 값 (선택)
     * @return 영속화된 SlipAuditLog
     * @throws BusinessException(NOT_FOUND) 슬립 미존재
     */
    @Transactional
    public SlipAuditLog recordOverlayPatch(UUID slipId, UUID actorId, String actorName,
                                           String actorColor, String fieldName,
                                           String oldValue, String newValue) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "슬립을 찾을 수 없습니다: " + slipId));
        int revisionNo = slip.incrementRevision();
        SlipAuditLog saved = auditLogRepository.save(SlipAuditLog.record(
                slipId, revisionNo, actorId, actorName, actorColor,
                fieldName, oldValue, newValue));
        broker.publish(slipId, EVENT_SLIP_EDIT, buildEventPayload(
                revisionNo, actorId, actorName, actorColor,
                List.of(new ChangeEntry(fieldName, oldValue, newValue))));
        return saved;
    }

    /**
     * 다중 필드 변경 일괄 audit 기록 + 단일 SSE broadcast.
     *
     * <p>같은 mutation (예: editHeader 한 번) 의 다중 필드 변경은 같은 revision_no 를 공유한다.
     * service 레이어가 changes 리스트를 빌드하여 본 메서드 호출 — slip 의 revisionCount 는
     * 단 1회만 +1.
     *
     * @param slipId 대상 슬립
     * @param actorId 수정자 UUID
     * @param actorName 수정자 표시명
     * @param actorColor FE 색상 hex (선택)
     * @param changes 변경된 필드 리스트 (1건 이상)
     * @return 영속화된 audit log 리스트 (입력 순서 유지)
     * @throws BusinessException(NOT_FOUND) 슬립 미존재
     * @throws BusinessException(INVALID_INPUT) changes 가 비어있을 때
     */
    @Transactional
    public List<SlipAuditLog> recordBatch(UUID slipId, UUID actorId, String actorName,
                                          String actorColor, List<ChangeEntry> changes) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "changes 가 비어있습니다 — audit 기록할 변경이 없습니다");
        }
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "슬립을 찾을 수 없습니다: " + slipId));
        int revisionNo = slip.incrementRevision();
        List<SlipAuditLog> saved = new ArrayList<>(changes.size());
        for (ChangeEntry change : changes) {
            saved.add(auditLogRepository.save(SlipAuditLog.record(
                    slipId, revisionNo, actorId, actorName, actorColor,
                    change.fieldName(), change.oldValue(), change.newValue())));
        }
        broker.publish(slipId, EVENT_SLIP_EDIT, buildEventPayload(
                revisionNo, actorId, actorName, actorColor, changes));
        return saved;
    }

    /**
     * 슬립별 audit log 전체 — FE timeline 표시. 최신 revision 우선.
     *
     * @param slipId 대상 슬립
     * @return 최신순 audit log (soft-deleted 자동 제외)
     */
    @Transactional(readOnly = true)
    public List<SlipAuditLog> listBySlip(UUID slipId) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        return auditLogRepository.findBySlipIdOrderByRevisionNoDescChangedAtDesc(slipId);
    }

    /**
     * 특정 revision 으로 복원 (undo) — revert 자체도 신규 revision 으로 audit 기록.
     *
     * <p>알고리즘:
     * <ol>
     *   <li>해당 revision 의 audit row 들 조회 — 각 row 의 oldValue 가 "복원할 과거 상태"</li>
     *   <li>현 시점 slip 값 snapshot → revertedChanges = [{name, currentValue, oldValueAtRev}]</li>
     *   <li>slip.applyOverlayPatch 로 oldValue 복원 (마감 lock 가드)</li>
     *   <li>{@link #recordBatch} 로 신규 revisionNo 의 audit row 들 INSERT
     *       — fieldName 은 동일, oldValue=현 값, newValue=과거 값</li>
     *   <li>SSE event {@code slip:reverted} broadcast (별도 event name 으로 FE 분기)</li>
     * </ol>
     *
     * @param slipId 대상 슬립
     * @param targetRevisionNo 복원 대상 revision 번호 (1 이상)
     * @param actorId 복원 작업자 UUID
     * @param actorName 복원 작업자 표시명
     * @param actorColor FE 색상 hex (선택)
     * @return 신규 INSERT 된 audit row 리스트
     * @throws BusinessException(NOT_FOUND) 슬립 미존재 또는 해당 revision audit 미존재
     * @throws BusinessException(INVALID_INPUT) targetRevisionNo &lt; 1
     * @throws BusinessException(CONFLICT) 마감 lock 적용 슬립
     */
    @Transactional
    public List<SlipAuditLog> revertToRevision(UUID slipId, int targetRevisionNo,
                                               UUID actorId, String actorName, String actorColor) {
        Objects.requireNonNull(slipId, "slipId 는 필수입니다");
        if (targetRevisionNo < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "targetRevisionNo 는 1 이상이어야 합니다: " + targetRevisionNo);
        }
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "슬립을 찾을 수 없습니다: " + slipId));
        List<SlipAuditLog> targetRows = auditLogRepository.findBySlipIdAndRevisionNo(
                slipId, targetRevisionNo);
        if (targetRows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "해당 revision 의 audit log 가 없습니다: revisionNo=" + targetRevisionNo);
        }

        // 1) 현 값 snapshot + slip 복원 (마감 lock 가드는 applyOverlayPatch 내부)
        List<ChangeEntry> revertedChanges = new ArrayList<>(targetRows.size());
        int newRevisionNo = slip.incrementRevision();
        for (SlipAuditLog row : targetRows) {
            String fieldName = row.getFieldName();
            String currentValue = slip.readOverlayField(fieldName);
            String restoreTo = row.getOldValue();
            slip.applyOverlayPatch(fieldName, restoreTo);
            revertedChanges.add(new ChangeEntry(fieldName, currentValue, restoreTo));
        }

        // 2) revert 자체를 신규 audit 로 기록 (감사 영원 보존)
        List<SlipAuditLog> saved = new ArrayList<>(revertedChanges.size());
        for (ChangeEntry change : revertedChanges) {
            saved.add(auditLogRepository.save(SlipAuditLog.record(
                    slipId, newRevisionNo, actorId, actorName, actorColor,
                    change.fieldName(), change.oldValue(), change.newValue())));
        }

        // 3) SSE broadcast — slip:reverted (FE 가 별도 분기 — "되돌리기 by 홍길동" 표시)
        Map<String, Object> payload = buildEventPayload(
                newRevisionNo, actorId, actorName, actorColor, revertedChanges);
        payload.put("revertedFromRevisionNo", targetRevisionNo);
        broker.publish(slipId, EVENT_SLIP_REVERTED, payload);

        log.info("[PR-H2] slip {} revert revision={} → 신규 revision={} ({} 필드 복원)",
                slipId, targetRevisionNo, newRevisionNo, saved.size());
        return saved;
    }

    /**
     * SSE event payload 빌더 — TM 보완 #2 (ArgumentCaptor payload 검증) 가 본 메서드의 출력을
     * 검증한다. 일관 schema 보장.
     */
    private Map<String, Object> buildEventPayload(int revisionNo, UUID actorId, String actorName,
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

    /**
     * 변경 1건의 record 컨테이너 — 다중 필드 batch 입력 + SSE payload 일관 schema 의 단위.
     *
     * @param fieldName 필드 식별자 (≤50자)
     * @param oldValue 이전 값 (null 가능)
     * @param newValue 새 값 (null 가능, 둘 다 null 은 audit factory 가 거부)
     */
    public record ChangeEntry(String fieldName, String oldValue, String newValue) {
    }
}
