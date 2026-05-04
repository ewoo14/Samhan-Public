package com.samhanair.logis.slip.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 전표 헤더 (plan §3.1) — Single Table Inheritance 로 출고/입고 1 테이블 통합 (Q1 결정).
 *
 * <p>상태 머신 (Q5: 낙관적 락 + 상태 전이 가드):
 * <pre>
 *   DRAFT → SAVED → SENT → ACCEPTED → PROCESSING → INSPECTING → COMPLETED
 *     - 출고: COMPLETED → SHIPPING → DELIVERED → CONFIRMED
 *     - 입고: COMPLETED → CONFIRMED (ship/deliver 단계 스킵)
 *   SENT/ACCEPTED/INSPECTING → REJECTED 가능 (검수자 거부)
 *   DRAFT/SAVED/SENT → CANCELED 가능
 * </pre>
 *
 * <p>Slice A (sales-polish-2) 신규 단계 INSPECTING — 검수자(창고원/INSPECTOR)가 출고 picking
 * 결과 확인 후 COMPLETED 로 전이. INSPECTING 트랜지션 시 inspectorUserId / inspectorSignedAt
 * 자동 기입 (작업지시서 결재란 검수인 셀 자동 표시).
 *
 * <p>모든 잘못된 상태 전이는 {@link BusinessException}({@link ErrorCode#CONFLICT}) 으로 통일.
 *
 * <p>낙관적 락: {@link Version} 으로 동시 mutation 충돌 감지 — 서비스 레이어에서 OptimisticLock 예외를
 * CONFLICT 로 매핑한다.
 */
@Entity
@Getter
@Table(name = "slips")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Slip extends BaseEntity {

    private static final Set<SlipStatus> EDITABLE_STATUSES =
            EnumSet.of(SlipStatus.DRAFT, SlipStatus.SAVED);
    private static final Set<SlipStatus> CANCELABLE_STATUSES =
            EnumSet.of(SlipStatus.DRAFT, SlipStatus.SAVED, SlipStatus.SENT);

    /** Slice C — 인수자 서명을 받을 수 있는 단계 (Plan §1.3 라이프사이클 표). */
    private static final Set<SlipStatus> SIGNABLE_STATUSES =
            EnumSet.of(SlipStatus.INSPECTING, SlipStatus.COMPLETED, SlipStatus.SHIPPING);

    /** Slice C — share token base64url 48 bytes = 64자 (DeliveryBatch 와 동일 룰). */
    private static final SecureRandom SIGNATURE_RNG = new SecureRandom();
    private static final int SIGNATURE_TOKEN_BYTE_LENGTH = 48;
    /** Slice C — share token 만료: +30일 (Plan §7 Q4 결정). */
    private static final int SIGNATURE_SHARE_EXPIRY_DAYS = 30;

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "slip_type", nullable = false, length = 20)
    private SlipType slipType;

    @Column(name = "slip_no", nullable = false, length = 30)
    private String slipNo;

    @Column(name = "slip_date", nullable = false)
    private LocalDate slipDate;

    @Column(name = "seq_no", nullable = false)
    private int seqNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SlipStatus status;

    @Column(name = "partner_id")
    private UUID partnerId;

    @Column(name = "partner_name", length = 100)
    private String partnerName;

    @Column(name = "source_warehouse_id")
    private UUID sourceWarehouseId;

    @Column(name = "destination_warehouse_id")
    private UUID destinationWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_tag", length = 30)
    private DeliveryTag deliveryTag;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "requester_id", nullable = false, length = 50)
    private String requesterId;

    @Column(name = "accepted_by", length = 50)
    private String acceptedBy;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 출고인 user-id — Slice A (sales-polish-2) ACCEPTED 트랜지션 시 자동 기입.
     * 사용자 피드백 #9: 작업지시서 결재란 출고인 셀을 acceptedBy 와 별도로 정확히 표시하기 위함.
     * 본 슬라이스 한정 도메인 명시 정책 예외 (Q4=A).
     */
    @Column(name = "dispatcher_user_id", length = 50)
    private String dispatcherUserId;

    /** 출고인 자동 서명 시각 (ACCEPTED 트랜지션 timestamp). */
    @Column(name = "dispatcher_signed_at")
    private LocalDateTime dispatcherSignedAt;

    /**
     * 검수인 user-id — Slice A (sales-polish-2) INSPECTING 트랜지션 시 자동 기입.
     * 4-eye 검증 패턴: 출고인(PROCESSING 시작자) 과 다른 검수인이 picking 결과 확인.
     */
    @Column(name = "inspector_user_id", length = 50)
    private String inspectorUserId;

    /** 검수인 자동 서명 시각 (INSPECTING 트랜지션 timestamp). */
    @Column(name = "inspector_signed_at")
    private LocalDateTime inspectorSignedAt;

    /**
     * 배송 기사명 — Slice B (notification-slice-B) 신규.
     * DRAFT/SAVED 단계에서 입력. {@link #editHeader} 로 갱신.
     * 같은 driverPhone + slipDate 슬립이 자동으로 단일 DeliveryBatch 로 그룹된다.
     */
    @Column(name = "driver_name", length = 50)
    private String driverName;

    /**
     * 배송 기사 연락처 — Slice B (notification-slice-B) 신규.
     * 한국 휴대폰 패턴 ({@code 010-XXXX-XXXX}) 권장 (FE PhoneInput 검증).
     * DeliveryBatch 자동 그룹의 그룹 키.
     */
    @Column(name = "driver_phone", length = 20)
    private String driverPhone;

    /**
     * 배송 배치 FK — Slice B (notification-slice-B) 신규. nullable.
     * DeliveryBatch.addSlip / removeSlip 도메인 메서드를 통해서만 변경되어야 한다 (양방향 일관성).
     * 본 슬립이 어떤 배송 배치(=기사 SMS 단위) 에 속하는지 식별.
     */
    @Column(name = "delivery_batch_id")
    private UUID deliveryBatchId;

    // ---------- Slice C (signature-slice-C Plan §1.1) — 인수자 전자서명 7 필드 ----------

    /**
     * 인수자 서명 시각 — Slice C. {@link #recordSignature} 호출 시 서버 timestamp 로 기록.
     * null 이면 서명 미완료 상태.
     */
    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    /** 인수자명 — Slice C. 자유 입력 (1~50자). */
    @Column(name = "signer_name", length = 50)
    private String signerName;

    /**
     * 서명 PNG 바이너리 — Slice C. ≤50KB (서비스 레이어 가드).
     * Q2 결정: DB bytea (월 1만건 미만 단계) — Phase 6+ MinIO 마이그.
     */
    @Lob
    @Column(name = "signature_png")
    private byte[] signaturePng;

    /**
     * SHA-256 hex 64자 — Slice C. 서버에서 PNG bytes 로 재계산하여 client hash 와 비교 검증.
     * mismatch 면 INVALID_INPUT (400). 무결성 1차 수단.
     */
    @Column(name = "signature_hash", length = 64)
    private String signatureHash;

    /** 서명 채널 — Slice C. {@link SignatureChannel#MOBILE_CANVAS} / PAPER_SCAN. */
    @Enumerated(EnumType.STRING)
    @Column(name = "signature_channel", length = 20)
    private SignatureChannel signatureChannel;

    /**
     * 인수자 share 토큰 — Slice C. base64url 64자, partial UNIQUE (NULL 허용).
     * 인수자 view 공개 endpoint {@code GET /public/signatures/{shareToken}} 진입 키.
     */
    /**
     * NOTE: {@code unique=true} 미사용 — V5 SQL 은 partial UNIQUE INDEX
     * ({@code WHERE signature_share_token IS NOT NULL}) 로 NULL 허용 + 발급 시
     * 유일성 강제. JPA inline unique 는 full UNIQUE constraint 를 생성하라는
     * 의미라 Hibernate {@code validate} 가 partial index 와 mismatch 로 거부.
     */
    @Column(name = "signature_share_token", length = 64)
    private String signatureShareToken;

    /**
     * Share 토큰 만료 시각 — Slice C. {@code signedAt + 30일} (Q4 결정).
     * 만료 후 인수자 view 410 GONE.
     */
    @Column(name = "signature_share_expires_at")
    private LocalDateTime signatureShareExpiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "slip", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<SlipLine> lines = new ArrayList<>();

    private Slip(SlipType slipType, String slipNo, LocalDate slipDate, int seqNo,
                 UUID sourceWarehouseId, UUID destinationWarehouseId,
                 UUID partnerId, String partnerName,
                 DeliveryTag deliveryTag, String memo, String requesterId) {
        this.slipType = slipType;
        this.slipNo = slipNo;
        this.slipDate = slipDate;
        this.seqNo = seqNo;
        this.sourceWarehouseId = sourceWarehouseId;
        this.destinationWarehouseId = destinationWarehouseId;
        this.partnerId = partnerId;
        this.partnerName = partnerName;
        this.deliveryTag = deliveryTag;
        this.memo = memo;
        this.requesterId = requesterId;
        this.status = SlipStatus.DRAFT;
        this.version = 0L;
    }

    /**
     * 출고전표 생성 — sourceWarehouseId 필수, destinationWarehouseId 는 거래처 직배 등 시 null 가능.
     *
     * @param slipNo 채번된 전표번호 ({@code yyyy/MM/dd-NNN})
     * @param slipDate 전표 날짜
     * @param seqNo 같은 날짜 내 순번 (1 이상)
     * @param sourceWarehouseId 출고지 창고 UUID (필수)
     * @param destinationWarehouseId 도착지 창고 UUID (선택)
     * @param partnerId 거래처 UUID (선택, 첫 슬라이스에서 검증 안 함)
     * @param partnerName 거래처명 snapshot (선택)
     * @param deliveryTag 배송 태그 (선택, OUTBOUND 호환만 허용)
     * @param memo 메모 (선택)
     * @param requesterId 요청자 user-id (필수)
     * @return DRAFT 상태의 신규 출고전표
     * @throws IllegalArgumentException sourceWarehouseId 가 null 이거나 deliveryTag 의 direction 이 INBOUND 일 때
     */
    public static Slip createOutbound(String slipNo, LocalDate slipDate, int seqNo,
                                      UUID sourceWarehouseId, UUID destinationWarehouseId,
                                      UUID partnerId, String partnerName,
                                      DeliveryTag deliveryTag, String memo, String requesterId) {
        if (sourceWarehouseId == null) {
            throw new IllegalArgumentException("출고전표는 sourceWarehouseId 가 필수입니다");
        }
        validateTagDirection(deliveryTag, SlipType.OUTBOUND);
        return new Slip(SlipType.OUTBOUND, slipNo, slipDate, seqNo,
                sourceWarehouseId, destinationWarehouseId,
                partnerId, partnerName, deliveryTag, memo, requesterId);
    }

    /**
     * 입고전표 생성 — destinationWarehouseId 필수 (도착지). sourceWarehouseId 는 항상 null.
     *
     * @param slipNo 채번된 전표번호 ({@code yyyy/MM/dd-NNN})
     * @param slipDate 전표 날짜
     * @param seqNo 같은 날짜 내 순번
     * @param destinationWarehouseId 입고 창고 UUID (필수)
     * @param partnerId 거래처 UUID (선택)
     * @param partnerName 거래처명 snapshot (선택)
     * @param deliveryTag 배송 태그 (선택, INBOUND 호환만 허용)
     * @param memo 메모 (선택)
     * @param requesterId 요청자 user-id (필수)
     * @return DRAFT 상태의 신규 입고전표
     * @throws IllegalArgumentException destinationWarehouseId 가 null 이거나 deliveryTag 의 direction 이 OUTBOUND 일 때
     */
    public static Slip createInbound(String slipNo, LocalDate slipDate, int seqNo,
                                     UUID destinationWarehouseId,
                                     UUID partnerId, String partnerName,
                                     DeliveryTag deliveryTag, String memo, String requesterId) {
        if (destinationWarehouseId == null) {
            throw new IllegalArgumentException("입고전표는 destinationWarehouseId 가 필수입니다");
        }
        validateTagDirection(deliveryTag, SlipType.INBOUND);
        return new Slip(SlipType.INBOUND, slipNo, slipDate, seqNo,
                null, destinationWarehouseId,
                partnerId, partnerName, deliveryTag, memo, requesterId);
    }

    private static void validateTagDirection(DeliveryTag tag, SlipType slipType) {
        if (tag != null && tag.getDirection() != slipType) {
            throw new IllegalArgumentException(
                    "배송 태그 " + tag.name() + "(" + tag.getDirection() + ") 는 "
                            + slipType + " 전표에 사용할 수 없습니다");
        }
    }

    /**
     * 라인 1건 추가 — 양방향 연관관계 유지. 서비스 레이어에서 DRAFT/SAVED 단계 가드 후 호출해야 한다.
     *
     * @param line {@link SlipLine#create} 로 생성된 라인 (slip 참조 이미 설정)
     */
    public void addLine(SlipLine line) {
        this.lines.add(line);
    }

    /**
     * 라인 1건 제거 (orphan removal). DRAFT/SAVED 단계에서만 호출되어야 함 (서비스 레이어 가드).
     *
     * @param line 제거할 라인 인스턴스
     * @return 제거 성공 여부
     */
    public boolean removeLine(SlipLine line) {
        return this.lines.remove(line);
    }

    /**
     * 헤더 부분 수정 — DRAFT 또는 SAVED 단계에서만 허용. null 이 아닌 인자만 적용.
     *
     * <p>Slice B (notification-slice-B): {@code driverName}, {@code driverPhone} 2 인자 신규
     * 추가 — 출고 슬립의 배송 기사 정보 입력. 같은 driverPhone + slipDate 슬립이 자동으로
     * 단일 DeliveryBatch 로 그룹된다 (관리자 화면 "링크발송" 메뉴).
     *
     * @param partnerId 거래처 UUID (null 이면 보존)
     * @param partnerName 거래처명 (null 이면 보존)
     * @param deliveryTag 배송 태그 (null 이면 보존). slipType 호환 검증.
     * @param memo 메모 (null 이면 보존)
     * @param driverName 배송 기사명 (null 이면 보존, 빈 문자열은 그대로 저장)
     * @param driverPhone 배송 기사 연락처 (null 이면 보존)
     * @throws BusinessException(CONFLICT) 현재 상태가 DRAFT/SAVED 가 아닐 때
     * @throws IllegalArgumentException deliveryTag 의 direction 이 slipType 과 불일치
     */
    public void editHeader(UUID partnerId, String partnerName, DeliveryTag deliveryTag, String memo,
                           String driverName, String driverPhone) {
        if (!EDITABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "수정 가능한 상태가 아닙니다: " + this.status);
        }
        if (deliveryTag != null) {
            validateTagDirection(deliveryTag, this.slipType);
            this.deliveryTag = deliveryTag;
        }
        if (partnerId != null) {
            this.partnerId = partnerId;
        }
        if (partnerName != null) {
            this.partnerName = partnerName;
        }
        if (memo != null) {
            this.memo = memo;
        }
        if (driverName != null) {
            this.driverName = driverName;
        }
        if (driverPhone != null) {
            this.driverPhone = driverPhone;
        }
    }

    /**
     * 슬립 생성 시 driver 정보 직접 설정 — 서비스 레이어에서 createOutbound/Inbound 직후 호출.
     * 별도 mutation API 가 아닌 생성 시점 보조 setter (CreateSlipRequest 의 driverName/Phone 적용용).
     * Slice B (notification-slice-B) 신규.
     *
     * @param driverName 배송 기사명
     * @param driverPhone 배송 기사 연락처
     */
    public void setDriverContact(String driverName, String driverPhone) {
        this.driverName = driverName;
        this.driverPhone = driverPhone;
    }

    /**
     * DeliveryBatch 연결 — DeliveryBatch.addSlip 내부에서만 호출 (package-private 의도).
     * Slice B (notification-slice-B) 신규.
     *
     * @param batchId 배치 UUID
     */
    public void assignToBatch(UUID batchId) {
        this.deliveryBatchId = batchId;
    }

    /**
     * DeliveryBatch 연결 해제 — DeliveryBatch.removeSlip 내부에서만 호출.
     * Slice B (notification-slice-B) 신규.
     */
    public void clearBatch() {
        this.deliveryBatchId = null;
    }

    /**
     * 작성중 → 저장완료 전이. DRAFT 에서만 허용.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 DRAFT 가 아닐 때
     */
    public void save() {
        requireStatus(SlipStatus.DRAFT);
        this.status = SlipStatus.SAVED;
    }

    /**
     * 저장완료 → 전송완료 전이. SAVED 에서만 허용.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 SAVED 가 아닐 때
     */
    public void send() {
        requireStatus(SlipStatus.SAVED);
        this.status = SlipStatus.SENT;
    }

    /**
     * 전송완료 → 수락 전이. SENT 에서만 허용. acceptedBy, acceptedAt 기록 +
     * Slice A (sales-polish-2): dispatcherUserId, dispatcherSignedAt 자동 기입
     * (작업지시서 결재란 출고인 셀 자동 표시 — 사용자 피드백 #9).
     *
     * @param acceptorUserId 수락자 user-id (창고/재고원). 출고인 자동 기입에도 동일 사용.
     * @throws BusinessException(CONFLICT) 현재 상태가 SENT 가 아닐 때
     */
    public void accept(String acceptorUserId) {
        requireStatus(SlipStatus.SENT);
        LocalDateTime now = LocalDateTime.now();
        this.status = SlipStatus.ACCEPTED;
        this.acceptedBy = acceptorUserId;
        this.acceptedAt = now;
        this.dispatcherUserId = acceptorUserId;
        this.dispatcherSignedAt = now;
    }

    /**
     * 수락 → 처리중 전이. ACCEPTED 에서만 허용.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 ACCEPTED 가 아닐 때
     */
    public void process() {
        requireStatus(SlipStatus.ACCEPTED);
        this.status = SlipStatus.PROCESSING;
    }

    /**
     * 처리중 → 검수중 전이 (출고 완료). PROCESSING 에서만 허용. **Slice A hotfix**: 사용자 명시
     * "출고 완료되면 검수 단계에 돌입" — 즉 complete() 가 출고 완료를 의미하며 검수 단계로 진입.
     * InventoryClient.deduct 는 SlipService.complete 가 본 도메인 메서드 호출 직후 처리.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 PROCESSING 이 아닐 때
     */
    public void complete() {
        requireStatus(SlipStatus.PROCESSING);
        this.status = SlipStatus.INSPECTING;
        // completedAt 은 검수 완료(inspect) 시점에 기록 — "처리완료" 의미상 검수까지 통과해야 진정한 완료.
    }

    /**
     * 검수중 → 처리완료 전이 (검수 완료). INSPECTING 에서만 허용. **Slice A hotfix**: 사용자 명시
     * "검수 단계 전표를 수락하고 확인 후 완료하면 검수 완료 처리" — 즉 inspect() 가 검수 완료를 의미.
     * inspectorUserId, inspectorSignedAt 자동 기입 + completedAt 기록 (결재란 검수인 셀 + 완료 시각).
     *
     * @param inspectorUserId 검수자 user-id (창고/검수/관리자/마스터). 4-eye 패턴 권장 —
     *     일반적으로 dispatcherUserId 와 다른 사용자 (단, 도메인 강제 X — 운영 정책).
     * @throws BusinessException(CONFLICT) 현재 상태가 INSPECTING 이 아닐 때
     */
    public void inspect(String inspectorUserId) {
        requireStatus(SlipStatus.INSPECTING);
        this.status = SlipStatus.COMPLETED;
        this.inspectorUserId = inspectorUserId;
        this.inspectorSignedAt = LocalDateTime.now();
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 처리완료 → 배송중 전이 (출고전표 한정). COMPLETED 에서만 허용.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 COMPLETED 가 아니거나, slipType 이 INBOUND 일 때
     */
    public void ship() {
        if (this.slipType != SlipType.OUTBOUND) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "배송 단계는 출고전표에만 적용됩니다");
        }
        requireStatus(SlipStatus.COMPLETED);
        this.status = SlipStatus.SHIPPING;
    }

    /**
     * 배송중 → 배송완료 전이 (출고전표 한정). SHIPPING 에서만 허용.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 SHIPPING 이 아니거나, slipType 이 INBOUND 일 때
     */
    public void deliver() {
        if (this.slipType != SlipType.OUTBOUND) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "배송 단계는 출고전표에만 적용됩니다");
        }
        requireStatus(SlipStatus.SHIPPING);
        this.status = SlipStatus.DELIVERED;
    }

    /**
     * 확정 전이 — 출고전표는 DELIVERED 에서, 입고전표는 COMPLETED 에서. confirmedAt 기록.
     *
     * @throws BusinessException(CONFLICT) 출고가 DELIVERED 가 아니거나 입고가 COMPLETED 가 아닐 때
     */
    public void confirm() {
        if (this.slipType == SlipType.OUTBOUND) {
            requireStatus(SlipStatus.DELIVERED);
        } else {
            requireStatus(SlipStatus.COMPLETED);
        }
        this.status = SlipStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * 반려 — SENT, ACCEPTED 또는 INSPECTING 에서만 허용. 사유 텍스트가 있으면 메모 앞에 prepend.
     *
     * <p>Slice A (sales-polish-2): INSPECTING 단계도 reject 허용 — 검수자가 picking 결과 거부.
     * cancel 은 ACCEPTED 부터는 거부되므로 (CANCELABLE_STATUSES 참조) INSPECTING 단계의
     * 거부 경로는 reject 만 가능.
     *
     * @param reasonText 반려 사유 (null/blank 이면 메모 변경 없음)
     * @throws BusinessException(CONFLICT) 현재 상태가 SENT/ACCEPTED/INSPECTING 셋 다 아닐 때
     */
    public void reject(String reasonText) {
        if (this.status != SlipStatus.SENT
                && this.status != SlipStatus.ACCEPTED
                && this.status != SlipStatus.INSPECTING) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "반려 가능한 상태가 아닙니다: " + this.status);
        }
        this.status = SlipStatus.REJECTED;
        if (reasonText != null && !reasonText.isBlank()) {
            String prefix = "[반려: " + reasonText + "] ";
            this.memo = (this.memo == null || this.memo.isBlank())
                    ? prefix.trim()
                    : prefix + this.memo;
        }
    }

    /**
     * 취소 — DRAFT/SAVED/SENT 에서만 허용. 그 외 단계는 운영 절차 별도 (현 슬라이스 미구현).
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 취소 가능 단계 밖일 때
     */
    public void cancel() {
        if (!CANCELABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "취소 가능한 상태가 아닙니다: " + this.status);
        }
        this.status = SlipStatus.CANCELED;
    }

    // ---------- Slice C (signature-slice-C) — 인수자 전자서명 라이프사이클 ----------

    /**
     * 인수자 서명 등록 — Slice C (signature-slice-C Plan §1.3 Layer 4 라이프사이클 표).
     *
     * <p>전이 가드: 현재 status 가 {@link SlipStatus#INSPECTING} / {@link SlipStatus#COMPLETED} /
     * {@link SlipStatus#SHIPPING} 중 하나여야 함. 그 외 단계는 CONFLICT (409).
     *
     * <p>부수효과 (현재 트랜잭션 내):
     * <ol>
     *   <li>{@code signedAt = now()}</li>
     *   <li>{@code signerName, signaturePng, signatureHash, signatureChannel} 갱신</li>
     *   <li>{@code signatureShareToken} 신규 발급 (base64url 64자, 매 호출마다 재생성)</li>
     *   <li>{@code signatureShareExpiresAt = signedAt + 30일} (Q4)</li>
     *   <li>SlipStatus 자체는 **변경 없음** — 서명은 라이프사이클 직교 메타 (Q3)</li>
     * </ol>
     *
     * <p>audit 적재는 service 레이어에서 별도로 처리 (도메인은 entity 만 반환, service 가 repository 로
     * 저장). audit log 는 entity 가 아니라 service 에서 INSERT — 도메인은 순수 mutation 만.
     *
     * @param signerName 인수자명 (1~50자, 필수)
     * @param png 서명 PNG bytes (필수, ≤50KB — service 레이어 가드)
     * @param hash 서명 SHA-256 hex 64자 (서버 재계산값과 일치해야 함 — service 레이어 검증)
     * @param channel 서명 채널 (필수)
     * @throws BusinessException(CONFLICT) 현재 상태가 SIGNABLE_STATUSES 안에 없을 때
     * @throws IllegalArgumentException signerName/png/hash/channel null/blank 또는 길이 위반
     */
    public void recordSignature(String signerName, byte[] png, String hash, SignatureChannel channel) {
        if (!SIGNABLE_STATUSES.contains(this.status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "서명 가능한 단계가 아닙니다 (현재: " + this.status
                            + ", 필요: INSPECTING/COMPLETED/SHIPPING)");
        }
        if (signerName == null || signerName.isBlank()) {
            throw new IllegalArgumentException("signerName 은 필수입니다");
        }
        if (signerName.length() > 50) {
            throw new IllegalArgumentException("signerName 은 최대 50자입니다");
        }
        if (png == null || png.length == 0) {
            throw new IllegalArgumentException("signaturePng 은 필수입니다");
        }
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("signatureHash 는 필수입니다");
        }
        if (channel == null) {
            throw new IllegalArgumentException("signatureChannel 은 필수입니다");
        }

        LocalDateTime now = LocalDateTime.now();
        this.signedAt = now;
        this.signerName = signerName;
        this.signaturePng = png;
        this.signatureHash = hash;
        this.signatureChannel = channel;
        this.signatureShareToken = generateShareToken();
        this.signatureShareExpiresAt = now.plusDays(SIGNATURE_SHARE_EXPIRY_DAYS);
    }

    /**
     * 인수자 서명 무효화 — Slice C (signature-slice-C Plan §1.3 라이프사이클 표).
     * MASTER 권한자만 호출 (service 레이어 PreAuthorize 가드).
     *
     * <p>전이 가드: {@code signedAt != null} 일 때만 호출 가능. 미서명 상태에서 호출 시 CONFLICT.
     *
     * <p>부수효과:
     * <ol>
     *   <li>5필드 모두 NULL: signedAt / signerName / signaturePng / signatureHash / signatureChannel</li>
     *   <li>share 토큰/만료 시각도 NULL — share URL 즉시 무효</li>
     *   <li>SlipStatus 변경 없음</li>
     *   <li>audit 적재는 service 레이어 (INVALIDATE action + reason + actorUserId)</li>
     * </ol>
     *
     * <p>service 레이어가 audit INSERT 시 사용할 직전 hash/signerName 정보는 본 메서드 호출 **전에**
     * 외부에서 snapshot 해야 함 (호출 후 NULL 이 됨). service 레이어에서 호출 순서 가드.
     *
     * @param reason 무효화 사유 (필수, ≤500자)
     * @throws BusinessException(CONFLICT) signedAt 가 null 일 때
     * @throws IllegalArgumentException reason null/blank 또는 500자 초과
     */
    public void invalidateSignature(String reason) {
        if (this.signedAt == null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "서명되지 않은 슬립은 무효화할 수 없습니다");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason 은 필수입니다");
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException("reason 은 최대 500자입니다");
        }

        this.signedAt = null;
        this.signerName = null;
        this.signaturePng = null;
        this.signatureHash = null;
        this.signatureChannel = null;
        this.signatureShareToken = null;
        this.signatureShareExpiresAt = null;
    }

    /**
     * 인수자 share 토큰이 만료되었는지 검증 — 인수자 view 공개 endpoint 가드.
     *
     * @return true 면 만료, false 이면 유효 또는 미서명 (token 자체가 null)
     */
    public boolean isSignatureShareExpired() {
        if (this.signatureShareExpiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(this.signatureShareExpiresAt);
    }

    /** 서명이 등록된 슬립인지 — admin 화면 / FE 표시 분기 헬퍼. */
    public boolean isSigned() {
        return this.signedAt != null;
    }

    private static String generateShareToken() {
        byte[] bytes = new byte[SIGNATURE_TOKEN_BYTE_LENGTH];
        SIGNATURE_RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 배송 태그가 야적/지방 등 {@code autoMemo=true} 인 경우, {@code "{slipDate}상차 {slipDate+1}하차"}
     * 형식의 자동 메모를 기존 메모 앞에 prepend 한다. 태그가 null 이거나 autoMemo=false 면 no-op.
     */
    public void applyDeliveryTagAutoMemo() {
        if (this.deliveryTag == null || !this.deliveryTag.isAutoMemo()) {
            return;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM/dd");
        String autoLine = String.format("[%s] %s 상차 %s 하차",
                this.deliveryTag.getDisplayName(),
                this.slipDate.format(fmt),
                this.slipDate.plusDays(1).format(fmt));
        this.memo = (this.memo == null || this.memo.isBlank())
                ? autoLine
                : autoLine + " | " + this.memo;
    }

    /**
     * 현재 상태가 편집 가능한 단계(DRAFT/SAVED) 인지 여부 — 서비스 레이어 가드 헬퍼.
     *
     * @return true 면 editHeader / addLine / removeLine / editLine 허용
     */
    public boolean isEditable() {
        return EDITABLE_STATUSES.contains(this.status);
    }

    /**
     * 라인 수정/추가/삭제가 가능한지 가드 — 불가능하면 즉시 CONFLICT 던짐.
     *
     * @throws BusinessException(CONFLICT) 현재 상태가 DRAFT/SAVED 가 아닐 때
     */
    public void requireEditable() {
        if (!isEditable()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "라인 수정 가능한 상태가 아닙니다: " + this.status);
        }
    }

    private void requireStatus(SlipStatus expected) {
        if (this.status != expected) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "전이 가능한 상태가 아닙니다: 현재 " + this.status + ", 필요 " + expected);
        }
    }
}
