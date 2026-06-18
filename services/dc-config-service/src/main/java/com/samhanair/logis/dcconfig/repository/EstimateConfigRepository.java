package com.samhanair.logis.dcconfig.repository;

import com.samhanair.logis.dcconfig.domain.EstimateConfig;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** 종합견적서 전역 가격 파라미터 싱글톤 repository. */
public interface EstimateConfigRepository extends JpaRepository<EstimateConfig, UUID> {

    Optional<EstimateConfig> findFirstBySingletonKeyTrueOrderByCreatedAtAsc();
}
