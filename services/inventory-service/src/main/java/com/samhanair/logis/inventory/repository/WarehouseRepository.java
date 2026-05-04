package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.Warehouse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Soft-delete 는 {@link Warehouse @SQLRestriction} 으로 엔티티 레벨에서 처리한다. */
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findAllByIsDeletedFalseOrderByDisplayOrderAsc();

    boolean existsByCodeAndIsDeletedFalse(String code);

    Optional<Warehouse> findByCode(String code);
}
