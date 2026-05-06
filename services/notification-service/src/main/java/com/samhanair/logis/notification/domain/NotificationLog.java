package com.samhanair.logis.notification.domain;

import com.samhanair.logis.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

/**
 * 발송 이력 — 게이트웨이 호출 1회당 1 row (재시도 시 attempt_no 증가).
 *
 * <p>{@link NotificationRequest} 와 N:1 관계. attempt_no 는 1-base, request 별 unique.
 * gateway_status 는 게이트웨이 응답 코드 ({@code SUCCESS} / {@code FAILURE_<code>}), gateway_response 는
 * raw response (디버깅 용).
 */
@Entity
@Getter
@Table(name = "notification_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "request_id", nullable = false, updatable = false)
    private NotificationRequest request;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private NotificationChannel channel;

    @Column(name = "attempt_no", nullable = false, updatable = false)
    private int attemptNo;

    @Column(name = "gateway_status", nullable = false, length = 50, updatable = false)
    private String gatewayStatus;

    @Column(name = "gateway_message_id", length = 200, updatable = false)
    private String gatewayMessageId;

    @Column(name = "gateway_response", columnDefinition = "TEXT", updatable = false)
    private String gatewayResponse;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    private NotificationLog(NotificationRequest request, int attemptNo, String gatewayStatus,
                            String gatewayMessageId, String gatewayResponse) {
        this.request = request;
        this.channel = request.getChannel();
        this.attemptNo = attemptNo;
        this.gatewayStatus = gatewayStatus;
        this.gatewayMessageId = gatewayMessageId;
        this.gatewayResponse = gatewayResponse;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 발송 결과 1건 기록. attempt_no 는 caller 가 결정 (request.attemptCount 와 일치 의무).
     */
    public static NotificationLog record(NotificationRequest request, int attemptNo, String gatewayStatus,
                                         String gatewayMessageId, String gatewayResponse) {
        return new NotificationLog(request, attemptNo, gatewayStatus, gatewayMessageId, gatewayResponse);
    }
}
