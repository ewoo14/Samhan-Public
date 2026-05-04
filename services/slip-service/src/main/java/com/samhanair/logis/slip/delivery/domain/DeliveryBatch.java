package com.samhanair.logis.slip.delivery.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.domain.Slip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 배송 배치 (Slice B notification-slice-B Plan §3.2) — 같은 driverPhone + batchDate 의 출고 슬립
 * N건을 단일 토큰으로 묶어 기사에게 SMS 1건으로 발송하기 위한 그룹.
 *
 * <p>그룹 키: {@code (driverPhone, batchDate)} — Plan §5.2 partial unique index 강제.
 * 기사 1명이 같은 날 받을 SMS 는 항상 1건이며 batchToken 으로 모바일 페이지 진입.
 *
 * <p>토큰: base64url 64자 ({@link SecureRandom} 48 bytes). UUID 와 다른 형식이라
 * memory {@code feedback_uuid_no_user_visibility.md} 의 UUID 비공개 가드와 무관.
 *
 * <p>라이프사이클 (Plan §3.3 Layer 4):
 * <pre>
 *   create(driver, date, slips) — initial, batchToken 생성, tokenExpiresAt = batchDate + 1일
 *   markSmsSent()                — Solapi 호출 성공 후, smsSentAt = now()
 *   markSmsFailed(error)         — Solapi 호출 실패 후, smsLastError 기록
 *   addSlip(slip)                — slip.deliveryBatchId 갱신 (양방향 연관관계 유지)
 *   removeSlip(slip)             — slip.deliveryBatchId = null
 * </pre>
 *
 * <p>본 entity 는 Slip 과 단일 단방향 관계 — Slip.deliveryBatchId UUID FK 만 보유 (양방향
 * OneToMany 미정의, fetch 비용 회피). batch 내 slip 목록은 SlipRepository 쿼리로 별도 조회.
 */
@Entity
@Getter
@Table(name = "delivery_batches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class DeliveryBatch extends BaseEntity {

    private static final SecureRandom RNG = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 48;  // base64url(48 bytes) = 64자
    private static final int TOKEN_EXPIRY_DAYS = 1;   // Plan N5: 배송일 +1일 자동 만료

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "batch_token", nullable = false, length = 64, unique = true)
    private String batchToken;

    @Column(name = "driver_name", nullable = false, length = 50)
    private String driverName;

    @Column(name = "driver_phone", nullable = false, length = 20)
    private String driverPhone;

    @Column(name = "batch_date", nullable = false)
    private LocalDate batchDate;

    @Column(name = "token_expires_at", nullable = false)
    private LocalDateTime tokenExpiresAt;

    @Column(name = "sms_sent_at")
    private LocalDateTime smsSentAt;

    @Column(name = "sms_last_error", length = 500)
    private String smsLastError;

    private DeliveryBatch(String batchToken, String driverName, String driverPhone,
                          LocalDate batchDate, LocalDateTime tokenExpiresAt) {
        this.batchToken = batchToken;
        this.driverName = driverName;
        this.driverPhone = driverPhone;
        this.batchDate = batchDate;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    /**
     * 배치 생성 + 슬립 N건 자동 연결. batchToken 자동 발급 (base64url 64자).
     * tokenExpiresAt = batchDate + 1일 23:59:59 (배송일 끝 + 1일 여유).
     *
     * @param driverName 기사명 (필수)
     * @param driverPhone 기사 연락처 (필수)
     * @param batchDate 배송일 (필수)
     * @param slips 묶을 슬립 목록 (각 slip.deliveryBatchId 갱신됨, null/empty 허용)
     * @return 신규 DeliveryBatch (id 는 save 후 채번)
     * @throws IllegalArgumentException driverName/driverPhone/batchDate 중 하나라도 null/blank
     */
    public static DeliveryBatch create(String driverName, String driverPhone,
                                       LocalDate batchDate, List<Slip> slips) {
        if (driverName == null || driverName.isBlank()) {
            throw new IllegalArgumentException("driverName 은 필수입니다");
        }
        if (driverPhone == null || driverPhone.isBlank()) {
            throw new IllegalArgumentException("driverPhone 은 필수입니다");
        }
        if (batchDate == null) {
            throw new IllegalArgumentException("batchDate 는 필수입니다");
        }
        String token = generateToken();
        LocalDateTime expires = batchDate.plusDays(TOKEN_EXPIRY_DAYS).atTime(23, 59, 59);
        DeliveryBatch batch = new DeliveryBatch(token, driverName, driverPhone, batchDate, expires);
        if (slips != null) {
            for (Slip slip : slips) {
                batch.addSlip(slip);
            }
        }
        return batch;
    }

    /**
     * SMS 발송 성공 기록 — Solapi 호출이 성공한 직후 서비스 레이어에서 호출.
     * {@code smsSentAt} 을 현재 시각으로 설정 + {@code smsLastError} 를 null 로 클리어.
     *
     * @throws BusinessException(CONFLICT) 이미 발송 완료 상태일 때 (재발송은 별도 정책)
     */
    public void markSmsSent() {
        if (this.smsSentAt != null) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 SMS 발송 완료된 배치입니다 — 재발송은 reset 후 진행하세요");
        }
        this.smsSentAt = LocalDateTime.now();
        this.smsLastError = null;
    }

    /**
     * SMS 발송 실패 기록 — Solapi 호출이 예외를 던진 직후 서비스 레이어에서 호출.
     * {@code smsSentAt} 은 null 유지 (재시도는 사용자 재클릭).
     *
     * @param error Solapi 응답 에러 메시지 (최대 500자, 초과 시 자동 truncate)
     */
    public void markSmsFailed(String error) {
        if (error != null && error.length() > 500) {
            error = error.substring(0, 500);
        }
        this.smsLastError = error;
    }

    /**
     * 토큰 재발급 — 만료/유출 시 관리자 수동 호출. {@code smsSentAt} 도 null 로 reset 하여
     * 재발송 가능 상태로 전환. 새 만료 시각은 batchDate + 1일.
     */
    public void regenerateToken() {
        this.batchToken = generateToken();
        this.tokenExpiresAt = this.batchDate.plusDays(TOKEN_EXPIRY_DAYS).atTime(23, 59, 59);
        this.smsSentAt = null;
        this.smsLastError = null;
    }

    /**
     * 슬립 1건 추가 — 양방향 연관관계 유지 (slip.deliveryBatchId 갱신).
     *
     * @param slip 추가할 슬립 (이미 다른 배치 소속이면 그 배치의 removeSlip 을 먼저 호출해야 함)
     */
    public void addSlip(Slip slip) {
        if (slip == null) {
            return;
        }
        slip.assignToBatch(this.id);
    }

    /**
     * 슬립 1건 제거 — slip.deliveryBatchId 를 null 로 클리어.
     *
     * @param slip 제거할 슬립
     */
    public void removeSlip(Slip slip) {
        if (slip == null) {
            return;
        }
        slip.clearBatch();
    }

    /**
     * 토큰이 만료되었는지 검증 — 공개 endpoint 진입 시 410 GONE 응답 결정에 사용.
     *
     * @return true 이면 만료, false 이면 유효
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.tokenExpiresAt);
    }

    /** SMS 발송 완료 여부. */
    public boolean isSent() {
        return this.smsSentAt != null;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
