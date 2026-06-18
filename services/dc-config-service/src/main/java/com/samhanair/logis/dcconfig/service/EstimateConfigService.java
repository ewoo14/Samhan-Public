package com.samhanair.logis.dcconfig.service;

import com.samhanair.logis.dcconfig.domain.EstimateConfig;
import com.samhanair.logis.dcconfig.dto.UpdateEstimateConfigRequest;
import com.samhanair.logis.dcconfig.repository.EstimateConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 종합견적서 전역 가격 파라미터 조회/수정 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EstimateConfigService {

    private final EstimateConfigRepository estimateConfigRepository;

    @Transactional
    public EstimateConfig getOrSeedDefault() {
        return estimateConfigRepository.findFirstBySingletonKeyTrueOrderByCreatedAtAsc()
                .orElseGet(() -> estimateConfigRepository.save(EstimateConfig.defaults()));
    }

    @Transactional
    public EstimateConfig update(UpdateEstimateConfigRequest request) {
        EstimateConfig config = getOrSeedDefault();
        config.update(
                request.commonHomeDiscountRate(),
                request.commonCommercialDiscountRate(),
                request.oldProductDiscountRate(),
                request.vatRate(),
                request.cardFeeRate(),
                request.advanceDiscountRate(),
                request.comboWarnRate(),
                request.footerNotice());
        return config;
    }
}
