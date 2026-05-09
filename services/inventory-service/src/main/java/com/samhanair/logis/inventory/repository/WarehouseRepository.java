package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.Warehouse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Soft-delete 는 {@link Warehouse @SQLRestriction} 으로 엔티티 레벨에서 처리한다. */
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findAllByIsDeletedFalseOrderByDisplayOrderAsc();

    boolean existsByCodeAndIsDeletedFalse(String code);

    Optional<Warehouse> findByCode(String code);

    /**
     * Phase 10 P0-5 — admin 창고 페이지 조회 (q 필터).
     *
     * <p>q 는 code / name / address LIKE (대소문자 무시). null/blank 시 필터 미적용.
     * is_deleted=false 활성 행만 반환 (entity {@code @SQLRestriction} 의존).
     */
    @Query("SELECT w FROM Warehouse w WHERE "
            + "(:q IS NULL "
            + " OR LOWER(w.code) LIKE LOWER(CONCAT('%', :q, '%')) "
            + " OR LOWER(w.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + " OR LOWER(COALESCE(w.address, '')) LIKE LOWER(CONCAT('%', :q, '%')) )")
    Page<Warehouse> searchAdmin(@Param("q") String q, Pageable pageable);
}
