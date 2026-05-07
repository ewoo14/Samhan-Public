package com.samhanair.logis.dashboard.service;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import com.samhanair.logis.dashboard.client.AccountingClient;
import com.samhanair.logis.dashboard.client.PartnerOrderClient;
import com.samhanair.logis.dashboard.domain.AggregateInterval;
import com.samhanair.logis.dashboard.domain.SalesAggregate;
import com.samhanair.logis.dashboard.repository.SalesAggregateRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매출 집계 service — Phase 9 W4.
 *
 * <p>accounting-service + partner-order-service 데이터를 일별 / 거래처별로 집계한 row 를 보유.
 * interval (DAILY / WEEKLY / MONTHLY) 은 query 시점 group-by 단위로만 사용 (DB 자체는 일별 row).
 */
@Service
@RequiredArgsConstructor
public class SalesAggregateService {

    private final SalesAggregateRepository repository;
    private final AccountingClient accountingClient;
    private final PartnerOrderClient partnerOrderClient;

    /**
     * 일자 범위 + (선택) 거래처 + interval 필터 조회.
     *
     * @param from 시작 일자
     * @param to 종료 일자
     * @param interval 집계 단위 (현 슬라이스 row 자체는 일별, interval 정보는 응답 metadata 용)
     * @param partnerId 거래처 UUID (nullable)
     * @return 매칭 row (aggregateDate ASC)
     */
    @Transactional(readOnly = true)
    public List<SalesAggregate> findAggregates(LocalDate from, LocalDate to, AggregateInterval interval,
                                                UUID partnerId) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from / to 필수");
        }
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "from 이 to 보다 이후일 수 없음");
        }
        if (interval == null) {
            interval = AggregateInterval.DAILY;
        }
        if (partnerId == null) {
            return repository.findAllByAggregateDateBetweenOrderByAggregateDateAsc(from, to);
        }
        return repository.findAllByPartnerIdAndAggregateDateBetweenOrderByAggregateDateAsc(partnerId, from, to);
    }

    /**
     * accounting-service + partner-order-service 호출 → upsert 1행. fail-soft 시 ZERO/0 으로 적재.
     */
    @Transactional
    public SalesAggregate aggregateOne(LocalDate aggregateDate, UUID partnerId) {
        if (aggregateDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "aggregateDate 필수");
        }
        if (partnerId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "partnerId 필수");
        }
        BigDecimal amount = accountingClient.sumSalesByPartner(partnerId, aggregateDate, aggregateDate);
        int itemCount = partnerOrderClient.countOrdersByPartner(partnerId, aggregateDate, aggregateDate);

        return repository.findFirstByAggregateDateAndPartnerId(aggregateDate, partnerId)
                .map(existing -> {
                    existing.update(amount, itemCount);
                    return existing;
                })
                .orElseGet(() -> repository.save(SalesAggregate.of(aggregateDate, partnerId, amount, itemCount)));
    }
}
