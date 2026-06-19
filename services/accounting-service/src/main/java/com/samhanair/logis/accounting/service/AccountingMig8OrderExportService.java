package com.samhanair.logis.accounting.service;

import com.samhanair.logis.accounting.web.dto.Mig8OrderExportResponse;
import com.samhanair.logis.accounting.web.dto.Mig8OrderLineExportResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MIG-8 이관 주문을 partner-order-service 로 넘기는 내부 export 조회 service. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountingMig8OrderExportService {

    public static final String MIG8_ORDER_KIND = "ECOUNT_MIG8";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Page<Mig8OrderExportResponse> exportMig8Orders(Pageable pageable) {
        MapSqlParameterSource pageParams = new MapSqlParameterSource()
                .addValue("kind", MIG8_ORDER_KIND)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                  FROM orders
                 WHERE is_deleted = FALSE
                   AND kind = :kind
                """, pageParams, Long.class);
        List<OrderRow> orders = jdbcTemplate.query("""
                SELECT id, order_no, partner_id, partner_name, manager_name, progress_status,
                       valid_until, payment_terms, reference, total_supply_amount, total_vat_amount,
                       linked_slip_no, external_ref
                  FROM orders
                 WHERE is_deleted = FALSE
                   AND kind = :kind
                 ORDER BY order_no ASC, id ASC
                 LIMIT :limit OFFSET :offset
                """, pageParams, this::mapOrder);
        if (orders.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        Map<UUID, List<Mig8OrderLineExportResponse>> linesByOrderId = linesByOrderId(orders);
        List<Mig8OrderExportResponse> content = orders.stream()
                .map(order -> order.toResponse(linesByOrderId.getOrDefault(order.id(), List.of())))
                .toList();
        return new PageImpl<>(content, pageable, total);
    }

    private Map<UUID, List<Mig8OrderLineExportResponse>> linesByOrderId(List<OrderRow> orders) {
        List<UUID> orderIds = orders.stream().map(OrderRow::id).toList();
        Map<UUID, List<Mig8OrderLineExportResponse>> linesByOrderId = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT order_id, line_no, product_id, item_name, quantity, unit_price,
                       supply_amount, vat_amount, item_due_date
                  FROM order_lines
                 WHERE is_deleted = FALSE
                   AND order_id IN (:orderIds)
                 ORDER BY order_id ASC, line_no ASC
                """, new MapSqlParameterSource("orderIds", orderIds), rs -> {
                    UUID orderId = getUuid(rs, "order_id");
                    linesByOrderId.computeIfAbsent(orderId, ignored -> new ArrayList<>())
                            .add(mapLine(rs));
                });
        return linesByOrderId;
    }

    private OrderRow mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new OrderRow(
                getUuid(rs, "id"),
                rs.getString("order_no"),
                getUuid(rs, "partner_id"),
                rs.getString("partner_name"),
                rs.getString("manager_name"),
                rs.getString("progress_status"),
                getLocalDate(rs, "valid_until"),
                rs.getString("payment_terms"),
                rs.getString("reference"),
                rs.getBigDecimal("total_supply_amount"),
                rs.getBigDecimal("total_vat_amount"),
                rs.getString("linked_slip_no"),
                rs.getString("external_ref"));
    }

    private Mig8OrderLineExportResponse mapLine(ResultSet rs) throws SQLException {
        return new Mig8OrderLineExportResponse(
                rs.getInt("line_no"),
                getUuid(rs, "product_id"),
                rs.getString("item_name"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("supply_amount"),
                rs.getBigDecimal("vat_amount"),
                getLocalDate(rs, "item_due_date"));
    }

    private static UUID getUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : (UUID) value;
    }

    private static LocalDate getLocalDate(ResultSet rs, String column) throws SQLException {
        java.sql.Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }

    private record OrderRow(
            UUID id,
            String orderNo,
            UUID partnerId,
            String partnerName,
            String managerName,
            String progressStatus,
            LocalDate validUntil,
            String paymentTerms,
            String reference,
            java.math.BigDecimal totalSupplyAmount,
            java.math.BigDecimal totalVatAmount,
            String linkedSlipNo,
            String externalRef
    ) {
        Mig8OrderExportResponse toResponse(List<Mig8OrderLineExportResponse> lines) {
            return new Mig8OrderExportResponse(
                    orderNo,
                    partnerId,
                    partnerName,
                    managerName,
                    progressStatus,
                    validUntil,
                    paymentTerms,
                    reference,
                    totalSupplyAmount,
                    totalVatAmount,
                    linkedSlipNo,
                    externalRef,
                    lines);
        }
    }
}
