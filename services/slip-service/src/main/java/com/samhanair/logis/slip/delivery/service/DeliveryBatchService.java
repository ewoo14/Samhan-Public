package com.samhanair.logis.slip.delivery.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.delivery.domain.DeliveryBatch;
import com.samhanair.logis.slip.delivery.repository.DeliveryBatchRepository;
import com.samhanair.logis.slip.delivery.sms.SmsGateway;
import com.samhanair.logis.slip.delivery.sms.SmsResult;
import com.samhanair.logis.slip.delivery.web.dto.DeliveryBatchResponse;
import com.samhanair.logis.slip.delivery.web.dto.PublicBatchResponse;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.repository.SlipRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeliveryBatch 워크플로 (Plan §3.3 + §4.1 + §4.2).
 *
 * <ul>
 *   <li>{@code autoGroupByDate(date)} — 같은 driverPhone + batchDate 슬립을 자동 그룹</li>
 *   <li>{@code sendSms(batchId)} — Solapi 호출 + markSmsSent/Failed 기록</li>
 *   <li>{@code addSlip / removeSlip} — 관리자 수동 분리/병합</li>
 *   <li>{@code regenerateToken(id)} — 토큰 재발급 + smsSentAt reset</li>
 *   <li>{@code findByToken(token)} — 공개 모바일 페이지 read-only lookup (만료 410)</li>
 * </ul>
 *
 * <p>SMS 본문 포맷 (Plan §1):
 * {@code "[삼한공조] 오늘 배송 N건: {publicBaseUrl}/d/{token}"}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DeliveryBatchService {

    private final DeliveryBatchRepository batchRepository;
    private final SlipRepository slipRepository;
    private final SmsGateway smsGateway;

    /** 모바일 공개 페이지 base URL — application.yml {@code app.public.base-url} 키. */
    @Value("${app.public.base-url:https://sign.samhan-air.com}")
    private String publicBaseUrl;

    /**
     * 자동 그룹화 — 해당 날짜의 driverPhone 별로 슬립을 묶어 신규/기존 배치에 연결한다.
     * 이미 같은 (driverPhone, batchDate) 활성 배치가 존재하면 재사용, 없으면 신규 생성.
     * 이미 deliveryBatchId 가 채워진 슬립은 스킵 (관리자가 수동 분리한 슬립 보존).
     *
     * @param date 배송일 (필수)
     * @return 그룹화 후의 배치 목록 응답 (드라이버명 ASC)
     */
    public List<DeliveryBatchResponse> autoGroupByDate(LocalDate date) {
        if (date == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "date 는 필수입니다");
        }
        List<Slip> candidates = slipRepository
                .findAllBySlipDateAndDriverPhoneIsNotNullAndIsDeletedFalse(date);

        // driverPhone 별 group by — LinkedHashMap 으로 stable order
        Map<String, List<Slip>> byPhone = candidates.stream()
                .filter(s -> s.getDriverPhone() != null && !s.getDriverPhone().isBlank())
                .collect(Collectors.groupingBy(
                        Slip::getDriverPhone,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (Map.Entry<String, List<Slip>> entry : byPhone.entrySet()) {
            String phone = entry.getKey();
            List<Slip> slipsForDriver = entry.getValue();
            String driverName = slipsForDriver.stream()
                    .map(Slip::getDriverName)
                    .filter(n -> n != null && !n.isBlank())
                    .findFirst()
                    .orElse("기사");

            DeliveryBatch batch = batchRepository
                    .findByDriverPhoneAndBatchDate(phone, date)
                    .orElseGet(() -> batchRepository.save(
                            DeliveryBatch.create(driverName, phone, date, List.of())));

            for (Slip slip : slipsForDriver) {
                if (slip.getDeliveryBatchId() == null) {
                    batch.addSlip(slip);
                }
            }
        }

        return list(date, null);
    }

    /**
     * 배치 목록 조회 — 링크발송 화면 source.
     *
     * @param date 배송일 (필수)
     * @param sentFilter null=전체, true=발송완료만, false=미발송만
     * @return 배치 응답 목록 (driverName ASC)
     */
    @Transactional(readOnly = true)
    public List<DeliveryBatchResponse> list(LocalDate date, Boolean sentFilter) {
        return batchRepository.findByBatchDateWithSentFilter(date, sentFilter).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    /** 배치 단건 상세 조회 (admin). */
    @Transactional(readOnly = true)
    public DeliveryBatchResponse getOne(UUID id) {
        DeliveryBatch batch = loadOrThrow(id);
        return toAdminResponse(batch);
    }

    /**
     * SMS 발송 트리거 — 관리자 수동 클릭 (Plan N1). Solapi 호출 후 결과에 따라
     * {@link DeliveryBatch#markSmsSent} 또는 {@link DeliveryBatch#markSmsFailed} 호출.
     *
     * @param id 배치 UUID
     * @return 갱신된 배치 응답
     * @throws BusinessException(NOT_FOUND) 배치 미발견
     * @throws BusinessException(CONFLICT) 이미 발송 완료, 또는 슬립이 0건일 때
     * @throws BusinessException(INTERNAL_ERROR) Solapi 호출 실패 (smsLastError 기록 후 던짐)
     */
    public DeliveryBatchResponse sendSms(UUID id) {
        DeliveryBatch batch = loadOrThrow(id);
        List<Slip> slips = slipRepository.findAllByDeliveryBatchIdAndIsDeletedFalse(id);
        if (slips.isEmpty()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "배치에 슬립이 없습니다 — 슬립 추가 후 재시도하세요");
        }
        if (batch.isSent()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 SMS 발송 완료된 배치입니다 — 재발송은 토큰 재발급 후 진행하세요");
        }

        String message = buildSmsBody(slips.size(), batch.getBatchToken());
        SmsResult result = smsGateway.sendSms(batch.getDriverPhone(), message);

        if (result.ok()) {
            batch.markSmsSent();
        } else {
            batch.markSmsFailed(result.errorMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "SMS 발송 실패: " + result.errorMessage());
        }
        return toAdminResponse(batch);
    }

    /** 슬립 1건 수동 추가 — 다른 배치에 속해있으면 그 배치에서 먼저 제거. */
    public DeliveryBatchResponse addSlip(UUID batchId, UUID slipId) {
        DeliveryBatch batch = loadOrThrow(batchId);
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "슬립을 찾을 수 없습니다"));
        // 다른 배치에 속해 있다면 그 배치에서 먼저 제거 (도메인 일관성)
        if (slip.getDeliveryBatchId() != null && !slip.getDeliveryBatchId().equals(batchId)) {
            DeliveryBatch previous = batchRepository.findById(slip.getDeliveryBatchId())
                    .orElse(null);
            if (previous != null) {
                previous.removeSlip(slip);
            }
        }
        batch.addSlip(slip);
        return toAdminResponse(batch);
    }

    /** 슬립 1건 수동 제거 — 슬립이 본 배치 소속이어야 함. */
    public DeliveryBatchResponse removeSlip(UUID batchId, UUID slipId) {
        DeliveryBatch batch = loadOrThrow(batchId);
        Slip slip = slipRepository.findById(slipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "슬립을 찾을 수 없습니다"));
        if (slip.getDeliveryBatchId() == null || !slip.getDeliveryBatchId().equals(batchId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "슬립이 본 배치에 속해있지 않습니다");
        }
        batch.removeSlip(slip);
        return toAdminResponse(batch);
    }

    /** 토큰 재발급 — 만료/유출 시 관리자 호출. smsSentAt 도 reset. */
    public DeliveryBatchResponse regenerateToken(UUID id) {
        DeliveryBatch batch = loadOrThrow(id);
        batch.regenerateToken();
        return toAdminResponse(batch);
    }

    /**
     * 공개 모바일 페이지 lookup — 토큰 단건 조회 + 만료 검증 (Plan §4.2).
     *
     * @param token batchToken (base64url 64자)
     * @return 공개 응답 (slip.id UUID 미노출)
     * @throws BusinessException(NOT_FOUND) 토큰 미발견 (404)
     * @throws BusinessException(CONFLICT) 토큰 만료 (Controller 가 410 으로 매핑)
     */
    @Transactional(readOnly = true)
    public PublicBatchResponse findByToken(String token) {
        DeliveryBatch batch = batchRepository.findByBatchToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "유효하지 않은 토큰입니다"));
        if (batch.isExpired()) {
            // Controller 측에서 410 GONE 으로 매핑
            throw new BusinessException(ErrorCode.CONFLICT, "토큰이 만료되었습니다");
        }
        List<Slip> slips = new ArrayList<>(slipRepository
                .findAllByDeliveryBatchIdAndIsDeletedFalse(batch.getId()));
        slips.sort(Comparator.comparing(Slip::getSlipNo));
        List<PublicBatchResponse.PublicSlipSummary> slipSummaries = slips.stream()
                .map(s -> new PublicBatchResponse.PublicSlipSummary(
                        s.getSlipNo(),
                        s.getPartnerName(),
                        s.getLines() == null ? 0 : s.getLines().size(),
                        s.getStatus().name()))
                .toList();
        return new PublicBatchResponse(batch.getDriverName(), batch.getBatchDate(), slipSummaries);
    }

    private DeliveryBatch loadOrThrow(UUID id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "배치를 찾을 수 없습니다"));
    }

    private DeliveryBatchResponse toAdminResponse(DeliveryBatch batch) {
        List<Slip> slips = slipRepository.findAllByDeliveryBatchIdAndIsDeletedFalse(batch.getId());
        List<String> slipNos = new ArrayList<>(slips.stream()
                .map(Slip::getSlipNo)
                .sorted()
                .toList());
        return DeliveryBatchResponse.of(batch, slipNos);
    }

    private String buildSmsBody(int slipCount, String token) {
        return String.format("[삼한공조] 오늘 배송 %d건: %s/d/%s",
                slipCount, publicBaseUrl, token);
    }
}
