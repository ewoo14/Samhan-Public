package com.samhanair.logis.product.repository;

import com.samhanair.logis.product.domain.Product;
import com.samhanair.logis.product.domain.ProductStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Soft-delete 는 {@link Product @SQLRestriction} 으로 엔티티 레벨에서 처리한다.
 * tag 검색은 PostgreSQL {@code jsonb @>} 연산자를 native query 로 사용한다.
 */
public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByModelNameAndIsDeletedFalse(String modelName);

    /**
     * 모델명 정확 매칭 단건 조회 (대소문자 구분, 삭제되지 않은 제품만).
     * Slip 출력 슬라이스의 {@code POST /products/internal/lookup-by-model} 및
     * {@code GET /products/by-model/{modelName}} 에서 사용. modelName 컬럼은
     * partial unique index 가 걸려 있어 단건 보장.
     *
     * @param modelName 정확히 일치할 제품 모델명 (예: {@code AJ040RXH4BC1})
     * @return 일치 제품 Optional. 없으면 {@link Optional#empty()}
     */
    Optional<Product> findByModelNameAndIsDeletedFalse(String modelName);

    List<Product> findAllByIdIn(Collection<UUID> ids);

    Page<Product> findAllByCategory_Id(UUID categoryId, Pageable pageable);

    Page<Product> findAllByStatus(ProductStatus status, Pageable pageable);

    Page<Product> findAllByCategory_IdAndStatus(UUID categoryId, ProductStatus status, Pageable pageable);

    /**
     * 자유 텍스트 검색 (name / model_name LIKE) + 선택적 카테고리/상태/태그 필터를
     * 단일 native 쿼리로 합쳐 처리. {@code :tagFilter} 는 jsonb 형태의 문자열
     * (예: '{"hp":"1.5"}') 또는 NULL.
     */
    @Query(value = """
            SELECT * FROM products p
            WHERE p.is_deleted = false
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:status      IS NULL OR p.status     = :status)
              AND (:q           IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                                        OR LOWER(p.model_name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (CAST(:tagFilter AS text) IS NULL OR p.tags @> CAST(:tagFilter AS jsonb))
            """,
           countQuery = """
            SELECT COUNT(*) FROM products p
            WHERE p.is_deleted = false
              AND (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:status      IS NULL OR p.status     = :status)
              AND (:q           IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                                        OR LOWER(p.model_name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (CAST(:tagFilter AS text) IS NULL OR p.tags @> CAST(:tagFilter AS jsonb))
            """,
           nativeQuery = true)
    Page<Product> search(@Param("categoryId") UUID categoryId,
                         @Param("status") String status,
                         @Param("q") String q,
                         @Param("tagFilter") String tagFilter,
                         Pageable pageable);
}
