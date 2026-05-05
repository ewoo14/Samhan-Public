package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.BundleComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** BundleComponent CRUD + 부모 BUNDLE 기준 component 라인 조회. */
public interface BundleComponentRepository extends JpaRepository<BundleComponent, UUID> {

    List<BundleComponent> findByBundleProductId(UUID bundleProductId);

    List<BundleComponent> findByComponentProductCode(String componentProductCode);
}
