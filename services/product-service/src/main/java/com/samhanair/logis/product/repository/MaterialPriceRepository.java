package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.MaterialPrice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** MaterialPrice CRUD + materialKey 기반 조회 (D4/D7/D8). */
public interface MaterialPriceRepository extends JpaRepository<MaterialPrice, UUID> {

    Optional<MaterialPrice> findByMaterialKey(String materialKey);
}
