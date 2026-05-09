package com.samhanair.logis.notification.service;

import com.samhanair.logis.notification.client.BlockedPartnerLookupClient;
import com.samhanair.logis.notification.domain.NotificationChannel;
import com.samhanair.logis.notification.domain.NotificationRequest;
import com.samhanair.logis.notification.domain.RecipientType;
import com.samhanair.logis.notification.dto.DispatchBatchSendRequest;
import com.samhanair.logis.notification.dto.DispatchBatchSendRequest.SendEntry;
import com.samhanair.logis.notification.dto.DispatchBatchSendResponse;
import com.samhanair.logis.notification.dto.DispatchBatchSendResponse.SendResultDetail;
import com.samhanair.logis.notification.dto.NotificationSendRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배차안내 SMS 실 발송 서비스 — PR-E1 BE-4 (Samhan Public 이식).
 *
 * <p>preview 단계에서 운영자가 메시지를 검토 / 수정한 뒤 본 endpoint 를 호출. 단톡방 매핑은 이미
 * preview 시 적용되어 (partnerCode, message, recipientPhone) 쌍이 요청에 포함됨.
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>각 entry → BLOCKED 거래처 가드 재확인 (preview 와 send 사이 시점차).</li>
 *   <li>blocked 미해당 → {@link NotificationService#send} 위임 (NotificationRequest entity 저장 +
 *       SmsAdapter 호출 + 결과 누적).</li>
 *   <li>blocked 해당 → 발송 skip + blocked 카운트 증가.</li>
 *   <li>응답 = sent / failed / blocked 카운트 + 상세 결과.</li>
 * </ol>
 *
 * <p>채널 = SMS, recipientType = EXTERNAL_PHONE (단톡방 운영자 / 거래처 담당자 외부 번호).
 *
 * <p>장애 격리 — 1건 실패가 전체 배치를 중단하지 않도록 entry 단위 try/catch (failed 누적).
 *
 * <p>UUID 비공개 가드 — 본 서비스는 UUID 미사용 (partnerCode + recipientPhone 만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchBatchSendService {

    private final BlockedPartnerLookupClient blockedPartnerLookupClient;
    private final NotificationService notificationService;

    /**
     * 실 발송 — entry N건 일괄 처리.
     *
     * @param req 입력 (date + entries N건)
     * @return 결과 카운트 (sent / failed / blocked) + 상세
     */
    @Transactional
    public DispatchBatchSendResponse send(DispatchBatchSendRequest req) {
        if (req == null || req.date() == null || req.entries() == null) {
            throw new IllegalArgumentException("date / entries 필수");
        }

        int sent = 0;
        int failed = 0;
        int blocked = 0;
        List<SendResultDetail> details = new ArrayList<>(req.entries().size());

        for (SendEntry entry : req.entries()) {
            String partnerCode = entry.partnerCode();
            String phone = entry.recipientPhone();

            // (1) BLOCKED 가드 (preview 이후 신규 차단 가능성 회피)
            boolean isBlocked = false;
            try {
                isBlocked = blockedPartnerLookupClient.isBlocked(partnerCode);
            } catch (Exception ex) {
                log.warn("DispatchBatchSendService — blocked lookup 실패 partnerCode={}, msg={}",
                        partnerCode, ex.getMessage());
                // fail-soft = false (가드 누락 회피 — 운영자 의도 우선) — 이전 검사는 false 유지
            }
            if (isBlocked) {
                blocked++;
                details.add(new SendResultDetail(partnerCode, phone, "BLOCKED",
                        "발송금지 거래처 — 자동 제외"));
                continue;
            }

            // (2) SMS 발송 위임 (NotificationService → SmsAdapter)
            try {
                NotificationSendRequest payload = new NotificationSendRequest(
                        RecipientType.EXTERNAL_PHONE,
                        null,
                        phone,
                        NotificationChannel.SMS,
                        "DISPATCH_BATCH",
                        null,
                        entry.message(),
                        null);
                NotificationRequest result = notificationService.send(payload);
                if (result.getStatus().name().equals("SENT")) {
                    sent++;
                    details.add(new SendResultDetail(partnerCode, phone, "SENT", null));
                } else {
                    failed++;
                    details.add(new SendResultDetail(partnerCode, phone, "FAILED",
                            "게이트웨이 응답 status=" + result.getStatus()));
                }
            } catch (Exception ex) {
                failed++;
                log.warn("DispatchBatchSendService — entry 발송 실패 partnerCode={}, phone={}, msg={}",
                        partnerCode, phone, ex.getMessage());
                details.add(new SendResultDetail(partnerCode, phone, "FAILED", ex.getMessage()));
            }
        }

        log.info("DispatchBatchSendService — date={}, sent={}, failed={}, blocked={}",
                req.date(), sent, failed, blocked);
        return new DispatchBatchSendResponse(req.date(), sent, failed, blocked, details);
    }
}
