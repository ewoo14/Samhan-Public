package com.samhanair.logis.slip.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.domain.Slip;
import com.samhanair.logis.slip.domain.SlipLine;
import com.samhanair.logis.slip.domain.SlipStatus;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.web.dto.AddLineRequest;
import com.samhanair.logis.slip.web.dto.CreateSlipRequest;
import com.samhanair.logis.slip.web.dto.EditHeaderRequest;
import com.samhanair.logis.slip.web.dto.SlipDetailResponse;
import com.samhanair.logis.slip.web.dto.SlipResponse;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전표 워크플로우 (Plan §3.1 — 첫 슬라이스 출고/입고만).
 *
 * <p>Inventory 연계 (Q2 결정):
 * <ul>
 *   <li>OUTBOUND accept → {@code /inventory/reserve} 라인별 호출</li>
 *   <li>OUTBOUND complete → {@code /inventory/deduct} fromReservation=true 라인별 호출</li>
 *   <li>INBOUND complete → {@code /inventory/lots/inbound} 라인별 호출</li>
 *   <li>OUTBOUND reject/cancel after ACCEPTED → {@code /inventory/release} 라인별 호출</li>
 * </ul>
 *
 * <p>낙관적 락(@Version) 충돌은 OptimisticLockException → CONFLICT 매핑.
 *
 * <p>상태 전이는 {@link Slip} 도메인 메서드에 위임 — 위반은 모두 BusinessException(CONFLICT).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SlipService {

    private static final String SLIP_REF_TYPE = "SLIP";

    private final SlipRepository slipRepository;
    private final SlipNumberService slipNumberService;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    /**
     * 새 전표를 DRAFT 상태로 생성한다 — slipType 분기로 createOutbound/createInbound 호출,
     * ProductClient 로 라인 productId 일괄 검증, 라인 추가, applyDeliveryTagAutoMemo 자동 호출 후
     * SlipNumberService 로 채번.
     *
     * @param req 생성 요청 (slipType / slipDate / 창고 / 거래처 / 라인 등)
     * @param requesterId 요청자 user-id (gateway X-User-Id 또는 "system")
     * @return 생성된 전표의 상세 응답 (라인 포함, status=DRAFT)
     * @throws BusinessException(INVALID_INPUT) 라인 productId 중 product-service 미존재 또는 입력 불량
     * @throws BusinessException(INTERNAL_ERROR) product-service 호출 실패
     * @throws IllegalArgumentException 출고전표 sourceWarehouseId null 또는 입고전표 destinationWarehouseId null
     */
    public SlipDetailResponse create(CreateSlipRequest req, String requesterId) {
        // 1. 라인 productId 일괄 검증 + lookup map 빌드 (snapshot 보강)
        List<UUID> productIds = req.lines().stream()
                .map(CreateSlipRequest.SlipLineRequest::productId)
                .distinct()
                .toList();
        List<ProductSummary> summaries = productClient.lookup(productIds);
        Map<UUID, ProductSummary> byId = new HashMap<>();
        for (ProductSummary s : summaries) {
            byId.put(s.id(), s);
        }

        // 2. 채번 (slipDate null 이면 today)
        LocalDate slipDate = req.slipDate() == null ? LocalDate.now() : req.slipDate();
        String slipNo = slipNumberService.next(slipDate);
        int seqNo = slipNumberService.extractSeqNo(slipNo);

        // 3. 헤더 생성
        Slip slip;
        if (req.slipType() == SlipType.OUTBOUND) {
            slip = Slip.createOutbound(slipNo, slipDate, seqNo,
                    req.sourceWarehouseId(), req.destinationWarehouseId(),
                    req.partnerId(), req.partnerName(),
                    req.deliveryTag(), req.memo(), requesterId);
        } else {
            slip = Slip.createInbound(slipNo, slipDate, seqNo,
                    req.destinationWarehouseId(),
                    req.partnerId(), req.partnerName(),
                    req.deliveryTag(), req.memo(), requesterId);
        }

        // 4. 라인 추가 (snapshot 명칭은 요청값 우선, 없으면 ProductSummary 보강)
        for (CreateSlipRequest.SlipLineRequest lineReq : req.lines()) {
            ProductSummary summary = byId.get(lineReq.productId());
            String productName = lineReq.productName() != null
                    ? lineReq.productName()
                    : (summary != null ? summary.name() : null);
            String modelName = lineReq.modelName() != null
                    ? lineReq.modelName()
                    : (summary != null ? summary.modelName() : null);
            slip.addLine(SlipLine.create(slip, lineReq.productId(),
                    productName, modelName,
                    lineReq.quantity(), lineReq.unitPrice(), lineReq.note()));
        }

        // 5. 자동 메모 (야적/지방 등)
        slip.applyDeliveryTagAutoMemo();

        Slip saved = slipRepository.save(slip);
        return SlipDetailResponse.from(saved);
    }

    /**
     * 헤더 부분 수정 — DRAFT/SAVED 단계만. 도메인 메서드가 가드.
     *
     * @param id 전표 ID
     * @param req 수정 요청 (null 필드는 보존)
     * @param callerId 호출자 user-id (감사용, 도메인에는 전달 안 함)
     * @return 갱신된 상세 응답
     * @throws BusinessException(NOT_FOUND) 전표 미발견
     * @throws BusinessException(CONFLICT) 현재 상태가 DRAFT/SAVED 가 아닐 때
     */
    public SlipDetailResponse editHeader(UUID id, EditHeaderRequest req, String callerId) {
        Slip slip = loadOrThrow(id);
        applyMutation(() -> slip.editHeader(req.partnerId(), req.partnerName(),
                req.deliveryTag(), req.memo()));
        return SlipDetailResponse.from(slip);
    }

    /**
     * 라인 1건 추가 — DRAFT/SAVED 단계만. ProductClient 로 productId 검증 후 추가.
     *
     * @param id 전표 ID
     * @param req 라인 요청
     * @param callerId 호출자 user-id (감사용)
     * @return 갱신된 상세 응답
     * @throws BusinessException(NOT_FOUND) 전표 미발견 또는 productId 미존재
     * @throws BusinessException(CONFLICT) DRAFT/SAVED 가 아닐 때
     */
    public SlipDetailResponse addLine(UUID id, AddLineRequest req, String callerId) {
        Slip slip = loadOrThrow(id);
        slip.requireEditable();
        ProductSummary summary = productClient.requireExists(req.productId());
        String productName = req.productName() != null ? req.productName() : summary.name();
        String modelName = req.modelName() != null ? req.modelName() : summary.modelName();
        applyMutation(() -> slip.addLine(SlipLine.create(slip, req.productId(),
                productName, modelName,
                req.quantity(), req.unitPrice(), req.note())));
        return SlipDetailResponse.from(slip);
    }

    /**
     * 라인 1건 제거 — DRAFT/SAVED 단계만. orphan removal 로 DB 에서도 제거.
     *
     * @param id 전표 ID
     * @param lineId 제거할 라인 ID
     * @param callerId 호출자 user-id
     * @throws BusinessException(NOT_FOUND) 전표/라인 미발견
     * @throws BusinessException(CONFLICT) DRAFT/SAVED 가 아닐 때
     */
    public void removeLine(UUID id, UUID lineId, String callerId) {
        Slip slip = loadOrThrow(id);
        slip.requireEditable();
        SlipLine line = slip.getLines().stream()
                .filter(l -> l.getId() != null && l.getId().equals(lineId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "라인을 찾을 수 없습니다"));
        applyMutation(() -> slip.removeLine(line));
    }

    /** 작성중 → 저장완료. */
    public SlipDetailResponse save(UUID id, String callerId) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::save);
        return SlipDetailResponse.from(slip);
    }

    /** 저장완료 → 전송완료. */
    public SlipDetailResponse send(UUID id) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::send);
        return SlipDetailResponse.from(slip);
    }

    /**
     * 수락 — SENT → ACCEPTED. OUTBOUND 면 라인별 inventoryClient.reserve 호출.
     *
     * @throws BusinessException(CONFLICT) 상태 불일치, 재고 부족, 또는 낙관적 락 충돌
     * @throws BusinessException(INTERNAL_ERROR) inventory-service 호출 실패
     */
    public SlipDetailResponse accept(UUID id, String acceptorUserId) {
        Slip slip = loadOrThrow(id);
        applyMutation(() -> slip.accept(acceptorUserId));
        if (slip.getSlipType() == SlipType.OUTBOUND) {
            for (SlipLine line : slip.getLines()) {
                inventoryClient.reserve(line.getProductId(), slip.getSourceWarehouseId(),
                        line.getQuantity(), SLIP_REF_TYPE, slip.getId());
            }
        }
        return SlipDetailResponse.from(slip);
    }

    /** 수락 → 처리중. */
    public SlipDetailResponse process(UUID id) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::process);
        return SlipDetailResponse.from(slip);
    }

    /**
     * 처리완료 — PROCESSING → COMPLETED. OUTBOUND 면 라인별 deduct(fromReservation=true),
     * INBOUND 면 라인별 inbound 호출.
     *
     * @throws BusinessException(CONFLICT) 상태 불일치, 재고 부족
     * @throws BusinessException(INTERNAL_ERROR) inventory-service 호출 실패
     */
    public SlipDetailResponse complete(UUID id) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::complete);
        if (slip.getSlipType() == SlipType.OUTBOUND) {
            for (SlipLine line : slip.getLines()) {
                inventoryClient.deduct(line.getProductId(), slip.getSourceWarehouseId(),
                        line.getQuantity(), true, SLIP_REF_TYPE, slip.getId());
            }
        } else {
            for (SlipLine line : slip.getLines()) {
                inventoryClient.inbound(line.getProductId(), slip.getDestinationWarehouseId(),
                        line.getQuantity(), slip.getSlipNo(), line.getUnitPrice());
            }
        }
        return SlipDetailResponse.from(slip);
    }

    /** 처리완료 → 배송중 (OUTBOUND 한정). */
    public SlipDetailResponse ship(UUID id) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::ship);
        return SlipDetailResponse.from(slip);
    }

    /** 배송중 → 배송완료 (OUTBOUND 한정). */
    public SlipDetailResponse deliver(UUID id) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::deliver);
        return SlipDetailResponse.from(slip);
    }

    /** 확정 — 출고는 DELIVERED→CONFIRMED, 입고는 COMPLETED→CONFIRMED. */
    public SlipDetailResponse confirm(UUID id, String callerId) {
        Slip slip = loadOrThrow(id);
        applyMutation(slip::confirm);
        return SlipDetailResponse.from(slip);
    }

    /**
     * 반려 — SENT/ACCEPTED → REJECTED. 직전 상태가 ACCEPTED 였고 OUTBOUND 면 inventory release.
     *
     * @param id 전표 ID
     * @param callerId 호출자 user-id
     * @param reasonText 반려 사유 (memo 앞에 prepend)
     * @return 갱신된 상세 응답
     * @throws BusinessException(NOT_FOUND) 전표 미발견
     * @throws BusinessException(CONFLICT) 현재 상태가 SENT/ACCEPTED 둘 다 아닐 때
     * @throws BusinessException(INTERNAL_ERROR) inventory release 호출 실패
     */
    public SlipDetailResponse reject(UUID id, String callerId, String reasonText) {
        Slip slip = loadOrThrow(id);
        SlipStatus previous = slip.getStatus();
        applyMutation(() -> slip.reject(reasonText));
        if (previous == SlipStatus.ACCEPTED && slip.getSlipType() == SlipType.OUTBOUND) {
            for (SlipLine line : slip.getLines()) {
                inventoryClient.release(line.getProductId(), slip.getSourceWarehouseId(),
                        line.getQuantity(), SLIP_REF_TYPE, slip.getId());
            }
        }
        return SlipDetailResponse.from(slip);
    }

    /**
     * 취소 — DRAFT/SAVED/SENT → CANCELED. ACCEPTED 단계는 cancel 불가 (도메인 가드 — 현 슬라이스 정책).
     * 단, 이전 spec 명시 "release if ACCEPTED" 를 지키기 위해 reject 와 동일한 release 분기 포함하지만,
     * 도메인이 ACCEPTED 단계 cancel 을 거부하므로 사실상 reject 경로로만 release 트리거됨.
     *
     * @param id 전표 ID
     * @param callerId 호출자 user-id
     * @return 갱신된 상세 응답
     * @throws BusinessException(NOT_FOUND) 전표 미발견
     * @throws BusinessException(CONFLICT) 현재 상태가 취소 가능 단계 밖일 때
     */
    public SlipDetailResponse cancel(UUID id, String callerId) {
        Slip slip = loadOrThrow(id);
        SlipStatus previous = slip.getStatus();
        applyMutation(slip::cancel);
        if (previous == SlipStatus.ACCEPTED && slip.getSlipType() == SlipType.OUTBOUND) {
            for (SlipLine line : slip.getLines()) {
                inventoryClient.release(line.getProductId(), slip.getSourceWarehouseId(),
                        line.getQuantity(), SLIP_REF_TYPE, slip.getId());
            }
        }
        return SlipDetailResponse.from(slip);
    }

    /**
     * 단건 조회 — 라인 포함 상세.
     *
     * @param id 전표 ID
     * @return 상세 응답
     * @throws BusinessException(NOT_FOUND) 전표 미발견
     */
    @Transactional(readOnly = true)
    public SlipDetailResponse getOne(UUID id) {
        return SlipDetailResponse.from(loadOrThrow(id));
    }

    /**
     * 페이지 조회 — slipType, status 필터 (둘 다 null 이면 전체).
     *
     * @param slipType 필터 (null 가능)
     * @param status 필터 (null 가능)
     * @param pageable 페이지 정보
     * @return 요약 응답 페이지
     */
    @Transactional(readOnly = true)
    public Page<SlipResponse> list(SlipType slipType, SlipStatus status, Pageable pageable) {
        Page<Slip> page;
        if (slipType != null && status != null) {
            page = slipRepository.findAllBySlipTypeAndStatusAndIsDeletedFalse(slipType, status, pageable);
        } else if (slipType != null) {
            page = slipRepository.findAllBySlipTypeAndIsDeletedFalse(slipType, pageable);
        } else if (status != null) {
            page = slipRepository.findAllByStatusAndIsDeletedFalse(status, pageable);
        } else {
            page = slipRepository.findAllByIsDeletedFalse(pageable);
        }
        return page.map(SlipResponse::from);
    }

    private Slip loadOrThrow(UUID id) {
        return slipRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "전표를 찾을 수 없습니다"));
    }

    /**
     * 도메인 mutation 실행 — IllegalState/IllegalArgument 를 BusinessException 으로 매핑하고
     * OptimisticLock 충돌은 그대로 CONFLICT 로 변환.
     */
    private void applyMutation(Runnable mutation) {
        try {
            mutation.run();
        } catch (BusinessException ex) {
            throw ex;
        } catch (OptimisticLockException | OptimisticLockingFailureException ex) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "전표 동시 수정 충돌 — 새로고침 후 재시도하세요");
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, ex.getMessage());
        }
    }
}
