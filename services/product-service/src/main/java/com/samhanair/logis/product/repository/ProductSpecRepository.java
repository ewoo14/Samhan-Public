package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.ProductSpec;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** ProductSpec CRUD + productId/displayOrder 정렬 + specKey 중복 검출. */
public interface ProductSpecRepository extends JpaRepository<ProductSpec, UUID> {

    List<ProductSpec> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    Optional<ProductSpec> findByProductIdAndSpecKey(UUID productId, String specKey);

    boolean existsByProductIdAndSpecKey(UUID productId, String specKey);
}
