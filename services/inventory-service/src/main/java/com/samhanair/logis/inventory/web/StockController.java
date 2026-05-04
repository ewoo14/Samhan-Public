package com.samhanair.logis.inventory.web;

import com.samhanair.logis.common.dto.ApiResponse;
import com.samhanair.logis.inventory.repository.StockBalanceRepository;
import com.samhanair.logis.inventory.repository.StockLotRepository;
import com.samhanair.logis.inventory.repository.StockMovementRepository;
import com.samhanair.logis.inventory.service.StockService;
import com.samhanair.logis.inventory.web.dto.AdjustRequest;
import com.samhanair.logis.inventory.web.dto.DeductRequest;
import com.samhanair.logis.inventory.web.dto.DeductionResponse;
import com.samhanair.logis.inventory.web.dto.InboundRequest;
import com.samhanair.logis.inventory.web.dto.ReleaseRequest;
import com.samhanair.logis.inventory.web.dto.ReservationResponse;
import com.samhanair.logis.inventory.web.dto.ReserveRequest;
import com.samhanair.logis.inventory.web.dto.StockBalanceResponse;
import com.samhanair.logis.inventory.web.dto.StockLotResponse;
import com.samhanair.logis.inventory.web.dto.StockMovementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 잔량/로트/이동 조회 + inbound/reserve/release/deduct/adjust mutation.
 *
 * <p>권한 매트릭스 (Plan §4 표):
 * <ul>
 *   <li>잔량/로트/이동 조회 — MASTER/MANAGER/DEVELOPER/WAREHOUSE/INVENTORY</li>
 *   <li>입고 (lots/inbound) — MASTER/MANAGER/WAREHOUSE/INVENTORY</li>
 *   <li>예약/해제/차감 (reserve/release/deduct) — MASTER/MANAGER/DEVELOPER/SALES/WAREHOUSE/INVENTORY</li>
 *   <li>조정 (adjust) — MASTER/MANAGER/INVENTORY</li>
 * </ul>
 */
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
public class StockController {

    private static final String CALLER_HEADER = "X-User-Id";

    private final StockService stockService;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockLotRepository stockLotRepository;
    private final StockMovementRepository stockMovementRepository;

    // -------- 조회 --------

    /**
     * 제품별 잔량 페이지 조회 — productId 필수, 모든 창고 잔량 페이지.
     *
     * @param productId 제품 UUID
     * @param page 0-based 페이지 번호
     * @param size 페이지 크기 (기본 20)
     * @return Page&lt;StockBalanceResponse&gt;
     */
    @Operation(summary = "재고 잔량 조회", description = "productId 의 모든 창고 잔량 페이지")
    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','WAREHOUSE','INVENTORY')")
    public ApiResponse<Page<StockBalanceResponse>> balances(
            @RequestParam UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(stockBalanceRepository
                .findAllByProductIdAndIsDeletedFalse(productId, pageable)
                .map(StockBalanceResponse::from));
    }

    /**
     * 로트 페이지 조회 — productId / warehouseId 옵션. 둘 다 없으면 전체.
     *
     * @param productId 필터 (선택)
     * @param warehouseId 필터 (선택)
     * @param page 0-based 페이지 번호
     * @param size 페이지 크기 (기본 20)
     * @return Page&lt;StockLotResponse&gt;
     */
    @Operation(summary = "로트 조회", description = "productId / warehouseId 조합 필터 페이지")
    @GetMapping("/lots")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','WAREHOUSE','INVENTORY')")
    public ApiResponse<Page<StockLotResponse>> lots(
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockLotResponse> result;
        if (productId != null && warehouseId != null) {
            result = stockLotRepository
                    .findAllByProductIdAndWarehouse_IdAndIsDeletedFalse(productId, warehouseId, pageable)
                    .map(StockLotResponse::from);
        } else if (productId != null) {
            result = stockLotRepository
                    .findAllByProductIdAndIsDeletedFalse(productId, pageable)
                    .map(StockLotResponse::from);
        } else if (warehouseId != null) {
            result = stockLotRepository
                    .findAllByWarehouse_IdAndIsDeletedFalse(warehouseId, pageable)
                    .map(StockLotResponse::from);
        } else {
            result = stockLotRepository.findAll(pageable).map(StockLotResponse::from);
        }
        return ApiResponse.ok(result);
    }

    /**
     * 이동 이력 페이지 조회 — lotId / productId / warehouseId 우선순위로 필터.
     *
     * @param lotId 가장 우선 (선택)
     * @param productId 차순위 (선택)
     * @param warehouseId 마지막 (선택)
     * @param page 0-based 페이지 번호
     * @param size 페이지 크기 (기본 20)
     * @return Page&lt;StockMovementResponse&gt; — occurredAt DESC
     */
    @Operation(summary = "이동 이력 조회", description = "occurredAt DESC. lot/product/warehouse 우선순위 필터")
    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','WAREHOUSE','INVENTORY')")
    public ApiResponse<Page<StockMovementResponse>> movements(
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockMovementResponse> result;
        if (lotId != null) {
            result = stockMovementRepository
                    .findAllByLotIdOrderByOccurredAtDesc(lotId, pageable)
                    .map(StockMovementResponse::from);
        } else if (productId != null) {
            result = stockMovementRepository
                    .findAllByProductIdOrderByOccurredAtDesc(productId, pageable)
                    .map(StockMovementResponse::from);
        } else if (warehouseId != null) {
            result = stockMovementRepository
                    .findAllByWarehouseIdOrderByOccurredAtDesc(warehouseId, pageable)
                    .map(StockMovementResponse::from);
        } else {
            result = stockMovementRepository.findAll(pageable).map(StockMovementResponse::from);
        }
        return ApiResponse.ok(result);
    }

    // -------- mutation --------

    /**
     * 입고 — 새 lot 생성 + balance 가산 + INBOUND movement 기록.
     *
     * @param request InboundRequest (productId/warehouseId/quantity/lotNo/receivedAt/unitCost/note)
     * @param callerHeader X-User-Id (감사용)
     * @return 생성된 StockLotResponse (201) / NOT_FOUND (404) / CONFLICT (409)
     */
    @Operation(summary = "재고 입고", description = "새 lot 생성 + balance 가산 + INBOUND movement 기록")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "입고 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "product/warehouse 미발견"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "낙관적 락 충돌")
    })
    @PostMapping("/lots/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','WAREHOUSE','INVENTORY')")
    public ApiResponse<StockLotResponse> inbound(
            @Valid @RequestBody InboundRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(stockService.inbound(request, callerOrSystem(callerHeader)));
    }

    /**
     * 예약 — availableQty → reservedQty 이동.
     *
     * @return ReservationResponse (200) / 가용 부족 시 CONFLICT (409)
     */
    @Operation(summary = "재고 예약", description = "availableQty 에서 reservedQty 로 이동")
    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','SALES','WAREHOUSE','INVENTORY')")
    public ApiResponse<ReservationResponse> reserve(
            @Valid @RequestBody ReserveRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(stockService.reserve(request, callerOrSystem(callerHeader)));
    }

    /**
     * 예약 해제 — reservedQty → availableQty 이동.
     *
     * @return ReservationResponse (200) / 예약 부족 시 CONFLICT (409)
     */
    @Operation(summary = "예약 해제", description = "reservedQty 에서 availableQty 로 되돌림")
    @PostMapping("/release")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','SALES','WAREHOUSE','INVENTORY')")
    public ApiResponse<ReservationResponse> release(
            @Valid @RequestBody ReleaseRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(stockService.release(request, callerOrSystem(callerHeader)));
    }

    /**
     * 출고 차감 — FIFO 로 가용 lot 차감 + balance.deduct + DEDUCT movement 기록.
     *
     * @return DeductionResponse (200) / 재고 부족 또는 version 충돌 시 CONFLICT (409)
     */
    @Operation(summary = "출고 차감", description = "FIFO 로 가용 lot 차감 (가장 오래된 lot 부터 소진)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "차감 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "재고 부족 또는 version 충돌")
    })
    @PostMapping("/deduct")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','DEVELOPER','SALES','WAREHOUSE','INVENTORY')")
    public ApiResponse<DeductionResponse> deduct(
            @Valid @RequestBody DeductRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(stockService.deduct(request, callerOrSystem(callerHeader)));
    }

    /**
     * 실사 조정 — delta 부호로 balance 가감 + ADJUST movement 기록.
     *
     * @return DeductionResponse (200) / 음수 결과 또는 version 충돌 시 CONFLICT (409)
     */
    @Operation(summary = "재고 조정", description = "실사 조정 — delta 부호로 balance 가감")
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('MASTER','MANAGER','INVENTORY')")
    public ApiResponse<DeductionResponse> adjust(
            @Valid @RequestBody AdjustRequest request,
            @RequestHeader(value = CALLER_HEADER, required = false) String callerHeader) {
        return ApiResponse.ok(stockService.adjust(request, callerOrSystem(callerHeader)));
    }

    private String callerOrSystem(String header) {
        return (header == null || header.isBlank()) ? "system" : header;
    }
}
